package com.shamia.assistant

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
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

                val micPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { granted ->
                    if (granted) {
                        vm.toggleVoiceListening()
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
                            vm.toggleVoiceListening()
                        } else {
                            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }
                )
            }
        }
    }
}
