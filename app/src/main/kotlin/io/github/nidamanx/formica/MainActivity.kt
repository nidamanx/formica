// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.nidamanx.formica

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.nidamanx.formica.ui.FormicaApp
import io.github.nidamanx.formica.ui.theme.FormicaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            FormicaTheme {
                FormicaApp()
            }
        }
    }
}
