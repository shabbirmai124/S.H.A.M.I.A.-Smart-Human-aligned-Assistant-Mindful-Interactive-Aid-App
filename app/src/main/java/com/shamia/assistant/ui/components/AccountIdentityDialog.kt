package com.shamia.assistant.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.shamia.assistant.model.UserIdentityProfile
import com.shamia.assistant.ui.theme.CardSlate
import com.shamia.assistant.ui.theme.ObsidianVoid
import com.shamia.assistant.ui.theme.SapphirePulse
import com.shamia.assistant.ui.theme.TextPrimary
import com.shamia.assistant.ui.theme.TextSecondary

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AccountIdentityDialog(
    identity: UserIdentityProfile,
    onDismiss: () -> Unit,
    onSave: (fullName: String, addressAs: String) -> Unit
) {
    var fullName by remember { mutableStateOf(identity.fullName) }
    var addressAs by remember { mutableStateOf(identity.addressAs) }
    val presets = listOf("Sir", "Md Shabbir Mia", "Shabbir", "Mia")

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardSlate,
        title = { Text("Owner Profile · Md Shabbir Mia", color = TextPrimary) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "All routine, syllabus, and permission settings remain encrypted in your private on-device vault.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = fullName,
                    onValueChange = { fullName = it },
                    label = { Text("Full Name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("identity_fullname_input")
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = addressAs,
                    onValueChange = { addressAs = it },
                    label = { Text("How S.H.A.M.I.A. addresses you") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("identity_address_input")
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Quick Presets",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    presets.forEach { preset ->
                        FilterChip(
                            selected = addressAs == preset,
                            onClick = { addressAs = preset },
                            label = { Text(preset) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(fullName, addressAs) },
                colors = ButtonDefaults.buttonColors(containerColor = SapphirePulse, contentColor = ObsidianVoid),
                modifier = Modifier.testTag("identity_save_button")
            ) {
                Text("Save Profile")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}
