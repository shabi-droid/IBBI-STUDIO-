package com.example.ui.state

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.ModerationItem
import com.example.data.model.UserProfile
import com.example.data.repository.IbbiRepository
import com.example.security.PasswordCrypto
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface AdminAccessState {
    data object Checking : AdminAccessState
    data class NeedsPasscodeSetup(val email: String) : AdminAccessState
    data class NeedsPasscodeLogin(val email: String) : AdminAccessState
    data class Denied(val reason: String) : AdminAccessState
    data class Unlocked(val email: String) : AdminAccessState
}

@OptIn(ExperimentalCoroutinesApi::class)
class AdminViewModel(
    private val repository: IbbiRepository,
    appContext: Context
) : ViewModel() {

    private val prefs = appContext.getSharedPreferences(ADMIN_PREFS, Context.MODE_PRIVATE)

    private val _accessState = MutableStateFlow<AdminAccessState>(AdminAccessState.Checking)
    val accessState: StateFlow<AdminAccessState> = _accessState.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    private val isUnlockedFlow: StateFlow<Boolean> = accessState
        .map { it is AdminAccessState.Unlocked }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val users: StateFlow<List<UserProfile>> = isUnlockedFlow
        .flatMapLatest { unlocked ->
            if (!unlocked) {
                flowOf(emptyList())
            } else {
                repository.observeAllUsersForAdmin()
                    .catch { error ->
                        val firestoreError = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (firestoreError == null && error is CancellationException) throw error
                        Log.e(TAG, "Admin users list failed", firestoreError ?: error)
                        _actionMessage.value = (firestoreError ?: error).localizedMessage ?: "Failed to load users."
                        emit(emptyList())
                    }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val moderationQueue: StateFlow<List<ModerationItem>> = isUnlockedFlow
        .flatMapLatest { unlocked ->
            if (!unlocked) {
                flowOf(emptyList())
            } else {
                repository.observeModerationQueue()
                    .catch { error ->
                        val firestoreError = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (firestoreError == null && error is CancellationException) throw error
                        Log.e(TAG, "Admin moderation queue failed", firestoreError ?: error)
                        _actionMessage.value = (firestoreError ?: error).localizedMessage ?: "Failed to load moderation queue."
                        emit(emptyList())
                    }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    init {
        refreshAdminEligibility()
    }

    fun refreshAdminEligibility() {
        viewModelScope.launch {
            _accessState.value = AdminAccessState.Checking
            _authError.value = null
            val user = Firebase.auth.currentUser
            if (user == null) {
                _accessState.value = AdminAccessState.Denied("Sign in with an authorized Google Admin account to access the Admin Portal.")
                return@launch
            }
            val isEligible = repository.checkAdminAccess().getOrDefault(false)
            if (!isEligible) {
                _accessState.value = AdminAccessState.Denied(
                    "Access restricted. Signed-in account (${user.email ?: "unknown"}) is not authorized for admin privileges."
                )
                return@launch
            }
            val email = user.email ?: "admin"
            val hasStoredHash = !prefs.getString(KEY_HASH, null).isNullOrBlank() &&
                !prefs.getString(KEY_SALT, null).isNullOrBlank()
            _accessState.value = if (hasStoredHash) {
                AdminAccessState.NeedsPasscodeLogin(email)
            } else {
                AdminAccessState.NeedsPasscodeSetup(email)
            }
        }
    }

    fun createAdminPasscode(enteredEmail: String, passcode: String, confirmPasscode: String) {
        val currentEmail = Firebase.auth.currentUser?.email.orEmpty()
        if (enteredEmail.trim().lowercase() != currentEmail.lowercase()) {
            _authError.value = "Admin email must match your verified signed-in admin account ($currentEmail)."
            return
        }
        if (passcode.length < 6) {
            _authError.value = "Admin passcode must be at least 6 characters."
            return
        }
        if (passcode != confirmPasscode) {
            _authError.value = "Passcodes do not match."
            return
        }
        val hashed = PasswordCrypto.hashPassword(passcode)
        prefs.edit()
            .putString(KEY_SALT, hashed.salt)
            .putString(KEY_HASH, hashed.hash)
            .apply()
        _authError.value = null
        _accessState.value = AdminAccessState.Unlocked(currentEmail)
    }

    fun verifyAdminLogin(enteredEmail: String, passcode: String) {
        val currentEmail = Firebase.auth.currentUser?.email.orEmpty()
        if (enteredEmail.trim().lowercase() != currentEmail.lowercase()) {
            _authError.value = "Invalid admin email for the active verified session."
            return
        }
        val salt = prefs.getString(KEY_SALT, null).orEmpty()
        val hash = prefs.getString(KEY_HASH, null).orEmpty()
        if (PasswordCrypto.verifyPassword(passcode, salt, hash)) {
            _authError.value = null
            _accessState.value = AdminAccessState.Unlocked(currentEmail)
        } else {
            _authError.value = "Incorrect admin passcode."
        }
    }

    fun lockAdminSession() {
        refreshAdminEligibility()
    }

    fun updateModerationStatus(flagId: String, newStatus: String) {
        viewModelScope.launch {
            repository.updateModerationStatus(flagId, newStatus)
                .onSuccess {
                    _actionMessage.value = "Flag marked as $newStatus."
                }
                .onFailure { err ->
                    _actionMessage.value = err.localizedMessage ?: "Failed to update flag."
                }
        }
    }

    fun clearActionMessage() {
        _actionMessage.value = null
    }

    private companion object {
        const val TAG = "AdminViewModel"
        const val ADMIN_PREFS = "ibbi_admin_security_prefs"
        const val KEY_SALT = "admin_pass_salt"
        const val KEY_HASH = "admin_pass_hash"
    }
}
