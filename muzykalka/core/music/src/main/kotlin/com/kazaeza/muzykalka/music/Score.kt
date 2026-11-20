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
    /** O-82: what strikes the chords of [hit] and of the band of [groove]; the loops of [chordsOverTonic] are the piano's always. */
    val chordInstrument: ChordInstrument = ChordInstrument.PIANO,
) {
    companion object {
        /** A2: the lowest drone tonic. Phone speakers barely reproduce anything lower. */
        val DRONE_FLOOR = MidiNote(45)

        /** O-17: the lowest note of a strummed chord (G2): the bass string, still audible on a phone. */
        val STRUM_FLOOR = MidiNote(43)

        /** O-71: the tonic under chords — as firm as the bass of a chord, so that it is never lost under them. */
        const val PEDAL_VELOCITY = 0.85f

        /** O-82: how long a struck piano chord rings, in beats, and the pause between its lowest and highest key, in seconds. */
        private const val PIANO_HIT_BEATS = 3.5
        private const val PIANO_ROLL_SECONDS = 0.012

        /** O-73: the mean gap between two strings of a downstroke, in seconds: a hand, not a harp (it was ~36 ms, now ~16). */
        const val STRUM_GAP_SECONDS = 0.016

        /** O-70: how each gap of a strum differs from the mean — a hand's unevenness, fixed. */
        private val STRUM_FEEL = doubleArrayOf(1.0, 0.8, 1.25, 0.9, 1.1)
        private const val STRUM_VELOCITY = 0.9f
        private const val STRUM_TAPER = 0.045f
        private const val STRUM_VELOCITY_MIN = 0.55f

        /** How long a strummed chord is held, in beats: it rings, then lets go. */
        private const val STRUM_BEATS = 3.5
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
     * O-71 (ADR-069): [chords] of [key], one per [beatsEach] beats, played OVER the tonic: the chords are only their upper
     * structure (the root position triad or seventh, no bass voice of their own) and the tonic is struck below them with each
     * chord. A chord with its own bass root is heard as a new home, and the ear loses the key; with the tonic underneath it
     * stays a chord of the key. O-82 (ADR-078): the tonic is a piano key like the chord above it — the held string of the
     * bass guitar was heard on the first chord alone, as a guitar doubling the piano.
     */
    fun chordsOverTonic(chords: List<ChordDegree>, key: Key, beatsEach: Double = 2.0, tempoBpm: Double = 90.0): Score {
        val tonic = bassNote(ChordDegree(Degree.ONE, ChordQuality.MAJOR), key)
        val notes = chords.flatMapIndexed { i, c ->
            val start = i * beatsEach
            listOf(ScoreNote(start, beatsEach * 0.97, Voice.PIANO, tonic, PEDAL_VELOCITY)) +
                voicing(c, key).map { ScoreNote(start, beatsEach * 0.95, Voice.PIANO, it, 0.7f) }
        }
        return Score(tempoBpm, notes)
    }

    /**
     * O-82 (ADR-078): [chord] of [key] struck once on the instrument the user chose for chords ([chordInstrument]): the strum of
     * [strum] on the guitar, or [pianoChord]. What a tap on a chord plays.
     */
    fun hit(chord: ChordDegree, key: Key, tempoBpm: Double = 100.0): Score = when (chordInstrument) {
        ChordInstrument.GUITAR -> strum(chord, key, tempoBpm)
        ChordInstrument.PIANO -> pianoChord(chord, key, tempoBpm)
    }

    /**
     * O-82: [chord] on the piano — its bass note low, the triad or seventh above it in the close position of [voicing],
     * rolled from the bottom up in a few milliseconds (a hand is never exactly together) and left to ring.
     */
    fun pianoChord(chord: ChordDegree, key: Key, tempoBpm: Double = 100.0): Score {
        val rollBeats = PIANO_ROLL_SECONDS * tempoBpm / 60.0
        val keys = listOf(bassNote(chord, key)) + voicing(chord, key)
        return Score(
            tempoBpm,
            keys.mapIndexed { i, n -> ScoreNote(i * rollBeats, PIANO_HIT_BEATS, Voice.PIANO, n, if (i == 0) 0.8f else 0.7f) },
        )
    }

    /**
     * O-17: [chord] of [key] strummed once on the guitar — what a tap on a tile of the song sheet
     * plays, so «где доминанта, где девятка, где минорный» can be settled by ear. O-73 (ADR-070): the chord is held the way
     * a guitarist holds it ([GuitarVoicings]) — five or six strings over two octaves, the open strings ringing — and
     * struck as a downstroke, bass string first; where no hand holds it the old stack from [STRUM_FLOOR] is played.
     */
    fun strum(chord: ChordDegree, key: Key, tempoBpm: Double = 100.0): Score {
        val strings = GuitarVoicings.shape(chord, key)?.notes(Tuning.STANDARD) ?: stackedStrum(chord, key)
        // O-70 (ADR-068): a hand, not a machine — the gaps between the strings breathe a little, the bass string is
        // struck firmest and the stroke lightens towards the thin strings; the pattern is fixed, so a chord is the
        // same each time it is tapped
        val gapBeats = STRUM_GAP_SECONDS * tempoBpm / 60.0
        var at = 0.0
        return Score(
            tempoBpm,
            strings.mapIndexed { i, n ->
                if (i > 0) at += gapBeats * STRUM_FEEL[(i - 1) % STRUM_FEEL.size]
                ScoreNote(at, STRUM_BEATS, Voice.GUITAR, n, (STRUM_VELOCITY - STRUM_TAPER * i).coerceAtLeast(STRUM_VELOCITY_MIN))
            },
        )
    }

    /**
     * O-78 (ADR-074): [chords] as a small band plays them, not as a synthesizer pad holds them — the chord is struck
     * (on the guitar a down-stroke on the first beat and a short up-stroke on the «and» of the second; O-82: the piano strikes it
     * the same way when it is the chosen [chordInstrument]), the bass walks from the root to
     * the fifth, and a kick, a snare and a hat keep time. The chord has [beatsEach] beats. No drone and no held tonic: the
     * key is in the music, as it is in a song. Used for the transfer check, where the same loop is heard in this texture and
     * over the drone.
     */
    fun groove(chords: List<ChordDegree>, key: Key, beatsEach: Double = 2.0, tempoBpm: Double = 100.0): Score {
        val notes = ArrayList<ScoreNote>()
        val half = beatsEach / 2.0
        chords.forEachIndexed { i, chord ->
            val t = i * beatsEach
            if (chordInstrument == ChordInstrument.GUITAR) {
                val strum = strum(chord, key, tempoBpm)
                // down-stroke: the whole chord, bass string first, until the up-stroke
                strum.notes.forEach { n -> notes += n.copy(startBeat = t + n.startBeat, durationBeats = (half * 1.45 - n.startBeat).coerceAtLeast(0.1)) }
                // up-stroke on the half-beat after the second: the thin strings, quieter, top string first
                val upper = strum.notes.takeLast(4).reversed()
                val gap = STRUM_GAP_SECONDS * tempoBpm / 60.0
                upper.forEachIndexed { k, n ->
                    notes += n.copy(startBeat = t + half * 1.5 + k * gap, durationBeats = half * 0.5, velocity = (n.velocity * 0.6f).coerceAtLeast(0.3f))
                }
            } else {
                // O-82: the same two strikes on the piano — the chord on the first beat, its upper three keys again, softer, on the «and»
                val keys = voicing(chord, key)
                keys.forEach { n -> notes += ScoreNote(t, half * 1.45, Voice.PIANO, n, 0.75f) }
                keys.takeLast(3).forEach { n -> notes += ScoreNote(t + half * 1.5, half * 0.5, Voice.PIANO, n, 0.45f) }
            }
            val root = bassNote(chord, key)
            val hasFifth = 7 in chord.quality.intervals
            notes += ScoreNote(t, half * 0.9, Voice.BASS, root, 0.85f)
            notes += ScoreNote(t + half, half * 0.9, Voice.BASS, if (hasFifth) root + 7 else root, 0.7f)
            notes += ScoreNote(t, 0.2, Voice.KICK, null, 0.8f)
            notes += ScoreNote(t + half, 0.2, Voice.SNARE, null, 0.7f)
            for (k in 0 until 4) notes += ScoreNote(t + k * beatsEach / 4.0, 0.1, Voice.HAT, null, if (k % 2 == 0) 0.3f else 0.45f)
        }
        return Score(tempoBpm, notes)
    }

    /** The chord as a stack of its tones from [STRUM_FLOOR], root position (the slash bass, if any, below it). */
    private fun stackedStrum(chord: ChordDegree, key: Key): List<MidiNote> {
        val bassPc = key.pitchClassOf(chord.bass ?: chord.root)
        val bass = STRUM_FLOOR + (bassPc - STRUM_FLOOR.pitchClass)
        val root = bass + (key.pitchClassOf(chord.root) - bass.pitchClass)
        val tones = chord.quality.intervals.map { root + it }
        return if (root == bass) tones else listOf(bass) + tones
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

    /**
     * O-40: one note of [key] held as the drone — [degree]'s pitch class low (from [DRONE_FLOOR]) and its octave
     * above, the way the tonic drone is made, so the tables of the tonic drones are the same ones.
     */
    fun noteDrone(key: Key, degree: Degree): List<MidiNote> {
        val low = DRONE_FLOOR + (key.pitchClassOf(degree) - DRONE_FLOOR.pitchClass)
        return listOf(low, low + 12)
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
