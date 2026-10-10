package com.example.shortsgesturecontrol

import android.os.Handler
import android.os.Looper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import java.util.UUID
import kotlin.math.abs

/**
 * The small, child-safe social layer for MochiGotchi.
 *
 * There is deliberately no free-form chat and no contacts permission. Friends
 * are connected with a short code, and play sessions contain only a fixed set
 * of friendly actions. Firestore presence is a convenience for the online
 * list, not a replacement for the pet snapshot.
 */
internal class SocialPlayManager {
    data class Profile(
        val displayName: String,
        val petName: String,
        val petKind: String,
        val stage: String,
        val hatched: Boolean
    )

    data class Friend(
        val uid: String,
        val displayName: String,
        val petName: String,
        val petKind: String,
        val stage: String,
        val online: Boolean,
        val lastSeen: Long
    )

    data class FriendRequest(
        val id: String,
        val senderUid: String,
        val recipientUid: String,
        val senderName: String,
        val senderPetName: String,
        val status: String,
        val documentPath: String
    )

    data class PlayRequest(
        val id: String,
        val senderUid: String,
        val recipientUid: String,
        val senderName: String,
        val senderPetName: String,
        val recipientName: String,
        val recipientPetName: String,
        val status: String,
        val sessionId: String?,
        val documentPath: String
    )

    data class PlayEvent(
        val id: String,
        val actorUid: String,
        val action: String,
        val createdAt: Long
    )

    class SessionSubscription(private val registrations: List<ListenerRegistration>) {
        fun close() = registrations.forEach { it.remove() }
    }

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val handler = Handler(Looper.getMainLooper())
    private val registrations = mutableListOf<ListenerRegistration>()
    private val friendProfileCache = HashMap<String, Map<String, Any?>>()
    private var profile: Profile? = null
    private var heartbeat: Runnable? = null
    private var activeUid: String? = null
    private var friendDocuments: Map<String, Map<String, Any?>> = emptyMap()

    var onFriendsChanged: ((List<Friend>) -> Unit)? = null
    var onFriendRequestsChanged: ((List<FriendRequest>) -> Unit)? = null
    var onPlayRequestsChanged: ((List<PlayRequest>) -> Unit)? = null

    fun isSignedIn(): Boolean = auth.currentUser != null

    fun startPresence(currentProfile: Profile) {
        val uid = auth.currentUser?.uid ?: return
        profile = currentProfile
        if (activeUid != uid) {
            clearListeners()
            activeUid = uid
            listenForSocialChanges(uid)
        }
        writePresence(online = true)
        heartbeat?.let(handler::removeCallbacks)
        val task = object : Runnable {
            override fun run() {
                if (auth.currentUser?.uid != activeUid) return
                writePresence(online = true)
                refreshFriendProfiles()
                handler.postDelayed(this, HEARTBEAT_MS)
            }
        }
        heartbeat = task
        handler.postDelayed(task, HEARTBEAT_MS)
    }

    fun updateProfile(currentProfile: Profile) {
        profile = currentProfile
        if (activeUid != null) writePresence(online = true)
    }

    fun stopPresence() {
        heartbeat?.let(handler::removeCallbacks)
        heartbeat = null
        if (activeUid != null) writePresence(online = false)
    }

    fun stop() {
        stopPresence()
        clearListeners()
        activeUid = null
    }

    fun createInviteCode(onComplete: (String?, String?) -> Unit) {
        val uid = auth.currentUser?.uid
        val currentProfile = profile
        if (uid == null || currentProfile == null) {
            onComplete(null, "Sign in with Google first so friends can find you.")
            return
        }
        val profileRef = profileRef(uid)
        profileRef.get().addOnSuccessListener { snapshot ->
            val existing = snapshot.getString("friendCode")
            if (!existing.isNullOrBlank()) {
                onComplete(existing, null)
                return@addOnSuccessListener
            }
            tryCreateInviteCode(uid, currentProfile, 0, onComplete)
        }.addOnFailureListener { onComplete(null, "I couldn't make an invite code right now.") }
    }

    private fun tryCreateInviteCode(
        uid: String,
        currentProfile: Profile,
        attempt: Int,
        onComplete: (String?, String?) -> Unit
    ) {
        if (attempt >= MAX_CODE_ATTEMPTS) {
            onComplete(null, "I couldn't make an invite code right now.")
            return
        }
        val code = randomCode()
        val codeRef = firestore.collection("friendCodes").document(code)
        val codeData = mapOf(
            "ownerUid" to uid,
            "displayName" to currentProfile.displayName,
            "petName" to currentProfile.petName,
            "createdAt" to FieldValue.serverTimestamp()
        )
        codeRef.get().addOnSuccessListener { existing ->
            if (existing.exists()) {
                tryCreateInviteCode(uid, currentProfile, attempt + 1, onComplete)
            } else {
                codeRef.set(codeData).addOnSuccessListener {
                    profileRef(uid).set(
                        mapOf("friendCode" to code),
                        SetOptions.merge()
                    ).addOnSuccessListener { onComplete(code, null) }
                        .addOnFailureListener { onComplete(null, "The code was made, but I couldn't save it yet.") }
                }.addOnFailureListener { tryCreateInviteCode(uid, currentProfile, attempt + 1, onComplete) }
            }
        }.addOnFailureListener { onComplete(null, "I couldn't make an invite code right now.") }
    }

    fun sendFriendCode(codeInput: String, onComplete: (Boolean, String) -> Unit) {
        val uid = auth.currentUser?.uid
        val currentProfile = profile
        val code = codeInput.trim().uppercase()
        if (uid == null || currentProfile == null) {
            onComplete(false, "Sign in with Google first so friends can find you.")
            return
        }
        if (code.length != CODE_LENGTH) {
            onComplete(false, "That code should be six letters or numbers.")
            return
        }
        firestore.collection("friendCodes").document(code).get()
            .addOnSuccessListener { snapshot ->
                val ownerUid = snapshot.getString("ownerUid")
                if (ownerUid.isNullOrBlank()) {
                    onComplete(false, "I couldn't find that code. Check it and try again.")
                    return@addOnSuccessListener
                }
                if (ownerUid == uid) {
                    onComplete(false, "That is your own invite code.")
                    return@addOnSuccessListener
                }
                val request = mapOf(
                    "senderUid" to uid,
                    "recipientUid" to ownerUid,
                    "senderName" to currentProfile.displayName,
                    "senderPetName" to currentProfile.petName,
                    "status" to "pending",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                val outbox = userCollection(uid).collection("friendRequests").document(ownerUid)
                val inbox = userCollection(ownerUid).collection("incomingFriendRequests").document(uid)
                firestore.runBatch { batch ->
                    batch.set(outbox, request, SetOptions.merge())
                    batch.set(inbox, request, SetOptions.merge())
                }.addOnSuccessListener { onComplete(true, "Friend request sent!") }
                    .addOnFailureListener { onComplete(false, "I couldn't send that request yet.") }
            }
            .addOnFailureListener { onComplete(false, "I couldn't check that code yet.") }
    }

    fun acceptFriendRequest(request: FriendRequest, onComplete: (Boolean) -> Unit = {}) {
        val uid = auth.currentUser?.uid ?: run { onComplete(false); return }
        if (request.recipientUid != uid) { onComplete(false); return }
        val outbox = userCollection(request.senderUid).collection("friendRequests").document(request.recipientUid)
        val inbox = userCollection(request.recipientUid).collection("incomingFriendRequests").document(request.senderUid)
        firestore.runBatch { batch ->
            batch.update(outbox, "status", "accepted", "updatedAt", FieldValue.serverTimestamp())
            batch.set(inbox, mapOf("status" to "accepted", "updatedAt" to FieldValue.serverTimestamp()), SetOptions.merge())
        }
            .addOnSuccessListener {
                saveFriend(uid, request.senderUid, request.senderName, request.senderPetName)
                onComplete(true)
            }
            .addOnFailureListener { onComplete(false) }
    }

    fun declineFriendRequest(request: FriendRequest, onComplete: (Boolean) -> Unit = {}) {
        val uid = auth.currentUser?.uid ?: run { onComplete(false); return }
        if (request.recipientUid != uid) { onComplete(false); return }
        val outbox = userCollection(request.senderUid).collection("friendRequests").document(request.recipientUid)
        val inbox = userCollection(request.recipientUid).collection("incomingFriendRequests").document(request.senderUid)
        firestore.runBatch { batch ->
            batch.update(outbox, "status", "declined", "updatedAt", FieldValue.serverTimestamp())
            batch.set(inbox, mapOf("status" to "declined", "updatedAt" to FieldValue.serverTimestamp()), SetOptions.merge())
        }
            .addOnSuccessListener { onComplete(true) }
            .addOnFailureListener { onComplete(false) }
    }

    fun sendPlayRequest(friend: Friend, onComplete: (Boolean, String) -> Unit) {
        val uid = auth.currentUser?.uid
        val currentProfile = profile
        if (uid == null || currentProfile == null) {
            onComplete(false, "Sign in with Google first.")
            return
        }
        val request = mapOf(
            "senderUid" to uid,
            "recipientUid" to friend.uid,
            "senderName" to currentProfile.displayName,
            "senderPetName" to currentProfile.petName,
            "recipientName" to friend.displayName,
            "recipientPetName" to friend.petName,
            "status" to "pending",
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        val outbox = userCollection(uid).collection("playRequests").document(friend.uid)
        val inbox = userCollection(friend.uid).collection("incomingPlayRequests").document(uid)
        firestore.runBatch { batch ->
            batch.set(outbox, request, SetOptions.merge())
            batch.set(inbox, request, SetOptions.merge())
        }.addOnSuccessListener { onComplete(true, "Play request sent!") }
            .addOnFailureListener { onComplete(false, "I couldn't send a play request yet.") }
    }

    fun acceptPlayRequest(request: PlayRequest, onComplete: (String?) -> Unit = {}) {
        val uid = auth.currentUser?.uid ?: run { onComplete(null); return }
        if (request.recipientUid != uid || request.status != "pending") { onComplete(null); return }
        val sessionId = UUID.randomUUID().toString()
        val sessionRef = firestore.collection("playSessions").document(sessionId)
        val outbox = userCollection(request.senderUid).collection("playRequests").document(request.recipientUid)
        val inbox = userCollection(request.recipientUid).collection("incomingPlayRequests").document(request.senderUid)
        val session = mapOf(
            "members" to listOf(request.senderUid, request.recipientUid),
            "status" to "active",
            "createdAt" to FieldValue.serverTimestamp(),
            "lastActivity" to FieldValue.serverTimestamp()
        )
        firestore.runBatch { batch ->
            batch.update(outbox, "status", "accepted", "sessionId", sessionId, "updatedAt", FieldValue.serverTimestamp())
            batch.set(inbox, mapOf("status" to "accepted", "sessionId" to sessionId, "updatedAt" to FieldValue.serverTimestamp()), SetOptions.merge())
            batch.set(sessionRef, session)
        }.addOnSuccessListener { onComplete(sessionId) }
            .addOnFailureListener { onComplete(null) }
    }

    fun declinePlayRequest(request: PlayRequest, onComplete: (Boolean) -> Unit = {}) {
        val uid = auth.currentUser?.uid ?: run { onComplete(false); return }
        if (request.recipientUid != uid) { onComplete(false); return }
        val outbox = userCollection(request.senderUid).collection("playRequests").document(request.recipientUid)
        val inbox = userCollection(request.recipientUid).collection("incomingPlayRequests").document(request.senderUid)
        firestore.runBatch { batch ->
            batch.update(outbox, "status", "declined", "updatedAt", FieldValue.serverTimestamp())
            batch.set(inbox, mapOf("status" to "declined", "updatedAt" to FieldValue.serverTimestamp()), SetOptions.merge())
        }
            .addOnSuccessListener { onComplete(true) }
            .addOnFailureListener { onComplete(false) }
    }

    fun sendPlayEvent(sessionId: String, action: String) {
        val uid = auth.currentUser?.uid ?: return
        val event = mapOf(
            "actorUid" to uid,
            "action" to action,
            "createdAt" to FieldValue.serverTimestamp()
        )
        val sessionRef = firestore.collection("playSessions").document(sessionId)
        sessionRef.collection("events").document(UUID.randomUUID().toString()).set(event)
        sessionRef.update("lastActivity", FieldValue.serverTimestamp())
    }

    fun endPlaySession(sessionId: String) {
        firestore.collection("playSessions").document(sessionId)
            .update("status", "ended", "lastActivity", FieldValue.serverTimestamp())
    }

    fun watchSession(
        sessionId: String,
        onEvents: (List<PlayEvent>) -> Unit,
        onStatus: (String) -> Unit
    ): SessionSubscription {
        val sessionRef = firestore.collection("playSessions").document(sessionId)
        val sessionRegistration = sessionRef.addSnapshotListener { snapshot, _ ->
            if (snapshot != null && snapshot.exists()) onStatus(snapshot.getString("status") ?: "ended")
        }
        val eventsRegistration = sessionRef.collection("events")
            .orderBy("createdAt")
            .limitToLast(40)
            .addSnapshotListener { snapshot, _ ->
                val events = snapshot?.documents.orEmpty().mapNotNull(::parsePlayEvent)
                onEvents(events)
            }
        return SessionSubscription(listOf(sessionRegistration, eventsRegistration))
    }

    private fun listenForSocialChanges(uid: String) {
        registrations += userCollection(uid).collection("incomingFriendRequests")
            .addSnapshotListener { snapshot, _ ->
                val incoming = snapshot?.documents.orEmpty().mapNotNull(::parseFriendRequest)
                    .filter { it.recipientUid == uid }
                emitFriendRequests(incoming, null)
            }
        registrations += userCollection(uid).collection("friendRequests")
            .addSnapshotListener { snapshot, _ ->
                val outgoing = snapshot?.documents.orEmpty().mapNotNull(::parseFriendRequest)
                emitFriendRequests(null, outgoing)
                outgoing.filter { it.status == "accepted" }.forEach {
                    saveFriend(uid, it.recipientUid, it.senderName, it.senderPetName)
                }
            }
        registrations += userCollection(uid).collection("friends")
            .addSnapshotListener { snapshot, _ ->
                friendDocuments = snapshot?.documents.orEmpty().associate { document ->
                    val friendUid = document.getString("friendUid") ?: document.id
                    friendUid to document.data.orEmpty()
                }
                refreshFriendProfiles()
            }
        registrations += userCollection(uid).collection("incomingPlayRequests")
            .addSnapshotListener { snapshot, _ ->
                val incoming = snapshot?.documents.orEmpty().mapNotNull(::parsePlayRequest)
                    .filter { it.recipientUid == uid }
                emitPlayRequests(incoming, null)
            }
        registrations += userCollection(uid).collection("playRequests")
            .addSnapshotListener { snapshot, _ ->
                emitPlayRequests(null, snapshot?.documents.orEmpty().mapNotNull(::parsePlayRequest))
            }
    }

    private var incomingFriendRequests: List<FriendRequest> = emptyList()
    private var outgoingFriendRequests: List<FriendRequest> = emptyList()
    private var incomingPlayRequests: List<PlayRequest> = emptyList()
    private var outgoingPlayRequests: List<PlayRequest> = emptyList()

    private fun emitFriendRequests(incoming: List<FriendRequest>?, outgoing: List<FriendRequest>?) {
        if (incoming != null) incomingFriendRequests = incoming
        if (outgoing != null) outgoingFriendRequests = outgoing
        onFriendRequestsChanged?.invoke((incomingFriendRequests + outgoingFriendRequests).distinctBy { it.documentPath })
    }

    private fun emitPlayRequests(incoming: List<PlayRequest>?, outgoing: List<PlayRequest>?) {
        if (incoming != null) incomingPlayRequests = incoming
        if (outgoing != null) outgoingPlayRequests = outgoing
        onPlayRequestsChanged?.invoke((incomingPlayRequests + outgoingPlayRequests).distinctBy { it.documentPath })
    }

    private fun saveFriend(uid: String, friendUid: String, friendName: String, friendPetName: String) {
        if (uid == friendUid) return
        userCollection(uid).collection("friends").document(friendUid).set(
            mapOf(
                "friendUid" to friendUid,
                "displayName" to friendName,
                "petName" to friendPetName,
                "createdAt" to FieldValue.serverTimestamp()
            ), SetOptions.merge()
        )
    }

    private fun refreshFriendProfiles() {
        if (friendDocuments.isEmpty()) {
            onFriendsChanged?.invoke(emptyList())
            return
        }
        friendDocuments.keys.forEach { friendUid ->
            profileRef(friendUid).get().addOnSuccessListener { snapshot ->
                friendProfileCache[friendUid] = snapshot.data.orEmpty().toMutableMap().apply {
                    this["lastSeenMillis"] = snapshot.getDate("lastSeen")?.time ?: 0L
                }
                emitFriends()
            }
        }
        emitFriends()
    }

    private fun emitFriends() {
        val now = System.currentTimeMillis()
        val friends = friendDocuments.map { (friendUid, base) ->
            val remote = friendProfileCache[friendUid].orEmpty()
            val lastSeen = (remote["lastSeenMillis"] as? Number)?.toLong() ?: 0L
            Friend(
                uid = friendUid,
                displayName = (remote["displayName"] as? String)
                    ?: (base["displayName"] as? String).orEmpty().ifBlank { "Friend" },
                petName = (remote["petName"] as? String)
                    ?: (base["petName"] as? String).orEmpty().ifBlank { "Mochi" },
                petKind = (remote["petKind"] as? String).orEmpty(),
                stage = (remote["stage"] as? String).orEmpty(),
                online = remote["online"] == true && lastSeen > 0L && abs(now - lastSeen) < ONLINE_WINDOW_MS,
                lastSeen = lastSeen
            )
        }.sortedWith(compareByDescending<Friend> { it.online }.thenBy { it.displayName.lowercase() })
        onFriendsChanged?.invoke(friends)
    }

    private fun writePresence(online: Boolean) {
        val uid = activeUid ?: auth.currentUser?.uid ?: return
        val currentProfile = profile ?: return
        profileRef(uid).set(
            mapOf(
                "displayName" to currentProfile.displayName,
                "petName" to currentProfile.petName,
                "petKind" to currentProfile.petKind,
                "stage" to currentProfile.stage,
                "hatched" to currentProfile.hatched,
                "online" to online,
                "lastSeen" to FieldValue.serverTimestamp()
            ), SetOptions.merge()
        )
    }

    private fun clearListeners() {
        registrations.forEach { it.remove() }
        registrations.clear()
        incomingFriendRequests = emptyList()
        outgoingFriendRequests = emptyList()
        incomingPlayRequests = emptyList()
        outgoingPlayRequests = emptyList()
        friendDocuments = emptyMap()
        friendProfileCache.clear()
    }

    private fun userCollection(uid: String) = firestore.collection("users").document(uid)
    private fun profileRef(uid: String) = userCollection(uid).collection("public").document("profile")

    private fun parseFriendRequest(document: DocumentSnapshot): FriendRequest? {
        val senderUid = document.getString("senderUid") ?: return null
        val recipientUid = document.getString("recipientUid") ?: return null
        return FriendRequest(
            id = document.id,
            senderUid = senderUid,
            recipientUid = recipientUid,
            senderName = document.getString("senderName").orEmpty().ifBlank { "A friend" },
            senderPetName = document.getString("senderPetName").orEmpty().ifBlank { "Mochi" },
            status = document.getString("status").orEmpty(),
            documentPath = document.reference.path
        )
    }

    private fun parsePlayRequest(document: DocumentSnapshot): PlayRequest? {
        val senderUid = document.getString("senderUid") ?: return null
        val recipientUid = document.getString("recipientUid") ?: return null
        return PlayRequest(
            id = document.id,
            senderUid = senderUid,
            recipientUid = recipientUid,
            senderName = document.getString("senderName").orEmpty().ifBlank { "A friend" },
            senderPetName = document.getString("senderPetName").orEmpty().ifBlank { "Mochi" },
            recipientName = document.getString("recipientName").orEmpty().ifBlank { "Friend" },
            recipientPetName = document.getString("recipientPetName").orEmpty().ifBlank { "Mochi" },
            status = document.getString("status").orEmpty(),
            sessionId = document.getString("sessionId"),
            documentPath = document.reference.path
        )
    }

    private fun parsePlayEvent(document: DocumentSnapshot): PlayEvent? {
        val actorUid = document.getString("actorUid") ?: return null
        val action = document.getString("action") ?: return null
        return PlayEvent(document.id, actorUid, action, document.getDate("createdAt")?.time ?: 0L)
    }

    private fun randomCode(): String = buildString {
        repeat(CODE_LENGTH) { append(CODE_ALPHABET.random()) }
    }

    companion object {
        private const val CODE_LENGTH = 6
        private const val MAX_CODE_ATTEMPTS = 5
        private const val HEARTBEAT_MS = 20_000L
        private const val ONLINE_WINDOW_MS = 65_000L
        private const val CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    }
}
