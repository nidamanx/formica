// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.nidamanx.formica.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/*
 * Palette "sky and honey" (owner, 2026-11-24), from the launcher icon: Material 3 tonal-spot
 * schemes (2021 specification) of the sky blue #3F92C4 for primary, secondary, neutral and
 * error roles, and of the ant's honey #E0A042 for the tertiary roles, which mark what
 * expires soon. Fixed on purpose: the app keeps its colours whatever the wallpaper.
 */
private val LightColors = lightColorScheme(
    primary = Color(0xFF256489),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFC8E6FF),
    onPrimaryContainer = Color(0xFF004C6E),
    inversePrimary = Color(0xFF94CDF7),
    secondary = Color(0xFF4F606E),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD3E5F5),
    onSecondaryContainer = Color(0xFF384956),
    tertiary = Color(0xFF805611),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDDB4),
    onTertiaryContainer = Color(0xFF633F00),
    background = Color(0xFFF6F9FE),
    onBackground = Color(0xFF181C20),
    surface = Color(0xFFF6F9FE),
    onSurface = Color(0xFF181C20),
    surfaceVariant = Color(0xFFDDE3EA),
    onSurfaceVariant = Color(0xFF41474D),
    surfaceTint = Color(0xFF256489),
    inverseSurface = Color(0xFF2D3135),
    inverseOnSurface = Color(0xFFEEF1F6),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A),
    outline = Color(0xFF71787E),
    outlineVariant = Color(0xFFC1C7CE),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFF6F9FE),
    surfaceContainer = Color(0xFFEBEEF3),
    surfaceContainerHigh = Color(0xFFE5E8ED),
    surfaceContainerHighest = Color(0xFFDFE3E8),
    surfaceContainerLow = Color(0xFFF1F4F9),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFD7DADF),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF94CDF7),
    onPrimary = Color(0xFF00344D),
    primaryContainer = Color(0xFF004C6E),
    onPrimaryContainer = Color(0xFFC8E6FF),
    inversePrimary = Color(0xFF256489),
    secondary = Color(0xFFB7C9D9),
    onSecondary = Color(0xFF21323F),
    secondaryContainer = Color(0xFF384956),
    onSecondaryContainer = Color(0xFFD3E5F5),
    tertiary = Color(0xFFF5BC6F),
    onTertiary = Color(0xFF452B00),
    tertiaryContainer = Color(0xFF633F00),
    onTertiaryContainer = Color(0xFFFFDDB4),
    background = Color(0xFF101417),
    onBackground = Color(0xFFDFE3E8),
    surface = Color(0xFF101417),
    onSurface = Color(0xFFDFE3E8),
    surfaceVariant = Color(0xFF41474D),
    onSurfaceVariant = Color(0xFFC1C7CE),
    surfaceTint = Color(0xFF94CDF7),
    inverseSurface = Color(0xFFDFE3E8),
    inverseOnSurface = Color(0xFF2D3135),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF8B9198),
    outlineVariant = Color(0xFF41474D),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF353A3E),
    surfaceContainer = Color(0xFF1C2024),
    surfaceContainerHigh = Color(0xFF262A2E),
    surfaceContainerHighest = Color(0xFF313539),
    surfaceContainerLow = Color(0xFF181C20),
    surfaceContainerLowest = Color(0xFF0A0F12),
    surfaceDim = Color(0xFF101417),
)

@Composable
fun FormicaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
