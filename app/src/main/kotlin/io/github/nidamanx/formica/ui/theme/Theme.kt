// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.nidamanx.formica.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

// Provisional Material 3 baseline colors: the app's own palette is chosen later
private val LightColors = lightColorScheme()
private val DarkColors = darkColorScheme()

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
