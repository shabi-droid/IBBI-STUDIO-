package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.auth.attemptAutoSignIn
import com.example.auth.authStateFlow
import com.example.auth.signOut
import com.example.data.ai.CompanionAiService
import com.example.data.model.PersonaId
import com.example.data.model.UserProfile
import com.example.data.repository.IbbiRepository
import com.example.security.configureAppCheck
import com.example.ui.screens.AdminPortalScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.ChatScreen
import com.example.ui.screens.CircleHomeScreen
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.TermsDisclosureDialog
import com.example.ui.screens.VoiceCallScreen
import com.example.ui.state.AdminViewModel
import com.example.ui.state.AppViewModel
import com.example.ui.state.ChatViewModel
import com.example.ui.state.ProfileUiState
import com.example.ui.theme.IbbiCircleTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure App Check with launch intent before any UI or Firebase operations
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        setContent {
            IbbiCircleTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
                }
            }
        }
    }
}

private const val ROUTE_HOME = "home"
private const val ROUTE_EDIT_PROFILE = "edit_profile"
private const val ROUTE_CHAT = "chat/{sessionId}/{personaId}"
private const val ROUTE_VOICE = "voice/{sessionId}/{personaId}"

@Composable
fun AppNavigation() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val auth = Firebase.auth
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)

    var showTermsDialog by rememberSaveable { mutableStateOf(false) }
    var showAdminPortal by rememberSaveable { mutableStateOf(false) }
    var autoSignInChecked by remember { mutableStateOf(auth.currentUser != null) }

    LaunchedEffect(Unit) {
        if (auth.currentUser == null) {
            attemptAutoSignIn(
                context = context,
                credentialManager = credentialManager,
                onAuthSuccess = { autoSignInChecked = true },
                onUnauthenticated = { autoSignInChecked = true },
                scope = scope
            )
        }
    }

    if (showTermsDialog) {
        TermsDisclosureDialog(onDismiss = { showTermsDialog = false })
    }

    if (showAdminPortal) {
        val adminViewModel: AdminViewModel = viewModel(
            key = currentUser?.uid ?: "admin_unauth",
            factory = viewModelFactory {
                initializer {
                    val app = checkNotNull(this[APPLICATION_KEY])
                    val databaseId = app.getString(R.string.firestore_database_id)
                    val db = FirebaseFirestore.getInstance(databaseId)
                    AdminViewModel(IbbiRepository(db, Firebase.auth), app.applicationContext)
                }
            }
        )
        AdminPortalScreen(
            viewModel = adminViewModel,
            onBack = { showAdminPortal = false }
        )
        return
    }

    val user = currentUser
    if (!autoSignInChecked && user == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    if (user == null) {
        AuthScreen(
            onAuthSuccess = { /* AuthStateListener updates currentUser reactively */ },
            onOpenAdminPortal = { showAdminPortal = true },
            onShowTerms = { showTermsDialog = true }
        )
    } else {
        val uid = user.uid
        key(uid) {
            AuthenticatedCircleFlow(
                currentUserId = uid,
                userEmail = user.email.orEmpty(),
                userDisplayName = user.displayName.orEmpty(),
                onOpenAdminPortal = { showAdminPortal = true },
                onShowTerms = { showTermsDialog = true },
                onSignOut = {
                    signOut(credentialManager = credentialManager, scope = scope)
                }
            )
        }
    }
}

@Composable
private fun AuthenticatedCircleFlow(
    currentUserId: String,
    userEmail: String,
    userDisplayName: String,
    onOpenAdminPortal: () -> Unit,
    onShowTerms: () -> Unit,
    onSignOut: () -> Unit
) {
    val appViewModel: AppViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                AppViewModel(IbbiRepository(db, Firebase.auth), currentUserId)
            }
        }
    )

    val profileState by appViewModel.profileState.collectAsStateWithLifecycle()
    val existingProfile by appViewModel.existingProfile.collectAsStateWithLifecycle()
    val sessions by appViewModel.sessions.collectAsStateWithLifecycle()
    val isSavingProfile by appViewModel.isSavingProfile.collectAsStateWithLifecycle()
    val formError by appViewModel.formError.collectAsStateWithLifecycle()
    val showUnder18Dialog by appViewModel.under18BlockedDialog.collectAsStateWithLifecycle()
    val isOpeningSession by appViewModel.isOpeningSession.collectAsStateWithLifecycle()

    when (val state = profileState) {
        is ProfileUiState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        is ProfileUiState.Error -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        is ProfileUiState.NeedsOnboarding -> {
            OnboardingScreen(
                defaultEmail = userEmail,
                defaultName = userDisplayName,
                existingProfile = existingProfile,
                isSaving = isSavingProfile,
                errorMessage = formError,
                isEditingMode = false,
                onBack = null,
                onSave = { email, name, dob, password, activity, field, hobbies, terms ->
                    appViewModel.saveProfile(
                        email = email,
                        name = name,
                        dateOfBirth = dob,
                        password = password,
                        isEditingExisting = false,
                        currentActivity = activity,
                        studyField = field,
                        hobbies = hobbies,
                        termsAccepted = terms
                    )
                },
                onShowTerms = onShowTerms
            )
        }

        is ProfileUiState.Ready -> {
            val profile: UserProfile = state.profile
            val navController = rememberNavController()

            NavHost(
                navController = navController,
                startDestination = ROUTE_HOME
            ) {
                composable(ROUTE_HOME) {
                    CircleHomeScreen(
                        userProfile = profile,
                        sessions = sessions,
                        showUnder18Dialog = showUnder18Dialog,
                        isOpeningSession = isOpeningSession,
                        onSelectPersona = { persona, startInVoice ->
                            appViewModel.openPersonaChat(
                                persona = persona,
                                userProfile = profile,
                                startInVoiceMode = startInVoice
                            ) { sessionId, personaId, voiceMode ->
                                val route = if (voiceMode) {
                                    "voice/$sessionId/${personaId.id}"
                                } else {
                                    "chat/$sessionId/${personaId.id}"
                                }
                                navController.navigate(route)
                            }
                        },
                        onDismissUnder18Dialog = { appViewModel.dismissUnder18Dialog() },
                        onPickFallbackPersona = { fallbackId ->
                            appViewModel.openFallbackPersona(
                                personaId = fallbackId,
                                userProfile = profile
                            ) { sessionId, personaId, _ ->
                                navController.navigate("chat/$sessionId/${personaId.id}")
                            }
                        },
                        onEditProfile = {
                            appViewModel.clearFormError()
                            navController.navigate(ROUTE_EDIT_PROFILE)
                        },
                        onOpenAdminPortal = onOpenAdminPortal,
                        onShowTerms = onShowTerms,
                        onSignOut = onSignOut
                    )
                }

                composable(ROUTE_EDIT_PROFILE) {
                    BackHandler { navController.popBackStack() }
                    OnboardingScreen(
                        defaultEmail = userEmail,
                        defaultName = userDisplayName,
                        existingProfile = profile,
                        isSaving = isSavingProfile,
                        errorMessage = formError,
                        isEditingMode = true,
                        onBack = { navController.popBackStack() },
                        onSave = { email, name, dob, password, activity, field, hobbies, terms ->
                            appViewModel.saveProfile(
                                email = email,
                                name = name,
                                dateOfBirth = dob,
                                password = password,
                                isEditingExisting = true,
                                currentActivity = activity,
                                studyField = field,
                                hobbies = hobbies,
                                termsAccepted = terms,
                                onSaved = { navController.popBackStack() }
                            )
                        },
                        onShowTerms = onShowTerms
                    )
                }

                composable(
                    route = ROUTE_CHAT,
                    arguments = listOf(
                        navArgument("sessionId") { type = NavType.StringType },
                        navArgument("personaId") { type = NavType.StringType }
                    )
                ) { backStackEntry ->
                    val sessionId = backStackEntry.arguments?.getString("sessionId") ?: return@composable
                    val personaRaw = backStackEntry.arguments?.getString("personaId") ?: "friend"
                    val personaId = PersonaId.fromId(personaRaw)

                    val chatViewModel: ChatViewModel = viewModel(
                        key = "${currentUserId}_$sessionId",
                        factory = viewModelFactory {
                            initializer {
                                val app = checkNotNull(this[APPLICATION_KEY])
                                val databaseId = app.getString(R.string.firestore_database_id)
                                val db = FirebaseFirestore.getInstance(databaseId)
                                ChatViewModel(
                                    repository = IbbiRepository(db, Firebase.auth),
                                    aiService = CompanionAiService(),
                                    currentUserId = currentUserId,
                                    sessionId = sessionId,
                                    personaId = personaId
                                )
                            }
                        }
                    )

                    ChatScreen(
                        viewModel = chatViewModel,
                        userProfile = profile,
                        onSwitchToVoiceCall = {
                            navController.navigate("voice/$sessionId/${personaId.id}")
                        },
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(
                    route = ROUTE_VOICE,
                    arguments = listOf(
                        navArgument("sessionId") { type = NavType.StringType },
                        navArgument("personaId") { type = NavType.StringType }
                    )
                ) { backStackEntry ->
                    val sessionId = backStackEntry.arguments?.getString("sessionId") ?: return@composable
                    val personaRaw = backStackEntry.arguments?.getString("personaId") ?: "friend"
                    val personaId = PersonaId.fromId(personaRaw)

                    val chatViewModel: ChatViewModel = viewModel(
                        key = "${currentUserId}_$sessionId",
                        factory = viewModelFactory {
                            initializer {
                                val app = checkNotNull(this[APPLICATION_KEY])
                                val databaseId = app.getString(R.string.firestore_database_id)
                                val db = FirebaseFirestore.getInstance(databaseId)
                                ChatViewModel(
                                    repository = IbbiRepository(db, Firebase.auth),
                                    aiService = CompanionAiService(),
                                    currentUserId = currentUserId,
                                    sessionId = sessionId,
                                    personaId = personaId
                                )
                            }
                        }
                    )

                    VoiceCallScreen(
                        viewModel = chatViewModel,
                        userProfile = profile,
                        onSwitchToTextChat = {
                            navController.navigate("chat/$sessionId/${personaId.id}") {
                                popUpTo(ROUTE_HOME)
                            }
                        },
                        onEndCall = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}
