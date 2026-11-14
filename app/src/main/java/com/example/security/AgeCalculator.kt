package com.example.security

import java.util.Calendar

data class AgeVerificationResult(
    val isValid: Boolean,
    val age: Int,
    val isAdult: Boolean,
    val errorMessage: String? = null
)

object AgeCalculator {
    private val dobRegex = Regex("""^(\d{4})-(\d{2})-(\d{2})$""")

    fun evaluate(dateOfBirth: String, now: Calendar = Calendar.getInstance()): AgeVerificationResult {
        val trimmed = dateOfBirth.trim()
        val match = dobRegex.matchEntire(trimmed)
            ?: return AgeVerificationResult(
                isValid = false,
                age = 0,
                isAdult = false,
                errorMessage = "Use YYYY-MM-DD format (e.g., 2004-08-19)."
            )

        val year = match.groupValues[1].toIntOrNull() ?: return invalidDate()
        val month = match.groupValues[2].toIntOrNull() ?: return invalidDate()
        val day = match.groupValues[3].toIntOrNull() ?: return invalidDate()

        if (month !in 1..12 || day !in 1..31) return invalidDate()

        val dobCal = Calendar.getInstance().apply {
            isLenient = false
            clear()
            set(year, month - 1, day)
        }

        try {
            // Triggers strict validation for invalid calendar days like Feb 30
            dobCal.timeInMillis
        } catch (_: Exception) {
            return invalidDate()
        }

        if (dobCal.after(now)) {
            return AgeVerificationResult(
                isValid = false,
                age = 0,
                isAdult = false,
                errorMessage = "Date of birth cannot be in the future."
            )
        }

        var age = now.get(Calendar.YEAR) - dobCal.get(Calendar.YEAR)
        val currentMonth = now.get(Calendar.MONTH)
        val dobMonth = dobCal.get(Calendar.MONTH)
        if (currentMonth < dobMonth ||
            (currentMonth == dobMonth && now.get(Calendar.DAY_OF_MONTH) < dobCal.get(Calendar.DAY_OF_MONTH))
        ) {
            age--
        }

        if (age < 10 || age > 120) {
            return AgeVerificationResult(
                isValid = false,
                age = age,
                isAdult = false,
                errorMessage = "Please enter a valid date of birth (age 10–120)."
            )
        }

        return AgeVerificationResult(
            isValid = true,
            age = age,
            isAdult = age >= 18,
            errorMessage = null
        )
    }

    private fun invalidDate() = AgeVerificationResult(
        isValid = false,
        age = 0,
        isAdult = false,
        errorMessage = "Please enter a valid calendar date (YYYY-MM-DD)."
    )
}
