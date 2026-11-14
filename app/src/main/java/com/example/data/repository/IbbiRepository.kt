package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.ChatMessage
import com.example.data.model.ChatSession
import com.example.data.model.ModerationItem
import com.example.data.model.PersonaCatalog
import com.example.data.model.PersonaId
import com.example.data.model.UserProfile
import com.example.security.AgeCalculator
import com.example.security.ModerationScanner
import com.example.security.PasswordCrypto
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class IbbiRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        auth = Firebase.auth
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun observeUserProfile(userId: String): Flow<UserProfile?> = flow {
        val path = "users/$userId"
        emitAll(
            db.collection("users").document(userId)
                .snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            UserProfile::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else {
                        null
                    }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(toLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    suspend fun getUserProfile(userId: String): Result<UserProfile?> = runCatchingWithCancel {
        val path = "users/$userId"
        try {
            val snapshot = db.collection("users").document(userId).get().await()
            if (snapshot.exists()) {
                snapshot.toObject(
                    UserProfile::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            } else {
                null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            throw e
        }
    }

    suspend fun upsertUserProfile(
        email: String,
        name: String,
        dateOfBirth: String,
        password: String?,
        currentActivity: String,
        studyField: String,
        hobbies: List<String>,
        termsAccepted: Boolean,
        profileCompleted: Boolean
    ): Result<UserProfile> = runCatchingWithCancel {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
        val ageEval = AgeCalculator.evaluate(dateOfBirth)
        require(ageEval.isValid) { ageEval.errorMessage ?: "Invalid date of birth" }
        require(termsAccepted) { "Terms must be accepted." }

        val existingSnap = try {
            docRef.get().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            throw e
        }

        val cleanHobbies = hobbies.map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(10)
        val cleanStudyField = if (currentActivity == "Studying" || currentActivity == "Both") {
            studyField.trim().take(120)
        } else {
            ""
        }

        if (!existingSnap.exists()) {
            val rawPassword = password?.takeIf { it.isNotEmpty() }
                ?: throw IllegalArgumentException("Password is required for registration.")
            val hashed = PasswordCrypto.hashPassword(rawPassword)
            val payload = mapOf(
                "userId" to uid,
                "email" to email.trim(),
                "name" to name.trim(),
                "dateOfBirth" to dateOfBirth.trim(),
                "age" to ageEval.age.toLong(),
                "isAdult" to ageEval.isAdult,
                "currentActivity" to currentActivity,
                "studyField" to cleanStudyField,
                "hobbies" to cleanHobbies,
                "passwordHash" to hashed.hash,
                "passwordSalt" to hashed.salt,
                "termsAccepted" to true,
                "profileCompleted" to profileCompleted,
                "messageCount" to 0L,
                "signupDate" to FieldValue.serverTimestamp(),
                "lastLoginAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            try {
                docRef.set(payload).await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.CREATE, docRef.path)
                throw e
            }
        } else {
            val existingProfile = existingSnap.toObject(
                UserProfile::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: throw IllegalStateException("Failed to deserialize existing profile.")

            val (hash, salt) = if (!password.isNullOrEmpty()) {
                val hashed = PasswordCrypto.hashPassword(password)
                hashed.hash to hashed.salt
            } else {
                existingProfile.passwordHash to existingProfile.passwordSalt
            }

            val updates = mapOf(
                "email" to email.trim(),
                "name" to name.trim(),
                "dateOfBirth" to dateOfBirth.trim(),
                "age" to ageEval.age.toLong(),
                "isAdult" to ageEval.isAdult,
                "currentActivity" to currentActivity,
                "studyField" to cleanStudyField,
                "hobbies" to cleanHobbies,
                "passwordHash" to hash,
                "passwordSalt" to salt,
                "termsAccepted" to true,
                "profileCompleted" to profileCompleted,
                "lastLoginAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            try {
                docRef.update(updates).await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.UPDATE, docRef.path)
                throw e
            }
        }

        docRef.get().await().toObject(
            UserProfile::class.java,
            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
        ) ?: UserProfile(
            id = uid,
            userId = uid,
            email = email.trim(),
            name = name.trim(),
            dateOfBirth = dateOfBirth.trim(),
            age = ageEval.age.toLong(),
            isAdult = ageEval.isAdult,
            currentActivity = currentActivity,
            studyField = cleanStudyField,
            hobbies = cleanHobbies,
            termsAccepted = true,
            profileCompleted = profileCompleted
        )
    }

    suspend fun touchLastLogin(): Result<Unit> = runCatchingWithCancel {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
        try {
            val snap = docRef.get().await()
            if (snap.exists()) {
                docRef.update(
                    mapOf(
                        "lastLoginAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    fun observeSessions(userId: String): Flow<List<ChatSession>> = flow {
        val path = "users/$userId/sessions"
        emitAll(
            db.collection("users").document(userId).collection("sessions")
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snap ->
                    snap.toObjects(
                        ChatSession::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun getOrCreateSession(personaId: PersonaId): Result<ChatSession> = runCatchingWithCancel {
        val uid = requireUserId()
        val sessionId = "session_${personaId.id}"
        val sessionRef = db.collection("users").document(uid).collection("sessions").document(sessionId)
        val persona = PersonaCatalog.byId(personaId)

        val existing = try {
            sessionRef.get().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, sessionRef.path)
            throw e
        }

        if (existing.exists()) {
            return@runCatchingWithCancel existing.toObject(
                ChatSession::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: ChatSession(id = sessionId, userId = uid, personaId = personaId.id, personaName = persona.name)
        }

        val payload = mapOf(
            "userId" to uid,
            "personaId" to personaId.id,
            "personaName" to persona.name,
            "lastMessagePreview" to "",
            "messageCount" to 0L,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            sessionRef.set(payload).await()
            sessionRef.get().await().toObject(
                ChatSession::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: ChatSession(id = sessionId, userId = uid, personaId = personaId.id, personaName = persona.name)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, sessionRef.path)
            throw e
        }
    }

    fun observeMessages(userId: String, sessionId: String): Flow<List<ChatMessage>> = flow {
        val path = "users/$userId/sessions/$sessionId/messages"
        emitAll(
            db.collection("users").document(userId)
                .collection("sessions").document(sessionId)
                .collection("messages")
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .snapshots()
                .map { snap ->
                    snap.toObjects(
                        ChatMessage::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun recordUserMessage(
        sessionId: String,
        personaId: PersonaId,
        text: String,
        inputMode: String,
        userEmail: String,
        userName: String
    ): Result<Boolean> = runCatchingWithCancel {
        val uid = requireUserId()
        val trimmed = text.trim().take(8000)
        val scanResult = ModerationScanner.scan(trimmed)

        val userRef = db.collection("users").document(uid)
        val sessionRef = userRef.collection("sessions").document(sessionId)
        val msgRef = sessionRef.collection("messages").document()

        val msgPayload = mapOf(
            "userId" to uid,
            "sessionId" to sessionId,
            "personaId" to personaId.id,
            "sender" to "user",
            "text" to trimmed,
            "inputMode" to inputMode,
            "groundingSources" to emptyList<String>(),
            "flagged" to scanResult.flagged,
            "createdAt" to FieldValue.serverTimestamp()
        )

        val batch = db.batch()
        batch.set(msgRef, msgPayload)
        batch.update(
            sessionRef,
            mapOf(
                "lastMessagePreview" to trimmed.take(140),
                "messageCount" to FieldValue.increment(1L),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        batch.update(
            userRef,
            mapOf(
                "messageCount" to FieldValue.increment(1L),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )

        if (scanResult.flagged) {
            val flagRef = db.collection("moderationQueue").document()
            val flagPayload = mapOf(
                "userId" to uid,
                "userEmail" to userEmail.ifBlank { auth.currentUser?.email ?: "unknown@user" },
                "userName" to userName.ifBlank { "IBBI User" },
                "personaId" to personaId.id,
                "sessionId" to sessionId,
                "messageText" to trimmed,
                "matchedKeywords" to scanResult.matchedKeywords,
                "severity" to scanResult.severity,
                "status" to "pending",
                "reviewedBy" to "",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            batch.set(flagRef, flagPayload)
        }

        try {
            batch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, msgRef.path)
            throw e
        }

        scanResult.flagged
    }

    suspend fun recordModelMessage(
        sessionId: String,
        personaId: PersonaId,
        text: String,
        inputMode: String,
        groundingSources: List<String> = emptyList()
    ): Result<Unit> = runCatchingWithCancel {
        val uid = requireUserId()
        val trimmed = text.trim().take(8000)
        val userRef = db.collection("users").document(uid)
        val sessionRef = userRef.collection("sessions").document(sessionId)
        val msgRef = sessionRef.collection("messages").document()

        val msgPayload = mapOf(
            "userId" to uid,
            "sessionId" to sessionId,
            "personaId" to personaId.id,
            "sender" to "model",
            "text" to trimmed,
            "inputMode" to inputMode,
            "groundingSources" to groundingSources.take(10),
            "flagged" to false,
            "createdAt" to FieldValue.serverTimestamp()
        )

        val batch = db.batch()
        batch.set(msgRef, msgPayload)
        batch.update(
            sessionRef,
            mapOf(
                "lastMessagePreview" to trimmed.take(140),
                "messageCount" to FieldValue.increment(1L),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )

        try {
            batch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, msgRef.path)
            throw e
        }
    }

    // --- Admin Dashboard Operations ---

    suspend fun checkAdminAccess(): Result<Boolean> = runCatchingWithCancel {
        val user = auth.currentUser ?: return@runCatchingWithCancel false
        if (user.isEmailVerified && user.email == "gzainab500@gmail.com") {
            return@runCatchingWithCancel true
        }
        val path = "admins/${user.uid}"
        try {
            val snap = db.collection("admins").document(user.uid).get().await()
            snap.exists()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            false
        }
    }

    fun observeAllUsersForAdmin(): Flow<List<UserProfile>> = flow {
        val path = "users"
        emitAll(
            db.collection("users")
                .orderBy("signupDate", Query.Direction.DESCENDING)
                .snapshots()
                .map { snap ->
                    snap.toObjects(
                        UserProfile::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeModerationQueue(): Flow<List<ModerationItem>> = flow {
        val path = "moderationQueue"
        emitAll(
            db.collection("moderationQueue")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snap ->
                    snap.toObjects(
                        ModerationItem::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun updateModerationStatus(
        flagId: String,
        newStatus: String
    ): Result<Unit> = runCatchingWithCancel {
        require(newStatus in listOf("pending", "reviewed", "dismissed"))
        val reviewer = auth.currentUser?.email ?: requireUserId()
        val docRef = db.collection("moderationQueue").document(flagId)
        try {
            docRef.update(
                mapOf(
                    "status" to newStatus,
                    "reviewedBy" to reviewer,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    private inline fun <T> runCatchingWithCancel(block: () -> T): Result<T> {
        return try {
            Result.success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
