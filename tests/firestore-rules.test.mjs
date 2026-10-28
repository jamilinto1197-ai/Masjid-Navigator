import { readFile } from "node:fs/promises";
import path from "node:path";
import {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds
} from "@firebase/rules-unit-testing";
import {
  collection,
  collectionGroup,
  deleteDoc,
  doc,
  getDoc,
  getDocs,
  serverTimestamp,
  setDoc,
  writeBatch
} from "firebase/firestore";

const rulesPath = path.resolve(process.cwd(), "firestore.rules");
const rules = await readFile(rulesPath, "utf8");

async function submitMessage(db, uid, mosqueId, messageId, message) {
  const batch = writeBatch(db);
  batch.set(doc(db, "mosques", mosqueId, "messages", messageId), message);
  batch.set(doc(db, "messageRateLimits", uid), {
    lastMessageAt: serverTimestamp(),
    lastMessageId: messageId
  });
  return batch.commit();
}

const env = await initializeTestEnvironment({
  projectId: "demo-masjidph",
  firestore: { rules }
});

let failed = false;
async function test(name, operation) {
  try {
    await operation();
    process.stdout.write("PASS " + name + "\n");
  } catch (error) {
    failed = true;
    process.stderr.write("FAIL " + name + ": " + (error?.stack || error) + "\n");
  }
}

try {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async context => {
    await setDoc(doc(context.firestore(), "mosques", "test-room"), {
      name: "Test Masjid",
      city: "Test City",
      province: "Test Province"
    });
  });

  const anonymous = env.unauthenticatedContext().firestore();
  const alice = env.authenticatedContext("alice", { email_verified: true }).firestore();
  const bob = env.authenticatedContext("bob", { email_verified: true }).firestore();
  const charlie = env.authenticatedContext("charlie", { email_verified: true }).firestore();
  const ghost = env.authenticatedContext("ghost", { email_verified: true }).firestore();
  const burst = env.authenticatedContext("burst", { email_verified: true }).firestore();
  const admin = env.authenticatedContext("moderator", { email_verified: true, admin: true }).firestore();
  const unverified = env.authenticatedContext("unverified", { email_verified: false }).firestore();
  const unverifiedAdmin = env.authenticatedContext("unverified-admin", { email_verified: false, admin: true }).firestore();

  await test("mosque directory is publicly readable", async () => {
    await assertSucceeds(getDocs(collection(anonymous, "mosques")));
  });

  await test("clients cannot create or overwrite mosque directory records", async () => {
    await assertFails(setDoc(doc(alice, "mosques", "forged"), { name: "Fake Masjid" }));
    await assertFails(setDoc(doc(alice, "mosques", "test-room"), { name: "Tampered" }));
  });

  const validProfile = {
    displayName: "Alice",
    location: "Tubod, Lanao del Norte",
    bio: "Community member",
    updatedAt: Date.now()
  };

  await test("a member can create and read their own valid profile", async () => {
    await assertSucceeds(setDoc(doc(alice, "users", "alice"), validProfile));
    await assertSucceeds(getDoc(doc(alice, "users", "alice")));
  });

  await test("a member cannot read another member's profile", async () => {
    await assertFails(getDoc(doc(alice, "users", "bob")));
  });

  await test("profile writes reject overlong names and unknown fields", async () => {
    await assertFails(setDoc(doc(alice, "users", "alice"), {
      ...validProfile,
      displayName: "x".repeat(61)
    }));
    await assertFails(setDoc(doc(alice, "users", "alice"), {
      ...validProfile,
      isAdmin: true
    }));
  });

  const validSettings = {
    updatedAt: new Date().toISOString(),
    alarmSettings: {
      prayers: { Fajr: true, Dhuhr: true, Asr: true, Maghrib: true, Isha: true },
      tune: "adhan",
      offset: 0,
      suhoor: false,
      iftar: false,
      ramadanOnly: true,
      suhoorOffset: 45
    }
  };

  await test("a member can write their own valid prayer settings", async () => {
    await assertSucceeds(setDoc(doc(alice, "userSettings", "alice"), validSettings));
  });

  await test("legacy web settings can initialize with an empty alarm map", async () => {
    await assertSucceeds(setDoc(doc(alice, "userSettings", "alice"), {
      alarmSettings: {},
      updatedAt: new Date().toISOString()
    }));
  });

  await test("partial legacy prayer preferences are accepted only with valid values", async () => {
    await assertSucceeds(setDoc(doc(alice, "userSettings", "alice"), {
      alarmSettings: { prayers: { Fajr: false }, offset: -5 },
      updatedAt: new Date().toISOString()
    }));
  });

  await test("alarm preferences reject unknown keys and invalid types", async () => {
    await assertFails(setDoc(doc(alice, "userSettings", "alice"), {
      alarmSettings: { ...validSettings.alarmSettings, hiddenAdmin: true },
      updatedAt: new Date().toISOString()
    }));
    await assertFails(setDoc(doc(alice, "userSettings", "alice"), {
      alarmSettings: { prayers: { Fajr: "off" } },
      updatedAt: new Date().toISOString()
    }));
  });

  await test("prayer settings reject unsupported alarm values", async () => {
    await assertFails(setDoc(doc(alice, "userSettings", "alice"), {
      ...validSettings,
      alarmSettings: { ...validSettings.alarmSettings, tune: "run-command" }
    }));
  });

  await test("only signed-in members can read community messages", async () => {
    await assertFails(getDocs(collection(anonymous, "mosques", "test-room", "messages")));
  });

  await test("unverified password accounts cannot read or post community messages or read profiles", async () => {
    await assertFails(getDocs(collection(unverified, "mosques", "test-room", "messages")));
    await assertFails(setDoc(doc(unverified, "mosques", "test-room", "messages", "unverified-message"), {
      authorUid: "unverified",
      authorName: "Unverified",
      text: "This write should be rejected",
      createdAt: serverTimestamp()
    }));
    await assertFails(getDoc(doc(unverified, "users", "unverified")));
  });

  await test("a signed-in member can post a well-formed message as themselves", async () => {
    await assertSucceeds(submitMessage(alice, "alice", "test-room", "message-1", {
      authorUid: "alice",
      authorName: "Alice",
      text: "Assalamu alaikum",
      createdAt: serverTimestamp()
    }));
    await assertFails(submitMessage(alice, "alice", "test-room", "message-2-too-soon", {
      authorUid: "alice",
      authorName: "Alice",
      text: "This message is too soon",
      createdAt: serverTimestamp()
    }));
  });

  await test("a single atomic batch cannot bypass the one-message cooldown", async () => {
    const batch = writeBatch(burst);
    batch.set(doc(burst, "mosques", "test-room", "messages", "burst-one"), {
      authorUid: "burst",
      authorName: "Burst",
      text: "First message",
      createdAt: serverTimestamp()
    });
    batch.set(doc(burst, "mosques", "test-room", "messages", "burst-two"), {
      authorUid: "burst",
      authorName: "Burst",
      text: "Second message in the same batch",
      createdAt: serverTimestamp()
    });
    batch.set(doc(burst, "messageRateLimits", "burst"), {
      lastMessageAt: serverTimestamp(),
      lastMessageId: "burst-one"
    });
    await assertFails(batch.commit());
  });

  await test("members cannot create chat rooms beneath non-existent mosques", async () => {
    await assertFails(submitMessage(ghost, "ghost", "does-not-exist", "ghost-message", {
      authorUid: "ghost",
      authorName: "Ghost",
      text: "This must be denied",
      createdAt: serverTimestamp()
    }));
  });

  await test("a signed-in member can read messages from an existing room", async () => {
    await assertSucceeds(getDocs(collection(alice, "mosques", "test-room", "messages")));
  });

  await test("members cannot report their own message", async () => {
    await assertFails(setDoc(doc(alice, "mosques", "test-room", "reports", "alice_message-1"), {
      reporterUid: "alice",
      messageId: "message-1",
      reason: "Spam",
      createdAt: serverTimestamp()
    }));
  });

  await test("reports reject invalid reasons and non-existent message IDs", async () => {
    await assertFails(setDoc(doc(bob, "mosques", "test-room", "reports", "bob_bad-reason"), {
      reporterUid: "bob",
      messageId: "message-1",
      reason: "Run command",
      createdAt: serverTimestamp()
    }));
    await assertFails(setDoc(doc(bob, "mosques", "test-room", "reports", "bob_missing-message"), {
      reporterUid: "bob",
      messageId: "does-not-exist",
      reason: "Other",
      createdAt: serverTimestamp()
    }));
  });

  await test("a member cannot list profiles or read another member's prayer settings", async () => {
    await assertFails(getDocs(collection(alice, "users")));
    await assertFails(getDoc(doc(alice, "userSettings", "bob")));
  });

  await test("anonymous and cross-user writes to private settings are denied", async () => {
    await assertFails(setDoc(doc(anonymous, "users", "anonymous"), validProfile));
    await assertFails(setDoc(doc(alice, "userSettings", "bob"), validSettings));
  });

  await test("messages cannot be edited after submission", async () => {
    await assertFails(setDoc(doc(alice, "mosques", "test-room", "messages", "message-1"), {
      authorUid: "alice",
      authorName: "Alice",
      text: "Edited message",
      createdAt: serverTimestamp()
    }));
  });

  await test("message writes cannot impersonate another user or exceed limits", async () => {
    await assertFails(submitMessage(bob, "bob", "test-room", "spoof", {
      authorUid: "alice",
      authorName: "Alice",
      text: "Impersonation attempt",
      createdAt: serverTimestamp()
    }));
    await assertFails(submitMessage(bob, "bob", "test-room", "too-long", {
      authorUid: "bob",
      authorName: "Bob",
      text: "x".repeat(1001),
      createdAt: serverTimestamp()
    }));
  });

  await test("a member can report another user's message", async () => {
    await assertSucceeds(setDoc(doc(bob, "mosques", "test-room", "reports", "bob_message-1"), {
      reporterUid: "bob",
      messageId: "message-1",
      reason: "Spam",
      createdAt: serverTimestamp()
    }));
  });

  await test("only a verified admin can read reports including across mosque rooms", async () => {
    await assertFails(getDocs(collection(alice, "mosques", "test-room", "reports")));
    await assertFails(getDocs(collectionGroup(alice, "reports")));
    await assertSucceeds(getDocs(collection(admin, "mosques", "test-room", "reports")));
    await assertSucceeds(getDocs(collectionGroup(admin, "reports")));
    await assertFails(getDocs(collectionGroup(unverifiedAdmin, "reports")));
  });

  await test("members without an admin claim cannot delete reports", async () => {
    await assertFails(deleteDoc(doc(alice, "mosques", "test-room", "reports", "bob_message-1")));
  });

  await test("reports are private and a user cannot file a duplicate report", async () => {
    await assertFails(getDocs(collection(alice, "mosques", "test-room", "reports")));
    await assertFails(setDoc(doc(bob, "mosques", "test-room", "reports", "bob_message-1"), {
      reporterUid: "bob",
      messageId: "message-1",
      reason: "Other",
      createdAt: serverTimestamp()
    }));
  });

  await test("a user can delete their own message but not another user's message", async () => {
    await assertFails(deleteDoc(doc(bob, "mosques", "test-room", "messages", "message-1")));
    await assertSucceeds(deleteDoc(doc(alice, "mosques", "test-room", "messages", "message-1")));
  });

  await test("a verified admin can delete another member's message and close a report", async () => {
    await assertSucceeds(submitMessage(charlie, "charlie", "test-room", "admin-delete-me", {
      authorUid: "charlie",
      authorName: "Charlie",
      text: "Inappropriate content",
      createdAt: serverTimestamp()
    }));
    await assertSucceeds(setDoc(doc(bob, "mosques", "test-room", "reports", "bob_admin-delete-me"), {
      reporterUid: "bob",
      messageId: "admin-delete-me",
      reason: "Abuse or harassment",
      createdAt: serverTimestamp()
    }));
    await assertFails(deleteDoc(doc(unverifiedAdmin, "mosques", "test-room", "messages", "admin-delete-me")));
    await assertSucceeds(deleteDoc(doc(admin, "mosques", "test-room", "messages", "admin-delete-me")));
    await assertSucceeds(deleteDoc(doc(admin, "mosques", "test-room", "reports", "bob_admin-delete-me")));
  });
} finally {
  await env.cleanup();
}

if (failed) process.exitCode = 1;
