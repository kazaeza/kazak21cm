package com.kazaeza.muzykalka.nav

import kotlinx.serialization.Serializable

/** Type-safe routes (Navigation Compose). */
/** The four tabs — «Сегодня», «Режимы», «Библиотека», «Прогресс» — as pages of one pager (O-29). */
@Serializable data object TabsRoute
@Serializable data object SettingsRoute
@Serializable data object SoundCheckRoute
@Serializable data class ExamRoute(val kind: String = "EAR")

/** Guided learning track details & stage picker. */
@Serializable data class TrackRoute(val trackId: String)

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

/** O-48 «Созвучия»: intervals and chords to hold, change and hear — no score (ADR-047). */
@Serializable data object HarmonyLabRoute

/** O-21 «Позиция»: the chords of a key in one zone of the neck (ADR-050). */
@Serializable data object PositionRoute

/** X-03 «Под бэкинг»: a library song's chords played bar by bar, to play over (ADR-052). */
@Serializable data class BackingRoute(val id: String)

/** O-47 «Коллекция ходов»: the library's opening loops and which of them the ear knows. */
@Serializable data object LoopsRoute

/** [training] is a [com.kazaeza.muzykalka.game.Training] name (O-55); optional [trackId]/[stageId] and [minutes] (0 = default budget) for track stages and budget scaling. */
@Serializable data class SetRoute(
    val minimal: Boolean,
    val training: String = "DAILY",
    val trackId: String? = null,
    val stageId: String? = null,
    val minutes: Int = 0,
)

@Serializable data class ItemRoute(val id: String)

/** «Улов»; [id] null = new item of [kind] (`LibraryKind.name`). */
@Serializable data class EditorRoute(val id: String? = null, val kind: String = "SONG")
