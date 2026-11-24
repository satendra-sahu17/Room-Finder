package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.BookingRequest
import com.example.data.model.BookingStatus
import com.example.data.model.BookingType
import com.example.data.model.RoomListing
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

class BookingRepository(
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

    fun observeMyBookings(uid: String): Flow<List<BookingRequest>> = flow {
        val currentUid = requireUserId()
        if (currentUid != uid) throw IllegalStateException("Session mismatch")
        val path = "bookings"
        emitAll(
            db.collection("bookings")
                .whereArrayContains("participantIds", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(BookingRequest::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
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

    suspend fun createBookingRequest(
        room: RoomListing,
        seekerName: String,
        bookingType: BookingType,
        preferredDate: String,
        message: String
    ): Result<Unit> {
        val path = "bookings"
        return try {
            val uid = requireUserId()
            require(room.ownerId != uid) { "You cannot book your own listing." }
            require(room.status == RoomStatus.AVAILABLE.name) { "This room is no longer available." }
            require(preferredDate.isNotBlank()) { "Please pick a preferred date." }

            // Prevent duplicate active request from same seeker for same room
            val existingSnap = db.collection("bookings")
                .whereArrayContains("participantIds", uid)
                .get()
                .await()
            val hasActive = existingSnap.toObjects(BookingRequest::class.java).any {
                it.roomId == room.id && it.seekerId == uid &&
                    it.status in listOf(
                        BookingStatus.PENDING.name,
                        BookingStatus.VISIT_SCHEDULED.name,
                        BookingStatus.ACCEPTED.name,
                        BookingStatus.CONFIRMED.name
                    )
            }
            require(!hasActive) { "You already have an active request for this room." }

            val docRef = db.collection("bookings").document()
            val payload = mapOf(
                "roomId" to room.id,
                "roomTitle" to room.title.take(120),
                "roomCity" to room.city.take(80),
                "roomRent" to room.monthlyRent,
                "roomPhoto" to (room.coverPhoto.ifBlank { room.photos.firstOrNull() ?: "" }).take(240_000),
                "seekerId" to uid,
                "seekerName" to seekerName.ifBlank { "Seeker" }.take(100),
                "ownerId" to room.ownerId,
                "ownerName" to room.ownerName.ifBlank { "Owner" }.take(100),
                "participantIds" to listOf(uid, room.ownerId),
                "bookingType" to bookingType.name,
                "preferredDate" to preferredDate.trim().take(40),
                "message" to message.trim().take(600),
                "status" to BookingStatus.PENDING.name,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()

            val actionLabel = if (bookingType == BookingType.VISIT) "visit request" else "booking request"
            notificationRepository.sendNotification(
                recipientId = room.ownerId,
                senderName = seekerName,
                type = "BOOKING_REQUEST",
                title = "New $actionLabel",
                body = "$seekerName requested ${room.title} for $preferredDate",
                targetId = room.id
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateBookingStatus(
        booking: BookingRequest,
        newStatus: BookingStatus,
        actorName: String,
        preferredDate: String = booking.preferredDate
    ): Result<Unit> {
        val path = "bookings/${booking.id}"
        return try {
            val uid = requireUserId()
            val bookingRef = db.collection("bookings").document(booking.id)
            val batch = db.batch()
            batch.update(
                bookingRef,
                mapOf(
                    "status" to newStatus.name,
                    "preferredDate" to preferredDate.trim().take(40),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            if (newStatus == BookingStatus.CONFIRMED && uid == booking.ownerId) {
                batch.update(
                    db.collection("rooms").document(booking.roomId),
                    mapOf(
                        "status" to RoomStatus.RENTED.name,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()

            val recipient = if (uid == booking.ownerId) booking.seekerId else booking.ownerId
            notificationRepository.sendNotification(
                recipientId = recipient,
                senderName = actorName,
                type = "BOOKING_UPDATE",
                title = "Booking ${newStatus.label}",
                body = "${booking.roomTitle} is now ${newStatus.label}",
                targetId = booking.roomId
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }
}
