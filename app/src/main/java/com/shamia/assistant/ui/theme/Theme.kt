package com.shamia.assistant.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Immutable
data class ShamiaColorPalette(
    val isLight: Boolean,
    val obsidianVoid: Color,
    val graphiteSurface: Color,
    val cardSlate: Color,
    val cardBorder: Color,
    val sapphirePulse: Color,
    val sapphireBright: Color,
    val sapphireContainer: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val emeraldOk: Color,
    val amberWarn: Color,
    val roseDeny: Color
)

private val ShamiaDarkPalette = ShamiaColorPalette(
    isLight = false,
    obsidianVoid = Color(0xFF05080D),
    graphiteSurface = Color(0xFF090E15),
    cardSlate = Color(0xFF0D131D),
    cardBorder = Color(0xFF1A2636),
    sapphirePulse = Color(0xFF29B6F6),
    sapphireBright = Color(0xFF38BDF8),
    sapphireContainer = Color(0xFF0B253A),
    textPrimary = Color(0xFFF1F5F9),
    textSecondary = Color(0xFF8B9BB0),
    emeraldOk = Color(0xFF34D399),
    amberWarn = Color(0xFFFBBF24),
    roseDeny = Color(0xFFFB7185)
)

/**
 * Crisp Google Material 3 Clean White Palette (matching Google App / Pixel clean white surfaces,
 * Google Blue #1A73E8 accent, subtle #F8F9FA tonal bars, and #DADCE0 hairline dividers).
 */
private val ShamiaGoogleWhitePalette = ShamiaColorPalette(
    isLight = true,
    obsidianVoid = Color(0xFFFFFFFF),
    graphiteSurface = Color(0xFFF8F9FA),
    cardSlate = Color(0xFFFFFFFF),
    cardBorder = Color(0xFFDADCE0),
    sapphirePulse = Color(0xFF1A73E8),
    sapphireBright = Color(0xFF1967D2),
    sapphireContainer = Color(0xFFE8F0FE),
    textPrimary = Color(0xFF202124),
    textSecondary = Color(0xFF5F6368),
    emeraldOk = Color(0xFF1E8E3E),
    amberWarn = Color(0xFFE37400),
    roseDeny = Color(0xFFD93025)
)

val LocalShamiaColors = staticCompositionLocalOf { ShamiaDarkPalette }

val isLightShamiaTheme: Boolean
    @Composable
    @ReadOnlyComposable
    get() = LocalShamiaColors.current.isLight

val ObsidianVoid: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalShamiaColors.current.obsidianVoid

val GraphiteSurface: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalShamiaColors.current.graphiteSurface

val CardSlate: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalShamiaColors.current.cardSlate

val CardBorder: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalShamiaColors.current.cardBorder

val SapphirePulse: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalShamiaColors.current.sapphirePulse

val SapphireBright: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalShamiaColors.current.sapphireBright

val SapphireContainer: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalShamiaColors.current.sapphireContainer

val TextPrimary: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalShamiaColors.current.textPrimary

val TextSecondary: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalShamiaColors.current.textSecondary

val EmeraldOk: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalShamiaColors.current.emeraldOk

val AmberWarn: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalShamiaColors.current.amberWarn

val RoseDeny: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalShamiaColors.current.roseDeny

private val ShamiaDarkColorScheme = darkColorScheme(
    primary = ShamiaDarkPalette.sapphirePulse,
    onPrimary = ShamiaDarkPalette.obsidianVoid,
    primaryContainer = ShamiaDarkPalette.sapphireContainer,
    onPrimaryContainer = ShamiaDarkPalette.sapphireBright,
    secondary = ShamiaDarkPalette.emeraldOk,
    onSecondary = ShamiaDarkPalette.obsidianVoid,
    secondaryContainer = Color(0xFF0D2E25),
    onSecondaryContainer = ShamiaDarkPalette.emeraldOk,
    tertiary = ShamiaDarkPalette.amberWarn,
    onTertiary = ShamiaDarkPalette.obsidianVoid,
    background = ShamiaDarkPalette.obsidianVoid,
    onBackground = ShamiaDarkPalette.textPrimary,
    surface = ShamiaDarkPalette.graphiteSurface,
    onSurface = ShamiaDarkPalette.textPrimary,
    surfaceVariant = ShamiaDarkPalette.cardSlate,
    onSurfaceVariant = ShamiaDarkPalette.textSecondary,
    outline = ShamiaDarkPalette.cardBorder,
    error = ShamiaDarkPalette.roseDeny,
    onError = ShamiaDarkPalette.obsidianVoid
)

private val ShamiaGoogleLightColorScheme = lightColorScheme(
    primary = ShamiaGoogleWhitePalette.sapphirePulse,
    onPrimary = Color.White,
    primaryContainer = ShamiaGoogleWhitePalette.sapphireContainer,
    onPrimaryContainer = ShamiaGoogleWhitePalette.sapphireBright,
    secondary = ShamiaGoogleWhitePalette.emeraldOk,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE6F4EA),
    onSecondaryContainer = Color(0xFF137333),
    tertiary = ShamiaGoogleWhitePalette.amberWarn,
    onTertiary = Color.White,
    background = ShamiaGoogleWhitePalette.obsidianVoid,
    onBackground = ShamiaGoogleWhitePalette.textPrimary,
    surface = ShamiaGoogleWhitePalette.graphiteSurface,
    onSurface = ShamiaGoogleWhitePalette.textPrimary,
    surfaceVariant = Color(0xFFF1F3F4),
    onSurfaceVariant = ShamiaGoogleWhitePalette.textSecondary,
    outline = ShamiaGoogleWhitePalette.cardBorder,
    error = ShamiaGoogleWhitePalette.roseDeny,
    onError = Color.White
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
        fontSize = 11.5.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.3.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        lineHeight = 13.sp,
        letterSpacing = 0.5.sp
    )
)

private val ShamiaShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp)
)

@Composable
fun ShamiaTheme(
    isLightTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val palette = if (isLightTheme) ShamiaGoogleWhitePalette else ShamiaDarkPalette
    val scheme = if (isLightTheme) ShamiaGoogleLightColorScheme else ShamiaDarkColorScheme
    CompositionLocalProvider(LocalShamiaColors provides palette) {
        MaterialTheme(
            colorScheme = scheme,
            typography = ShamiaTypography,
            shapes = ShamiaShapes,
            content = content
        )
    }
}
