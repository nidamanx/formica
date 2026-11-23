// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.nidamanx.formica.ui

import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.github.nidamanx.formica.R
import io.github.nidamanx.formica.ui.exchange.BackupsScreen
import io.github.nidamanx.formica.ui.exchange.ExchangeScreen
import io.github.nidamanx.formica.ui.exchange.ImportScreen
import io.github.nidamanx.formica.ui.expiries.ExpiriesScreen
import io.github.nidamanx.formica.ui.pantry.PantryScreen
import io.github.nidamanx.formica.ui.product.ProductDetailScreen
import io.github.nidamanx.formica.ui.product.ProductFormScreen
import io.github.nidamanx.formica.ui.settings.CatalogKind
import io.github.nidamanx.formica.ui.settings.CatalogScreen
import io.github.nidamanx.formica.ui.settings.SettingsScreen
import io.github.nidamanx.formica.ui.shopping.ShoppingScreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** The three sections of the bottom bar. */
enum class TopLevelDestination(
    val route: String,
    @param:StringRes val label: Int,
    val icon: ImageVector,
) {
    PANTRY("pantry", R.string.nav_pantry, Icons.Filled.Home),
    SHOPPING_LIST("shopping_list", R.string.nav_shopping_list, Icons.Filled.ShoppingCart),
    EXPIRIES("expiries", R.string.nav_expiries, Icons.Filled.DateRange),
}

private object Routes {
    const val SETTINGS = "settings"
    const val CATALOG = "settings/catalog/{kind}"
    const val EXCHANGE = "settings/exchange"
    const val IMPORT = "import"
    const val BACKUPS = "settings/exchange/backups"
    const val PRODUCT_NEW = "new-product"
    const val PRODUCT_EDIT = "product/{id}/edit"
    const val PRODUCT_DETAIL = "product/{id}"
    fun catalog(kind: CatalogKind) = "settings/catalog/${kind.name}"
    fun productEdit(id: String) = "product/$id/edit"
    fun productDetail(id: String) = "product/$id"
}

/**
 * @param openExpiries start on the Expiries section (tap on the daily notification)
 * @param pendingImport a file to import: the import preview opens for it
 * @param undoImport the "Undo" of the message after an import; false if the copy cannot be read
 */
@Composable
fun FormicaApp(
    openExpiries: Boolean = false,
    pendingImport: StateFlow<Uri?> = MutableStateFlow(null),
    undoImport: suspend () -> Boolean = { false },
) {
    val navController = rememberNavController()
    LaunchedEffect(Unit) {
        if (openExpiries) navController.navigateToSection(TopLevelDestination.EXPIRIES)
    }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val importedText = stringResource(R.string.import_done)
    val undoLabel = stringResource(R.string.action_undo)
    val undoneText = stringResource(R.string.msg_import_undone)
    val unreadableText = stringResource(R.string.import_unreadable)
    val importRequest by pendingImport.collectAsStateWithLifecycle()
    LaunchedEffect(importRequest) {
        if (importRequest != null) navController.navigate(Routes.IMPORT) { launchSingleTop = true }
    }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val atTopLevel = TopLevelDestination.entries.any { it.route == currentDestination?.route }

    // Each screen has its own Scaffold and top bar: this one only adds the bottom bar
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
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
            composable(TopLevelDestination.PANTRY.route) {
                PantryScreen(
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    onNewProduct = { navController.navigate(Routes.PRODUCT_NEW) },
                    onOpenProduct = { navController.navigate(Routes.productDetail(it)) },
                )
            }
            composable(TopLevelDestination.SHOPPING_LIST.route) {
                ShoppingScreen(onOpenSettings = { navController.navigate(Routes.SETTINGS) })
            }
            composable(TopLevelDestination.EXPIRIES.route) {
                ExpiriesScreen(
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    onOpenProduct = { navController.navigate(Routes.productDetail(it)) },
                )
            }
            composable(Routes.PRODUCT_NEW) {
                ProductFormScreen(productId = null, onDone = { navController.popBackStack() })
            }
            composable(Routes.PRODUCT_DETAIL) { entry ->
                val id = requireNotNull(entry.arguments?.getString("id"))
                ProductDetailScreen(
                    productId = id,
                    onBack = { navController.popBackStack() },
                    onEdit = { navController.navigate(Routes.productEdit(id)) },
                )
            }
            composable(Routes.PRODUCT_EDIT) { entry ->
                ProductFormScreen(
                    productId = requireNotNull(entry.arguments?.getString("id")),
                    onDone = { navController.popBackStack() },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenCatalog = { navController.navigate(Routes.catalog(it)) },
                    onOpenExchange = { navController.navigate(Routes.EXCHANGE) },
                )
            }
            composable(Routes.IMPORT) {
                ImportScreen(
                    onClose = { navController.popBackStack() },
                    onImported = {
                        navController.navigateToSection(TopLevelDestination.PANTRY)
                        scope.launch {
                            val result = snackbar.showSnackbar(
                                message = importedText,
                                actionLabel = undoLabel,
                                withDismissAction = true,
                            )
                            if (result == SnackbarResult.ActionPerformed) {
                                snackbar.showSnackbar(if (undoImport()) undoneText else unreadableText)
                            }
                        }
                    },
                )
            }
            composable(Routes.EXCHANGE) {
                ExchangeScreen(
                    onBack = { navController.popBackStack() },
                    onOpenBackups = { navController.navigate(Routes.BACKUPS) },
                )
            }
            composable(Routes.BACKUPS) {
                BackupsScreen(onBack = { navController.popBackStack() })
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
