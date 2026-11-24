package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.ListingReport
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

class AdminRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth = Firebase.auth

    suspend fun checkIsAdmin(): Boolean {
        val user = auth.currentUser ?: return false
        if (user.email == "sahusatendra205@gmail.com" && user.isEmailVerified) return true
        return try {
            db.collection("admins").document(user.uid).get().await().exists()
        } catch (_: Exception) {
            false
        }
    }

    fun observeOpenReports(): Flow<List<ListingReport>> = flow {
        val uid = auth.currentUser?.uid ?: throw IllegalStateException("Not authenticated")
        val path = "reports"
        emitAll(
            db.collection("reports")
                .whereEqualTo("status", "OPEN")
                .snapshots()
                .map { snap ->
                    snap.toObjects(ListingReport::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
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

    suspend fun resolveReport(report: ListingReport, removeListing: Boolean): Result<Unit> {
        val path = "reports/${report.id}"
        return try {
            val uid = auth.currentUser?.uid ?: throw IllegalStateException("Not authenticated")
            val batch = db.batch()
            batch.update(
                db.collection("reports").document(report.id),
                mapOf("status" to if (removeListing) "RESOLVED" else "DISMISSED")
            )
            if (removeListing && report.targetType == "ROOM") {
                batch.update(
                    db.collection("rooms").document(report.targetId),
                    mapOf("status" to RoomStatus.REJECTED.name, "updatedAt" to FieldValue.serverTimestamp())
                )
            }
            val logRef = db.collection("adminLogs").document()
            batch.set(
                logRef,
                mapOf(
                    "adminId" to uid,
                    "action" to if (removeListing) "REJECT_LISTING" else "DISMISS_REPORT",
                    "targetId" to report.targetId,
                    "notes" to report.reason.take(200),
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun verifyOwner(targetUserId: String): Result<Unit> {
        val path = "publicProfiles/$targetUserId"
        return try {
            val uid = auth.currentUser?.uid ?: throw IllegalStateException("Not authenticated")
            val batch = db.batch()
            batch.update(
                db.collection("users").document(targetUserId),
                mapOf("verified" to true, "updatedAt" to FieldValue.serverTimestamp())
            )
            batch.update(
                db.collection("publicProfiles").document(targetUserId),
                mapOf("verified" to true, "updatedAt" to FieldValue.serverTimestamp())
            )
            val logRef = db.collection("adminLogs").document()
            batch.set(
                logRef,
                mapOf(
                    "adminId" to uid,
                    "action" to "VERIFY_OWNER",
                    "targetId" to targetUserId,
                    "notes" to "Verified owner profile",
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
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
