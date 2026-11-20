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
const DATABASE_ID = "ai-studio-android-eduflown-a6bee6e7-cae2-4f23-a85a-77dbd7638aae";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

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

test("Unauthenticated user cannot read or write subjects or notes", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore(DATABASE_ID);
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/subjects`).get());
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/notes`).get());
  await assertFails(
    unauthDb.collection(`users/${ALICE_UID}/subjects`).doc("subj_1").set({
      userId: ALICE_UID,
      name: "Biology",
      colorTag: "ROSE",
      iconName: "SCIENCE",
      createdAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("Owner can create, list, partially update, and delete subjects", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore(DATABASE_ID);
  const now = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/subjects`).doc("subj_bio").set({
      userId: ALICE_UID,
      name: "Cell Biology",
      colorTag: "ROSE",
      iconName: "SCIENCE",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(aliceDb.collection(`users/${ALICE_UID}/subjects`).get());

  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/subjects`).doc("subj_bio").update({
      name: "AP Biology",
      colorTag: "BERRY",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject ghost field on create
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/subjects`).doc("subj_bad").set({
      id: "subj_bad",
      userId: ALICE_UID,
      name: "Chemistry",
      colorTag: "ROSE",
      iconName: "SCIENCE",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Reject invalid color enum
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/subjects`).doc("subj_bad_color").set({
      userId: ALICE_UID,
      name: "Chemistry",
      colorTag: "GREEN",
      iconName: "SCIENCE",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Reject mutating immutable createdAt
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/subjects`).doc("subj_bio").update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  await assertSucceeds(aliceDb.collection(`users/${ALICE_UID}/subjects`).doc("subj_bio").delete());
});

test("Owner can create, update highlights/definitions, and query notes when subject exists", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore(DATABASE_ID);
  const now = new Date(Date.now() - 5000);

  // Cannot create orphaned note without subject
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/notes`).doc("note_orphan").set({
      userId: ALICE_UID,
      subjectId: "missing_subj",
      title: "Orphan Note",
      extractedText: "Mitochondria is the powerhouse of the cell.",
      photoTag: "Ch 2 Page 14",
      thumbnailBase64: "",
      highlights: ["Mitochondria is the powerhouse of the cell."],
      definitions: [{ id: "d1", term: "Mitochondria", definition: "Organelle producing ATP", highlightColor: "ROSE" }],
      createdAt: now,
      updatedAt: now,
    })
  );

  // Create subject first
  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/subjects`).doc("subj_bio").set({
      userId: ALICE_UID,
      name: "Biology",
      colorTag: "ROSE",
      iconName: "SCIENCE",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Create note matching Kotlin StudyNote.toCreateMap()
  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/notes`).doc("note_1").set({
      userId: ALICE_UID,
      subjectId: "subj_bio",
      title: "Cellular Respiration",
      extractedText: "Mitochondria generate most of the cell's supply of ATP.",
      photoTag: "Bio Ch.4 p.12",
      thumbnailBase64: "",
      highlights: ["Mitochondria generate most of the cell's supply of ATP."],
      definitions: [{ id: "def_1", term: "Mitochondria", definition: "Organelle generating ATP.", highlightColor: "ROSE" }],
      createdAt: now,
      updatedAt: now,
    })
  );

  // Partial update on note
  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/notes`).doc("note_1").update({
      extractedText: "Mitochondria generate ATP via oxidative phosphorylation.",
      highlights: ["Mitochondria generate ATP via oxidative phosphorylation."],
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // List notes matching repository query
  await assertSucceeds(aliceDb.collection(`users/${ALICE_UID}/notes`).get());

  // Reject future tampered timestamp
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/notes`).doc("note_future").set({
      userId: ALICE_UID,
      subjectId: "subj_bio",
      title: "Future Note",
      extractedText: "Text",
      photoTag: "",
      thumbnailBase64: "",
      highlights: [],
      definitions: [],
      createdAt: new Date(Date.now() + 60000),
      updatedAt: new Date(Date.now() + 60000),
    })
  );
});

test("Cross-user isolation blocks Bob from reading or modifying Alice's notes and subjects", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore(DATABASE_ID);
    const now = new Date(Date.now() - 10000);
    await db.collection(`users/${ALICE_UID}/subjects`).doc("subj_bio").set({
      userId: ALICE_UID,
      name: "Biology",
      colorTag: "ROSE",
      iconName: "SCIENCE",
      createdAt: now,
      updatedAt: now,
    });
    await db.collection(`users/${ALICE_UID}/notes`).doc("note_1").set({
      userId: ALICE_UID,
      subjectId: "subj_bio",
      title: "Private Note",
      extractedText: "Secret study summary",
      photoTag: "",
      thumbnailBase64: "",
      highlights: [],
      definitions: [],
      createdAt: now,
      updatedAt: now,
    });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore(DATABASE_ID);
  await assertFails(bobDb.collection(`users/${ALICE_UID}/subjects`).get());
  await assertFails(bobDb.collection(`users/${ALICE_UID}/notes`).get());
  await assertFails(bobDb.collection(`users/${ALICE_UID}/notes`).doc("note_1").get());
  await assertFails(
    bobDb.collection(`users/${ALICE_UID}/notes`).doc("note_1").update({
      title: "Hijacked",
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(bobDb.collection(`users/${ALICE_UID}/notes`).doc("note_1").delete());
});
