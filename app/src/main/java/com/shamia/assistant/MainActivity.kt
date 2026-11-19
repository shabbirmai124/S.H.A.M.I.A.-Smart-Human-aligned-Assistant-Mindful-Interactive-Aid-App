package com.shamia.assistant

import android.app.Activity
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shamia.assistant.ui.ShamiaHudScreen
import com.shamia.assistant.ui.ShamiaViewModel
import com.shamia.assistant.ui.theme.ShamiaTheme
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ShamiaTheme {
                val vm: ShamiaViewModel = viewModel()
                val uiState by vm.ui.collectAsStateWithLifecycle()
                val context = LocalContext.current

                LaunchedEffect(uiState.windowBrightnessPct) {
                    val pct = uiState.windowBrightnessPct
                    if (pct != null) {
                        val lp = window.attributes
                        lp.screenBrightness = (pct / 100f).coerceIn(0.10f, 1.0f)
                        window.attributes = lp
                    }
                }

                val systemVoiceIntentLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult()
                ) { result ->
                    if (result.resultCode == Activity.RESULT_OK) {
                        val spoken = result.data
                            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                            ?.firstOrNull()
                        if (!spoken.isNullOrBlank()) {
                            vm.sendMessage(spoken)
                        }
                    }
                }

                val launchFallbackVoiceDialog: () -> Unit = {
                    runCatching {
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(
                                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                            )
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US.toLanguageTag())
                            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to S.H.A.M.I.A.")
                        }
                        systemVoiceIntentLauncher.launch(intent)
                    }
                }

                val micPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { granted ->
                    if (granted) {
                        vm.toggleVoiceListening(onFallbackToSystemVoiceDialog = launchFallbackVoiceDialog)
                    }
                }

                ShamiaHudScreen(
                    vm = vm,
                    onRequestMicPermissionAndListen = {
                        val granted = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                        if (granted) {
                            vm.toggleVoiceListening(onFallbackToSystemVoiceDialog = launchFallbackVoiceDialog)
                        } else {
                            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }
                )
            }
        }
    }
}
