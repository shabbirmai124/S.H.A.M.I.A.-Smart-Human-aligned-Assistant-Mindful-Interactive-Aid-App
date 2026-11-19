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
import androidx.compose.ui.geometry.Size
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
        CoreVisualState.SPEAKING -> SapphireBright
        CoreVisualState.LISTENING -> EmeraldOk
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
    val outerOrbitAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (coreState) {
                    CoreVisualState.THINKING -> 4200
                    CoreVisualState.LISTENING -> 5600
                    else -> 14000
                },
                easing = LinearEasing
            )
        ),
        label = "outer_orbit_angle"
    )
    val midOrbitAngle by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (coreState) {
                    CoreVisualState.LISTENING, CoreVisualState.THINKING -> 3600
                    else -> 9800
                },
                easing = LinearEasing
            )
        ),
        label = "mid_orbit_angle"
    )
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (coreState == CoreVisualState.LISTENING || coreState == CoreVisualState.SPEAKING) 700 else 2100
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val isLight = com.shamia.assistant.ui.theme.isLightShamiaTheme
    val dialPlateColor = if (isLight) Color(0xFFF1F5FD) else Color(0xFF0B131E)
    val cavityColor = if (isLight) Color(0xFFE8F0FE) else Color(0xFF050A11)
    val glowMidColor = if (isLight) Color(0xFFD2E3FC).copy(alpha = 0.45f) else Color(0xFF0A1420).copy(alpha = 0.35f)
    val sphereOuterDark = if (isLight) Color(0xFF174EA6) else Color(0xFF082F49)
    val sphereMidDark = if (isLight) Color(0xFF1967D2) else Color(0xFF0369A1)
    val sphereInnerLight = if (isLight) Color(0xFF8AB4F8) else Color(0xFF7DD3FC)

    val orbSize = if (compact) 74.dp else 168.dp

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

                // 1. Soft atmospheric ambient glow
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            accent.copy(alpha = 0.22f * pulse),
                            glowMidColor,
                            Color.Transparent
                        ),
                        center = center,
                        radius = r
                    ),
                    radius = r,
                    center = center
                )

                // 2. Outer instrument dial plate + thin precision rim
                drawCircle(
                    color = dialPlateColor,
                    radius = r * 0.90f,
                    center = center
                )
                drawCircle(
                    color = accent.copy(alpha = if (isLight) 0.38f else 0.26f),
                    radius = r * 0.90f,
                    center = center,
                    style = Stroke(width = if (compact) 1.2f else 1.8f)
                )

                // 3. 60 Precision chronometer ticks (12 major ticks + 48 fine ticks)
                for (i in 0 until 60) {
                    val deg = i * 6.0
                    val rad = deg * PI / 180.0
                    val isMajor = i % 5 == 0
                    val rStart = r * (if (isMajor) 0.77f else 0.82f)
                    val rEnd = r * 0.865f
                    drawLine(
                        color = accent.copy(alpha = if (isMajor) 0.75f else 0.24f),
                        start = Offset(
                            center.x + (rStart * cos(rad)).toFloat(),
                            center.y + (rStart * sin(rad)).toFloat()
                        ),
                        end = Offset(
                            center.x + (rEnd * cos(rad)).toFloat(),
                            center.y + (rEnd * sin(rad)).toFloat()
                        ),
                        strokeWidth = if (isMajor) (if (compact) 1.8f else 2.6f) else 1f,
                        cap = StrokeCap.Round
                    )
                }

                // 4. Primary rotating orbital telemetry arcs + glowing satellite nodes
                rotate(degrees = outerOrbitAngle, pivot = center) {
                    val orbitR = r * 0.67f
                    val arcTopLeft = Offset(center.x - orbitR, center.y - orbitR)
                    val arcSize = Size(orbitR * 2f, orbitR * 2f)

                    drawArc(
                        color = accent.copy(alpha = 0.76f),
                        startAngle = 215f,
                        sweepAngle = 72f,
                        useCenter = false,
                        topLeft = arcTopLeft,
                        size = arcSize,
                        style = Stroke(width = if (compact) 1.5f else 2.4f, cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = accent.copy(alpha = 0.56f),
                        startAngle = 315f,
                        sweepAngle = 58f,
                        useCenter = false,
                        topLeft = arcTopLeft,
                        size = arcSize,
                        style = Stroke(width = if (compact) 1.4f else 2.2f, cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = accent.copy(alpha = 0.76f),
                        startAngle = 38f,
                        sweepAngle = 68f,
                        useCenter = false,
                        topLeft = arcTopLeft,
                        size = arcSize,
                        style = Stroke(width = if (compact) 1.5f else 2.4f, cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = accent.copy(alpha = 0.48f),
                        startAngle = 135f,
                        sweepAngle = 48f,
                        useCenter = false,
                        topLeft = arcTopLeft,
                        size = arcSize,
                        style = Stroke(width = if (compact) 1.3f else 2.0f, cap = StrokeCap.Round)
                    )

                    val node1Rad = 262.0 * PI / 180.0
                    val node2Rad = 82.0 * PI / 180.0
                    drawCircle(
                        color = if (isLight) accent else Color(0xFFBAE6FD),
                        radius = if (compact) 2.2f else 3.8f,
                        center = Offset(
                            center.x + (orbitR * cos(node1Rad)).toFloat(),
                            center.y + (orbitR * sin(node1Rad)).toFloat()
                        )
                    )
                    drawCircle(
                        color = accent,
                        radius = if (compact) 2.0f else 3.4f,
                        center = Offset(
                            center.x + (orbitR * cos(node2Rad)).toFloat(),
                            center.y + (orbitR * sin(node2Rad)).toFloat()
                        )
                    )
                }

                // 5. Counter-rotating inner micro-dashed orbital ring
                rotate(degrees = midOrbitAngle, pivot = center) {
                    drawCircle(
                        color = accent.copy(alpha = 0.40f),
                        radius = r * 0.56f,
                        center = center,
                        style = Stroke(
                            width = if (compact) 1.1f else 1.6f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 10f), 0f),
                            cap = StrokeCap.Round
                        )
                    )
                }

                // 6. Inner core cavity + crisp aperture ring
                drawCircle(
                    color = cavityColor,
                    radius = r * 0.44f,
                    center = center
                )
                drawCircle(
                    color = accent.copy(alpha = 0.62f),
                    radius = r * 0.44f,
                    center = center,
                    style = Stroke(width = if (compact) 1.4f else 2.2f)
                )

                // 7. 3D Luminous Quantum Core Sphere with off-center specular glow
                val sphereR = r * 0.30f * pulse
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White,
                            sphereInnerLight,
                            accent,
                            sphereMidDark,
                            sphereOuterDark
                        ),
                        center = Offset(center.x - sphereR * 0.14f, center.y - sphereR * 0.16f),
                        radius = sphereR * 1.15f
                    ),
                    radius = sphereR,
                    center = center
                )

                // 8. Top glass crescent specular reflection on the 3D sphere
                val highlightR = sphereR * 0.80f
                drawArc(
                    color = Color.White.copy(alpha = 0.72f),
                    startAngle = 212f,
                    sweepAngle = 116f,
                    useCenter = false,
                    topLeft = Offset(center.x - highlightR, center.y - highlightR),
                    size = Size(highlightR * 2f, highlightR * 2f),
                    style = Stroke(width = if (compact) 1.2f else 2.0f, cap = StrokeCap.Round)
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
        }
    }
}
