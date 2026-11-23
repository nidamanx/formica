// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.nidamanx.formica.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.github.nidamanx.formica.R
import io.github.nidamanx.formica.ui.components.SectionTopBar
import io.github.nidamanx.formica.ui.settings.CatalogKind
import io.github.nidamanx.formica.ui.settings.CatalogScreen
import io.github.nidamanx.formica.ui.settings.SettingsScreen

/** The three sections of the bottom bar. */
enum class TopLevelDestination(
    val route: String,
    @param:StringRes val label: Int,
    @param:StringRes val placeholder: Int,
    val icon: ImageVector,
) {
    PANTRY("pantry", R.string.nav_pantry, R.string.placeholder_pantry, Icons.Filled.Home),
    SHOPPING_LIST(
        "shopping_list",
        R.string.nav_shopping_list,
        R.string.placeholder_shopping_list,
        Icons.Filled.ShoppingCart,
    ),
    EXPIRIES("expiries", R.string.nav_expiries, R.string.placeholder_expiries, Icons.Filled.DateRange),
}

private object Routes {
    const val SETTINGS = "settings"
    const val CATALOG = "settings/catalog/{kind}"
    fun catalog(kind: CatalogKind) = "settings/catalog/${kind.name}"
}

@Composable
fun FormicaApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val atTopLevel = TopLevelDestination.entries.any { it.route == currentDestination?.route }

    // Each screen has its own Scaffold and top bar: this one only adds the bottom bar
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (atTopLevel) {
                NavigationBar {
                    TopLevelDestination.entries.forEach { destination ->
                        val selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = { navController.navigateToSection(destination) },
                            // The label already names the item for TalkBack
                            icon = { Icon(destination.icon, contentDescription = null) },
                            label = { Text(stringResource(destination.label)) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = TopLevelDestination.PANTRY.route,
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
        ) {
            TopLevelDestination.entries.forEach { destination ->
                composable(destination.route) {
                    PlaceholderScreen(destination) { navController.navigate(Routes.SETTINGS) }
                }
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenCatalog = { navController.navigate(Routes.catalog(it)) },
                )
            }
            composable(Routes.CATALOG) { entry ->
                val kind = CatalogKind.valueOf(requireNotNull(entry.arguments?.getString("kind")))
                CatalogScreen(kind = kind, onBack = { navController.popBackStack() })
            }
        }
    }
}

private fun NavHostController.navigateToSection(destination: TopLevelDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun PlaceholderScreen(destination: TopLevelDestination, onOpenSettings: () -> Unit) {
    Scaffold(topBar = { SectionTopBar(stringResource(destination.label), onOpenSettings) }) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(destination.placeholder))
        }
    }
}
