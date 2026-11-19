package com.shamia.assistant

import android.Manifest
import android.app.Activity
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shamia.assistant.model.AppThemeMode
import com.shamia.assistant.ui.ShamiaHudScreen
import com.shamia.assistant.ui.ShamiaViewModel
import com.shamia.assistant.ui.theme.ShamiaTheme
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: ShamiaViewModel = viewModel()
            val uiState by vm.ui.collectAsStateWithLifecycle()
            val context = LocalContext.current
            val view = LocalView.current
            val isLight = uiState.themeMode == AppThemeMode.LIGHT
            var pendingHandsFreeRequest by remember { mutableStateOf(false) }

            SideEffect {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = isLight
                controller.isAppearanceLightNavigationBars = isLight
            }

            LaunchedEffect(uiState.windowBrightnessPct, uiState.screenTorchActive) {
                val lp = window.attributes
                if (uiState.screenTorchActive) {
                    lp.screenBrightness = 1.0f
                    window.attributes = lp
                } else {
                    val pct = uiState.windowBrightnessPct
                    if (pct != null) {
                        lp.screenBrightness = (pct / 100f).coerceIn(0.10f, 1.0f)
                        window.attributes = lp
                    }
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
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                        putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to S.H.A.M.I.A.")
                    }
                    systemVoiceIntentLauncher.launch(intent)
                }
            }

            val micPermissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission()
            ) { granted ->
                val wasHandsFree = pendingHandsFreeRequest
                pendingHandsFreeRequest = false
                if (granted) {
                    if (wasHandsFree) {
                        vm.toggleHandsFreeVoiceAssistant(onFallbackToSystemVoiceDialog = launchFallbackVoiceDialog)
                    } else {
                        vm.toggleVoiceListening(onFallbackToSystemVoiceDialog = launchFallbackVoiceDialog)
                    }
                }
            }

            ShamiaTheme(isLightTheme = isLight) {
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
                            pendingHandsFreeRequest = false
                            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    onRequestHandsFreeVoiceMode = {
                        val granted = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                        if (granted) {
                            vm.toggleHandsFreeVoiceAssistant(onFallbackToSystemVoiceDialog = launchFallbackVoiceDialog)
                        } else {
                            pendingHandsFreeRequest = true
                            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }
                )
            }
        }
    }
}
