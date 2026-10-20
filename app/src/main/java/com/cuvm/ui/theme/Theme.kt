package com.cuvm.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Values from replica/design/tokens.json (roles -> M3 color scheme). Change them there first.
private val Dark = darkColorScheme(
    background = Color(0xFF111416), surface = Color(0xFF111416),
    surfaceContainer = Color(0xFF1D2022), surfaceContainerLow = Color(0xFF1D2022),
    surfaceContainerHigh = Color(0xFF272A2D), surfaceContainerHighest = Color(0xFF272A2D),
    outlineVariant = Color(0xFF44474A), outline = Color(0xFF8E9194),
    onBackground = Color(0xFFE1E3E5), onSurface = Color(0xFFE1E3E5), onSurfaceVariant = Color(0xFFC4C7CA),
    primary = Color(0xFFA8C8FF), onPrimary = Color(0xFF0A305F),
    primaryContainer = Color(0xFF254777), onPrimaryContainer = Color(0xFFD5E3FF),
    secondaryContainer = Color(0xFF254777), onSecondaryContainer = Color(0xFFD5E3FF),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005), scrim = Color(0xFF000000),
)

private val Light = lightColorScheme(
    background = Color(0xFFF8F9FB), surface = Color(0xFFF8F9FB),
    surfaceContainer = Color(0xFFECEEF1), surfaceContainerLow = Color(0xFFECEEF1),
    surfaceContainerHigh = Color(0xFFE2E4E7), surfaceContainerHighest = Color(0xFFE2E4E7),
    outlineVariant = Color(0xFFC4C7CA), outline = Color(0xFF74777A),
    onBackground = Color(0xFF191C1E), onSurface = Color(0xFF191C1E), onSurfaceVariant = Color(0xFF44474A),
    primary = Color(0xFF3D5F90), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD5E3FF), onPrimaryContainer = Color(0xFF001B3C),
    secondaryContainer = Color(0xFFD5E3FF), onSecondaryContainer = Color(0xFF001B3C),
    error = Color(0xFFBA1A1A), onError = Color(0xFFFFFFFF), scrim = Color(0xFF000000),
)

@Composable
fun CuciVmTheme(theme: String, style: String, content: @Composable () -> Unit) {
    val dark = when (theme) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
    val ctx = LocalContext.current
    val scheme = when {
        style == "wallpaper" && Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> Dark
        else -> Light
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
