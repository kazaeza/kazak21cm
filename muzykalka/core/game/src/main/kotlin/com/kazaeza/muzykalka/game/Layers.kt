package com.kazaeza.muzykalka.game

import com.kazaeza.muzykalka.music.Arranger
import com.kazaeza.muzykalka.music.ChordDegree
import com.kazaeza.muzykalka.music.ChordFunction
import com.kazaeza.muzykalka.music.ChordParser
import com.kazaeza.muzykalka.music.ChordQuality
import com.kazaeza.muzykalka.music.Degree
import com.kazaeza.muzykalka.music.DegreeNote
import com.kazaeza.muzykalka.music.Harmony
import com.kazaeza.muzykalka.music.Key
import com.kazaeza.muzykalka.music.KeyPicker
import com.kazaeza.muzykalka.music.MidiNote
import com.kazaeza.muzykalka.music.Mode
import com.kazaeza.muzykalka.music.RuNames
import com.kazaeza.muzykalka.music.Score
import com.kazaeza.muzykalka.music.Voice
import kotlin.random.Random

/** The two things a chord is that are not the same thing: what it does in the key, and what it is made of. */
enum class Layer { FUNCTION, COLOR }

/**
 * One question of «Слои аккорда»: the tonic hums, a chord of the key is played over it and ONE layer of it is asked —
 * [Layer.FUNCTION] (home, away, tension) or [Layer.COLOR] (major, minor, seventh…). [answer] is what is heard after.
 */
data class LayersTask(
    override val itemKey: String,
    override val prompt: Prompt,
    val level: Int,
    val key: Key,
    val layer: Layer,
    val chord: ChordDegree,
    val function: ChordFunction,
    val functionChoices: List<ChordFunction>,
    val colorChoices: List<ChordQuality>,
    val question: Score,
    override val drone: List<MidiNote>,
    /** The sound after the answer: what the function does (it stays, comes home, resolves) or the colour taken apart. */
    val answer: Score,
    override val intro: Score? = null,
    override val introDrone: List<MidiNote>? = null,
    /** Level 3: this is the second question about a chord that was just named by its job. */
    val sameChordAgain: Boolean = false,
) : DroneTask {
    override val replay: Score get() = question
}

/**
 * O-75 «Слои аккорда» (ADR-072, idea 6 of the research notes): a chord is two things a beginner takes for one — what it DOES
 * in the key (home, away, tension) and what it is MADE of (major, minor, seventh). «Мажор — это дом, минор — это грусть»
 * is the usual mix-up; vi is minor and home, IV is major and away, V⁷ is a seventh and the same tension as V. So a job
 * is asked on chords of every quality (the job is picked first, evenly, then a chord that has it), and on the last level
 * the same chord is asked twice, as two layers peeled in turn: first the job, then the colour. The tonic hums throughout
 * (a new key is set by its scale, as in «Аккорд над тоникой»); the key holds for [KEY_BLOCK] questions.
 *
 * Whether it works is a hypothesis, not a known result: the attempts are stored with the layer in the item key, so the
 * two layers can be compared in the statistics.
 */
class LayersDriver(
    private val keyPicker: KeyPicker,
    private val arranger: Arranger = Arranger(),
) : ModeDriver<LayersTask> {
    override val variant: Variant = Variant.TIR_LAYERS

    override val accepts: Set<Class<out Answer>> = setOf(Answer.FunctionChoice::class.java, Answer.QualityChoice::class.java, Answer.Pass::class.java)

    private var blockKey: Key? = null
    private var blockLeft = 0
    private var blockLevel = 0
    private var lastFunction: ChordFunction? = null
    private var lastChord: ChordDegree? = null

    /** Level 3: the chord just asked by its job, to be asked again by its colour. */
    private var pending: ChordDegree? = null

    override fun nextTask(context: DriverContext): LayersTask {
        val level = context.level.coerceIn(1, Levels.TIR_LAYERS.size)
        val r = context.random
        val again = pending.takeIf { level >= 3 && blockKey != null && blockLevel == level }
        pending = null
        val (key, newKey) = if (again != null) requireNotNull(blockKey) to false else blockKeyFor(r, level)
        val mode = key.mode
        val pad = pad(mode, level)
        val layer: Layer
        val chord: ChordDegree
        if (again != null) {
            layer = Layer.COLOR
            chord = again
        } else {
            layer = Layer.FUNCTION
            // the job first, evenly (HOME has three chords on the pad and TENSION two: a plain pick would mostly ask home),
            // then a chord that has it, one the last question was not
            val job = ItemWeights.pick(r, ChordFunction.entries, avoid = lastFunction) {
                ItemWeights.weight("layers:fn:L$level:${it.name}", context.history)
            }
            val byJob = pad.filter { ChordFunction.of(it, mode) == job }
            chord = ItemWeights.pick(r, byJob, avoid = lastChord.takeIf { byJob.size > 1 }) {
                ItemWeights.weight("layers:fn:L$level:${ChordParser.formatRoman(it)}", context.history)
            }
            lastFunction = job
            lastChord = chord
            if (level >= 3) pending = chord
        }
        val function = ChordFunction.of(chord, mode)
        val question = arranger.chordsOverTonic(listOf(chord), key, beatsEach = CHORD_BEATS, tempoBpm = TEMPO)
        val itemKey = when (layer) {
            Layer.FUNCTION -> "layers:fn:L$level:${function.name}"
            Layer.COLOR -> "layers:color:L$level:${chord.quality.name}"
        }
        return LayersTask(
            itemKey = itemKey,
            prompt = Prompt(title = "?", audio = question, key = key),
            level = level, key = key, layer = layer, chord = chord, function = function,
            functionChoices = ChordFunction.entries.toList(),
            colorChoices = colors(mode),
            question = question, drone = arranger.drone(key),
            answer = if (layer == Layer.FUNCTION) doing(chord, function, key) else apart(chord, key),
            intro = if (newKey) orientation(key) else null,
            introDrone = if (newKey) arranger.drone(key) else null,
            sameChordAgain = again != null,
        )
    }

    override fun evaluate(task: LayersTask, answer: Answer): Evaluation {
        val expectedText: String
        val expectedCode: String
        val (correct, given) = when (task.layer) {
            Layer.FUNCTION -> {
                expectedText = task.function.ru
                expectedCode = task.function.name
                when (answer) {
                    is Answer.FunctionChoice -> (answer.function == task.function) to (answer.function.ru to answer.function.name)
                    Answer.Pass -> false to ("не знаю" to "?")
                    else -> false to ("—" to "—")
                }
            }
            Layer.COLOR -> {
                expectedText = RuNames.quality(task.chord.quality)
                expectedCode = task.chord.quality.name
                when (answer) {
                    is Answer.QualityChoice -> (answer.quality == task.chord.quality) to (RuNames.quality(answer.quality) to answer.quality.name)
                    Answer.Pass -> false to ("не знаю" to "?")
                    else -> false to ("—" to "—")
                }
            }
        }
        return Evaluation(
            correct = correct,
            expectedText = expectedText,
            givenText = given.first,
            correction = if (correct) null else task.question.then(task.answer, gapBeats = 0.5),
            taskHadSound = true,
            confirmation = if (correct) task.answer else null,
            expectedCode = expectedCode,
            givenCode = given.second,
        )
    }

    /** What the job sounds like: home stays, a step away comes back, tension resolves. */
    private fun doing(chord: ChordDegree, function: ChordFunction, key: Key): Score {
        val pulls = ChordFunction.pullsTo(chord, key.mode)
        val chords = when (function) {
            ChordFunction.HOME -> listOf(chord)
            ChordFunction.AWAY -> listOf(chord, pulls.last())
            ChordFunction.TENSION -> listOf(chord, pulls.first())
        }
        return arranger.chordsOverTonic(chords, key, beatsEach = if (chords.size == 1) CHORD_BEATS else RESOLVE_BEATS, tempoBpm = TEMPO)
    }

    /** What the colour is made of: the tones of the chord, one at a time, up and back to its root. */
    private fun apart(chord: ChordDegree, key: Key): Score {
        val tones = chord.quality.intervals.map {
            val semis = chord.root.semitones + it
            DegreeNote(Degree.canonical(Math.floorMod(semis, 12)), Math.floorDiv(semis, 12))
        }
        return arranger.line(tones + tones.first(), key, Voice.PIANO, beatsEach = 0.6, tempoBpm = TEMPO, tonicFloor = MidiNote(53))
    }

    /** New key over the drone: the scale up to the octave and back to 1 — major or minor is set. */
    private fun orientation(key: Key): Score {
        val up = key.mode.degrees.map { DegreeNote(it) } + DegreeNote(Degree.ONE, 1)
        return arranger.line(up + DegreeNote(Degree.ONE), key, Voice.TONE, beatsEach = 0.4, tempoBpm = TEMPO, tonicFloor = key.tonicAtOrAbove(MidiNote(55)))
    }

    private fun blockKeyFor(r: Random, level: Int): Pair<Key, Boolean> {
        val kept = blockKey?.takeIf { blockLeft > 0 && blockLevel == level }
        val key = kept ?: keyPicker.next(if (level >= 2 && r.nextDouble() < MINOR_SHARE) Mode.MINOR else Mode.MAJOR)
        if (kept == null) {
            blockKey = key
            blockLevel = level
            blockLeft = KEY_BLOCK
        }
        blockLeft--
        return key to (kept == null)
    }

    companion object {
        const val TEMPO = 90.0
        const val CHORD_BEATS = 2.5
        const val RESOLVE_BEATS = 1.5

        /** Questions in one key: a function question and its colour question each count one. */
        const val KEY_BLOCK = 8
        const val MINOR_SHARE = 0.4
        const val PROMOTION_TARGET_MS = 4_000L

        /**
         * The chords that can be asked on [level]: level 1 the five triads with a plain job (I ii IV V vi — not iii, which the
         * ear takes for either, and not the diminished vii°); then every triad and seventh of the key.
         */
        fun pad(mode: Mode, level: Int): List<ChordDegree> = when {
            level <= 1 -> listOf(0, 1, 3, 4, 5).map { Harmony.diatonicChord(mode, it) }
            else -> (0..6).flatMap { listOf(Harmony.diatonicChord(mode, it), Harmony.diatonicChord(mode, it, sevenths = true)) }
        }

        /** The colours offered on the colour layer: every quality a chord of the key has, triads first. */
        fun colors(mode: Mode): List<ChordQuality> =
            pad(mode, 2).map { it.quality }.distinct().sortedBy { ChordQuality.entries.indexOf(it) }
    }
}
