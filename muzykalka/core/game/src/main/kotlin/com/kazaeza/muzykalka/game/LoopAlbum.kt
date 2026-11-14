package com.kazaeza.muzykalka.game

import com.kazaeza.muzykalka.music.ChordDegree
import com.kazaeza.muzykalka.music.Mode
import com.kazaeza.muzykalka.music.SongOpenings

/** A song of a loop, as the album lists it. */
data class LoopSong(val id: String, val title: String, val artist: String?, val learning: Boolean)

/**
 * O-47 «Коллекция ходов»: one opening loop of the library — the chords, the songs that begin with it, and how the
 * user has done on it in «Чья гармония?». [known]: the last [LoopAlbum.KNOWN_RUN] answers on it were all right.
 */
data class LoopCard(
    /** Equal for loops the ear cannot tell apart ([SongOpenings.shape]); the card's identity. */
    val shape: String,
    /** The loop as most of its songs write it, and the mode it is written in. */
    val chords: List<ChordDegree>,
    val mode: Mode,
    val songs: List<LoopSong>,
    val asked: Int,
    val right: Int,
    val known: Boolean,
)

/**
 * O-47: the album of the library's opening loops. Pure: built from the songs and the attempts of «Чья гармония?»
 * (levels 1–2 record the loop, level 3 the song — both count for the song's loop).
 */
object LoopAlbum {
    /** Right answers in a row, the latest ones, that make a loop «known». */
    const val KNOWN_RUN = 3

    fun build(songs: List<GuessSong>, attempts: List<AttemptRecord>): List<LoopCard> {
        class Member(val song: GuessSong, val opening: List<ChordDegree>)

        val members = songs.mapNotNull { s ->
            SongOpenings.changes(s.song, SongGuessDriver.OPENING).takeIf { it.size >= SongGuessDriver.MIN_CHANGES }?.let { Member(s, it) }
        }
        val byShape = members.groupBy { SongOpenings.shape(it.opening) }
        val shapeOfSignature = members.associate { SongOpenings.signature(it.opening) to SongOpenings.shape(it.opening) }
        val shapeOfSong = members.associate { it.song.id to SongOpenings.shape(it.opening) }
        // every answer of the quiz, oldest first, filed under the loop it was about
        val answers = HashMap<String, MutableList<Boolean>>()
        attempts.filter { it.variant == Variant.TIR_SONG }.sortedBy { it.atEpochMs }.forEach { a ->
            val code = a.itemKey.substringAfter("song:L", "").substringAfter(':', "")
            val shape = if (a.itemKey.startsWith("song:L3:")) shapeOfSong[code] else shapeOfSignature[code]
            if (shape != null) answers.getOrPut(shape) { ArrayList() } += a.correct
        }
        return byShape.map { (shape, group) ->
            val results = answers[shape].orEmpty()
            // the spelling most songs of the loop use; the title order breaks ties, so the card does not flicker
            val chords = group.groupBy { SongOpenings.signature(it.opening) }.entries
                .sortedWith(compareByDescending<Map.Entry<String, List<Member>>> { it.value.size }.thenBy { it.key })
                .first().value.first()
            LoopCard(
                shape = shape,
                chords = chords.opening,
                mode = chords.song.song.mode,
                songs = group.map { LoopSong(it.song.id, it.song.song.title, it.song.song.artist, it.song.learning) }
                    .sortedWith(compareByDescending<LoopSong> { it.learning }.thenBy { it.title.lowercase() }),
                asked = results.size,
                right = results.count { it },
                known = results.size >= KNOWN_RUN && results.takeLast(KNOWN_RUN).all { it },
            )
        }.sortedWith(compareByDescending<LoopCard> { it.songs.size }.thenBy { SongOpenings.signature(it.chords) })
    }
}
