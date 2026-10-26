package com.firkat.fillin.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.staticCompositionLocalOf

val SaldomiLightColors = lightColorScheme(
    // Primary
    primary = Color(0xFF064A37),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD4EFE1),
    onPrimaryContainer = Color(0xFF083D2D),
    inversePrimary = Color(0xFF4FD18F),

    // Secondary
    secondary = Color(0xFF416B5B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD8E9DE),
    onSecondaryContainer = Color(0xFF1A4334),

    // Tertiary
    tertiary = Color(0xFF356C75),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD1EDF2),
    onTertiaryContainer = Color(0xFF12434B),

    // Background
    background = Color(0xFFF6F9F6),
    onBackground = Color(0xFF102B23),

    // Surface
    surface = Color.White,
    onSurface = Color(0xFF102B23),
    surfaceVariant = Color(0xFFEAF2ED),
    onSurfaceVariant = Color(0xFF102B23),
    surfaceTint = Color(0xFF064A37),

    // Surface containers
    surfaceDim = Color(0xFFDAE4DD),
    surfaceBright = Color.White,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF4F8F5),
    surfaceContainer = Color(0xFFEDF4EF),
    surfaceContainerHigh = Color(0xFFE7F0E9),
    surfaceContainerHighest = Color(0xFFDFEAE2),

    // Inverse
    inverseSurface = Color(0xFF263B33),
    inverseOnSurface = Color(0xFFF1F7F2),

    // Outline
    outline = Color(0xFF708B7C),
    outlineVariant = Color(0xFFDAE6DE),

    // Error
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),

    // Fixed colors
    primaryFixed = Color(0xFFB5F1D0),
    primaryFixedDim = Color(0xFF87DDAE),
    onPrimaryFixed = Color(0xFF062719),
    onPrimaryFixedVariant = Color(0xFF1B5841),

    secondaryFixed = Color(0xFFD4EADD),
    secondaryFixedDim = Color(0xFFB9D2C3),
    onSecondaryFixed = Color(0xFF102B23),
    onSecondaryFixedVariant = Color(0xFF315746),

    tertiaryFixed = Color(0xFFCFEEF1),
    tertiaryFixedDim = Color(0xFFAFD6DC),
    onTertiaryFixed = Color(0xFF0B2D33),
    onTertiaryFixedVariant = Color(0xFF285A63),

    scrim = Color.Black,
)

val SaldomiDarkColors = darkColorScheme(
    // Primary
    primary = Color(0xFF4FD18F),
    onPrimary = Color(0xFF06271D),
    primaryContainer = Color(0xFF13573E),
    onPrimaryContainer = Color(0xFFC5F4D6),
    inversePrimary = Color(0xFF064A37),

    // Secondary
    secondary = Color(0xFFB4D6C2),
    onSecondary = Color(0xFF193C2D),
    secondaryContainer = Color(0xFF2C5644),
    onSecondaryContainer = Color(0xFFD0E9D8),

    // Tertiary
    tertiary = Color(0xFFA4D3DC),
    onTertiary = Color(0xFF123941),
    tertiaryContainer = Color(0xFF2B5760),
    onTertiaryContainer = Color(0xFFD1EEF2),

    // Background
    background = Color(0xFF12251E),
    onBackground = Color(0xFFEDF7F0),

    // Surface
    surface = Color(0xFF203630),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF203630),
    onSurfaceVariant = Color.White,
    surfaceTint = Color(0xFF4FD18F),

    // Surface containers
    surfaceDim = Color(0xFF172B23),
    surfaceBright = Color(0xFF405F51),
    surfaceContainerLowest = Color(0xFF152C23),
    surfaceContainerLow = Color(0xFF233B30),
    surfaceContainer = Color(0xFF294237),
    surfaceContainerHigh = Color(0xFF304C40),
    surfaceContainerHighest = Color(0xFF38574A),

    // Inverse
    inverseSurface = Color(0xFFECF5EC),
    inverseOnSurface = Color(0xFF23382F),

    // Outline
    outline = Color(0xFF8BA89A),
    outlineVariant = Color(0xFF35564A),

    // Error
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),

    // Fixed colors
    primaryFixed = Color(0xFFB5F1D0),
    primaryFixedDim = Color(0xFF87DDAE),
    onPrimaryFixed = Color(0xFF062719),
    onPrimaryFixedVariant = Color(0xFF1B5841),

    secondaryFixed = Color(0xFFD4EADD),
    secondaryFixedDim = Color(0xFFB9D2C3),
    onSecondaryFixed = Color(0xFF102B23),
    onSecondaryFixedVariant = Color(0xFF315746),

    tertiaryFixed = Color(0xFFCFEEF1),
    tertiaryFixedDim = Color(0xFFAFD6DC),
    onTertiaryFixed = Color(0xFF0B2D33),
    onTertiaryFixedVariant = Color(0xFF285A63),

    scrim = Color.Black,
)

val LocalSaldomiSemanticColors =
    staticCompositionLocalOf {
        SaldomiLightSemanticColors
    }

val MaterialTheme.saldomiColors: SaldomiSemanticColors
    @Composable
    get() = LocalSaldomiSemanticColors.current


@Composable
fun FillinTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) {
        SaldomiDarkColors
    } else {
        SaldomiLightColors
    }

    val semanticColors = if (darkTheme) {
        SaldomiDarkSemanticColors
    } else {
        SaldomiLightSemanticColors
    }

    CompositionLocalProvider(
        LocalSaldomiSemanticColors provides semanticColors
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content,
        )
    }
}