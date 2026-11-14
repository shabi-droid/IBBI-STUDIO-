package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun TermsDisclosureDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "IBBI Circle Terms & Safety",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "1. AI Companionship & Transparency",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "All personas in IBBI Circle (Maya, Leo, Dr. Aris, and Elena) are AI companions powered by generative AI. If you ask whether you are speaking to an AI, your companion will always confirm honestly.",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "2. Age Eligibility",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Your date of birth is used to verify whether your account is 18 or older. The Partner persona is strictly limited to confirmed 18+ users.",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "3. Conversation Review & Misuse Prevention",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "To prevent misuse and keep our community safe, conversations—especially messages containing concerning keywords—may be reviewed by authorized moderators.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "4. Account Security",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Your password is stored encrypted using salted PBKDF2-HMAC-SHA256 hashing and is never displayed in plain text anywhere.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("terms_close_button")
            ) {
                Text("Got It")
            }
        }
    )
}
