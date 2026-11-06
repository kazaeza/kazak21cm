package com.kazaeza.muzykalka.nav

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.kazaeza.muzykalka.AppGraph
import com.kazaeza.muzykalka.LocalAppGraph
import com.kazaeza.muzykalka.data.repo.LibraryKind
import com.kazaeza.muzykalka.feature.capture.EditorScreen
import com.kazaeza.muzykalka.feature.exam.ExamScreen
import com.kazaeza.muzykalka.feature.game.GameScreen
import com.kazaeza.muzykalka.feature.game.RoundSpec
import com.kazaeza.muzykalka.feature.lexicon.LexiconScreen
import com.kazaeza.muzykalka.feature.library.ItemScreen
import com.kazaeza.muzykalka.feature.library.LibraryScreen
import com.kazaeza.muzykalka.feature.modes.ModeDetailScreen
import com.kazaeza.muzykalka.feature.modes.ModesScreen
import com.kazaeza.muzykalka.feature.progress.ProgressScreen
import com.kazaeza.muzykalka.feature.set.SetScreen
import com.kazaeza.muzykalka.feature.settings.SettingsScreen
import com.kazaeza.muzykalka.feature.settings.SoundCheckScreen
import com.kazaeza.muzykalka.feature.taper.TaperScreen
import com.kazaeza.muzykalka.feature.taper.TaperSpec
import com.kazaeza.muzykalka.feature.today.TodayScreen
import com.kazaeza.muzykalka.game.ModeId
import com.kazaeza.muzykalka.game.Variant
import com.kazaeza.muzykalka.ui.theme.Juicy
import kotlin.reflect.KClass

private data class Tab(val route: Any, val routeClass: KClass<*>, val label: String, val icon: String)

private val TABS = listOf(
    Tab(TodayRoute, TodayRoute::class, "Сегодня", "🏠"),
    Tab(ModesRoute, ModesRoute::class, "Режимы", "🎮"),
    Tab(LibraryRoute, LibraryRoute::class, "Библиотека", "📚"),
    Tab(LexiconRoute, LexiconRoute::class, "Лексикон", "💬"),
    Tab(ProgressRoute, ProgressRoute::class, "Прогресс", "📈"),
)

@Composable
fun MuzykalkaRoot(graph: AppGraph) {
    CompositionLocalProvider(LocalAppGraph provides graph) {
        val nav = rememberNavController()
        val entry by nav.currentBackStackEntryAsState()
        val destination = entry?.destination
        val showBar = TABS.any { tab -> destination?.hasRoute(tab.routeClass) == true }
        fun tab(route: Any) = nav.navigate(route) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }

        Scaffold(
            containerColor = Juicy.Ink,
            bottomBar = {
                if (showBar) {
                    NavigationBar(containerColor = Juicy.Ink2) {
                        TABS.forEach { t ->
                            val selected = destination?.hasRoute(t.routeClass) == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = { tab(t.route) },
                                icon = { Text(t.icon, style = MaterialTheme.typography.titleLarge) },
                                label = { Text(t.label, maxLines = 1, style = MaterialTheme.typography.labelSmall) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedTextColor = Juicy.Orange,
                                    unselectedTextColor = Juicy.TextFaint,
                                    indicatorColor = Juicy.Ink4,
                                ),
                            )
                        }
                    }
                }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().background(Juicy.Ink).padding(bottom = padding.calculateBottomPadding())) {
                NavHost(
                    nav,
                    startDestination = TodayRoute,
                    enterTransition = { fadeIn() },
                    exitTransition = { fadeOut() },
                ) {
                    composable<TodayRoute> {
                        TodayScreen(
                            onStartSet = { nav.navigate(SetRoute(minimal = false)) },
                            onMinimalDay = { nav.navigate(SetRoute(minimal = true)) },
                            onSettings = { nav.navigate(SettingsRoute) },
                            onPlay = { v -> nav.navigate(GameRoute(v.name)) },
                            onLibrary = { tab(LibraryRoute) },
                            onProgress = { tab(ProgressRoute) },
                            onExam = { nav.navigate(ExamRoute) },
                        )
                    }
                    composable<ModesRoute> { ModesScreen(onOpen = { m -> nav.navigate(ModeRoute(m.name)) }) }
                    composable<ModeRoute> { back ->
                        val mode = ModeId.valueOf(back.toRoute<ModeRoute>().mode)
                        ModeDetailScreen(
                            mode = mode,
                            onBack = { nav.popBackStack() },
                            onPlay = { v, level -> nav.navigate(GameRoute(v.name, level)) },
                            onTaper = { nav.navigate(TaperRoute()) },
                        )
                    }
                    composable<LibraryRoute> {
                        LibraryScreen(
                            onCapture = { kind -> nav.navigate(EditorRoute(kind = kind.name)) },
                            onOpen = { id -> nav.navigate(ItemRoute(id)) },
                        )
                    }
                    composable<ItemRoute> { back ->
                        ItemScreen(
                            id = back.toRoute<ItemRoute>().id,
                            onBack = { nav.popBackStack() },
                            onEdit = { id -> nav.navigate(EditorRoute(id = id)) },
                            onOrder = { id -> nav.navigate(TaperRoute(itemId = id)) },
                        )
                    }
                    composable<EditorRoute> { back ->
                        val r = back.toRoute<EditorRoute>()
                        EditorScreen(editId = r.id, kind = LibraryKind.valueOf(r.kind), onDone = { nav.popBackStack() })
                    }
                    composable<LexiconRoute> { LexiconScreen() }
                    composable<ProgressRoute> { ProgressScreen(onExam = { nav.navigate(ExamRoute) }) }
                    composable<SettingsRoute> {
                        SettingsScreen(graph.settings, onBack = { nav.popBackStack() }, onSoundCheck = { nav.navigate(SoundCheckRoute) })
                    }
                    composable<SoundCheckRoute> { SoundCheckScreen(onBack = { nav.popBackStack() }) }
                    composable<GameRoute> { back ->
                        val r = back.toRoute<GameRoute>()
                        GameScreen(
                            spec = RoundSpec(Variant.valueOf(r.variant), level = r.level.takeIf { it > 0 }),
                            onExit = { nav.popBackStack() },
                        )
                    }
                    composable<TaperRoute> { back ->
                        val r = back.toRoute<TaperRoute>()
                        TaperScreen(
                            spec = TaperSpec(onlyItemId = r.itemId),
                            onExit = { nav.popBackStack() },
                            onOpenLibrary = { tab(LibraryRoute) },
                        )
                    }
                    composable<SetRoute> { back ->
                        SetScreen(minimal = back.toRoute<SetRoute>().minimal, onExit = { nav.popBackStack() }, onLibrary = { tab(LibraryRoute) })
                    }
                    composable<ExamRoute> { ExamScreen(onExit = { nav.popBackStack() }) }
                }
            }
        }
    }
}
