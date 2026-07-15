package com.example.earnitv2.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = WarmCoral,
    onPrimary = WarmInk,
    primaryContainer = WarmCoralContainer,
    onPrimaryContainer = WarmText,
    secondary = WarmTextMuted,
    onSecondary = WarmInk,
    secondaryContainer = WarmSurfaceRaised,
    onSecondaryContainer = WarmText,
    tertiary = WarmSuccess,
    onTertiary = WarmInk,
    tertiaryContainer = WarmSuccessContainer,
    onTertiaryContainer = WarmText,
    background = WarmInk,
    onBackground = WarmText,
    surface = WarmSurface,
    onSurface = WarmText,
    surfaceVariant = WarmSurfaceRaised,
    onSurfaceVariant = WarmTextMuted,
    outline = WarmOutline,
    outlineVariant = WarmOutline
)

private val LightColorScheme = lightColorScheme(
    primary = CoralDark,
    onPrimary = CreamSurface,
    primaryContainer = CoralLightContainer,
    onPrimaryContainer = CocoaText,
    secondary = CocoaMuted,
    onSecondary = CreamSurface,
    secondaryContainer = CreamSurfaceRaised,
    onSecondaryContainer = CocoaText,
    tertiary = SuccessDark,
    onTertiary = CreamSurface,
    tertiaryContainer = SuccessLightContainer,
    onTertiaryContainer = CocoaText,
    background = CreamBackground,
    onBackground = CocoaText,
    surface = CreamSurface,
    onSurface = CocoaText,
    surfaceVariant = CreamSurfaceRaised,
    onSurfaceVariant = CocoaMuted,
    outline = CreamOutline,
    outlineVariant = CreamOutline
)

@Composable
fun EarnitV2Theme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
