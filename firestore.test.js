const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const GUEST_UID = "guest_anon_100";
const ALICE_UID = "alice_real_123";
const BOB_UID = "bob_real_456";
const MODERATOR_UID = "mod_user_777";
const ADMIN_UID = "admin_user_888";
const OWNER_UID = "owner_user_999";

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

async function seedRolesAndSampleImage() {
  const now = new Date(Date.now() - 10000);
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await db.collection("adminUsers").doc(OWNER_UID).set({
      role: "owner",
      active: true,
      createdAt: now,
      updatedAt: now,
    });
    await db.collection("adminUsers").doc(ADMIN_UID).set({
      role: "admin",
      active: true,
      createdAt: now,
      updatedAt: now,
    });
    await db.collection("adminUsers").doc(MODERATOR_UID).set({
      role: "moderator",
      active: true,
      createdAt: now,
      updatedAt: now,
    });
    await db.collection("images").doc("img_1").set({
      title: "Golden Hour Editorial Portrait",
      shortName: "Golden Hour",
      description: "Rim lighting with 85mm f/1.4 prime lens.",
      categoryId: "women_portrait",
      categoryName: "Women Portrait",
      imageUrl: "https://example.com/photo-1.jpg",
      aspectRatio: 0.75,
      featured: true,
      trending: true,
      recommended: true,
      collectionName: "Golden Hour Masters",
      keywords: "portrait,golden hour,editorial",
      displayOrder: 1,
      status: "published",
      viewCount: 10,
      likeCount: 2,
      commentCount: 1,
      unlockCount: 5,
      shareCount: 1,
      createdAt: now,
      updatedAt: now,
    });
    await db.collection("imagePrompts").doc("img_1").set({
      promptText: "Editorial close-up portrait at golden hour, 85mm f/1.4 lens --ar 3:4",
      updatedAt: now,
    });
  });
}

test("1. Prompt Lock: guest/un-unlocked user cannot read imagePrompts; unlocked user and admin can", async () => {
  await seedRolesAndSampleImage();

  const guestDb = testEnv.authenticatedContext(GUEST_UID, {
    firebase: { sign_in_provider: "anonymous" },
  }).firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, {
    firebase: { sign_in_provider: "google.com" },
  }).firestore();
  const adminDb = testEnv.authenticatedContext(ADMIN_UID, {
    firebase: { sign_in_provider: "google.com" },
  }).firestore();

  // Neither guest nor Alice (before unlocking) can read imagePrompts/img_1
  await assertFails(guestDb.collection("imagePrompts").doc("img_1").get());
  await assertFails(aliceDb.collection("imagePrompts").doc("img_1").get());

  // Alice records verified reward unlock in promptUnlocks/{uid}_{imageId}
  await assertSucceeds(
    aliceDb.collection("promptUnlocks").doc(`${ALICE_UID}_img_1`).set({
      userId: ALICE_UID,
      imageId: "img_1",
      imageTitle: "Golden Hour Editorial Portrait",
      rewardVerified: true,
      adNetwork: "AdMob",
      createdAt: new Date(Date.now() - 1000),
    })
  );

  // Now Alice CAN read imagePrompts/img_1, while guest still cannot
  await assertSucceeds(aliceDb.collection("imagePrompts").doc("img_1").get());
  await assertFails(guestDb.collection("imagePrompts").doc("img_1").get());

  // Admin can read and update imagePrompts directly
  await assertSucceeds(adminDb.collection("imagePrompts").doc("img_1").get());
  await assertSucceeds(
    adminDb.collection("imagePrompts").doc("img_1").set({
      promptText: "Updated master prompt --ar 3:4 --v 6.1",
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("2. Safe Counters: counters on images can only move by +1 or -1; arbitrary jumps fail", async () => {
  await seedRolesAndSampleImage();

  const aliceDb = testEnv.authenticatedContext(ALICE_UID, {
    firebase: { sign_in_provider: "google.com" },
  }).firestore();

  // Incrementing viewCount by exactly +1 succeeds
  await assertSucceeds(
    aliceDb.collection("images").doc("img_1").update({
      viewCount: 11,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Decrementing likeCount by -1 succeeds
  await assertSucceeds(
    aliceDb.collection("images").doc("img_1").update({
      likeCount: 1,
      updatedAt: new Date(Date.now() - 900),
    })
  );

  // Arbitrary counter jump (+500 views or +10 likes) MUST fail
  await assertFails(
    aliceDb.collection("images").doc("img_1").update({
      viewCount: 500,
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(
    aliceDb.collection("images").doc("img_1").update({
      likeCount: 15,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Decrementing viewCount (-1) MUST fail (views only increment)
  await assertFails(
    aliceDb.collection("images").doc("img_1").update({
      viewCount: 10,
      updatedAt: new Date(Date.now() - 400),
    })
  );
});

test("3. Non-admin cannot write images, imagePrompts, or adminUsers; Admin/Owner can", async () => {
  await seedRolesAndSampleImage();
  const now = new Date(Date.now() - 1000);

  const aliceDb = testEnv.authenticatedContext(ALICE_UID, {
    firebase: { sign_in_provider: "google.com" },
  }).firestore();
  const adminDb = testEnv.authenticatedContext(ADMIN_UID, {
    firebase: { sign_in_provider: "google.com" },
  }).firestore();
  const ownerDb = testEnv.authenticatedContext(OWNER_UID, {
    firebase: { sign_in_provider: "google.com" },
  }).firestore();

  const validNewImage = {
    title: "Milanese Chiaroscuro",
    shortName: "Milanese",
    description: "Studio portrait",
    categoryId: "men_portrait",
    categoryName: "Men Portrait",
    imageUrl: "https://example.com/photo-2.jpg",
    aspectRatio: 0.8,
    featured: false,
    trending: true,
    recommended: false,
    collectionName: "Portraits",
    keywords: "men,portrait",
    displayOrder: 2,
    status: "published",
    viewCount: 0,
    likeCount: 0,
    commentCount: 0,
    unlockCount: 0,
    shareCount: 0,
    createdAt: now,
    updatedAt: now,
  };

  // Alice cannot create images, imagePrompts, or adminUsers
  await assertFails(aliceDb.collection("images").doc("img_2").set(validNewImage));
  await assertFails(
    aliceDb.collection("imagePrompts").doc("img_2").set({
      promptText: "Unauthorized prompt",
      updatedAt: now,
    })
  );
  await assertFails(
    aliceDb.collection("adminUsers").doc(ALICE_UID).set({
      role: "admin",
      active: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Admin CAN create images (without promptText) and imagePrompts
  await assertSucceeds(adminDb.collection("images").doc("img_2").set(validNewImage));
  await assertSucceeds(
    adminDb.collection("imagePrompts").doc("img_2").set({
      promptText: "Authorized admin prompt",
      updatedAt: now,
    })
  );

  // Image document containing promptText is rejected by schema validation
  await assertFails(
    adminDb.collection("images").doc("img_bad").set({
      ...validNewImage,
      promptText: "Should not be in images collection",
    })
  );

  // Only Owner can manage adminUsers
  await assertFails(
    adminDb.collection("adminUsers").doc(BOB_UID).set({
      role: "moderator",
      active: true,
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(
    ownerDb.collection("adminUsers").doc(BOB_UID).set({
      role: "moderator",
      active: true,
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("4. Moderator limits & Guest vs Real User enforcement on likes, comments, and reports", async () => {
  await seedRolesAndSampleImage();
  const now = new Date(Date.now() - 2000);

  const guestDb = testEnv.authenticatedContext(GUEST_UID, {
    firebase: { sign_in_provider: "anonymous" },
  }).firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, {
    firebase: { sign_in_provider: "google.com" },
  }).firestore();
  const modDb = testEnv.authenticatedContext(MODERATOR_UID, {
    firebase: { sign_in_provider: "google.com" },
  }).firestore();

  // Guest (anonymous user) CAN browse published images, count a view (+1), save favorites,
  // create a verified promptUnlock, and read the unlocked imagePrompt
  await assertSucceeds(guestDb.collection("images").where("status", "==", "published").get());
  await assertSucceeds(
    guestDb.collection("images").doc("img_1").update({
      viewCount: 11,
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertSucceeds(
    guestDb.collection("favorites").doc(`${GUEST_UID}_img_1`).set({
      userId: GUEST_UID,
      imageId: "img_1",
      imageTitle: "Golden Hour Editorial Portrait",
      imageUrl: "https://example.com/photo-1.jpg",
      categoryName: "Women Portrait",
      aspectRatio: 0.75,
      createdAt: now,
    })
  );
  await assertSucceeds(
    guestDb.collection("promptUnlocks").doc(`${GUEST_UID}_img_1`).set({
      userId: GUEST_UID,
      imageId: "img_1",
      imageTitle: "Golden Hour Editorial Portrait",
      rewardVerified: true,
      adNetwork: "AdMob",
      createdAt: now,
    })
  );
  await assertSucceeds(guestDb.collection("imagePrompts").doc("img_1").get());

  // Guest CANNOT like, post comments, or submit reports (requires sign_in_provider != 'anonymous')
  await assertFails(
    guestDb.collection("likes").doc(`${GUEST_UID}_img_1`).set({
      userId: GUEST_UID,
      imageId: "img_1",
      createdAt: now,
    })
  );
  await assertFails(
    guestDb.collection("comments").doc("c_guest").set({
      imageId: "img_1",
      userId: GUEST_UID,
      userName: "Guest",
      text: "Guest comment",
      reported: false,
      reportReason: "",
      adminReply: "",
      status: "visible",
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertFails(
    guestDb.collection("reports").doc("rep_guest").set({
      commentId: "c_1",
      imageId: "img_1",
      reporterId: GUEST_UID,
      reason: "Spam check",
      status: "open",
      createdAt: now,
    })
  );

  // Real user (Alice) CAN like, post comment, and create a report in reports/{reportId}
  await assertSucceeds(
    aliceDb.collection("likes").doc(`${ALICE_UID}_img_1`).set({
      userId: ALICE_UID,
      imageId: "img_1",
      createdAt: now,
    })
  );
  await assertSucceeds(
    aliceDb.collection("comments").doc("c_1").set({
      imageId: "img_1",
      userId: ALICE_UID,
      userName: "Alice",
      text: "Great lighting composition!",
      reported: false,
      reportReason: "",
      adminReply: "",
      status: "visible",
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(
    aliceDb.collection("reports").doc("rep_1").set({
      commentId: "c_1",
      imageId: "img_1",
      reporterId: ALICE_UID,
      reason: "Spam check",
      status: "open",
      createdAt: now,
    })
  );

  // Alice cannot read reports, but Moderator CAN read/update reports and moderate comments
  await assertFails(aliceDb.collection("reports").doc("rep_1").get());
  await assertSucceeds(modDb.collection("reports").doc("rep_1").get());
  await assertSucceeds(
    modDb.collection("reports").doc("rep_1").update({
      status: "reviewed",
    })
  );
  await assertSucceeds(
    modDb.collection("comments").doc("c_1").update({
      adminReply: "Thank you for your feedback!",
      status: "visible",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Moderator CANNOT write images or imagePrompts
  await assertFails(
    modDb.collection("imagePrompts").doc("img_1").set({
      promptText: "Moderator trying to overwrite prompt",
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(modDb.collection("images").doc("img_1").delete());
});
