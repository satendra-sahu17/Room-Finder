package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.FavouriteItem
import com.example.data.model.RoomComment
import com.example.data.model.RoomListing
import com.example.data.model.RoomReview
import com.example.data.model.RoomStatus
import com.example.data.util.OperationType
import com.example.data.util.handleFirestoreError
import com.example.data.util.unwrapFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class RoomRepository(
    private val db: FirebaseFirestore,
    private val notificationRepository: NotificationRepository = NotificationRepository(db)
) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth = Firebase.auth

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")

    fun observeAvailableRooms(): Flow<List<RoomListing>> = flow {
        val uid = requireUserId()
        val path = "rooms"
        emitAll(
            db.collection("rooms")
                .whereEqualTo("status", RoomStatus.AVAILABLE.name)
                .snapshots()
                .map { snap ->
                    snap.toObjects(RoomListing::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val root = unwrapFirestoreError(error)
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(root, OperationType.LIST, path)
                    }
                    throw root
                }
        )
    }

    fun observeOwnerRooms(ownerId: String): Flow<List<RoomListing>> = flow {
        val uid = requireUserId()
        if (uid != ownerId) throw IllegalStateException("Session mismatch")
        val path = "rooms"
        emitAll(
            db.collection("rooms")
                .whereEqualTo("ownerId", ownerId)
                .snapshots()
                .map { snap ->
                    snap.toObjects(RoomListing::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val root = unwrapFirestoreError(error)
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(root, OperationType.LIST, path)
                    }
                    throw root
                }
        )
    }

    fun observeRoom(roomId: String): Flow<RoomListing?> = flow {
        val uid = requireUserId()
        val path = "rooms/$roomId"
        emitAll(
            db.collection("rooms").document(roomId)
                .snapshots()
                .map { it.toObject(RoomListing::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { error ->
                    val root = unwrapFirestoreError(error)
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(root, OperationType.GET, path)
                    }
                    throw root
                }
        )
    }

    suspend fun saveRoom(existingRoomId: String?, room: RoomListing): Result<String> {
        var docPath = "rooms"
        return try {
            val uid = requireUserId()
            require(room.title.trim().length >= 3) { "Title must be at least 3 characters." }
            require(room.description.trim().length >= 10) { "Description must be at least 10 characters." }
            require(room.monthlyRent in 500L..1_000_000L) { "Monthly rent must be between ₹500 and ₹10,00,000." }
            require(room.city.isNotBlank() && room.area.isNotBlank()) { "City and area are required." }

            val boundedPhotos = room.photos.filter { it.isNotBlank() }.take(4)
            val cover = room.coverPhoto.ifBlank { boundedPhotos.firstOrNull() ?: "" }

            if (existingRoomId.isNullOrBlank()) {
                val docRef = db.collection("rooms").document()
                docPath = docRef.path
                val payload = mapOf(
                    "ownerId" to uid,
                    "ownerName" to room.ownerName.ifBlank { "Owner" }.take(100),
                    "ownerPhoto" to room.ownerPhoto.take(195_000),
                    "ownerVerified" to room.ownerVerified,
                    "title" to room.title.trim().take(120),
                    "description" to room.description.trim().take(2000),
                    "category" to room.category,
                    "monthlyRent" to room.monthlyRent,
                    "securityDeposit" to room.securityDeposit.coerceIn(0L, 5_000_000L),
                    "roomSizeSqFt" to room.roomSizeSqFt.coerceIn(0L, 50_000L),
                    "occupants" to room.occupants.coerceIn(1L, 20L),
                    "availableFrom" to room.availableFrom.take(40),
                    "furnishing" to room.furnishing,
                    "bathroomType" to room.bathroomType,
                    "genderPreference" to room.genderPreference,
                    "facilities" to room.facilities.take(20),
                    "country" to room.country.ifBlank { "India" }.take(80),
                    "state" to room.state.take(80),
                    "city" to room.city.trim().take(80),
                    "area" to room.area.trim().take(100),
                    "address" to room.address.trim().take(240),
                    "postalCode" to room.postalCode.trim().take(20),
                    "hideExactAddress" to room.hideExactAddress,
                    "latitude" to room.latitude.coerceIn(-90.0, 90.0),
                    "longitude" to room.longitude.coerceIn(-180.0, 180.0),
                    "coverPhoto" to cover.take(240_000),
                    "photos" to boundedPhotos,
                    "videoUrl" to room.videoUrl.trim().take(500),
                    "status" to if (room.status == RoomStatus.PAUSED.name) RoomStatus.PAUSED.name else RoomStatus.AVAILABLE.name,
                    "likesCount" to 0L,
                    "commentsCount" to 0L,
                    "viewsCount" to 0L,
                    "favouritesCount" to 0L,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
                Result.success(docRef.id)
            } else {
                val docRef = db.collection("rooms").document(existingRoomId)
                docPath = docRef.path
                val updatePayload = mapOf(
                    "ownerName" to room.ownerName.ifBlank { "Owner" }.take(100),
                    "ownerPhoto" to room.ownerPhoto.take(195_000),
                    "title" to room.title.trim().take(120),
                    "description" to room.description.trim().take(2000),
                    "category" to room.category,
                    "monthlyRent" to room.monthlyRent,
                    "securityDeposit" to room.securityDeposit.coerceIn(0L, 5_000_000L),
                    "roomSizeSqFt" to room.roomSizeSqFt.coerceIn(0L, 50_000L),
                    "occupants" to room.occupants.coerceIn(1L, 20L),
                    "availableFrom" to room.availableFrom.take(40),
                    "furnishing" to room.furnishing,
                    "bathroomType" to room.bathroomType,
                    "genderPreference" to room.genderPreference,
                    "facilities" to room.facilities.take(20),
                    "country" to room.country.ifBlank { "India" }.take(80),
                    "state" to room.state.take(80),
                    "city" to room.city.trim().take(80),
                    "area" to room.area.trim().take(100),
                    "address" to room.address.trim().take(240),
                    "postalCode" to room.postalCode.trim().take(20),
                    "hideExactAddress" to room.hideExactAddress,
                    "latitude" to room.latitude.coerceIn(-90.0, 90.0),
                    "longitude" to room.longitude.coerceIn(-180.0, 180.0),
                    "coverPhoto" to cover.take(240_000),
                    "photos" to boundedPhotos,
                    "videoUrl" to room.videoUrl.trim().take(500),
                    "status" to room.status,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.update(updatePayload).await()
                Result.success(existingRoomId)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateRoomStatus(roomId: String, status: RoomStatus): Result<Unit> {
        val docPath = "rooms/$roomId"
        return try {
            requireUserId()
            db.collection("rooms").document(roomId).update(
                mapOf("status" to status.name, "updatedAt" to FieldValue.serverTimestamp())
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteRoom(roomId: String): Result<Unit> {
        val docPath = "rooms/$roomId"
        return try {
            requireUserId()
            db.collection("rooms").document(roomId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun incrementRoomView(roomId: String) {
        try {
            requireUserId()
            db.collection("rooms").document(roomId).update(
                mapOf(
                    "viewsCount" to FieldValue.increment(1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Non-blocking telemetry
        }
    }

    fun observeLikedRoomIds(uid: String): Flow<Set<String>> = flow {
        val currentUid = requireUserId()
        if (currentUid != uid) throw IllegalStateException("Session mismatch")
        val path = "users/$uid/likedRooms"
        emitAll(
            db.collection("users").document(uid).collection("likedRooms")
                .snapshots()
                .map { snap -> snap.documents.map { it.id }.toSet() }
                .catch { error ->
                    val root = unwrapFirestoreError(error)
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(root, OperationType.LIST, path)
                    }
                    throw root
                }
        )
    }

    suspend fun toggleLike(
        room: RoomListing,
        actorName: String,
        currentlyLiked: Boolean
    ): Result<Unit> {
        val docPath = "rooms/${room.id}/likes"
        return try {
            val uid = requireUserId()
            val roomRef = db.collection("rooms").document(room.id)
            val likeRef = roomRef.collection("likes").document(uid)
            val userLikeRef = db.collection("users").document(uid).collection("likedRooms").document(room.id)

            val batch = db.batch()
            if (currentlyLiked) {
                batch.delete(likeRef)
                batch.delete(userLikeRef)
                batch.update(
                    roomRef,
                    mapOf(
                        "likesCount" to FieldValue.increment(-1L),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            } else {
                batch.set(
                    likeRef,
                    mapOf(
                        "userId" to uid,
                        "roomId" to room.id,
                        "ownerId" to room.ownerId,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
                batch.set(
                    userLikeRef,
                    mapOf(
                        "userId" to uid,
                        "roomId" to room.id,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
                batch.update(
                    roomRef,
                    mapOf(
                        "likesCount" to FieldValue.increment(1L),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()

            if (!currentlyLiked && room.ownerId != uid) {
                notificationRepository.sendNotification(
                    recipientId = room.ownerId,
                    senderName = actorName,
                    type = "LIKE",
                    title = "New like on your room",
                    body = "$actorName liked \"${room.title}\"",
                    targetId = room.id
                )
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    fun observeFavourites(uid: String): Flow<List<FavouriteItem>> = flow {
        val currentUid = requireUserId()
        if (currentUid != uid) throw IllegalStateException("Session mismatch")
        val path = "users/$uid/favourites"
        emitAll(
            db.collection("users").document(uid).collection("favourites")
                .snapshots()
                .map { snap ->
                    snap.toObjects(FavouriteItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val root = unwrapFirestoreError(error)
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(root, OperationType.LIST, path)
                    }
                    throw root
                }
        )
    }

    suspend fun toggleFavourite(room: RoomListing, currentlySaved: Boolean): Result<Unit> {
        var docPath = "users"
        return try {
            val uid = requireUserId()
            val favRef = db.collection("users").document(uid).collection("favourites").document(room.id)
            val roomRef = db.collection("rooms").document(room.id)
            docPath = favRef.path

            val batch = db.batch()
            if (currentlySaved) {
                batch.delete(favRef)
                if (room.status == RoomStatus.AVAILABLE.name) {
                    batch.update(
                        roomRef,
                        mapOf(
                            "favouritesCount" to FieldValue.increment(-1L),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            } else {
                batch.set(
                    favRef,
                    mapOf(
                        "userId" to uid,
                        "roomId" to room.id,
                        "roomTitle" to room.title.take(120),
                        "roomCity" to room.city.take(80),
                        "roomRent" to room.monthlyRent,
                        "roomPhoto" to (room.coverPhoto.ifBlank { room.photos.firstOrNull() ?: "" }).take(240_000),
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
                if (room.status == RoomStatus.AVAILABLE.name) {
                    batch.update(
                        roomRef,
                        mapOf(
                            "favouritesCount" to FieldValue.increment(1L),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    fun observeComments(roomId: String): Flow<List<RoomComment>> = flow {
        val uid = requireUserId()
        val path = "rooms/$roomId/comments"
        emitAll(
            db.collection("rooms").document(roomId).collection("comments")
                .whereEqualTo("roomStatus", RoomStatus.AVAILABLE.name)
                .snapshots()
                .map { snap ->
                    snap.toObjects(RoomComment::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val root = unwrapFirestoreError(error)
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(root, OperationType.LIST, path)
                    }
                    throw root
                }
        )
    }

    suspend fun addComment(
        room: RoomListing,
        authorName: String,
        authorPhoto: String,
        text: String
    ): Result<Unit> {
        val path = "rooms/${room.id}/comments"
        return try {
            val uid = requireUserId()
            val cleanText = text.trim()
            require(cleanText.isNotEmpty() && cleanText.length <= 600) { "Comment must be 1–600 characters." }

            val roomRef = db.collection("rooms").document(room.id)
            val commentRef = roomRef.collection("comments").document()
            val batch = db.batch()
            batch.set(
                commentRef,
                mapOf(
                    "roomId" to room.id,
                    "roomOwnerId" to room.ownerId,
                    "roomStatus" to room.status,
                    "authorId" to uid,
                    "authorName" to authorName.ifBlank { "Member" }.take(100),
                    "authorPhoto" to authorPhoto.take(195_000),
                    "text" to cleanText,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(
                roomRef,
                mapOf(
                    "commentsCount" to FieldValue.increment(1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()

            if (room.ownerId != uid) {
                notificationRepository.sendNotification(
                    recipientId = room.ownerId,
                    senderName = authorName,
                    type = "COMMENT",
                    title = "New comment on ${room.title}",
                    body = "$authorName: ${cleanText.take(80)}",
                    targetId = room.id
                )
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun editComment(roomId: String, commentId: String, newText: String): Result<Unit> {
        val path = "rooms/$roomId/comments/$commentId"
        return try {
            requireUserId()
            val cleanText = newText.trim()
            require(cleanText.isNotEmpty() && cleanText.length <= 600) { "Comment must be 1–600 characters." }
            db.collection("rooms").document(roomId).collection("comments").document(commentId)
                .update(mapOf("text" to cleanText, "updatedAt" to FieldValue.serverTimestamp()))
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteComment(roomId: String, commentId: String): Result<Unit> {
        val path = "rooms/$roomId/comments/$commentId"
        return try {
            requireUserId()
            val roomRef = db.collection("rooms").document(roomId)
            val commentRef = roomRef.collection("comments").document(commentId)
            val batch = db.batch()
            batch.delete(commentRef)
            batch.update(
                roomRef,
                mapOf(
                    "commentsCount" to FieldValue.increment(-1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    fun observeReviews(roomId: String): Flow<List<RoomReview>> = flow {
        val uid = requireUserId()
        val path = "rooms/$roomId/reviews"
        emitAll(
            db.collection("rooms").document(roomId).collection("reviews")
                .whereEqualTo("roomStatus", RoomStatus.AVAILABLE.name)
                .snapshots()
                .map { snap ->
                    snap.toObjects(RoomReview::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val root = unwrapFirestoreError(error)
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(root, OperationType.LIST, path)
                    }
                    throw root
                }
        )
    }

    suspend fun submitReview(
        room: RoomListing,
        bookingId: String,
        reviewerName: String,
        reviewerPhoto: String,
        rating: Int,
        comment: String
    ): Result<Unit> {
        val path = "rooms/${room.id}/reviews"
        return try {
            val uid = requireUserId()
            require(rating in 1..5) { "Rating must be 1 to 5 stars." }
            val cleanComment = comment.trim()
            require(cleanComment.length in 3..800) { "Review must be 3–800 characters." }

            // Use bookingId as reviewId to naturally prevent duplicate reviews for the same booking!
            val reviewRef = db.collection("rooms").document(room.id).collection("reviews").document(bookingId)
            reviewRef.set(
                mapOf(
                    "roomId" to room.id,
                    "roomOwnerId" to room.ownerId,
                    "roomStatus" to room.status,
                    "reviewerId" to uid,
                    "reviewerName" to reviewerName.ifBlank { "Tenant" }.take(100),
                    "reviewerPhoto" to reviewerPhoto.take(195_000),
                    "bookingId" to bookingId,
                    "rating" to rating.toLong(),
                    "comment" to cleanComment,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun replyToReview(roomId: String, reviewId: String, response: String): Result<Unit> {
        val path = "rooms/$roomId/reviews/$reviewId"
        return try {
            requireUserId()
            val clean = response.trim()
            require(clean.isNotEmpty() && clean.length <= 600) { "Response must be 1–600 characters." }
            db.collection("rooms").document(roomId).collection("reviews").document(reviewId)
                .update(mapOf("ownerResponse" to clean, "updatedAt" to FieldValue.serverTimestamp()))
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun submitReport(
        targetType: String,
        targetId: String,
        reason: String,
        details: String
    ): Result<Unit> {
        val path = "reports"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("reports").document()
            docRef.set(
                mapOf(
                    "reporterId" to uid,
                    "targetType" to targetType,
                    "targetId" to targetId,
                    "reason" to reason.trim().take(120),
                    "details" to details.trim().take(600),
                    "status" to "OPEN",
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }
}
