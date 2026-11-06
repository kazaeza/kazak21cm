package com.kazaeza.muzykalka.nav

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.kazaeza.muzykalka.AppGraph
import com.kazaeza.muzykalka.feature.capture.CaptureScreen
import com.kazaeza.muzykalka.feature.game.GameScreen
import com.kazaeza.muzykalka.feature.lexicon.LexiconScreen
import com.kazaeza.muzykalka.feature.library.LibraryScreen
import com.kazaeza.muzykalka.feature.modes.ModesScreen
import com.kazaeza.muzykalka.feature.progress.ProgressScreen
import com.kazaeza.muzykalka.feature.settings.SettingsScreen
import com.kazaeza.muzykalka.feature.today.TodayScreen
import com.kazaeza.muzykalka.game.Variant
import kotlin.reflect.KClass

private data class Tab(val route: Any, val routeClass: KClass<*>, val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab(TodayRoute, TodayRoute::class, "Сегодня", Icons.Filled.Home),
    Tab(ModesRoute, ModesRoute::class, "Режимы", Icons.Filled.PlayArrow),
    Tab(LibraryRoute, LibraryRoute::class, "Библиотека", Icons.AutoMirrored.Filled.List),
    Tab(LexiconRoute, LexiconRoute::class, "Лексикон", Icons.Filled.Create),
    Tab(ProgressRoute, ProgressRoute::class, "Прогресс", Icons.Filled.Star),
)

@Composable
fun MuzykalkaRoot(graph: AppGraph) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val destination = entry?.destination
    val showBar = TABS.any { tab -> destination?.hasRoute(tab.routeClass) == true }

    Scaffold(
        bottomBar = {
            if (showBar) {
                NavigationBar {
                    TABS.forEach { tab ->
                        NavigationBarItem(
                            selected = destination?.hasRoute(tab.routeClass) == true,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label, maxLines = 1) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = TodayRoute, modifier = Modifier.padding(padding)) {
            composable<TodayRoute> {
                TodayScreen(
                    onStartSet = { nav.navigate(GameRoute(Variant.TIR_CLASSIC.name)) },
                    onMinimalDay = { nav.navigate(GameRoute(Variant.TAPER_ORDER.name)) },
                    onSettings = { nav.navigate(SettingsRoute) },
                )
            }
            composable<ModesRoute> { ModesScreen(onPlay = { v -> nav.navigate(GameRoute(v.name)) }) }
            composable<LibraryRoute> { LibraryScreen(onCapture = { nav.navigate(CaptureRoute) }) }
            composable<LexiconRoute> { LexiconScreen() }
            composable<ProgressRoute> { ProgressScreen() }
            composable<SettingsRoute> { SettingsScreen(graph.settings, onBack = { nav.popBackStack() }) }
            composable<CaptureRoute> { CaptureScreen(onBack = { nav.popBackStack() }) }
            composable<GameRoute> { back ->
                val route = back.toRoute<GameRoute>()
                GameScreen(Variant.valueOf(route.variant), onExit = { nav.popBackStack() })
            }
        }
    }
}
