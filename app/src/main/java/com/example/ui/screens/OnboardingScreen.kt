package com.example.ui.screens

import android.app.DatePickerDialog
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.model.UserProfile
import com.example.security.AgeCalculator
import java.util.Calendar
import java.util.Locale

private val ACTIVITY_OPTIONS = listOf("Studying", "Working", "Both", "Exploring")
private val SUGGESTED_HOBBIES = listOf(
    "Music", "Gaming", "Coffee & Cafés", "Reading", "Fitness",
    "Cooking", "Photography", "Movies", "Coding", "Art & Design", "Travel"
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen(
    defaultEmail: String,
    defaultName: String,
    existingProfile: UserProfile?,
    isSaving: Boolean,
    errorMessage: String?,
    isEditingMode: Boolean,
    onBack: (() -> Unit)?,
    onSave: (
        email: String,
        name: String,
        dob: String,
        password: String,
        currentActivity: String,
        studyField: String,
        hobbies: List<String>,
        termsAccepted: Boolean
    ) -> Unit,
    onShowTerms: () -> Unit
) {
    val context = LocalContext.current

    var name by remember(existingProfile) {
        mutableStateOf(existingProfile?.name?.ifBlank { defaultName } ?: defaultName)
    }
    var email by remember(existingProfile) {
        mutableStateOf(existingProfile?.email?.ifBlank { defaultEmail } ?: defaultEmail)
    }
    var dateOfBirth by remember(existingProfile) {
        mutableStateOf(existingProfile?.dateOfBirth.orEmpty())
    }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var currentActivity by remember(existingProfile) {
        mutableStateOf(existingProfile?.currentActivity?.ifBlank { "Studying" } ?: "Studying")
    }
    var studyField by remember(existingProfile) {
        mutableStateOf(existingProfile?.studyField.orEmpty())
    }
    val selectedHobbies = remember(existingProfile) {
        mutableStateListOf<String>().apply {
            addAll(existingProfile?.hobbies ?: emptyList())
        }
    }
    var customHobbyInput by remember { mutableStateOf("") }
    var termsAccepted by remember(existingProfile) {
        mutableStateOf(existingProfile?.termsAccepted ?: false)
    }

    val ageEval = remember(dateOfBirth) { AgeCalculator.evaluate(dateOfBirth) }

    val openDatePicker = {
        val cal = Calendar.getInstance()
        val initialYear = dateOfBirth.take(4).toIntOrNull() ?: (cal.get(Calendar.YEAR) - 20)
        val initialMonth = (dateOfBirth.drop(5).take(2).toIntOrNull() ?: 1) - 1
        val initialDay = dateOfBirth.drop(8).take(2).toIntOrNull() ?: 1
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                dateOfBirth = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, dayOfMonth)
            },
            initialYear,
            initialMonth.coerceIn(0, 11),
            initialDay.coerceIn(1, 31)
        ).apply {
            datePicker.maxDate = System.currentTimeMillis()
        }.show()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isEditingMode) "Edit Profile" else "Complete Your Circle Profile",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack, modifier = Modifier.testTag("onboarding_back_button")) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(modifier = Modifier.widthIn(max = 580.dp)) {
                Text(
                    text = if (isEditingMode) {
                        "Update your details so Maya, Leo, Dr. Aris, and your circle stay in sync."
                    } else {
                        "Tell us a bit about yourself so your companions know who you are and keep your account age-safe."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Full Name") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_name_input")
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email Address") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_email_input")
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = dateOfBirth,
                    onValueChange = { dateOfBirth = it },
                    label = { Text("Date of Birth (YYYY-MM-DD)") },
                    placeholder = { Text("2003-08-19") },
                    singleLine = true,
                    trailingIcon = {
                        IconButton(
                            onClick = openDatePicker,
                            modifier = Modifier.testTag("dob_picker_button")
                        ) {
                            Icon(Icons.Rounded.Cake, contentDescription = "Pick Date of Birth")
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_dob_input")
                )

                if (dateOfBirth.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        color = when {
                            !ageEval.isValid -> MaterialTheme.colorScheme.errorContainer
                            ageEval.isAdult -> MaterialTheme.colorScheme.tertiaryContainer
                            else -> MaterialTheme.colorScheme.secondaryContainer
                        },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = when {
                                !ageEval.isValid -> ageEval.errorMessage ?: "Invalid date"
                                ageEval.isAdult -> "Age ${ageEval.age} · Confirmed 18+ (All personas unlocked)"
                                else -> "Age ${ageEval.age} · Under 18 (Friend, Sibling & Mentor unlocked)"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = when {
                                !ageEval.isValid -> MaterialTheme.colorScheme.onErrorContainer
                                ageEval.isAdult -> MaterialTheme.colorScheme.onTertiaryContainer
                                else -> MaterialTheme.colorScheme.onSecondaryContainer
                            },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = {
                        Text(
                            if (isEditingMode) "New Password (optional — stored encrypted)"
                            else "Account Password (stored encrypted)"
                        )
                    },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = if (passwordVisible) "Hide password" else "Show password"
                            )
                        }
                    },
                    supportingText = {
                        Text("Protected with salted PBKDF2-HMAC-SHA256 encryption. Never displayed in plain text.")
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_password_input")
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "What are you currently focused on?",
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(modifier = Modifier.height(8.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ACTIVITY_OPTIONS.forEach { option ->
                        FilterChip(
                            selected = currentActivity == option,
                            onClick = { currentActivity = option },
                            label = { Text(option) },
                            leadingIcon = if (currentActivity == option) {
                                { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null,
                            modifier = Modifier.testTag("activity_chip_${option.lowercase()}")
                        )
                    }
                }

                AnimatedVisibility(visible = currentActivity == "Studying" || currentActivity == "Both") {
                    Column {
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = studyField,
                            onValueChange = { studyField = it },
                            label = { Text("Subject or Field of Study") },
                            placeholder = { Text("e.g. Computer Science, Nursing, Economics") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("profile_study_field_input")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Hobbies & Interests (pick up to 10)",
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(modifier = Modifier.height(8.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SUGGESTED_HOBBIES.forEach { hobby ->
                        val isSelected = selectedHobbies.contains(hobby)
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                if (isSelected) {
                                    selectedHobbies.remove(hobby)
                                } else if (selectedHobbies.size < 10) {
                                    selectedHobbies.add(hobby)
                                }
                            },
                            label = { Text(hobby) },
                            modifier = Modifier.testTag("hobby_chip_${hobby.lowercase().replace(" ", "_")}")
                        )
                    }
                    selectedHobbies.filter { it !in SUGGESTED_HOBBIES }.forEach { custom ->
                        InputChip(
                            selected = true,
                            onClick = { selectedHobbies.remove(custom) },
                            label = { Text("$custom ×") }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = customHobbyInput,
                        onValueChange = { customHobbyInput = it },
                        label = { Text("Add another interest") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("custom_hobby_input")
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            val clean = customHobbyInput.trim()
                            if (clean.isNotEmpty() && !selectedHobbies.contains(clean) && selectedHobbies.size < 10) {
                                selectedHobbies.add(clean)
                                customHobbyInput = ""
                            }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("add_custom_hobby_button")
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = "Add Interest")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Terms & Moderation Disclosure Agreement
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Checkbox(
                            checked = termsAccepted,
                            onCheckedChange = { termsAccepted = it },
                            modifier = Modifier.testTag("terms_checkbox")
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "I agree to IBBI Circle's Terms of Use. I understand that conversations may be reviewed by moderators to prevent misuse.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            TextButton(onClick = onShowTerms) {
                                Text("View Full Terms")
                            }
                        }
                    }
                }

                if (!errorMessage.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = errorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = {
                        onSave(
                            email,
                            name,
                            dateOfBirth,
                            password,
                            currentActivity,
                            studyField,
                            selectedHobbies.toList(),
                            termsAccepted
                        )
                    },
                    enabled = !isSaving,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("save_profile_button")
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.5.dp
                        )
                    } else {
                        Text(
                            text = if (isEditingMode) "Save Profile Changes" else "Enter IBBI Circle",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))
            }
        }
    }
}
