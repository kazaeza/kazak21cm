package com.kazaeza.muzykalka.music

/** Sound sources the synth (core:dsp) can render. */
enum class Voice { PIANO, GUITAR, BASS, KICK, SNARE, HAT, CLICK }

/**
 * One timed sound. Drum voices ignore [note] (may be null). Times are in beats; the renderer
 * converts with [Score.tempoBpm]. Absolute (MIDI) on purpose: a Score is what comes OUT of the
 * speaker, produced from degrees by an arranger — never authored by hand in absolute terms.
 */
data class ScoreNote(
    val startBeat: Double,
    val durationBeats: Double,
    val voice: Voice,
    val note: MidiNote? = null,
    val velocity: Float = 0.8f,
) {
    init {
        require(startBeat >= 0.0) { "startBeat < 0" }
        require(durationBeats > 0.0) { "durationBeats <= 0" }
        require(velocity in 0f..1f) { "velocity out of 0..1" }
        require(note != null || voice in setOf(Voice.KICK, Voice.SNARE, Voice.HAT, Voice.CLICK)) { "$voice needs a note" }
    }

    val endBeat: Double get() = startBeat + durationBeats
}

/** A short piece of audio to play: a cadence, a question, a correction, a backing loop. */
data class Score(val tempoBpm: Double, val notes: List<ScoreNote>) {
    init {
        require(tempoBpm in 20.0..400.0) { "tempo out of range" }
    }

    val lengthBeats: Double get() = notes.maxOfOrNull { it.endBeat } ?: 0.0
    val lengthSeconds: Double get() = lengthBeats * 60.0 / tempoBpm

    /** This score followed by [other] after a gap of [gapBeats] (other's tempo is ignored). */
    fun then(other: Score, gapBeats: Double = 0.0): Score {
        val offset = lengthBeats + gapBeats
        return copy(notes = notes + other.notes.map { it.copy(startBeat = it.startBeat + offset) })
    }

    companion object {
        fun empty(tempoBpm: Double = 90.0) = Score(tempoBpm, emptyList())
    }
}

/**
 * Turns degrees into sound in a concrete key. The ONLY bridge from relative to absolute pitch
 * for playback. Voicings are close-position around [pianoCenter], bass in the low guitar register.
 */
class Arranger(
    private val pianoCenter: MidiNote = MidiNote(60),
    private val bassFloor: MidiNote = MidiNote(36),
) {
    /** Close voicing of [chord] in [key]: root-position bass + upper structure nearest to [pianoCenter]. */
    fun voicing(chord: ChordDegree, key: Key): List<MidiNote> {
        val rootPc = key.pitchClassOf(chord.root)
        val lowestRoot = pianoCenter - 7 + (rootPc - (pianoCenter - 7).pitchClass)
        return chord.quality.intervals.map { lowestRoot + it }
    }

    fun bassNote(chord: ChordDegree, key: Key): MidiNote {
        val pc = key.pitchClassOf(chord.bass ?: chord.root)
        return bassFloor + (pc - bassFloor.pitchClass)
    }

    /** Block chords, one per [beatsEach] beats, with bass. Used for cadences. */
    fun chords(chords: List<ChordDegree>, key: Key, beatsEach: Double = 1.0, tempoBpm: Double = 90.0): Score {
        val notes = chords.flatMapIndexed { i, c ->
            val start = i * beatsEach
            voicing(c, key).map { ScoreNote(start, beatsEach * 0.95, Voice.PIANO, it, 0.7f) } +
                ScoreNote(start, beatsEach * 0.95, Voice.BASS, bassNote(c, key), 0.8f)
        }
        return Score(tempoBpm, notes)
    }

    /** A melodic line; the tonic of octave 0 is placed at or above [tonicFloor]. */
    fun line(
        notes: List<DegreeNote>,
        key: Key,
        voice: Voice = Voice.PIANO,
        beatsEach: Double = 1.0,
        tempoBpm: Double = 90.0,
        tonicFloor: MidiNote = MidiNote(57),
    ): Score {
        val tonic = key.tonicAtOrAbove(tonicFloor)
        return Score(tempoBpm, notes.mapIndexed { i, n -> ScoreNote(i * beatsEach, beatsEach * 0.95, voice, key.midiOf(n, tonic)) })
    }
}
