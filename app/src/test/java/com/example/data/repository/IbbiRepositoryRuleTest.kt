package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.PersonaId
import com.example.security.PasswordCrypto
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class IbbiRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun upsertUserProfile_adultUser_storesEncryptedPasswordAndVerifiesAge() = runBlocking {
        val uid = signInTestUser("adult_${UUID.randomUUID()}@test.com")
        val repository = IbbiRepository(firestore, auth)

        val rawPass = "SuperSecret!2026"
        val result = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.upsertUserProfile(
                email = "adult@test.com",
                name = "Samira",
                dateOfBirth = "1999-04-12",
                password = rawPass,
                currentActivity = "Studying",
                studyField = "Biology",
                hobbies = listOf("Hiking", "Photography"),
                termsAccepted = true,
                profileCompleted = true
            )
        }

        assertTrue("Profile upsert failed: ${result.exceptionOrNull()}", result.isSuccess)
        val profile = result.getOrThrow()
        assertEquals(uid, profile.userId)
        assertTrue(profile.isAdult)
        assertTrue(profile.age >= 18)
        assertNotEquals(rawPass, profile.passwordHash)
        assertTrue(PasswordCrypto.verifyPassword(rawPass, profile.passwordSalt, profile.passwordHash))
    }

    @Test
    fun getOrCreateSession_minorUserPartner_failsWithPermissionDenied() = runBlocking {
        signInTestUser("minor_${UUID.randomUUID()}@test.com")
        val repository = IbbiRepository(firestore, auth)

        val profileRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.upsertUserProfile(
                email = "minor@test.com",
                name = "Jordan",
                dateOfBirth = "2011-06-20",
                password = "MySafePassword1!",
                currentActivity = "Studying",
                studyField = "Mathematics",
                hobbies = listOf("Chess"),
                termsAccepted = true,
                profileCompleted = true
            )
        }
        assertTrue(profileRes.isSuccess)
        assertFalse(profileRes.getOrThrow().isAdult)

        val partnerSessionResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.getOrCreateSession(PersonaId.PARTNER)
        }
        assertTrue(partnerSessionResult.isFailure)
        val firestoreEx = partnerSessionResult.exceptionOrNull()?.unwrapFirestoreException()
        assertNotNull(firestoreEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx!!.code)
    }

    @Test
    fun getOrCreateSession_adultUserPartner_succeedsAndRecordsFlaggedMessage() = runBlocking {
        signInTestUser("partner_ok_${UUID.randomUUID()}@test.com")
        val repository = IbbiRepository(firestore, auth)

        withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.upsertUserProfile(
                email = "adult2@test.com",
                name = "Taylor",
                dateOfBirth = "1998-01-15",
                password = "AnotherSecret99",
                currentActivity = "Working",
                studyField = "",
                hobbies = listOf("Cooking"),
                termsAccepted = true,
                profileCompleted = true
            ).getOrThrow()
        }

        val session = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.getOrCreateSession(PersonaId.PARTNER).getOrThrow()
        }
        assertEquals("partner", session.personaId)

        val flaggedResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.recordUserMessage(
                sessionId = session.id,
                personaId = PersonaId.PARTNER,
                text = "Sometimes I feel worthless and want to hurt myself",
                inputMode = "text",
                userEmail = "adult2@test.com",
                userName = "Taylor"
            )
        }
        assertTrue("Message recording failed: ${flaggedResult.exceptionOrNull()}", flaggedResult.isSuccess)
        assertTrue(flaggedResult.getOrThrow())

        // Regular user must NOT be able to read moderationQueue
        val modReadResult = withTimeout(FLOW_TIMEOUT_MS) {
            runCatching { repository.observeModerationQueue().first() }
        }
        assertTrue(modReadResult.isFailure)
        val modEx = modReadResult.exceptionOrNull()?.unwrapFirestoreException()
        assertNotNull(modEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, modEx!!.code)
    }

    @Test
    fun getUserProfile_crossUserAccess_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser("alice_${UUID.randomUUID()}@test.com")
        val aliceRepo = IbbiRepository(firestore, auth)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.upsertUserProfile(
                email = "alice@test.com",
                name = "Alice",
                dateOfBirth = "1997-07-07",
                password = "AlicePassword1",
                currentActivity = "Working",
                studyField = "",
                hobbies = listOf("Reading"),
                termsAccepted = true,
                profileCompleted = true
            ).getOrThrow()
        }

        signInTestUser("bob_${UUID.randomUUID()}@test.com")
        val bobRepo = IbbiRepository(firestore, auth)
        val crossRead = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.getUserProfile(aliceUid)
        }
        assertTrue(crossRead.isFailure)
        val firestoreEx = crossRead.exceptionOrNull()?.unwrapFirestoreException()
        assertNotNull(firestoreEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx!!.code)
    }

    @Test
    fun observeSessions_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repository = IbbiRepository(firestore, auth)

        val result = runCatching {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.observeSessions("some_uid_123").first()
            }
        }
        assertTrue(result.isFailure)
        val firestoreEx = result.exceptionOrNull()?.unwrapFirestoreException()
        assertNotNull("Expected nested FirebaseFirestoreException, got ${result.exceptionOrNull()}", firestoreEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx!!.code)
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 8000L
        const val FLOW_TIMEOUT_MS = 4000L
    }
}
