package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.AccountType
import com.example.data.model.FollowRelation
import com.example.data.model.PublicProfile
import com.example.data.model.UserAccount
import com.example.data.util.OperationType
import com.example.data.util.handleFirestoreError
import com.example.data.util.unwrapFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
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
import org.json.JSONArray
import org.json.JSONObject

class UserRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth = Firebase.auth

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")

    suspend fun ensureUserInitialized(
        firebaseUser: FirebaseUser,
        pendingDraft: com.example.data.util.PendingRegistrationDraft? = null
    ): Result<Unit> {
        val uid = firebaseUser.uid
        return try {
            val userRef = db.collection("users").document(uid)
            val publicRef = db.collection("publicProfiles").document(uid)

            val userSnap = userRef.get().await()
            val publicSnap = publicRef.get().await()

            val displayName = pendingDraft?.fullName?.takeIf { it.isNotBlank() }
                ?: firebaseUser.displayName?.takeIf { it.isNotBlank() }
                ?: "RoomFinder Member"
            val email = firebaseUser.email ?: ""
            val rawHandle = (email.substringBefore("@").ifBlank { displayName.lowercase().replace(Regex("[^a-z0-9]"), "") })
                .take(24).ifBlank { "user_${uid.take(6)}" }
            val photoUrl = firebaseUser.photoUrl?.toString() ?: ""
            val chosenType = pendingDraft?.accountType ?: AccountType.BOTH
            val chosenPhone = pendingDraft?.phone?.take(30) ?: ""
            val chosenCity = pendingDraft?.city?.take(80) ?: ""

            val batch = db.batch()
            var needsCommit = false

            if (!userSnap.exists()) {
                val privatePayload = mapOf(
                    "userId" to uid,
                    "fullName" to displayName.take(100),
                    "username" to rawHandle.padEnd(3, '0'),
                    "email" to email.take(160),
                    "phone" to chosenPhone,
                    "bio" to "",
                    "city" to chosenCity,
                    "accountType" to chosenType.name,
                    "photoData" to photoUrl,
                    "verified" to false,
                    "showPhone" to false,
                    "showEmail" to false,
                    "followersCount" to 0L,
                    "followingCount" to 0L,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                batch.set(userRef, privatePayload)
                needsCommit = true
            }

            if (!publicSnap.exists()) {
                val publicPayload = mapOf(
                    "userId" to uid,
                    "fullName" to displayName.take(100),
                    "username" to rawHandle.padEnd(3, '0'),
                    "bio" to "",
                    "city" to chosenCity,
                    "accountType" to chosenType.name,
                    "photoData" to photoUrl,
                    "verified" to false,
                    "visibility" to "PUBLIC",
                    "ratingAvg" to 0.0,
                    "ratingCount" to 0L,
                    "followersCount" to 0L,
                    "followingCount" to 0L,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                batch.set(publicRef, publicPayload)
                needsCommit = true
            }

            if (needsCommit) {
                batch.commit().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "users/$uid")
            Result.failure(e)
        }
    }

    fun observeMyAccount(uid: String): Flow<UserAccount?> = flow {
        val currentUid = requireUserId()
        if (currentUid != uid) throw IllegalStateException("Session mismatch")
        val path = "users/$uid"
        emitAll(
            db.collection("users").document(uid)
                .snapshots()
                .map { it.toObject(UserAccount::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { error ->
                    val root = unwrapFirestoreError(error)
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(root, OperationType.GET, path)
                    }
                    throw root
                }
        )
    }

    fun observePublicProfiles(): Flow<List<PublicProfile>> = flow {
        val uid = requireUserId()
        val path = "publicProfiles"
        emitAll(
            db.collection("publicProfiles")
                .whereEqualTo("visibility", "PUBLIC")
                .snapshots()
                .map { snap -> snap.toObjects(PublicProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { error ->
                    val root = unwrapFirestoreError(error)
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(root, OperationType.LIST, path)
                    }
                    throw root
                }
        )
    }

    fun observeFollowing(uid: String): Flow<List<FollowRelation>> = flow {
        val currentUid = requireUserId()
        if (currentUid != uid) throw IllegalStateException("Session mismatch")
        val path = "users/$uid/following"
        emitAll(
            db.collection("users").document(uid).collection("following")
                .snapshots()
                .map { snap -> snap.toObjects(FollowRelation::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { error ->
                    val root = unwrapFirestoreError(error)
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(root, OperationType.LIST, path)
                    }
                    throw root
                }
        )
    }

    suspend fun updateProfile(
        fullName: String,
        username: String,
        phone: String,
        bio: String,
        city: String,
        accountType: AccountType,
        photoData: String,
        showPhone: Boolean,
        showEmail: Boolean,
        isPublic: Boolean
    ): Result<Unit> {
        var path = "users"
        return try {
            val uid = requireUserId()
            val cleanName = fullName.trim().take(100)
            val cleanUsername = username.trim().take(40)
            require(cleanName.isNotEmpty()) { "Full name cannot be empty." }
            require(cleanUsername.length >= 2) { "Username must be at least 2 characters." }
            path = "users/$uid"

            val userUpdate = mapOf(
                "fullName" to cleanName,
                "username" to cleanUsername,
                "phone" to phone.trim().take(30),
                "bio" to bio.trim().take(300),
                "city" to city.trim().take(80),
                "accountType" to accountType.name,
                "photoData" to photoData.take(195_000),
                "showPhone" to showPhone,
                "showEmail" to showEmail,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            val publicUpdate = mapOf(
                "fullName" to cleanName,
                "username" to cleanUsername,
                "bio" to bio.trim().take(300),
                "city" to city.trim().take(80),
                "accountType" to accountType.name,
                "photoData" to photoData.take(195_000),
                "visibility" to if (isPublic) "PUBLIC" else "PRIVATE",
                "updatedAt" to FieldValue.serverTimestamp()
            )

            val batch = db.batch()
            batch.update(db.collection("users").document(uid), userUpdate)
            batch.update(db.collection("publicProfiles").document(uid), publicUpdate)
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun toggleFollow(
        targetUserId: String,
        targetName: String,
        currentlyFollowing: Boolean
    ): Result<Unit> {
        var path = "users"
        return try {
            val uid = requireUserId()
            require(targetUserId.isNotBlank() && targetUserId != uid) { "Invalid target user." }
            val followRef = db.collection("users").document(uid).collection("following").document(targetUserId)
            val publicRef = db.collection("publicProfiles").document(targetUserId)
            path = followRef.path

            val batch = db.batch()
            if (currentlyFollowing) {
                batch.delete(followRef)
                batch.update(
                    publicRef,
                    mapOf(
                        "followersCount" to FieldValue.increment(-1L),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            } else {
                batch.set(
                    followRef,
                    mapOf(
                        "followerId" to uid,
                        "targetUserId" to targetUserId,
                        "targetName" to targetName.take(100),
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
                batch.update(
                    publicRef,
                    mapOf(
                        "followersCount" to FieldValue.increment(1L),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun exportUserDataJson(): Result<String> {
        return try {
            val uid = requireUserId()
            val accountSnap = db.collection("users").document(uid).get().await()
            val roomsSnap = db.collection("rooms").whereEqualTo("ownerId", uid).get().await()
            val favsSnap = db.collection("users").document(uid).collection("favourites").get().await()
            val bookingsSnap = db.collection("bookings").whereArrayContains("participantIds", uid).get().await()

            val root = JSONObject().apply {
                put("userId", uid)
                put("profile", JSONObject(accountSnap.data ?: emptyMap<String, Any>()))
                put("listingsCount", roomsSnap.size())
                put("favourites", JSONArray(favsSnap.documents.map { it.getString("roomTitle") ?: it.id }))
                put("bookingsCount", bookingsSnap.size())
            }
            Result.success(root.toString(2))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, "users/${auth.currentUser?.uid}")
            Result.failure(e)
        }
    }

    suspend fun deleteMyAccount(): Result<Unit> {
        var path = "users"
        return try {
            val uid = requireUserId()
            path = "users/$uid"
            val roomsSnap = db.collection("rooms").whereEqualTo("ownerId", uid).get().await()
            val batch = db.batch()
            roomsSnap.documents.forEach { doc ->
                batch.update(
                    doc.reference,
                    mapOf("status" to "PAUSED", "updatedAt" to FieldValue.serverTimestamp())
                )
            }
            batch.delete(db.collection("users").document(uid))
            batch.delete(db.collection("publicProfiles").document(uid))
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }
}
