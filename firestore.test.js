const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const OWNER_UID = "owner_101";
const SEEKER_UID = "seeker_202";
const OTHER_UID = "other_303";
const ADMIN_EMAIL = "sahusatendra205@gmail.com";

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

function pastDate(ms = 5000) {
  return new Date(Date.now() - ms);
}

function validRoomPayload(ownerId = OWNER_UID, status = "AVAILABLE") {
  const now = pastDate(3000);
  return {
    ownerId,
    ownerName: "Aarav Sharma",
    ownerVerified: false,
    title: "Sunlit Single Room in Vijay Nagar",
    description: "Quiet furnished single room with fast Wi-Fi, balcony, and 24/7 water.",
    category: "SINGLE_ROOM",
    monthlyRent: 8500,
    securityDeposit: 8500,
    roomSizeSqFt: 180,
    occupants: 1,
    availableFrom: "2026-12-01",
    furnishing: "FURNISHED",
    bathroomType: "ATTACHED",
    genderPreference: "ANYONE",
    facilities: ["Wi-Fi", "Air Conditioning", "Balcony"],
    country: "India",
    state: "Madhya Pradesh",
    city: "Jabalpur",
    area: "Vijay Nagar",
    address: "Sector 2, Vijay Nagar, Jabalpur",
    postalCode: "482002",
    hideExactAddress: false,
    latitude: 23.1815,
    longitude: 79.9073,
    photos: [],
    status,
    likesCount: 0,
    commentsCount: 0,
    viewsCount: 0,
    favouritesCount: 0,
    createdAt: now,
    updatedAt: now,
  };
}

test("Unauthenticated users cannot read or write rooms, users, or bookings", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("rooms").where("status", "==", "AVAILABLE").get());
  await assertFails(unauthDb.collection("rooms").doc("room_1").set(validRoomPayload()));
  await assertFails(unauthDb.collection("users").doc(OWNER_UID).get());
  await assertFails(unauthDb.collection("bookings").doc("b1").get());
});

test("User PII isolation and anti-self-verification", async () => {
  const ownerDb = testEnv.authenticatedContext(OWNER_UID).firestore();
  const seekerDb = testEnv.authenticatedContext(SEEKER_UID).firestore();
  const now = pastDate();

  await assertSucceeds(
    ownerDb.collection("users").doc(OWNER_UID).set({
      userId: OWNER_UID,
      fullName: "Aarav Sharma",
      username: "aarav_s",
      email: "aarav@example.com",
      phone: "9876543210",
      city: "Jabalpur",
      accountType: "OWNER",
      verified: false,
      showPhone: true,
      showEmail: false,
      followersCount: 0,
      followingCount: 0,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Self-verification must fail
  await assertFails(
    ownerDb.collection("users").doc(OWNER_UID).update({
      verified: true,
      updatedAt: pastDate(1000),
    })
  );

  // Another user cannot read private PII
  await assertFails(seekerDb.collection("users").doc(OWNER_UID).get());

  // Public profile works without PII and matches filtered query
  await assertSucceeds(
    ownerDb.collection("publicProfiles").doc(OWNER_UID).set({
      userId: OWNER_UID,
      fullName: "Aarav Sharma",
      username: "aarav_s",
      city: "Jabalpur",
      accountType: "OWNER",
      visibility: "PUBLIC",
      verified: false,
      ratingAvg: 0,
      ratingCount: 0,
      followersCount: 0,
      followingCount: 0,
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(
    seekerDb.collection("publicProfiles").where("visibility", "==", "PUBLIC").get()
  );
  await assertFails(seekerDb.collection("publicProfiles").get());
});

test("Room CRUD, query alignment, anti-spoofing, and social counters", async () => {
  const ownerDb = testEnv.authenticatedContext(OWNER_UID).firestore();
  const seekerDb = testEnv.authenticatedContext(SEEKER_UID).firestore();

  // Owner cannot spoof verified badge on create
  await assertFails(
    ownerDb.collection("rooms").doc("room_spoof").set({
      ...validRoomPayload(),
      ownerVerified: true,
    })
  );

  // Valid create succeeds (no `id` field)
  await assertSucceeds(
    ownerDb.collection("rooms").doc("room_1").set(validRoomPayload())
  );

  // Reject `@DocumentId` `id` inside payload
  await assertFails(
    ownerDb.collection("rooms").doc("room_bad").set({
      ...validRoomPayload(),
      id: "room_bad",
    })
  );

  // Filtered query matches rule; unfiltered query fails
  await assertSucceeds(
    seekerDb.collection("rooms").where("status", "==", "AVAILABLE").get()
  );
  await assertSucceeds(
    ownerDb.collection("rooms").where("ownerId", "==", OWNER_UID).get()
  );
  await assertFails(seekerDb.collection("rooms").get());

  // Seeker cannot edit room title, only social counters
  await assertFails(
    seekerDb.collection("rooms").doc("room_1").update({
      title: "Hijacked Title",
      updatedAt: pastDate(500),
    })
  );
  await assertSucceeds(
    seekerDb.collection("rooms").doc("room_1").update({
      likesCount: 1,
      updatedAt: pastDate(500),
    })
  );

  // Owner can pause room; paused room hidden from seeker get
  await assertSucceeds(
    ownerDb.collection("rooms").doc("room_1").update({
      status: "PAUSED",
      updatedAt: pastDate(400),
    })
  );
  await assertFails(seekerDb.collection("rooms").doc("room_1").get());
});

test("Comments, Likes, Favourites, and Bookings access control", async () => {
  const ownerDb = testEnv.authenticatedContext(OWNER_UID).firestore();
  const seekerDb = testEnv.authenticatedContext(SEEKER_UID).firestore();
  const otherDb = testEnv.authenticatedContext(OTHER_UID).firestore();
  const now = pastDate();

  await assertSucceeds(
    ownerDb.collection("rooms").doc("room_1").set(validRoomPayload())
  );

  // Comment create, edit own, reject other's edit
  await assertSucceeds(
    seekerDb.collection("rooms").doc("room_1").collection("comments").doc("c_1").set({
      roomId: "room_1",
      roomOwnerId: OWNER_UID,
      roomStatus: "AVAILABLE",
      authorId: SEEKER_UID,
      authorName: "Riya",
      text: "Is Wi-Fi included in rent?",
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(
    seekerDb.collection("rooms").doc("room_1").collection("comments").doc("c_1").update({
      text: "Is high-speed Wi-Fi included?",
      updatedAt: pastDate(500),
    })
  );
  await assertFails(
    otherDb.collection("rooms").doc("room_1").collection("comments").doc("c_1").update({
      text: "Tampered",
      updatedAt: pastDate(500),
    })
  );

  // Booking workflow & terminal lock
  await assertSucceeds(
    seekerDb.collection("bookings").doc("book_1").set({
      roomId: "room_1",
      roomTitle: "Sunlit Single Room in Vijay Nagar",
      roomCity: "Jabalpur",
      roomRent: 8500,
      seekerId: SEEKER_UID,
      seekerName: "Riya",
      ownerId: OWNER_UID,
      ownerName: "Aarav Sharma",
      participantIds: [SEEKER_UID, OWNER_UID],
      bookingType: "VISIT",
      preferredDate: "2026-12-02",
      message: "Would like to visit at 5 PM.",
      status: "PENDING",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Third party cannot read or update booking
  await assertFails(otherDb.collection("bookings").doc("book_1").get());
  await assertFails(
    otherDb.collection("bookings").doc("book_1").update({
      status: "ACCEPTED",
      updatedAt: pastDate(300),
    })
  );

  // Seeker cannot self-accept booking
  await assertFails(
    seekerDb.collection("bookings").doc("book_1").update({
      status: "ACCEPTED",
      updatedAt: pastDate(300),
    })
  );

  // Owner accepts booking, then confirmed (terminal), then further update fails
  await assertSucceeds(
    ownerDb.collection("bookings").doc("book_1").update({
      status: "ACCEPTED",
      updatedAt: pastDate(300),
    })
  );
  await assertSucceeds(
    ownerDb.collection("bookings").doc("book_1").update({
      status: "CONFIRMED",
      updatedAt: pastDate(200),
    })
  );
  await assertFails(
    ownerDb.collection("bookings").doc("book_1").update({
      status: "REJECTED",
      updatedAt: pastDate(100),
    })
  );

  // Verified booking allows review; non-participant cannot review
  await assertSucceeds(
    seekerDb.collection("rooms").doc("room_1").collection("reviews").doc("rev_1").set({
      roomId: "room_1",
      roomOwnerId: OWNER_UID,
      roomStatus: "AVAILABLE",
      reviewerId: SEEKER_UID,
      reviewerName: "Riya",
      bookingId: "book_1",
      rating: 5,
      comment: "Clean room and responsive owner!",
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertFails(
    otherDb.collection("rooms").doc("room_1").collection("reviews").doc("rev_2").set({
      roomId: "room_1",
      roomOwnerId: OWNER_UID,
      roomStatus: "AVAILABLE",
      reviewerId: OTHER_UID,
      reviewerName: "Stranger",
      bookingId: "book_1",
      rating: 1,
      comment: "Fake review",
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("Real-time chat participant isolation & admin email verification check", async () => {
  const ownerDb = testEnv.authenticatedContext(OWNER_UID).firestore();
  const seekerDb = testEnv.authenticatedContext(SEEKER_UID).firestore();
  const otherDb = testEnv.authenticatedContext(OTHER_UID).firestore();
  const spoofAdminDb = testEnv.authenticatedContext("spoof_uid", {
    email: ADMIN_EMAIL,
    email_verified: false,
  }).firestore();
  const verifiedAdminDb = testEnv.authenticatedContext("admin_uid", {
    email: ADMIN_EMAIL,
    email_verified: true,
  }).firestore();
  const now = pastDate();

  await assertSucceeds(
    seekerDb.collection("conversations").doc("conv_1").set({
      participantIds: [SEEKER_UID, OWNER_UID],
      roomId: "room_1",
      roomTitle: "Sunlit Single Room",
      lastMessage: "Hi, is this room still free?",
      lastSenderId: SEEKER_UID,
      unreadBy: [OWNER_UID],
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(
    seekerDb.collection("conversations").doc("conv_1").collection("messages").doc("m_1").set({
      conversationId: "conv_1",
      participantIds: [SEEKER_UID, OWNER_UID],
      senderId: SEEKER_UID,
      text: "Hi, is this room still free?",
      readStatus: "SENT",
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertFails(otherDb.collection("conversations").doc("conv_1").get());
  await assertFails(
    otherDb.collection("conversations").doc("conv_1").collection("messages").doc("m_1").get()
  );
  await assertFails(
    otherDb.collection("conversations").doc("conv_1").collection("messages").get()
  );
  const emptySnap = await assertSucceeds(
    otherDb.collection("conversations").doc("conv_1").collection("messages").where("participantIds", "array-contains", OTHER_UID).get()
  );
  if (emptySnap.size !== 0) {
    throw new Error("Expected 0 messages for non-participant");
  }

  // Admin email spoofing check
  await assertSucceeds(
    seekerDb.collection("reports").doc("rep_1").set({
      reporterId: SEEKER_UID,
      targetType: "ROOM",
      targetId: "room_1",
      reason: "Duplicate listing",
      status: "OPEN",
      createdAt: now,
    })
  );
  await assertFails(spoofAdminDb.collection("reports").get());
  await assertSucceeds(verifiedAdminDb.collection("reports").get());
});
