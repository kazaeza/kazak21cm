package com.kazaeza.muzykalka.nav

import kotlinx.serialization.Serializable

/** Type-safe routes (Navigation Compose). */
/** The four tabs — «Сегодня», «Режимы», «Библиотека», «Прогресс» — as pages of one pager (O-29). */
@Serializable data object TabsRoute
@Serializable data object SettingsRoute
@Serializable data object SoundCheckRoute
@Serializable data object ExamRoute

/** «Вайб ступеней»: listen to every degree against its tonic (ADR-015). */
@Serializable data object FeelRoute

/** O-16 «Угадай про себя»: hands-free, the answer after a pause (ADR-030). */
@Serializable data object ListenRoute

/** Full-screen drill; [variant] is a `Variant.name`, [level] 0 = selected level. */
@Serializable data class GameRoute(val variant: String, val level: Int = 0)

/** The Taper; [itemId] orders exactly that item. */
@Serializable data class TaperRoute(val itemId: String? = null)

/** O-10: a random song from the likes, without its name. */
@Serializable data object BlindRoute

@Serializable data class SetRoute(val minimal: Boolean)

@Serializable data class ItemRoute(val id: String)

/** «Улов»; [id] null = new item of [kind] (`LibraryKind.name`). */
@Serializable data class EditorRoute(val id: String? = null, val kind: String = "SONG")
