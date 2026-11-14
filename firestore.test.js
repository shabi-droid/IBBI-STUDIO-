const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";
const MINOR_UID = "minor_789";
const ADMIN_UID = "admin_001";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function pastTimestamp() {
  return new Date(Date.now() - 5000);
}

function validUserPayload(uid, overrides = {}) {
  const now = pastTimestamp();
  return {
    userId: uid,
    email: `${uid}@example.com`,
    name: "Alice Test",
    dateOfBirth: "2000-05-15",
    age: 26,
    isAdult: true,
    currentActivity: "Studying",
    studyField: "Computer Science",
    hobbies: ["Reading", "Coffee"],
    passwordHash: "a".repeat(64),
    passwordSalt: "b".repeat(24),
    termsAccepted: true,
    profileCompleted: true,
    messageCount: 0,
    signupDate: now,
    lastLoginAt: now,
    updatedAt: now,
    ...overrides,
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user cannot read or list users or moderationQueue", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("moderationQueue").get());
});

test("Authenticated user can create and read own profile, cannot read/list others", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set(validUserPayload(ALICE_UID))
  );
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).get());

  // Cross-user PII blanket test
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").get());
});

test("Shadow update with ghost field or inconsistent isAdult is rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .set(validUserPayload(ALICE_UID, { isAdmin: true }))
  );
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .set(validUserPayload(ALICE_UID, { age: 16, isAdult: true }))
  );
});

test("Minor user cannot create partner session, but can create friend session", async () => {
  const minorDb = testEnv.authenticatedContext(MINOR_UID).firestore();
  await assertSucceeds(
    minorDb
      .collection("users")
      .doc(MINOR_UID)
      .set(
        validUserPayload(MINOR_UID, {
          dateOfBirth: "2010-03-10",
          age: 16,
          isAdult: false,
        })
      )
  );

  const now = pastTimestamp();
  await assertFails(
    minorDb
      .collection("users")
      .doc(MINOR_UID)
      .collection("sessions")
      .doc("sess_partner")
      .set({
        userId: MINOR_UID,
        personaId: "partner",
        personaName: "Elena",
        lastMessagePreview: "",
        messageCount: 0,
        createdAt: now,
        updatedAt: now,
      })
  );

  await assertSucceeds(
    minorDb
      .collection("users")
      .doc(MINOR_UID)
      .collection("sessions")
      .doc("sess_friend")
      .set({
        userId: MINOR_UID,
        personaId: "friend",
        personaName: "Maya",
        lastMessagePreview: "",
        messageCount: 0,
        createdAt: now,
        updatedAt: now,
      })
  );
});

test("Adult user can create partner session and messages", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set(validUserPayload(ALICE_UID))
  );

  const now = pastTimestamp();
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("sessions")
      .doc("sess_partner")
      .set({
        userId: ALICE_UID,
        personaId: "partner",
        personaName: "Elena",
        lastMessagePreview: "",
        messageCount: 0,
        createdAt: now,
        updatedAt: now,
      })
  );

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("sessions")
      .doc("sess_partner")
      .collection("messages")
      .doc("msg_1")
      .set({
        userId: ALICE_UID,
        sessionId: "sess_partner",
        personaId: "partner",
        sender: "user",
        text: "Hey Elena!",
        inputMode: "text",
        groundingSources: [],
        flagged: false,
        createdAt: now,
      })
  );
});

test("User can submit pending moderation flag but cannot read or update moderationQueue", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = pastTimestamp();
  await assertSucceeds(
    aliceDb.collection("moderationQueue").doc("flag_1").set({
      userId: ALICE_UID,
      userEmail: "alice@example.com",
      userName: "Alice",
      personaId: "friend",
      sessionId: "sess_1",
      messageText: "hurt myself",
      matchedKeywords: ["hurt myself"],
      severity: "high",
      status: "pending",
      reviewedBy: "",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertFails(aliceDb.collection("moderationQueue").get());
  await assertFails(
    aliceDb
      .collection("moderationQueue")
      .doc("flag_1")
      .update({ status: "dismissed", reviewedBy: "alice", updatedAt: now })
  );
});

test("Email spoofing with email_verified=false is rejected for admin access", async () => {
  const spoofedAdminDb = testEnv
    .authenticatedContext("spoof_1", {
      email: "gzainab500@gmail.com",
      email_verified: false,
    })
    .firestore();

  await assertFails(spoofedAdminDb.collection("users").get());
  await assertFails(spoofedAdminDb.collection("moderationQueue").get());
});

test("Verified admin can list users and review moderationQueue", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = pastTimestamp();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set(validUserPayload(ALICE_UID))
  );
  await assertSucceeds(
    aliceDb.collection("moderationQueue").doc("flag_1").set({
      userId: ALICE_UID,
      userEmail: "alice@example.com",
      userName: "Alice",
      personaId: "friend",
      sessionId: "sess_1",
      messageText: "hurt myself",
      matchedKeywords: ["hurt myself"],
      severity: "high",
      status: "pending",
      reviewedBy: "",
      createdAt: now,
      updatedAt: now,
    })
  );

  const adminDb = testEnv
    .authenticatedContext(ADMIN_UID, {
      email: "gzainab500@gmail.com",
      email_verified: true,
    })
    .firestore();

  await assertSucceeds(adminDb.collection("users").get());
  await assertSucceeds(adminDb.collection("moderationQueue").get());
  await assertSucceeds(
    adminDb
      .collection("moderationQueue")
      .doc("flag_1")
      .update({
        status: "reviewed",
        reviewedBy: "gzainab500@gmail.com",
        updatedAt: pastTimestamp(),
      })
  );
});
