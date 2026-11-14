package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.ReportProblem
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.auth.onGoogleSignInClicked
import com.example.data.model.ModerationItem
import com.example.data.model.UserProfile
import com.example.ui.state.AdminAccessState
import com.example.ui.state.AdminViewModel
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminPortalScreen(
    viewModel: AdminViewModel,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val accessState by viewModel.accessState.collectAsStateWithLifecycle()
    val authError by viewModel.authError.collectAsStateWithLifecycle()
    val users by viewModel.users.collectAsStateWithLifecycle()
    val moderationQueue by viewModel.moderationQueue.collectAsStateWithLifecycle()
    val actionMessage by viewModel.actionMessage.collectAsStateWithLifecycle()

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("admin_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Text(
                        text = if (accessState is AdminAccessState.Unlocked) "Admin Dashboard" else "Admin Login",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                actions = {
                    if (accessState is AdminAccessState.Unlocked) {
                        OutlinedButton(
                            onClick = { viewModel.lockAdminSession() },
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .testTag("admin_lock_button")
                        ) {
                            Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Lock")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (val state = accessState) {
                is AdminAccessState.Checking -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                is AdminAccessState.Denied -> {
                    AdminDeniedCard(
                        reason = state.reason,
                        onSignedInAsAdmin = { viewModel.refreshAdminEligibility() }
                    )
                }

                is AdminAccessState.NeedsPasscodeSetup -> {
                    AdminPasscodeSetupForm(
                        adminEmail = state.email,
                        errorMessage = authError,
                        onSubmit = { email, pass, confirm ->
                            viewModel.createAdminPasscode(email, pass, confirm)
                        }
                    )
                }

                is AdminAccessState.NeedsPasscodeLogin -> {
                    AdminPasscodeLoginForm(
                        defaultEmail = state.email,
                        errorMessage = authError,
                        onSubmit = { email, pass ->
                            viewModel.verifyAdminLogin(email, pass)
                        }
                    )
                }

                is AdminAccessState.Unlocked -> {
                    AdminDashboardContent(
                        users = users,
                        moderationQueue = moderationQueue,
                        actionMessage = actionMessage,
                        onDismissActionMessage = { viewModel.clearActionMessage() },
                        onUpdateModerationStatus = { flagId, newStatus ->
                            viewModel.updateModerationStatus(flagId, newStatus)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun AdminDeniedCard(
    reason: String,
    onSignedInAsAdmin: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    var isLoading by remember { mutableStateOf(false) }
    var signInError by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.widthIn(max = 460.dp)
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.AdminPanelSettings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )
                Text("Separate Admin Authentication", style = MaterialTheme.typography.titleLarge)
                Text(
                    text = reason,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!signInError.isNullOrBlank()) {
                    Text(
                        text = signInError.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Button(
                    onClick = {
                        isLoading = true
                        signInError = null
                        onGoogleSignInClicked(
                            context = context,
                            credentialManager = credentialManager,
                            onAuthSuccess = {
                                isLoading = false
                                onSignedInAsAdmin()
                            },
                            onAuthError = { msg ->
                                isLoading = false
                                signInError = msg
                            },
                            scope = scope,
                            onAuthCancelled = { isLoading = false }
                        )
                    },
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("admin_google_signin_button")
                ) {
                    Text(if (isLoading) "Signing in..." else "Sign in with Admin Google Account")
                }
            }
        }
    }
}

@Composable
private fun AdminPasscodeSetupForm(
    adminEmail: String,
    errorMessage: String?,
    onSubmit: (String, String, String) -> Unit
) {
    var email by remember(adminEmail) { mutableStateOf(adminEmail) }
    var passcode by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.widthIn(max = 460.dp)
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Create Admin Passcode", style = MaterialTheme.typography.titleLarge)
                Text(
                    text = "Set a separate Admin Passcode to protect the user table and moderation queue. Stored salted & hashed (never plaintext).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Admin Email") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("admin_setup_email")
                )
                OutlinedTextField(
                    value = passcode,
                    onValueChange = { passcode = it },
                    label = { Text("New Admin Passcode (min 6 chars)") },
                    singleLine = true,
                    visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { visible = !visible }) {
                            Icon(
                                if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = "Toggle passcode visibility"
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("admin_setup_passcode")
                )
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { confirm = it },
                    label = { Text("Confirm Admin Passcode") },
                    singleLine = true,
                    visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("admin_setup_confirm")
                )
                if (!errorMessage.isNullOrBlank()) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Button(
                    onClick = { onSubmit(email, passcode, confirm) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("admin_setup_submit")
                ) {
                    Text("Save & Unlock Dashboard")
                }
            }
        }
    }
}

@Composable
private fun AdminPasscodeLoginForm(
    defaultEmail: String,
    errorMessage: String?,
    onSubmit: (String, String) -> Unit
) {
    var email by remember(defaultEmail) { mutableStateOf(defaultEmail) }
    var passcode by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.widthIn(max = 460.dp)
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Admin Login", style = MaterialTheme.typography.titleLarge)
                Text(
                    text = "Enter your authorized admin email and admin passcode to unlock user metrics and the moderation queue.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Admin Email") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("admin_login_email")
                )
                OutlinedTextField(
                    value = passcode,
                    onValueChange = { passcode = it },
                    label = { Text("Admin Passcode") },
                    singleLine = true,
                    visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { visible = !visible }) {
                            Icon(
                                if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = "Toggle passcode visibility"
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("admin_login_passcode")
                )
                if (!errorMessage.isNullOrBlank()) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Button(
                    onClick = { onSubmit(email, passcode) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("admin_login_submit")
                ) {
                    Text("Unlock Admin Dashboard")
                }
            }
        }
    }
}

@Composable
private fun AdminDashboardContent(
    users: List<UserProfile>,
    moderationQueue: List<ModerationItem>,
    actionMessage: String?,
    onDismissActionMessage: () -> Unit,
    onUpdateModerationStatus: (String, String) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val pendingCount = moderationQueue.count { it.status == "pending" }
    val totalMessages = users.sumOf { it.messageCount }

    Column(modifier = Modifier.fillMaxSize()) {
        if (!actionMessage.isNullOrBlank()) {
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = actionMessage,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismissActionMessage) {
                        Icon(Icons.Rounded.Close, contentDescription = "Dismiss")
                    }
                }
            }
        }

        // KPI Row
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            AdminStatCard("Users", users.size.toString(), Modifier.weight(1f))
            AdminStatCard("Messages", totalMessages.toString(), Modifier.weight(1f))
            AdminStatCard("Pending Flags", pendingCount.toString(), Modifier.weight(1f))
        }

        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Users (${users.size})") },
                icon = { Icon(Icons.Rounded.People, contentDescription = null) },
                modifier = Modifier.testTag("admin_users_tab")
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Moderation ($pendingCount)") },
                icon = { Icon(Icons.Rounded.ReportProblem, contentDescription = null) },
                modifier = Modifier.testTag("admin_moderation_tab")
            )
        }

        if (selectedTab == 0) {
            AdminUsersTable(users = users)
        } else {
            AdminModerationQueueList(
                items = moderationQueue,
                onUpdateStatus = onUpdateModerationStatus
            )
        }
    }
}

@Composable
private fun AdminStatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AdminUsersTable(users: List<UserProfile>) {
    if (users.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "No registered user profiles yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val hScroll = rememberScrollState()
    val dateFmt = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US) }
    val formatTs: (Timestamp?) -> String = { ts ->
        ts?.toDate()?.let { dateFmt.format(it) } ?: "—"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .horizontalScroll(hScroll)
            .padding(16.dp)
    ) {
        // Table Header — explicitly omits any password fields
        Row(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            TableCell("Email", 220.dp, bold = true)
            TableCell("Name", 130.dp, bold = true)
            TableCell("Age / 18+", 95.dp, bold = true)
            TableCell("Signup Date", 145.dp, bold = true)
            TableCell("Last Login", 145.dp, bold = true)
            TableCell("Messages", 85.dp, bold = true)
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(users, key = { it.userId.ifBlank { it.id } }) { user ->
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TableCell(user.email, 220.dp)
                    TableCell(user.name, 130.dp)
                    TableCell("${user.age} (${if (user.isAdult) "18+" else "<18"})", 95.dp)
                    TableCell(formatTs(user.signupDate), 145.dp)
                    TableCell(formatTs(user.lastLoginAt), 145.dp)
                    TableCell(user.messageCount.toString(), 85.dp, bold = true)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
            }
        }
    }
}

@Composable
private fun TableCell(text: String, width: androidx.compose.ui.unit.Dp, bold: Boolean = false) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
        modifier = Modifier
            .width(width)
            .padding(end = 8.dp)
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AdminModerationQueueList(
    items: List<ModerationItem>,
    onUpdateStatus: (String, String) -> Unit
) {
    var statusFilter by remember { mutableStateOf("pending") }
    val filtered = remember(items, statusFilter) {
        if (statusFilter == "all") items else items.filter { it.status == statusFilter }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            listOf("pending", "reviewed", "dismissed", "all").forEach { status ->
                FilterChip(
                    selected = statusFilter == status,
                    onClick = { statusFilter = status },
                    label = { Text(status.replaceFirstChar { it.uppercase() }) },
                    modifier = Modifier.testTag("mod_filter_$status")
                )
            }
        }

        if (filtered.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "No flagged messages in '$statusFilter'.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filtered, key = { it.id }) { item ->
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${item.userName} (${item.userEmail})",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Surface(
                                    color = if (item.severity == "high") {
                                        MaterialTheme.colorScheme.errorContainer
                                    } else {
                                        MaterialTheme.colorScheme.secondaryContainer
                                    },
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "${item.severity.uppercase()} · ${item.status.uppercase()}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (item.severity == "high") {
                                            MaterialTheme.colorScheme.onErrorContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSecondaryContainer
                                        },
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            Text(
                                text = "Persona: ${item.personaId} · Flagged text: \"${item.messageText}\"",
                                style = MaterialTheme.typography.bodyMedium
                            )

                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                item.matchedKeywords.forEach { kw ->
                                    Surface(
                                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = "Keyword: $kw",
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }

                            if (item.status == "pending") {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.padding(top = 4.dp)
                                ) {
                                    Button(
                                        onClick = { onUpdateStatus(item.id, "reviewed") },
                                        modifier = Modifier
                                            .height(48.dp)
                                            .testTag("mod_review_${item.id}")
                                    ) {
                                        Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Mark Reviewed")
                                    }
                                    OutlinedButton(
                                        onClick = { onUpdateStatus(item.id, "dismissed") },
                                        modifier = Modifier
                                            .height(48.dp)
                                            .testTag("mod_dismiss_${item.id}")
                                    ) {
                                        Text("Dismiss")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
