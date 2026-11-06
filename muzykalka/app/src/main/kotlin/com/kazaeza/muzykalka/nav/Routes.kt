package com.kazaeza.muzykalka.nav

import kotlinx.serialization.Serializable

/** Type-safe routes (Navigation Compose). */
@Serializable data object TodayRoute
@Serializable data object ModesRoute
@Serializable data object LibraryRoute
@Serializable data object ProgressRoute
@Serializable data object SettingsRoute
@Serializable data object SoundCheckRoute
@Serializable data object ExamRoute

/** «Вайб ступеней»: listen to every degree against its tonic (ADR-015). */
@Serializable data object FeelRoute

/** [mode] is a `ModeId.name`. */
@Serializable data class ModeRoute(val mode: String)

/** Full-screen drill; [variant] is a `Variant.name`, [level] 0 = selected level. */
@Serializable data class GameRoute(val variant: String, val level: Int = 0)

/** The Taper; [itemId] orders exactly that item. */
@Serializable data class TaperRoute(val itemId: String? = null)

@Serializable data class SetRoute(val minimal: Boolean)

@Serializable data class ItemRoute(val id: String)

/** «Улов»; [id] null = new item of [kind] (`LibraryKind.name`). */
@Serializable data class EditorRoute(val id: String? = null, val kind: String = "SONG")
