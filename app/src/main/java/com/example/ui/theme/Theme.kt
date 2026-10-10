package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.example.domain.model.ThemeMode

@Composable
fun AuraMusicTheme(
    themeMode: ThemeMode = ThemeMode.AMOLED,
    accentColorArgb: Long = 0xFF1DB954L,
    solidBackgroundArgb: Long? = null,
    content: @Composable () -> Unit
) {
    val colorScheme = remember(themeMode, accentColorArgb, solidBackgroundArgb) {
        val primary = Color(accentColorArgb)
        val onPrimary = if (primary.luminance() > 0.45f) Color(0xFF0A0E12) else Color.White
        val primaryContainer = primary.copy(alpha = 0.22f)

        when (themeMode) {
            ThemeMode.LIGHT -> lightColorScheme(
                primary = primary,
                onPrimary = onPrimary,
                primaryContainer = primary.copy(alpha = 0.16f),
                onPrimaryContainer = Color(0xFF0B141A),
                secondary = primary,
                background = LightBackground,
                onBackground = LightOnBackground,
                surface = LightSurface,
                onSurface = LightOnBackground,
                surfaceVariant = LightSurfaceVariant,
                onSurfaceVariant = LightOnSurfaceVariant
            )

            ThemeMode.DARK -> {
                val bg = solidBackgroundArgb?.let { Color(it) } ?: DarkBackground
                darkColorScheme(
                    primary = primary,
                    onPrimary = onPrimary,
                    primaryContainer = primaryContainer,
                    onPrimaryContainer = Color.White,
                    secondary = primary,
                    background = bg,
                    onBackground = DarkOnBackground,
                    surface = DarkSurface,
                    onSurface = DarkOnBackground,
                    surfaceVariant = DarkSurfaceVariant,
                    onSurfaceVariant = DarkOnSurfaceVariant
                )
            }

            ThemeMode.AMOLED -> darkColorScheme(
                primary = primary,
                onPrimary = onPrimary,
                primaryContainer = primaryContainer,
                onPrimaryContainer = Color.White,
                secondary = primary,
                background = AmoledBackground,
                onBackground = AmoledOnBackground,
                surface = AmoledSurface,
                onSurface = AmoledOnBackground,
                surfaceVariant = AmoledSurfaceVariant,
                onSurfaceVariant = AmoledOnSurfaceVariant
            )
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    AuraMusicTheme(
        themeMode = if (darkTheme) ThemeMode.AMOLED else ThemeMode.LIGHT,
        content = content
    )
}
