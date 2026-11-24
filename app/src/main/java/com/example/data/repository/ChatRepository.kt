package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.ChatMessage
import com.example.data.model.Conversation
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

class ChatRepository(
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

    fun observeConversations(uid: String): Flow<List<Conversation>> = flow {
        val currentUid = requireUserId()
        if (currentUid != uid) throw IllegalStateException("Session mismatch")
        val path = "conversations"
        emitAll(
            db.collection("conversations")
                .whereArrayContains("participantIds", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(Conversation::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.updatedAt ?: Timestamp(0, 0) }
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

    fun observeMessages(conversationId: String, uid: String): Flow<List<ChatMessage>> = flow {
        val currentUid = requireUserId()
        if (currentUid != uid) throw IllegalStateException("Session mismatch")
        val path = "conversations/$conversationId/messages"
        emitAll(
            db.collection("conversations").document(conversationId).collection("messages")
                .whereArrayContains("participantIds", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(ChatMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
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

    suspend fun getOrCreateConversation(
        myName: String,
        myPhoto: String,
        otherUserId: String,
        otherName: String,
        otherPhoto: String,
        roomId: String,
        roomTitle: String
    ): Result<String> {
        var path = "conversations"
        return try {
            val uid = requireUserId()
            require(otherUserId != uid) { "You cannot start a chat with yourself." }
            val ordered = listOf(uid, otherUserId).sorted()
            val conversationId = "${ordered[0]}_${ordered[1]}"
            val docRef = db.collection("conversations").document(conversationId)
            path = docRef.path

            val existing = runCatching { docRef.get().await() }.getOrNull()
            if (existing == null || !existing.exists()) {
                val payload = mapOf(
                    "participantIds" to ordered,
                    "participantNames" to mapOf(uid to myName.take(100), otherUserId to otherName.take(100)),
                    "participantPhotos" to mapOf(uid to myPhoto.take(195_000), otherUserId to otherPhoto.take(195_000)),
                    "roomId" to roomId.take(128),
                    "roomTitle" to roomTitle.take(120),
                    "lastMessage" to "Started conversation about $roomTitle".take(1000),
                    "lastSenderId" to uid,
                    "unreadBy" to emptyList<String>(),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            }
            Result.success(conversationId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun sendMessage(
        conversationId: String,
        participantIds: List<String>,
        otherUserId: String,
        senderName: String,
        text: String,
        imageData: String = ""
    ): Result<Unit> {
        val path = "conversations/$conversationId/messages"
        return try {
            val uid = requireUserId()
            val cleanText = text.trim().take(1500)
            require(cleanText.isNotEmpty() || imageData.isNotBlank()) { "Message cannot be empty." }

            val convRef = db.collection("conversations").document(conversationId)
            val msgRef = convRef.collection("messages").document()

            val msgPayload = buildMap<String, Any> {
                put("conversationId", conversationId)
                put("participantIds", participantIds)
                put("senderId", uid)
                if (cleanText.isNotEmpty()) put("text", cleanText)
                if (imageData.isNotBlank()) put("imageData", imageData.take(240_000))
                put("readStatus", "SENT")
                put("createdAt", FieldValue.serverTimestamp())
                put("updatedAt", FieldValue.serverTimestamp())
            }

            val preview = if (cleanText.isNotEmpty()) cleanText else "Sent a photo"
            val batch = db.batch()
            batch.set(msgRef, msgPayload)
            batch.update(
                convRef,
                mapOf(
                    "lastMessage" to preview.take(1000),
                    "lastSenderId" to uid,
                    "unreadBy" to listOf(otherUserId),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()

            notificationRepository.sendNotification(
                recipientId = otherUserId,
                senderName = senderName,
                type = "MESSAGE",
                title = "New message from $senderName",
                body = preview.take(120),
                targetId = conversationId
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun markConversationRead(
        conversation: Conversation,
        messages: List<ChatMessage>,
        myUid: String
    ) {
        try {
            val uid = requireUserId()
            if (uid != myUid) return
            val batch = db.batch()
            var hasChanges = false

            if (myUid in conversation.unreadBy) {
                batch.update(
                    db.collection("conversations").document(conversation.id),
                    mapOf(
                        "unreadBy" to conversation.unreadBy.filterNot { it == myUid },
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                hasChanges = true
            }

            messages.filter { it.senderId != myUid && it.readStatus == "SENT" }.take(20).forEach { msg ->
                val ref = db.collection("conversations").document(conversation.id)
                    .collection("messages").document(msg.id)
                batch.update(
                    ref,
                    mapOf("readStatus" to "READ", "updatedAt" to FieldValue.serverTimestamp())
                )
                hasChanges = true
            }

            if (hasChanges) {
                batch.commit().await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Non-fatal read receipt error
        }
    }
}
