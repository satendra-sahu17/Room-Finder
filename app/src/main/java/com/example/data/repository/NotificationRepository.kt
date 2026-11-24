package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.AppNotification
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

class NotificationRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth = Firebase.auth

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")

    fun observeNotifications(uid: String): Flow<List<AppNotification>> = flow {
        val currentUid = requireUserId()
        if (currentUid != uid) throw IllegalStateException("Session mismatch")
        val path = "users/$uid/notifications"
        emitAll(
            db.collection("users").document(uid).collection("notifications")
                .snapshots()
                .map { snap ->
                    snap.toObjects(AppNotification::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
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

    suspend fun sendNotification(
        recipientId: String,
        senderName: String,
        type: String,
        title: String,
        body: String,
        targetId: String
    ) {
        try {
            val uid = auth.currentUser?.uid ?: return
            if (recipientId.isBlank() || recipientId == uid) return
            val docRef = db.collection("users").document(recipientId).collection("notifications").document()
            docRef.set(
                mapOf(
                    "recipientId" to recipientId,
                    "senderId" to uid,
                    "senderName" to senderName.take(100),
                    "type" to type,
                    "title" to title.take(120),
                    "body" to body.take(300),
                    "targetId" to targetId.take(128),
                    "readStatus" to "UNREAD",
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Non-blocking notification dispatch
        }
    }

    suspend fun markAsRead(uid: String, notificationId: String): Result<Unit> {
        val path = "users/$uid/notifications/$notificationId"
        return try {
            requireUserId()
            db.collection("users").document(uid).collection("notifications").document(notificationId)
                .update(mapOf("readStatus" to "READ"))
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun markAllAsRead(uid: String, notifications: List<AppNotification>): Result<Unit> {
        val path = "users/$uid/notifications"
        return try {
            requireUserId()
            val unread = notifications.filter { it.readStatus == "UNREAD" }
            if (unread.isEmpty()) return Result.success(Unit)
            val batch = db.batch()
            unread.take(50).forEach { item ->
                batch.update(
                    db.collection("users").document(uid).collection("notifications").document(item.id),
                    mapOf("readStatus" to "READ")
                )
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }
}
