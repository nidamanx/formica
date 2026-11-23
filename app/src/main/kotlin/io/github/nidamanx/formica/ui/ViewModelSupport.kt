// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.nidamanx.formica.ui

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.CreationExtras
import io.github.nidamanx.formica.AppContainer
import io.github.nidamanx.formica.FormicaApplication

/** The app container, for the ViewModel factories (no DI framework). */
fun CreationExtras.appContainer(): AppContainer = (this[APPLICATION_KEY] as FormicaApplication).container
