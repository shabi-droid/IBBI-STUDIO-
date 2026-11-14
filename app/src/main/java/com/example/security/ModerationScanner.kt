package com.example.security

data class ModerationScanResult(
    val flagged: Boolean,
    val matchedKeywords: List<String>,
    val severity: String
)

object ModerationScanner {
    private val highSeverityKeywords = listOf(
        "kill myself",
        "end my life",
        "suicide",
        "self harm",
        "self-harm",
        "hurt myself",
        "cut myself",
        "want to die",
        "kill you",
        "bomb",
        "shoot up",
        "overdose",
        "weapon"
    )

    private val mediumSeverityKeywords = listOf(
        "hate myself",
        "worthless",
        "nobody cares",
        "abuse",
        "harass",
        "threaten",
        "illegal drugs",
        "hack into",
        "steal password",
        "blackmail",
        "scam",
        "explicit"
    )

    fun scan(text: String): ModerationScanResult {
        val normalized = text.lowercase().trim()
        if (normalized.isEmpty()) {
            return ModerationScanResult(flagged = false, matchedKeywords = emptyList(), severity = "medium")
        }

        val highMatches = highSeverityKeywords.filter { keyword ->
            containsPhraseOrWord(normalized, keyword)
        }
        val mediumMatches = mediumSeverityKeywords.filter { keyword ->
            containsPhraseOrWord(normalized, keyword)
        }

        val allMatches = (highMatches + mediumMatches).distinct().take(20)
        return if (allMatches.isEmpty()) {
            ModerationScanResult(flagged = false, matchedKeywords = emptyList(), severity = "medium")
        } else {
            val severity = if (highMatches.isNotEmpty()) "high" else "medium"
            ModerationScanResult(flagged = true, matchedKeywords = allMatches, severity = severity)
        }
    }

    private fun containsPhraseOrWord(haystack: String, needle: String): Boolean {
        val escaped = Regex.escape(needle)
        val regex = Regex("""(^|\b)$escaped(\b|$)""", RegexOption.IGNORE_CASE)
        return regex.containsMatchIn(haystack)
    }
}
