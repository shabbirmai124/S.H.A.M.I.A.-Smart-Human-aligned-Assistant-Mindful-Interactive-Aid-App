package com.shamia.assistant.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shamia.assistant.model.CoreVisualState
import com.shamia.assistant.ui.theme.AmberWarn
import com.shamia.assistant.ui.theme.EmeraldOk
import com.shamia.assistant.ui.theme.SapphireBright
import com.shamia.assistant.ui.theme.SapphirePulse
import com.shamia.assistant.ui.theme.TextPrimary
import com.shamia.assistant.ui.theme.TextSecondary
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun QuantumCore(
    coreState: CoreVisualState,
    addressAs: String,
    activeModelName: String,
    compact: Boolean = false,
    onTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = when (coreState) {
        CoreVisualState.AWAITING_PERMISSION -> AmberWarn
        CoreVisualState.SPEAKING -> EmeraldOk
        CoreVisualState.LISTENING -> SapphireBright
        else -> SapphirePulse
    }

    val stateLabel = when (coreState) {
        CoreVisualState.IDLE -> "Ready for $addressAs"
        CoreVisualState.LISTENING -> "Listening..."
        CoreVisualState.THINKING -> "Reasoning..."
        CoreVisualState.SPEAKING -> "Speaking..."
        CoreVisualState.AWAITING_PERMISSION -> "Awaiting Authorization"
    }

    val infiniteTransition = rememberInfiniteTransition(label = "quantum_core")
    val outerAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (coreState == CoreVisualState.THINKING) 5000 else 16000,
                easing = LinearEasing
            )
        ),
        label = "outer_angle"
    )
    val innerAngle by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (coreState == CoreVisualState.LISTENING) 4500 else 11000,
                easing = LinearEasing
            )
        ),
        label = "inner_angle"
    )
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (coreState == CoreVisualState.LISTENING || coreState == CoreVisualState.SPEAKING) 850 else 2200
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val orbSize = if (compact) 72.dp else 172.dp

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(orbSize)
                .minimumInteractiveComponentSize()
                .clip(CircleShape)
                .testTag("arc_reactor_button")
                .semantics {
                    contentDescription = "S.H.A.M.I.A. Quantum Core voice trigger. Status: $stateLabel"
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Button,
                    onClick = onTap
                )
        ) {
            Canvas(modifier = Modifier.size(orbSize)) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val r = size.minDimension / 2f

                // Outer radial halo
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(accent.copy(alpha = 0.24f * pulse), Color.Transparent),
                        center = center,
                        radius = r
                    ),
                    radius = r,
                    center = center
                )

                // Machined precision bezel + 60 ticks
                drawCircle(
                    color = accent.copy(alpha = 0.30f),
                    radius = r * 0.88f,
                    center = center,
                    style = Stroke(width = 1.5f)
                )
                for (i in 0 until 60) {
                    val deg = i * 6.0
                    val rad = deg * PI / 180.0
                    val isMajor = i % 5 == 0
                    val rStart = r * (if (isMajor) 0.78f else 0.83f)
                    val rEnd = r * 0.88f
                    drawLine(
                        color = accent.copy(alpha = if (isMajor) 0.65f else 0.25f),
                        start = Offset(
                            center.x + (rStart * cos(rad)).toFloat(),
                            center.y + (rStart * sin(rad)).toFloat()
                        ),
                        end = Offset(
                            center.x + (rEnd * cos(rad)).toFloat(),
                            center.y + (rEnd * sin(rad)).toFloat()
                        ),
                        strokeWidth = if (isMajor) 2f else 1f
                    )
                }

                // Rotating outer telemetry dashed ring + orbital nodes
                rotate(degrees = outerAngle, pivot = center) {
                    drawCircle(
                        color = accent.copy(alpha = 0.60f),
                        radius = r * 0.70f,
                        center = center,
                        style = Stroke(
                            width = if (compact) 2f else 3f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 12f), 0f),
                            cap = StrokeCap.Round
                        )
                    )
                    drawCircle(
                        color = accent,
                        radius = if (compact) 2.5f else 4.5f,
                        center = Offset(center.x + r * 0.70f, center.y)
                    )
                    drawCircle(
                        color = accent.copy(alpha = 0.75f),
                        radius = if (compact) 2f else 3.5f,
                        center = Offset(center.x - r * 0.70f, center.y)
                    )
                }

                // Counter-rotating inner aperture arc
                rotate(degrees = innerAngle, pivot = center) {
                    drawArc(
                        color = accent.copy(alpha = 0.75f),
                        startAngle = 0f,
                        sweepAngle = 210f,
                        useCenter = false,
                        topLeft = Offset(center.x - r * 0.52f, center.y - r * 0.52f),
                        size = androidx.compose.ui.geometry.Size(r * 1.04f, r * 1.04f),
                        style = Stroke(width = if (compact) 2f else 3.2f, cap = StrokeCap.Round)
                    )
                }

                // Acoustic wave rings during voice states
                if (coreState == CoreVisualState.LISTENING || coreState == CoreVisualState.SPEAKING) {
                    drawCircle(
                        color = accent.copy(alpha = 0.32f),
                        radius = r * 0.60f * pulse,
                        center = center,
                        style = Stroke(width = 2f)
                    )
                }

                // Luminous breathing core orb
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = 0.92f), accent, accent.copy(alpha = 0.15f)),
                        center = center,
                        radius = r * 0.36f * pulse
                    ),
                    radius = r * 0.34f * pulse,
                    center = center
                )
            }
        }

        if (!compact) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stateLabel.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = accent,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp
            )
            Text(
                text = activeModelName,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                modifier = Modifier.padding(top = 2.dp)
            )
        } else {
            Text(
                text = if (coreState == CoreVisualState.LISTENING) "STOP" else "TAP MIC",
                fontSize = 9.sp,
                color = TextPrimary.copy(alpha = 0.8f),
                fontWeight = FontWeight.Medium
            )
        }
    }
}
