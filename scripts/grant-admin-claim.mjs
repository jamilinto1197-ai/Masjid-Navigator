import { cert, getApps, initializeApp } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";
import { readFileSync } from "node:fs";

const email = process.argv[2];
const serviceAccountPath = process.env.GOOGLE_APPLICATION_CREDENTIALS;

if (!email || !serviceAccountPath) {
  console.error("Usage: GOOGLE_APPLICATION_CREDENTIALS=/secure/service-account.json npm run grant-admin -- jamilinto@gmail.com");
  process.exit(1);
}

const serviceAccount = JSON.parse(readFileSync(serviceAccountPath, "utf8"));
if (getApps().length === 0) initializeApp({ credential: cert(serviceAccount) });

const user = await getAuth().getUserByEmail(email);
await getAuth().setCustomUserClaims(user.uid, { ...(user.customClaims || {}), admin: true });
console.log(`Granted admin claim to ${email}. The user must sign out and sign in again, or force-refresh their ID token.`);
