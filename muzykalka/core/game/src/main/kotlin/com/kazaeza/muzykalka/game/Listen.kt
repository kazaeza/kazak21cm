package com.kazaeza.muzykalka.game

import com.kazaeza.muzykalka.music.Arranger
import com.kazaeza.muzykalka.music.ChordDegree
import com.kazaeza.muzykalka.music.Degree
import com.kazaeza.muzykalka.music.Harmony
import com.kazaeza.muzykalka.music.Key
import com.kazaeza.muzykalka.music.KeyPicker
import com.kazaeza.muzykalka.music.MidiNote
import com.kazaeza.muzykalka.music.RuNames
import com.kazaeza.muzykalka.music.Score
import com.kazaeza.muzykalka.music.Voice
import kotlin.random.Random

/** O-16 «Угадай про себя» (ADR-030): what holds under the hidden degree. */
enum class ListenScope(val ru: String) {
    DRONE("над тоникой"),
    CHORDS("над I IV V vi"),
    ALL_CHORDS("над всеми аккордами"),
}

/** One hands-free question: hear it, name it in your head during the pause, then hear the answer. */
data class ListenItem(
    val key: Key,
    val degree: Degree,
    /** The chord under the note; null = the tonic drone. */
    val over: ChordDegree?,
    val drone: List<MidiNote>,
    /** The note — after the scale when the key is new ([newKey]). */
    val question: Score,
    /** After the pause: the note again, walking home. */
    val answer: Score,
    val newKey: Boolean,
    /** What the voice says with the answer: «пять». */
    val spoken: String,
)

/**
 * «Угадай про себя» (O-16, ADR-030): Tir and «Над аккордами» without buttons — the degree sounds,
 * a pause lets you name it silently, then the answer is shown, said aloud and walks home. Made for
 * headphones and a phone in the pocket: the delay turns plain listening into gentle recall
 * (RESEARCH §4 A). Questions come from the same drivers as the games, so keys, blocks and registers
 * behave the same; nothing is judged or recorded (there is no answer to judge).
 */
class ListenDeck(
    keyPicker: KeyPicker,
    private val random: Random,
    val scope: ListenScope,
    private val arranger: Arranger = Arranger(),
) {
    private val driver = TirDriver(
        keyPicker,
        variant = if (scope == ListenScope.DRONE) Variant.TIR_CLASSIC else Variant.TIR_OVER_CHORDS,
        arranger = arranger,
    )

    /** DRONE: every degree of the mode (Tir level 2); chords: «Над аккордами» level 1 or 2. */
    private val level = if (scope == ListenScope.CHORDS) 1 else 2

    fun next(): ListenItem {
        val t = driver.nextTask(DriverContext(random, level, emptyList()))
        val note = t.notes.single()
        val walk = arranger.line(
            listOf(note) + Harmony.resolution(note, t.key.mode), t.key, Voice.TONE,
            beatsEach = 0.6, tempoBpm = TirDriver.TEMPO, tonicFloor = t.tonic,
        )
        val question = t.prompt.audio ?: t.question
        return ListenItem(
            key = t.key, degree = note.degree, over = t.over, drone = requireNotNull(t.drone),
            question = question, answer = walk, newKey = question != t.question,
            spoken = RuNames.spokenDegree(note.degree),
        )
    }

    companion object {
        /** Pauses to choose from: time to name the degree in your head before the answer. */
        val DELAYS_MS = listOf(2_000L, 4_000L, 6_000L)
        const val DEFAULT_DELAY_MS = 4_000L
    }
}
