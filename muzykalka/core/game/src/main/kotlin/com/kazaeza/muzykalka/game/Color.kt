package com.kazaeza.muzykalka.game

import com.kazaeza.muzykalka.music.Arranger
import com.kazaeza.muzykalka.music.ChordDegree
import com.kazaeza.muzykalka.music.ChordQuality
import com.kazaeza.muzykalka.music.Degree
import com.kazaeza.muzykalka.music.DegreeFormatter
import com.kazaeza.muzykalka.music.DegreeNotation
import com.kazaeza.muzykalka.music.DegreeNote
import com.kazaeza.muzykalka.music.Key
import com.kazaeza.muzykalka.music.KeyPicker
import com.kazaeza.muzykalka.music.MidiNote
import com.kazaeza.muzykalka.music.Mode
import com.kazaeza.muzykalka.music.RuNames
import com.kazaeza.muzykalka.music.Score
import com.kazaeza.muzykalka.music.Voice

/**
 * One question of «Цвет аккорда»: [question] is a chord of [quality] on the root of [key] — a pitch of chance that is
 * never named. There is no key in the musical sense and no drone: the body of the chord is the whole question.
 */
data class ColorTask(
    override val itemKey: String,
    override val prompt: Prompt,
    val level: Int,
    /** Only its tonic matters: the chord's root. Not shown (rule 1). */
    val key: Key,
    val quality: ChordQuality,
    val choices: List<ChordQuality>,
    val question: Score,
    /** The chord taken apart, tone by tone — heard after the answer. */
    val apart: Score,
) : DroneTask {
    override val drone: List<MidiNote>? get() = null
    override val replay: Score get() = question

    /** The tones as degrees above the root: 1, ♭3, 5, ♭7. */
    val tones: List<Degree> get() = quality.intervals.map { Degree.canonical(it) }
}

/**
 * O-50 «Цвет аккорда» (ADR-048): the body of a chord, drilled for speed — the companion of «Созвучия» (ADR-047), where
 * it is learnt by hand. A chord sounds on a random root; its quality is picked. Level 1: major or minor. Level 2: all
 * the triads (diminished, augmented, the two suspended). Level 3: the seventh chords. Level 4: everything.
 * After the answer the chord is taken apart tone by tone (rule 5 for a miss: the chord again first).
 */
class ColorDriver(
    private val keyPicker: KeyPicker,
    private val notation: DegreeNotation = DegreeNotation.ARABIC,
    private val arranger: Arranger = Arranger(),
) : ModeDriver<ColorTask> {
    override val variant: Variant = Variant.TIR_COLOR

    override val accepts: Set<Class<out Answer>> = setOf(Answer.QualityChoice::class.java, Answer.Pass::class.java)

    private var last: ChordQuality? = null

    override fun nextTask(context: DriverContext): ColorTask {
        val level = context.level.coerceIn(1, Levels.TIR_COLOR.size)
        val pad = pad(level)
        val prefix = "color:L$level:"
        // two colours cannot avoid a repeat; a wider pad does not ask the same one twice in a row
        val quality = ItemWeights.pick(context.random, pad, avoid = last.takeIf { pad.size > 2 }) { ItemWeights.weight(prefix + it.name, context.history) }
        last = quality
        val key = keyPicker.next(Mode.MAJOR)
        val chord = ChordDegree(Degree.ONE, quality)
        val question = arranger.chords(listOf(chord), key, beatsEach = CHORD_BEATS, tempoBpm = TEMPO)
        val tones = quality.intervals.map { DegreeNote(Degree.canonical(it), Math.floorDiv(it, 12)) }
        // up the chord and back to its root: the stack is heard from the inside
        val apart = arranger.line(tones + DegreeNote(Degree.ONE), key, Voice.PIANO, beatsEach = 0.6, tempoBpm = TEMPO, tonicFloor = MidiNote(53))
        return ColorTask(
            itemKey = prefix + quality.name,
            prompt = Prompt(title = "?", audio = question, key = null),
            level = level, key = key, quality = quality, choices = pad, question = question, apart = apart,
        )
    }

    fun name(quality: ChordQuality): String = DegreeFormatter.chord(ChordDegree(Degree.ONE, quality), notation) + " · " + RuNames.quality(quality)

    override fun evaluate(task: ColorTask, answer: Answer): Evaluation {
        val (correct, givenText, givenCode) = when (answer) {
            is Answer.QualityChoice -> Triple(answer.quality == task.quality, name(answer.quality), answer.quality.name)
            Answer.Pass -> Triple(false, "не знаю", "?")
            else -> Triple(false, "—", "—")
        }
        return Evaluation(
            correct = correct,
            expectedText = name(task.quality),
            givenText = givenText,
            correction = if (correct) null else task.question.then(task.apart, gapBeats = 0.5),
            taskHadSound = true,
            confirmation = if (correct) task.apart else null,
            expectedCode = task.quality.name,
            givenCode = givenCode,
        )
    }

    companion object {
        const val TEMPO = 100.0
        const val CHORD_BEATS = 2.5
        const val PROMOTION_TARGET_MS = 3_000L

        val TRIADS = listOf(ChordQuality.MAJOR, ChordQuality.MINOR, ChordQuality.DIMINISHED, ChordQuality.AUGMENTED, ChordQuality.SUS2, ChordQuality.SUS4)
        val SEVENTHS = listOf(ChordQuality.DOMINANT7, ChordQuality.MAJOR7, ChordQuality.MINOR7, ChordQuality.HALF_DIMINISHED7, ChordQuality.DIMINISHED7)

        /** The qualities asked and offered on [level]. */
        fun pad(level: Int): List<ChordQuality> = when (level) {
            1 -> TRIADS.take(2)
            2 -> TRIADS
            3 -> SEVENTHS
            else -> TRIADS + SEVENTHS
        }
    }
}
