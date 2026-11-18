package com.shamia.assistant.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shamia.assistant.ui.theme.EmeraldOk
import com.shamia.assistant.ui.theme.GraphiteSurface
import com.shamia.assistant.ui.theme.SapphirePulse
import com.shamia.assistant.ui.theme.TextPrimary
import com.shamia.assistant.ui.theme.TextSecondary
import kotlinx.coroutines.delay

private enum class BreathPhase(val label: String, val seconds: Int, val targetScale: Float) {
    INHALE("Inhale slowly", 4, 1.0f),
    HOLD("Hold gently", 4, 1.0f),
    EXHALE("Exhale completely", 6, 0.56f),
    REST("Rest", 2, 0.56f)
}

@Composable
fun BreathingGuide(modifier: Modifier = Modifier) {
    var active by remember { mutableStateOf(false) }
    var phase by remember { mutableStateOf(BreathPhase.INHALE) }
    var remainingSec by remember { mutableIntStateOf(BreathPhase.INHALE.seconds) }

    LaunchedEffect(active) {
        if (!active) {
            phase = BreathPhase.INHALE
            remainingSec = BreathPhase.INHALE.seconds
            return@LaunchedEffect
        }
        var currentPhaseIdx = 0
        while (active) {
            val p = BreathPhase.entries[currentPhaseIdx]
            phase = p
            for (s in p.seconds downTo 1) {
                if (!active) break
                remainingSec = s
                delay(1000L)
            }
            currentPhaseIdx = (currentPhaseIdx + 1) % BreathPhase.entries.size
        }
    }

    val animatedScale by animateFloatAsState(
        targetValue = if (active) phase.targetScale else 0.72f,
        animationSpec = tween(durationMillis = 900),
        label = "breath_scale"
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = GraphiteSurface,
        border = BorderStroke(1.dp, EmeraldOk.copy(alpha = 0.30f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(44.dp)
                        .scale(animatedScale)
                        .clip(CircleShape)
                        .background(EmeraldOk.copy(alpha = 0.20f))
                ) {
                    Text(
                        text = if (active) "${remainingSec}s" else "4-4-6",
                        style = MaterialTheme.typography.labelSmall,
                        color = EmeraldOk,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = if (active) phase.label else "4-4-6 Mindful Reset",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (active) "Guided calm cadence active" else "Tap Start for a 16s breathing cycle",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
            OutlinedButton(
                onClick = { active = !active },
                border = BorderStroke(1.dp, if (active) SapphirePulse else EmeraldOk),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = if (active) SapphirePulse else EmeraldOk
                ),
                modifier = Modifier.testTag("breathing_toggle_button")
            ) {
                Text(if (active) "Stop" else "Start")
            }
        }
    }
}
