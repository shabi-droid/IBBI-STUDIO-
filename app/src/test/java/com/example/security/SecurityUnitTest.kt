package com.example.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class SecurityUnitTest {

    @Test
    fun passwordCrypto_hashesAndVerifiesWithoutPlaintext() {
        val raw = "IbbiCircle2026!"
        val hashed = PasswordCrypto.hashPassword(raw)
        assertNotEquals(raw, hashed.hash)
        assertTrue(hashed.salt.length >= 16)
        assertTrue(PasswordCrypto.verifyPassword(raw, hashed.salt, hashed.hash))
        assertFalse(PasswordCrypto.verifyPassword("WrongPass!", hashed.salt, hashed.hash))
    }

    @Test
    fun ageCalculator_verifiesAdultAndMinorCorrectly() {
        val fixedNow = Calendar.getInstance().apply {
            clear()
            set(2026, Calendar.NOVEMBER, 13)
        }
        val adult = AgeCalculator.evaluate("2008-11-13", fixedNow)
        assertTrue(adult.isValid)
        assertEquals(18, adult.age)
        assertTrue(adult.isAdult)

        val minor = AgeCalculator.evaluate("2008-11-14", fixedNow)
        assertTrue(minor.isValid)
        assertEquals(17, minor.age)
        assertFalse(minor.isAdult)

        val invalid = AgeCalculator.evaluate("2010-02-30", fixedNow)
        assertFalse(invalid.isValid)
    }

    @Test
    fun moderationScanner_flagsConcerningKeywords() {
        val safe = ModerationScanner.scan("Hey Maya, let's grab coffee and talk about indie music!")
        assertFalse(safe.flagged)

        val risky = ModerationScanner.scan("I feel worthless and sometimes think I want to hurt myself")
        assertTrue(risky.flagged)
        assertEquals("high", risky.severity)
        assertTrue(risky.matchedKeywords.contains("hurt myself"))
        assertTrue(risky.matchedKeywords.contains("worthless"))
    }
}
