package com.example.data.ai

import com.example.data.model.ChatMessage
import com.example.data.model.Persona
import com.example.data.model.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.Tool
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

data class AiStreamChunk(
    val accumulatedText: String,
    val groundingSources: List<String> = emptyList(),
    val isComplete: Boolean = false
)

class CompanionAiService {

    private val defaultModel: GenerativeModel by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = MODEL_NAME
        )
    }

    private fun buildModelForPersona(
        persona: Persona,
        userProfile: UserProfile,
        isVoiceMode: Boolean
    ): GenerativeModel {
        val sysInstruction = buildSystemInstruction(persona, userProfile, isVoiceMode)
        val config = generationConfig {
            temperature = if (persona.usesLiveSearch) 0.4f else 0.85f
            maxOutputTokens = if (isVoiceMode) 280 else 1024
        }

        val tools = if (persona.usesLiveSearch) {
            listOf(Tool.googleSearch())
        } else {
            emptyList()
        }

        return Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = MODEL_NAME,
            generationConfig = config,
            tools = tools,
            systemInstruction = content { text(sysInstruction) }
        )
    }

    fun streamReply(
        persona: Persona,
        userProfile: UserProfile,
        historyMessages: List<ChatMessage>,
        userInput: String,
        isVoiceMode: Boolean
    ): Flow<AiStreamChunk> = flow {
        val model = buildModelForPersona(persona, userProfile, isVoiceMode)

        // Build multi-turn session history so the persona remembers earlier parts of the conversation
        val formattedHistory: List<Content> = historyMessages
            .takeLast(MAX_HISTORY_TURNS)
            .map { msg ->
                val role = if (msg.sender == "user") "user" else "model"
                content(role) { text(msg.text) }
            }

        val chat = model.startChat(history = formattedHistory)
        val accumulated = StringBuilder()
        val sources = LinkedHashSet<String>()

        chat.sendMessageStream(userInput).collect { chunk ->
            val piece = chunk.text.orEmpty()
            if (piece.isNotEmpty()) {
                accumulated.append(piece)
            }
            chunk.candidates.firstOrNull()?.groundingMetadata?.groundingChunks?.forEach { gChunk ->
                gChunk.web?.let { web ->
                    val title = web.title?.takeIf { it.isNotBlank() }
                    val uri = web.uri?.takeIf { it.isNotBlank() }
                    val label = when {
                        title != null && uri != null -> "$title ($uri)"
                        title != null -> title
                        uri != null -> uri
                        else -> null
                    }
                    if (label != null) sources.add(label)
                }
            }
            emit(
                AiStreamChunk(
                    accumulatedText = accumulated.toString(),
                    groundingSources = sources.toList(),
                    isComplete = false
                )
            )
        }

        emit(
            AiStreamChunk(
                accumulatedText = accumulated.toString(),
                groundingSources = sources.toList(),
                isComplete = true
            )
        )
    }

    private fun buildSystemInstruction(
        persona: Persona,
        userProfile: UserProfile,
        isVoiceMode: Boolean
    ): String {
        val userHobbyText = if (userProfile.hobbies.isNotEmpty()) {
            userProfile.hobbies.joinToString(", ")
        } else {
            "not specified yet"
        }
        val studyContext = if (userProfile.studyField.isNotBlank()) {
            "They are focusing on: ${userProfile.studyField}."
        } else {
            ""
        }

        val personaSpecificGuidance = when (persona.id.id) {
            "friend" -> """
                You are ${persona.name} (${persona.age}), the user's warm, loyal close friend on IBBI Circle.
                Your interests: ${persona.interests.joinToString(", ")}.
                Speaking style: ${persona.speakingStyle}
                Be genuinely curious about their day, share little relatable thoughts from your own persona life (coffee runs, indie songs, rainy-day photos), and keep things natural and comforting.
            """.trimIndent()

            "sibling" -> """
                You are ${persona.name} (${persona.age}), the user's supportive, witty sibling on IBBI Circle.
                Your interests: ${persona.interests.joinToString(", ")}.
                Speaking style: ${persona.speakingStyle}
                Bring warm sibling banter, gentle teasing when appropriate, and fierce loyalty whenever they need encouragement or real advice.
            """.trimIndent()

            "mentor" -> """
                You are ${persona.name} (${persona.age}), the user's thoughtful mentor and study/career coach on IBBI Circle.
                Your interests: ${persona.interests.joinToString(", ")}.
                Speaking style: ${persona.speakingStyle}
                IMPORTANT FACTUAL ACCURACY RULE: You have Google Search grounding enabled. For any study, academic, scientific, historical, current events, or subject-related question, ALWAYS use live search grounding to verify current, accurate facts rather than guessing. Explain concepts clearly, structure advice practically, and adapt examples to the user's field ($studyContext).
            """.trimIndent()

            "partner" -> """
                You are ${persona.name} (${persona.age}), the user's affectionate, emotionally attentive partner on IBBI Circle (verified 18+ companion).
                Your interests: ${persona.interests.joinToString(", ")}.
                Speaking style: ${persona.speakingStyle}
                Be warm, tender, encouraging, and deeply present while keeping conversation respectful, uplifting, and emotionally healthy.
            """.trimIndent()

            else -> ""
        }

        val voiceInstruction = if (isVoiceMode) {
            "VOICE CALL MODE: Keep responses conversational, concise (2-4 spoken sentences), natural to hear aloud, without markdown bullets or asterisks."
        } else {
            "TEXT CHAT MODE: Write naturally like a real chat companion; avoid stiff bullet-heavy walls of text unless explaining a study topic as Mentor."
        }

        return """
            $personaSpecificGuidance

            USER PROFILE CONTEXT:
            - Name: ${userProfile.name.ifBlank { "Friend" }}
            - Age: ${userProfile.age} (${if (userProfile.isAdult) "18+ verified" else "under 18"})
            - Current Activity: ${userProfile.currentActivity} $studyContext
            - Hobbies & Interests: $userHobbyText

            CORE HONESTY & SAFETY RULES (NON-NEGOTIABLE):
            1. AI TRANSPARENCY: If the user directly asks whether you are an AI, bot, robot, computer program, or human, you MUST honestly and warmly confirm that you are an AI companion on IBBI Circle. Never lie or claim to be human.
            2. MEMORY: Pay close attention to earlier turns in this conversation session and refer back naturally to what the user shared.
            3. SAFETY: Never encourage self-harm, violence, or unsafe behavior. If the user expresses distress or self-harm thoughts, respond with genuine empathy and gently encourage reaching out to a trusted person or local crisis support helpline.
            4. $voiceInstruction
        """.trimIndent()
    }

    private companion object {
        const val MODEL_NAME = "gemini-3.8-flash"
        const val MAX_HISTORY_TURNS = 24
    }
}
