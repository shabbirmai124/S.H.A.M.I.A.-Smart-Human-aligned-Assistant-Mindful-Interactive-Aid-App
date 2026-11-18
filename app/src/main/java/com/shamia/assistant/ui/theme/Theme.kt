package com.shamia.assistant.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val ObsidianVoid = Color(0xFF06080C)
val GraphiteSurface = Color(0xFF0D1117)
val CardSlate = Color(0xFF121822)
val CardBorder = Color(0xFF1E293B)
val SapphirePulse = Color(0xFF38BDF8)
val SapphireBright = Color(0xFF7DD3FC)
val SapphireContainer = Color(0xFF0B2A42)
val TextPrimary = Color(0xFFE8EEF3)
val TextSecondary = Color(0xFF94A3B8)
val EmeraldOk = Color(0xFF34D399)
val AmberWarn = Color(0xFFFBBF24)
val RoseDeny = Color(0xFFFB7185)

private val ShamiaDarkColorScheme = darkColorScheme(
    primary = SapphirePulse,
    onPrimary = ObsidianVoid,
    primaryContainer = SapphireContainer,
    onPrimaryContainer = SapphireBright,
    secondary = EmeraldOk,
    onSecondary = ObsidianVoid,
    secondaryContainer = Color(0xFF0D2E25),
    onSecondaryContainer = EmeraldOk,
    tertiary = AmberWarn,
    onTertiary = ObsidianVoid,
    background = ObsidianVoid,
    onBackground = TextPrimary,
    surface = GraphiteSurface,
    onSurface = TextPrimary,
    surfaceVariant = CardSlate,
    onSurfaceVariant = TextSecondary,
    outline = CardBorder,
    error = RoseDeny,
    onError = ObsidianVoid
)

private val ShamiaTypography = Typography(
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.2).sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.5.sp,
        lineHeight = 19.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.4.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        lineHeight = 13.sp,
        letterSpacing = 0.6.sp
    )
)

private val ShamiaShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp)
)

@Composable
fun ShamiaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ShamiaDarkColorScheme,
        typography = ShamiaTypography,
        shapes = ShamiaShapes,
        content = content
    )
}
