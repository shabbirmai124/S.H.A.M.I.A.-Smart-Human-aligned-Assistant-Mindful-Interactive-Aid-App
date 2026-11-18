package com.shamia.assistant.ui.tabs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shamia.assistant.ui.theme.CardBorder
import com.shamia.assistant.ui.theme.CardSlate
import com.shamia.assistant.ui.theme.ObsidianVoid
import com.shamia.assistant.ui.theme.SapphireBright
import com.shamia.assistant.ui.theme.SapphirePulse
import com.shamia.assistant.ui.theme.TextPrimary

@Composable
fun AddCustomHfModelCard(
    onAddCustomModel: (name: String, repoOrTag: String, minRamGb: Int) -> Unit
) {
    var customName by remember { mutableStateOf("") }
    var customRepo by remember { mutableStateOf("onnx-community/gemma-3-1b-it-ONNX") }
    var customRam by remember { mutableStateOf("4") }

    Surface(shape = RoundedCornerShape(14.dp), color = CardSlate, border = BorderStroke(1.dp, CardBorder)) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Add Custom HF Model",
                style = MaterialTheme.typography.titleSmall,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold
            )
            OutlinedTextField(
                value = customRepo,
                onValueChange = { customRepo = it },
                label = { Text("Hugging Face repo or Ollama tag") },
                placeholder = { Text("onnx-community/gemma-3-1b-it-ONNX") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("custom_hf_repo_input")
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = customName,
                    onValueChange = { customName = it },
                    label = { Text("Display Name (optional)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = customRam,
                    onValueChange = { customRam = it.filter { ch -> ch.isDigit() } },
                    label = { Text("RAM GB") },
                    singleLine = true,
                    modifier = Modifier.width(84.dp)
                )
            }
            Button(
                onClick = {
                    val repo = customRepo.trim()
                    if (repo.isNotEmpty()) {
                        val resolvedName = customName.trim().ifEmpty {
                            repo.substringAfterLast("/").ifEmpty { repo }
                        }
                        onAddCustomModel(resolvedName, repo, customRam.toIntOrNull() ?: 4)
                        customName = ""
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = SapphirePulse, contentColor = ObsidianVoid),
                modifier = Modifier.fillMaxWidth().testTag("add_custom_hf_button")
            ) { Text("Add") }
        }
    }
}

@Composable
fun LocalOllamaEndpointCard(
    ollamaEndpoint: String,
    serverBaseUrl: String,
    testStatus: String,
    onSaveEndpointsAndTest: (ollamaUrl: String, serverUrl: String) -> Unit
) {
    var ollamaInput by remember(ollamaEndpoint) { mutableStateOf(ollamaEndpoint) }
    var serverInput by remember(serverBaseUrl) { mutableStateOf(serverBaseUrl) }

    Surface(shape = RoundedCornerShape(14.dp), color = CardSlate, border = BorderStroke(1.dp, CardBorder)) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Local Ollama Endpoint",
                style = MaterialTheme.typography.titleSmall,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold
            )
            OutlinedTextField(
                value = ollamaInput,
                onValueChange = { ollamaInput = it },
                label = { Text("Ollama URL") },
                placeholder = { Text("http://127.0.0.1:11434") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("ollama_endpoint_input")
            )
            OutlinedTextField(
                value = serverInput,
                onValueChange = { serverInput = it },
                label = { Text("Optional Paired Server URL") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = { onSaveEndpointsAndTest(ollamaInput, serverInput) },
                colors = ButtonDefaults.buttonColors(containerColor = SapphirePulse, contentColor = ObsidianVoid),
                modifier = Modifier.fillMaxWidth().testTag("test_ollama_button")
            ) {
                Text("Test")
            }
            if (testStatus.isNotBlank()) {
                Text(testStatus, style = MaterialTheme.typography.bodySmall, color = SapphireBright)
            }
        }
    }
}
