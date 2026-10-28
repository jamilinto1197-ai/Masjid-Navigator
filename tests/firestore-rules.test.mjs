import { readFile } from "node:fs/promises";
import path from "node:path";
import {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds
} from "@firebase/rules-unit-testing";
import {
  collection,
  deleteDoc,
  doc,
  getDoc,
  getDocs,
  serverTimestamp,
  setDoc
} from "firebase/firestore";

const rulesPath = path.resolve(process.cwd(), "firestore.rules");
const rules = await readFile(rulesPath, "utf8");
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
  const alice = env.authenticatedContext("alice").firestore();
  const bob = env.authenticatedContext("bob").firestore();

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

  await test("prayer settings reject unsupported alarm values", async () => {
    await assertFails(setDoc(doc(alice, "userSettings", "alice"), {
      ...validSettings,
      alarmSettings: { ...validSettings.alarmSettings, tune: "run-command" }
    }));
  });

  await test("only signed-in members can read community messages", async () => {
    await assertFails(getDocs(collection(anonymous, "mosques", "test-room", "messages")));
  });

  await test("a signed-in member can post a well-formed message as themselves", async () => {
    await assertSucceeds(setDoc(doc(alice, "mosques", "test-room", "messages", "message-1"), {
      authorUid: "alice",
      authorName: "Alice",
      text: "Assalamu alaikum",
      createdAt: serverTimestamp()
    }));
  });

  await test("members cannot create chat rooms beneath non-existent mosques", async () => {
    await assertFails(setDoc(doc(alice, "mosques", "does-not-exist", "messages", "ghost"), {
      authorUid: "alice",
      authorName: "Alice",
      text: "This must be denied",
      createdAt: serverTimestamp()
    }));
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
    await assertFails(setDoc(doc(bob, "mosques", "test-room", "messages", "spoof"), {
      authorUid: "alice",
      authorName: "Alice",
      text: "Impersonation attempt",
      createdAt: serverTimestamp()
    }));
    await assertFails(setDoc(doc(bob, "mosques", "test-room", "messages", "too-long"), {
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
} finally {
  await env.cleanup();
}

if (failed) process.exitCode = 1;
