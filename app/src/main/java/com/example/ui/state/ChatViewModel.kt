package com.example.ui.state

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.CompanionAiService
import com.example.data.model.ChatMessage
import com.example.data.model.Persona
import com.example.data.model.PersonaCatalog
import com.example.data.model.PersonaId
import com.example.data.model.UserProfile
import com.example.data.repository.IbbiRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

sealed interface ChatMessagesState {
    data object Loading : ChatMessagesState
    data class Success(val messages: List<ChatMessage>) : ChatMessagesState
    data class Error(val message: String) : ChatMessagesState
}

class ChatViewModel(
    private val repository: IbbiRepository,
    private val aiService: CompanionAiService,
    private val currentUserId: String,
    private val sessionId: String,
    private val personaId: PersonaId
) : ViewModel() {

    val persona: Persona = PersonaCatalog.byId(personaId)

    val messagesState: StateFlow<ChatMessagesState> =
        repository.observeMessages(currentUserId, sessionId)
            .map<List<ChatMessage>, ChatMessagesState> { ChatMessagesState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.e(TAG, "Error observing chat messages", rootError)
                emit(ChatMessagesState.Error(rootError.message ?: "Failed to load conversation."))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = ChatMessagesState.Loading
            )

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _streamingReply = MutableStateFlow("")
    val streamingReply: StateFlow<String> = _streamingReply.asStateFlow()

    private val _streamingSources = MutableStateFlow<List<String>>(emptyList())
    val streamingSources: StateFlow<List<String>> = _streamingSources.asStateFlow()

    private val _aiError = MutableStateFlow<String?>(null)
    val aiError: StateFlow<String?> = _aiError.asStateFlow()

    private val _moderationNotice = MutableStateFlow(false)
    val moderationNotice: StateFlow<Boolean> = _moderationNotice.asStateFlow()

    fun clearAiError() {
        _aiError.value = null
    }

    fun dismissModerationNotice() {
        _moderationNotice.value = false
    }

    fun sendMessage(
        rawText: String,
        userProfile: UserProfile,
        isVoiceMode: Boolean,
        onReplyReadyForVoice: (String) -> Unit = {}
    ) {
        val text = rawText.trim()
        if (text.isEmpty() || _isGenerating.value) return

        val existingHistory = (messagesState.value as? ChatMessagesState.Success)?.messages ?: emptyList()

        viewModelScope.launch {
            _isGenerating.value = true
            _streamingReply.value = ""
            _streamingSources.value = emptyList()
            _aiError.value = null

            val recordUserRes = repository.recordUserMessage(
                sessionId = sessionId,
                personaId = personaId,
                text = text,
                inputMode = if (isVoiceMode) "voice" else "text",
                userEmail = userProfile.email,
                userName = userProfile.name
            )
            recordUserRes.onSuccess { wasFlagged ->
                if (wasFlagged) {
                    _moderationNotice.value = true
                }
            }.onFailure { err ->
                Log.e(TAG, "Failed to record user message", err)
                _aiError.value = err.localizedMessage ?: "Failed to send message."
                _isGenerating.value = false
                return@launch
            }

            try {
                var finalText = ""
                var finalSources = emptyList<String>()

                withTimeout(60_000L) {
                    aiService.streamReply(
                        persona = persona,
                        userProfile = userProfile,
                        historyMessages = existingHistory,
                        userInput = text,
                        isVoiceMode = isVoiceMode
                    ).collect { chunk ->
                        _streamingReply.value = chunk.accumulatedText
                        _streamingSources.value = chunk.groundingSources
                        if (chunk.isComplete) {
                            finalText = chunk.accumulatedText.trim()
                            finalSources = chunk.groundingSources
                        }
                    }
                }

                if (finalText.isNotEmpty()) {
                    repository.recordModelMessage(
                        sessionId = sessionId,
                        personaId = personaId,
                        text = finalText,
                        inputMode = if (isVoiceMode) "voice" else "text",
                        groundingSources = finalSources
                    ).onFailure { err ->
                        Log.e(TAG, "Failed to persist model message", err)
                        _aiError.value = err.localizedMessage ?: "Failed to save reply."
                    }
                    if (isVoiceMode) {
                        onReplyReadyForVoice(finalText)
                    }
                } else {
                    _aiError.value = "${persona.name} couldn't respond right now. Please try again."
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "AI generation timed out", e)
                _aiError.value = "Response timed out after 60 seconds. Please try again."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Firebase AI generation failed", e)
                _aiError.value = e.localizedMessage ?: "AI companion could not respond."
            } finally {
                _streamingReply.value = ""
                _streamingSources.value = emptyList()
                _isGenerating.value = false
            }
        }
    }

    private companion object {
        const val TAG = "ChatViewModel"
    }
}
