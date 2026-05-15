package com.kutira.kone.data.repository

import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.kutira.kone.data.model.FabricScrap
import com.kutira.kone.data.model.Message
import com.kutira.kone.data.model.SwapRequest
import com.kutira.kone.data.model.User
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

class FabricRepository {

    private val auth = FirebaseAuth.getInstance()
    private val db   = FirebaseFirestore.getInstance()

    // App-scoped coroutine scope (survives fragment destruction, safe for fire-and-forget)
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        private var initialized = false
    }

    init {
        if (!initialized) {
            try {
                val settings = FirebaseFirestoreSettings.Builder()
                    .setPersistenceEnabled(true)
                    .build()
                db.firestoreSettings = settings
                initialized = true
            } catch (e: Exception) {
                // Already initialized — safe to ignore
            }
        }
    }

    // ─── Auth ─────────────────────────────────────────

    suspend fun signIn(email: String, password: String): Result<Unit> = runCatching {
        auth.signInWithEmailAndPassword(email, password).await()
    }

    suspend fun signUp(name: String, email: String, password: String): Result<Unit> = runCatching {
        val result = auth.createUserWithEmailAndPassword(email, password).await()
        val uid = result.user?.uid ?: throw IllegalStateException("User creation failed")

        val user = User(uid = uid, name = name, email = email)

        // Fire-and-forget user doc write — use appScope, not GlobalScope
        appScope.launch {
            try {
                db.collection("users").document(uid).set(user).await()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun isLoggedIn() = auth.currentUser != null
    fun currentUserId() = auth.currentUser?.uid ?: ""
    fun currentUserEmail() = auth.currentUser?.email ?: ""
    fun signOut() = auth.signOut()

    suspend fun getCurrentUser(): User? {
        val uid = currentUserId()
        if (uid.isEmpty()) return null
        return try {
            db.collection("users").document(uid).get().await()
                .toObject(User::class.java)
        } catch (e: Exception) { null }
    }

    // ─── Images ───────────────────────────────────────

    suspend fun uploadImageAndGetUrl(context: android.content.Context, uri: Uri): String? {
        return try {
            GcsUploader.uploadImage(context, uri)
        } catch (e: Exception) { null }
    }

    // ─── Scraps ───────────────────────────────────────

    suspend fun uploadScrap(scrap: FabricScrap): Result<Unit> = runCatching {
        val id = UUID.randomUUID().toString()
        db.collection("scraps").document(id).set(scrap.copy(id = id)).await()

        // Increment counter — best-effort
        try {
            db.collection("users").document(scrap.userId)
                .set(
                    mapOf("scrapsListed" to com.google.firebase.firestore.FieldValue.increment(1)),
                    SetOptions.merge()
                ).await()
        } catch (e: Exception) { /* non-fatal */ }
    }

    suspend fun getAllScraps(): List<FabricScrap> = try {
        db.collection("scraps")
            .whereEqualTo("status", "available")
            .limit(20)
            .get().await()
            .toObjects(FabricScrap::class.java)
    } catch (e: Exception) { emptyList() }

    suspend fun getScrapsByMaterial(material: String): List<FabricScrap> = try {
        val query = if (material == "All") {
            db.collection("scraps").whereEqualTo("status", "available")
        } else {
            db.collection("scraps")
                .whereEqualTo("material", material)
                .whereEqualTo("status", "available")
        }
        query.limit(20).get().await().toObjects(FabricScrap::class.java)
    } catch (e: Exception) { emptyList() }

    suspend fun getMyListings(): List<FabricScrap> = try {
        db.collection("scraps")
            .whereEqualTo("userId", currentUserId())
            .limit(50)
            .get().await()
            .toObjects(FabricScrap::class.java)
    } catch (e: Exception) { emptyList() }

    suspend fun updateScrap(scrapId: String, title: String, description: String): Result<Unit> = runCatching {
        db.collection("scraps").document(scrapId)
            .update(mapOf("title" to title, "description" to description))
            .await()
    }

    suspend fun deleteScrap(scrapId: String): Result<Unit> = runCatching {
        db.collection("scraps").document(scrapId).delete().await()
    }

    // ─── Swap Requests ───────────────────────────────

    suspend fun sendSwapRequest(request: SwapRequest): Result<Unit> = runCatching {
        val id = UUID.randomUUID().toString()
        val updatedRequest = request.copy(
            id = id,
            status = "pending",
            timestamp = System.currentTimeMillis()
        )
        db.collection("swaps").document(id).set(updatedRequest).await()
    }

    suspend fun getMySwapRequests(): List<SwapRequest> = try {
        db.collection("swaps")
            .whereEqualTo("ownerId", currentUserId())
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(20)
            .get().await()
            .toObjects(SwapRequest::class.java)
    } catch (e: Exception) {
        e.printStackTrace()
        emptyList()
    }

    suspend fun updateSwapStatus(swapId: String, status: String): Result<Unit> = runCatching {
        db.collection("swaps").document(swapId).update("status", status).await()

        if (status == "accepted") {
            val swap = try {
                db.collection("swaps").document(swapId).get().await()
                    .toObject(SwapRequest::class.java)
            } catch (e: Exception) { null } ?: return@runCatching

            // Hide scrap from marketplace
            try {
                db.collection("scraps").document(swap.scrapId)
                    .update("status", "swapped").await()
            } catch (e: Exception) { /* non-fatal */ }

            // Notify requester
            try {
                db.collection("notifications").add(
                    mapOf(
                        "toUserId" to swap.requesterId,
                        "message" to "Your swap request has been accepted!",
                        "timestamp" to System.currentTimeMillis()
                    )
                ).await()
            } catch (e: Exception) { /* non-fatal */ }

            // Update owner's swap count
            try {
                db.collection("users").document(currentUserId())
                    .set(
                        mapOf("swapsCompleted" to com.google.firebase.firestore.FieldValue.increment(1)),
                        SetOptions.merge()
                    ).await()
            } catch (e: Exception) { /* non-fatal */ }
        }
    }

    suspend fun getRequestsSentByMe(): List<SwapRequest> = try {
        db.collection("swaps")
            .whereEqualTo("requesterId", currentUserId())
            .limit(20)
            .get().await()
            .toObjects(SwapRequest::class.java)
    } catch (e: Exception) { emptyList() }

    // ─── Messages ────────────────────────────────────

    suspend fun sendMessage(senderId: String, receiverId: String, text: String) {
        try {
            db.collection("messages").add(
                mapOf(
                    "senderId" to senderId,
                    "receiverId" to receiverId,
                    "message" to text,
                    "timestamp" to System.currentTimeMillis()
                )
            ).await()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun listenMessages(
        user1: String,
        user2: String,
        onUpdate: (List<Message>) -> Unit
    ) {
        db.collection("messages")
            .orderBy("timestamp")
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                if (snapshot != null) {
                    val list = snapshot.toObjects(Message::class.java).filter {
                        (it.senderId == user1 && it.receiverId == user2) ||
                        (it.senderId == user2 && it.receiverId == user1)
                    }
                    onUpdate(list)
                }
            }
    }
}
