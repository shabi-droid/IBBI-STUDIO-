package com.example.ui.state

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.ChatSession
import com.example.data.model.Persona
import com.example.data.model.PersonaCatalog
import com.example.data.model.PersonaId
import com.example.data.model.UserProfile
import com.example.data.repository.IbbiRepository
import com.example.security.AgeCalculator
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface ProfileUiState {
    data object Loading : ProfileUiState
    data object NeedsOnboarding : ProfileUiState
    data class Ready(val profile: UserProfile) : ProfileUiState
    data class Error(val message: String) : ProfileUiState
}

class AppViewModel(
    private val repository: IbbiRepository,
    private val currentUserId: String
) : ViewModel() {

    val profileState: StateFlow<ProfileUiState> = repository.observeUserProfile(currentUserId)
        .map { profile ->
            if (profile == null || !profile.profileCompleted) {
                ProfileUiState.NeedsOnboarding
            } else {
                ProfileUiState.Ready(profile)
            }
        }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.e(TAG, "Error observing profile", rootError)
            emit(ProfileUiState.Error(rootError.message ?: "Unable to load your profile."))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = ProfileUiState.Loading
        )

    val existingProfile: StateFlow<UserProfile?> = repository.observeUserProfile(currentUserId)
        .catch { error ->
            if (error is CancellationException) throw error
            emit(null)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = null
        )

    val sessions: StateFlow<List<ChatSession>> = repository.observeSessions(currentUserId)
        .catch { error ->
            if (error is CancellationException) throw error
            Log.w(TAG, "Error observing sessions", error)
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    private val _isSavingProfile = MutableStateFlow(false)
    val isSavingProfile: StateFlow<Boolean> = _isSavingProfile.asStateFlow()

    private val _formError = MutableStateFlow<String?>(null)
    val formError: StateFlow<String?> = _formError.asStateFlow()

    private val _under18BlockedDialog = MutableStateFlow(false)
    val under18BlockedDialog: StateFlow<Boolean> = _under18BlockedDialog.asStateFlow()

    private val _isOpeningSession = MutableStateFlow(false)
    val isOpeningSession: StateFlow<Boolean> = _isOpeningSession.asStateFlow()

    init {
        viewModelScope.launch {
            repository.touchLastLogin()
        }
    }

    fun clearFormError() {
        _formError.value = null
    }

    fun dismissUnder18Dialog() {
        _under18BlockedDialog.value = false
    }

    fun saveProfile(
        email: String,
        name: String,
        dateOfBirth: String,
        password: String,
        isEditingExisting: Boolean,
        currentActivity: String,
        studyField: String,
        hobbies: List<String>,
        termsAccepted: Boolean,
        onSaved: () -> Unit = {}
    ) {
        if (name.isBlank()) {
            _formError.value = "Please enter your name."
            return
        }
        if (email.isBlank() || !email.contains("@")) {
            _formError.value = "Please enter a valid email address."
            return
        }
        val ageEval = AgeCalculator.evaluate(dateOfBirth)
        if (!ageEval.isValid) {
            _formError.value = ageEval.errorMessage ?: "Please enter a valid date of birth."
            return
        }
        if (!isEditingExisting && password.length < 6) {
            _formError.value = "Please choose a password of at least 6 characters (stored encrypted)."
            return
        }
        if (isEditingExisting && password.isNotEmpty() && password.length < 6) {
            _formError.value = "New password must be at least 6 characters."
            return
        }
        if ((currentActivity == "Studying" || currentActivity == "Both") && studyField.isBlank()) {
            _formError.value = "Please share your subject or field of study."
            return
        }
        if (hobbies.isEmpty()) {
            _formError.value = "Please select or add at least one hobby or interest."
            return
        }
        if (!termsAccepted) {
            _formError.value = "Please accept the Terms & Moderation Disclosure to continue."
            return
        }

        viewModelScope.launch {
            _isSavingProfile.value = true
            _formError.value = null
            val result = repository.upsertUserProfile(
                email = email,
                name = name,
                dateOfBirth = dateOfBirth,
                password = password.takeIf { it.isNotEmpty() },
                currentActivity = currentActivity,
                studyField = studyField,
                hobbies = hobbies,
                termsAccepted = termsAccepted,
                profileCompleted = true
            )
            _isSavingProfile.value = false
            result.onSuccess {
                onSaved()
            }.onFailure { err ->
                _formError.value = err.localizedMessage ?: "Failed to save profile."
            }
        }
    }

    fun openPersonaChat(
        persona: Persona,
        userProfile: UserProfile,
        startInVoiceMode: Boolean,
        onSessionReady: (sessionId: String, personaId: PersonaId, voiceMode: Boolean) -> Unit
    ) {
        if (persona.requiresAdult && !userProfile.isAdult) {
            _under18BlockedDialog.value = true
            return
        }
        viewModelScope.launch {
            _isOpeningSession.value = true
            val result = repository.getOrCreateSession(persona.id)
            _isOpeningSession.value = false
            result.onSuccess { session ->
                onSessionReady(session.id, persona.id, startInVoiceMode)
            }.onFailure { err ->
                _formError.value = err.localizedMessage ?: "Unable to start chat session."
            }
        }
    }

    fun openFallbackPersona(
        personaId: PersonaId,
        userProfile: UserProfile,
        onSessionReady: (sessionId: String, personaId: PersonaId, voiceMode: Boolean) -> Unit
    ) {
        _under18BlockedDialog.value = false
        val target = PersonaCatalog.byId(personaId)
        openPersonaChat(target, userProfile, startInVoiceMode = false, onSessionReady = onSessionReady)
    }

    private companion object {
        const val TAG = "AppViewModel"
    }
}
