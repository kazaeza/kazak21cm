package com.kazaeza.muzykalka.nav

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
import com.kazaeza.muzykalka.feature.common.LocalTabBarSpace
import com.kazaeza.muzykalka.feature.exam.ExamScreen
import com.kazaeza.muzykalka.feature.feel.FeelScreen
import com.kazaeza.muzykalka.feature.listen.ListenScreen
import com.kazaeza.muzykalka.feature.game.GameScreen
import com.kazaeza.muzykalka.feature.game.RoundSpec
import com.kazaeza.muzykalka.feature.library.ItemScreen
import com.kazaeza.muzykalka.feature.library.LibraryScreen
import com.kazaeza.muzykalka.feature.modes.ModesScreen
import com.kazaeza.muzykalka.feature.progress.ProgressScreen
import com.kazaeza.muzykalka.feature.set.SetScreen
import com.kazaeza.muzykalka.feature.settings.SettingsScreen
import com.kazaeza.muzykalka.feature.settings.SoundCheckScreen
import com.kazaeza.muzykalka.feature.taper.BlindScreen
import com.kazaeza.muzykalka.feature.taper.TaperScreen
import com.kazaeza.muzykalka.feature.taper.TaperSpec
import com.kazaeza.muzykalka.feature.today.TodayScreen
import com.kazaeza.muzykalka.game.Variant
import com.kazaeza.muzykalka.ui.component.FloatingTabBar
import com.kazaeza.muzykalka.ui.component.FloatingTabBarMargin
import com.kazaeza.muzykalka.ui.component.FloatingTabBarReserve
import com.kazaeza.muzykalka.ui.component.TabIcon
import com.kazaeza.muzykalka.ui.component.TabSpec
import com.kazaeza.muzykalka.ui.theme.Juicy
import com.kazaeza.muzykalka.ui.theme.Motion
import kotlin.reflect.KClass

private data class Tab(val route: Any, val routeClass: KClass<*>, val spec: TabSpec)

/** Four tabs, no more: the app is a practice tool, not a super-app. */
private val TABS = listOf(
    Tab(TodayRoute, TodayRoute::class, TabSpec("Сегодня", TabIcon.TODAY)),
    Tab(ModesRoute, ModesRoute::class, TabSpec("Режимы", TabIcon.MODES)),
    Tab(LibraryRoute, LibraryRoute::class, TabSpec("Библиотека", TabIcon.LIBRARY)),
    Tab(ProgressRoute, ProgressRoute::class, TabSpec("Прогресс", TabIcon.PROGRESS)),
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

        val selectedTab = TABS.indexOfFirst { t -> destination?.hasRoute(t.routeClass) == true }

        Box(Modifier.fillMaxSize().background(Juicy.Ink)) {
            CompositionLocalProvider(LocalTabBarSpace provides if (showBar) FloatingTabBarReserve else 0.dp) {
                NavHost(
                    nav,
                    startDestination = TodayRoute,
                    // «Обертон»: screens surface out of the dark — a slow fade with a slight rise
                    enterTransition = { fadeIn(tween(Motion.MEDIUM)) + scaleIn(tween(Motion.MEDIUM), initialScale = 0.985f) },
                    exitTransition = { fadeOut(tween(Motion.SHORT)) },
                    popEnterTransition = { fadeIn(tween(Motion.MEDIUM)) },
                    popExitTransition = { fadeOut(tween(Motion.SHORT)) + scaleOut(tween(Motion.SHORT), targetScale = 0.985f) },
                ) {
                    composable<TodayRoute> {
                        TodayScreen(
                            onStartSet = { nav.navigate(SetRoute(minimal = false)) },
                            onShortSet = { nav.navigate(SetRoute(minimal = true)) },
                            onSettings = { nav.navigate(SettingsRoute) },
                            onPlay = { v -> nav.navigate(GameRoute(v.name)) },
                            onFeel = { nav.navigate(FeelRoute) },
                            onLibrary = { tab(LibraryRoute) },
                        )
                    }
                    composable<ModesRoute> {
                        ModesScreen(
                            onPlay = { v, level -> nav.navigate(GameRoute(v.name, level)) },
                            onSongs = { nav.navigate(TaperRoute()) },
                            onBlind = { nav.navigate(BlindRoute) },
                            onFeel = { nav.navigate(FeelRoute) },
                            onListen = { nav.navigate(ListenRoute) },
                            onLibrary = { tab(LibraryRoute) },
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
                    composable<ProgressRoute> { ProgressScreen(onExam = { nav.navigate(ExamRoute) }, onFeel = { nav.navigate(FeelRoute) }) }
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
                    composable<BlindRoute> { BlindScreen(onExit = { nav.popBackStack() }) }
                    composable<SetRoute> { back ->
                        SetScreen(minimal = back.toRoute<SetRoute>().minimal, onExit = { nav.popBackStack() }, onLibrary = { tab(LibraryRoute) })
                    }
                    composable<ExamRoute> { ExamScreen(onExit = { nav.popBackStack() }) }
                    composable<ListenRoute> { ListenScreen(onBack = { nav.popBackStack() }) }
                    composable<FeelRoute> {
                        FeelScreen(onBack = { nav.popBackStack() }, onTir = { nav.navigate(GameRoute(Variant.TIR_CLASSIC.name)) })
                    }
                }
            }
            AnimatedVisibility(
                visible = showBar,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = FloatingTabBarMargin),
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it },
            ) {
                FloatingTabBar(
                    tabs = TABS.map { it.spec },
                    selected = selectedTab,
                    onSelect = { i -> tab(TABS[i].route) },
                )
            }
        }
    }
}
