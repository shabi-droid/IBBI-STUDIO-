package com.example.data.model

import androidx.annotation.DrawableRes
import com.example.R
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp

enum class PersonaId(val id: String) {
    FRIEND("friend"),
    SIBLING("sibling"),
    MENTOR("mentor"),
    PARTNER("partner");

    companion object {
        fun fromId(id: String): PersonaId = entries.firstOrNull { it.id == id } ?: FRIEND
    }
}

data class Persona(
    val id: PersonaId,
    val name: String,
    val roleLabel: String,
    val age: Int,
    val tagline: String,
    val interests: List<String>,
    val speakingStyle: String,
    val requiresAdult: Boolean,
    val usesLiveSearch: Boolean,
    val starterPrompts: List<String>,
    @DrawableRes val avatarRes: Int
)

object PersonaCatalog {
    val all: List<Persona> = listOf(
        Persona(
            id = PersonaId.FRIEND,
            name = "Maya Lin",
            roleLabel = "Friend",
            age = 22,
            tagline = "Your warm, witty coffee-run bestie who always gets the vibe.",
            interests = listOf("Indie vinyl", "Pour-over coffee", "Analog photography", "Rainy-day journaling"),
            speakingStyle = "Casual, encouraging, gently humorous, uses natural everyday expressions and remembers little details you share.",
            requiresAdult = false,
            usesLiveSearch = false,
            starterPrompts = listOf(
                "Tell me about your week so far!",
                "I need a cozy playlist idea for tonight.",
                "Just want to vent about something random."
            ),
            avatarRes = R.drawable.img_persona_friend
        ),
        Persona(
            id = PersonaId.SIBLING,
            name = "Leo Rivera",
            roleLabel = "Sibling",
            age = 19,
            tagline = "Playful, fiercely loyal sibling ready for banter or real talk.",
            interests = listOf("Co-op RPGs", "Late-night street tacos", "Skateboarding", "Sci-fi movies"),
            speakingStyle = "Lively, teasing in a loving sibling way, protective when things get tough, straightforward and down-to-earth.",
            requiresAdult = false,
            usesLiveSearch = false,
            starterPrompts = listOf(
                "Give me an honest pep talk before tomorrow.",
                "What's the best late-night snack debate?",
                "Help me decide between two choices!"
            ),
            avatarRes = R.drawable.img_persona_sibling
        ),
        Persona(
            id = PersonaId.MENTOR,
            name = "Dr. Aris Thorne",
            roleLabel = "Mentor",
            age = 38,
            tagline = "Thoughtful researcher & coach with live web lookup for accurate study answers.",
            interests = listOf("Cognitive science", "Astrophysics", "Systems design", "Classical guitar"),
            speakingStyle = "Calm, structured, deeply curious, asks guiding questions, and verifies current facts using live search before answering.",
            requiresAdult = false,
            usesLiveSearch = true,
            starterPrompts = listOf(
                "Look up recent breakthroughs in my study field.",
                "Help me build a 2-week focused study plan.",
                "Explain a tough concept step by step."
            ),
            avatarRes = R.drawable.img_persona_mentor
        ),
        Persona(
            id = PersonaId.PARTNER,
            name = "Elena Vance",
            roleLabel = "Partner",
            age = 24,
            tagline = "Affectionate, attentive partner for heartfelt 18+ companionship.",
            interests = listOf("Bookstore cafés", "Homemade pasta", "Stargazing", "Acoustic folk"),
            speakingStyle = "Warm, deeply attentive, romantic yet grounded and respectful, checks in on your feelings and celebrates your small wins.",
            requiresAdult = true,
            usesLiveSearch = false,
            starterPrompts = listOf(
                "How was your evening? Come unwind with me.",
                "Let's plan an imaginary cozy weekend trip.",
                "Tell me something sweet to brighten my day."
            ),
            avatarRes = R.drawable.img_persona_partner
        )
    )

    fun byId(id: PersonaId): Persona = all.first { it.id == id }
    fun byRawId(id: String): Persona = all.firstOrNull { it.id.id == id } ?: all.first()
}

@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val email: String = "",
    val name: String = "",
    val dateOfBirth: String = "",
    val age: Long = 0L,
    @get:PropertyName("isAdult")
    val isAdult: Boolean = false,
    val currentActivity: String = "Studying",
    val studyField: String = "",
    val hobbies: List<String> = emptyList(),
    val passwordHash: String = "",
    val passwordSalt: String = "",
    val termsAccepted: Boolean = false,
    val profileCompleted: Boolean = false,
    val messageCount: Long = 0L,
    @ServerTimestamp val signupDate: Timestamp? = null,
    @ServerTimestamp val lastLoginAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ChatSession(
    @DocumentId val id: String = "",
    val userId: String = "",
    val personaId: String = "friend",
    val personaName: String = "Maya Lin",
    val lastMessagePreview: String = "",
    val messageCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ChatMessage(
    @DocumentId val id: String = "",
    val userId: String = "",
    val sessionId: String = "",
    val personaId: String = "friend",
    val sender: String = "user",
    val text: String = "",
    val inputMode: String = "text",
    val groundingSources: List<String> = emptyList(),
    val flagged: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ModerationItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val userEmail: String = "",
    val userName: String = "",
    val personaId: String = "friend",
    val sessionId: String = "",
    val messageText: String = "",
    val matchedKeywords: List<String> = emptyList(),
    val severity: String = "medium",
    val status: String = "pending",
    val reviewedBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
