package com.kazaeza.muzykalka.nav

import kotlinx.serialization.Serializable

/** The eight screens of the plan. Type-safe routes (Navigation Compose 2.8+). */
@Serializable data object TodayRoute
@Serializable data object ModesRoute
@Serializable data object LibraryRoute
@Serializable data object LexiconRoute
@Serializable data object ProgressRoute
@Serializable data object SettingsRoute
@Serializable data object CaptureRoute

/** Full-screen game; [variant] is a `Variant.name`. */
@Serializable data class GameRoute(val variant: String)
