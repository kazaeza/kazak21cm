package com.kazaeza.muzykalka.music

/**
 * Sound sources the synth (core:dsp) can render. [TONE] is the soft sustained voice that sings
 * over the tonic drone (ADR-017): one timbre family for the reference and the question.
 */
enum class Voice { PIANO, GUITAR, BASS, TONE, KICK, SNARE, HAT, CLICK }

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
    companion object {
        /** A2: the lowest drone tonic. Phone speakers barely reproduce anything lower. */
        val DRONE_FLOOR = MidiNote(45)
    }

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

    /**
     * A sketch of [song] in [key]: every section once, in the order they first appear, a chord per
     * [beatsEach] beats, at most [maxChords] chords — enough to check by ear what was just played,
     * not a recording of the song.
     */
    fun sketch(song: Song, key: Key, beatsEach: Double = 2.0, tempoBpm: Double = 84.0, maxChords: Int = 32): Score {
        val chords = song.performanceOrder.distinct().flatMap { s -> s.bars.flatMap { it.chords } }.take(maxChords)
        return chords(chords, key, beatsEach, tempoBpm)
    }

    /**
     * The tonic drone of [key] (ADR-017): the tonic and its octave, low but still audible on a
     * phone speaker. Mode-neutral on purpose — every degree is heard against home alone.
     */
    fun drone(key: Key): List<MidiNote> {
        val low = key.tonicAtOrAbove(DRONE_FLOOR)
        return listOf(low, low + 12)
    }

    /**
     * O-08: a chord of [key] held as the drone instead of the tonic — its root low (from
     * [DRONE_FLOOR]) and the triad an octave above. At most four notes, as the drone bank holds.
     */
    fun chordDrone(chord: ChordDegree, key: Key): List<MidiNote> {
        val rootPc = key.pitchClassOf(chord.root)
        val low = DRONE_FLOOR + (rootPc - DRONE_FLOOR.pitchClass)
        return listOf(low) + chord.quality.intervals.take(3).map { low + 12 + it }
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
