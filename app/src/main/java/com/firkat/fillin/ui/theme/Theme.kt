package com.firkat.fillin.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

val LocalSaldomiSemanticColors =
    staticCompositionLocalOf {
        SaldomiLightSemanticColors
    }

val LocalSaldomiDesignColors =
    staticCompositionLocalOf {
        SaldomiLightDesignColors
    }

val MaterialTheme.saldomiDesignColors: SaldomiDesignColors
    @Composable
    get() = LocalSaldomiDesignColors.current

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
        LocalSaldomiSemanticColors provides semanticColors,
        LocalSaldomiDesignColors provides if (darkTheme) SaldomiDarkDesignColors else SaldomiLightDesignColors
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
