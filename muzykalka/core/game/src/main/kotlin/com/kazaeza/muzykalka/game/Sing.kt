package com.kazaeza.muzykalka.game

import com.kazaeza.muzykalka.music.Arranger
import com.kazaeza.muzykalka.music.DEFAULT_A4_HZ
import com.kazaeza.muzykalka.music.Degree
import com.kazaeza.muzykalka.music.DegreeFormatter
import com.kazaeza.muzykalka.music.DegreeNotation
import com.kazaeza.muzykalka.music.DegreeNote
import com.kazaeza.muzykalka.music.Harmony
import com.kazaeza.muzykalka.music.Key
import com.kazaeza.muzykalka.music.KeyPicker
import com.kazaeza.muzykalka.music.MidiNote
import com.kazaeza.muzykalka.music.Mode
import com.kazaeza.muzykalka.music.PitchReading
import com.kazaeza.muzykalka.music.RuNames
import com.kazaeza.muzykalka.music.Score
import com.kazaeza.muzykalka.music.Voice
import kotlin.math.abs
import kotlin.math.roundToInt

/** A task with a tonic drone under it (ADR-017): the game keeps it sounding while the task lasts. */
interface DroneTask : Task {
    /** Notes to hold under this task; null = no drone. */
    val drone: List<MidiNote>?

    /** What «🔁» (and the return from a pause) plays again. */
    val replay: Score?
}

/** «Спой ступень»: the degree is shown, the user sings it. */
data class SingTask(
    override val itemKey: String,
    override val prompt: Prompt,
    val key: Key,
    /** Home in the register of the reference; any octave of the degree is accepted. */
    val tonic: MidiNote,
    val degree: Degree,
    override val drone: List<MidiNote>?,
    /** The tonic alone: «home» again (without a drone it also opens every question). */
    val reference: Score,
) : DroneTask {
    override val replay: Score get() = reference
}

/**
 * «Спой ступень» (ticket O-07, RESEARCH §4 C): name → sound, the way back that Tir does not train —
 * to play a degree by ear it has to be heard inside first, and singing is the shortest way there.
 * The degree is shown; the first stable sung note is the answer (rule 4), in any octave; its
 * distance in cents is kept (graduation: VOICE, median ≤ 30 cents).
 *
 * With headphones the tonic drone sounds all the time, as in Tir. Without them it would leak into
 * the microphone and be «heard» as a sung note, so the drone stays off and every question opens
 * with the tonic instead (ADR-021). The key holds for [TirDriver.KEY_BLOCK] questions; a new key
 * opens with its scale.
 *
 * «Пропевка» ([Variant.KAMERTON_SEARCH], O-14, ADR-028) is the same task in search mode: «спой
 * квинту», and a miss does not end it — the engine keeps listening until the degree is found.
 * Level 1 asks only the steady degrees 1 · 3 · 5.
 */
class SingDriver(
    private val keyPicker: KeyPicker,
    private val notation: DegreeNotation = DegreeNotation.ARABIC,
    private val withDrone: Boolean = true,
    private val arranger: Arranger = Arranger(),
    override val variant: Variant = Variant.KAMERTON_SING,
) : ModeDriver<SingTask> {
    init {
        require(variant == Variant.KAMERTON_SING || variant == Variant.KAMERTON_SEARCH) { "SingDriver plays $variant" }
    }

    override val searches: Boolean get() = variant == Variant.KAMERTON_SEARCH

    override val accepts: Set<Class<out Answer>> = setOf(Answer.Sung::class.java, Answer.NotePlayed::class.java, Answer.Pass::class.java)

    private var blockKey: Key? = null
    private var blockLeft = 0

    override fun nextTask(context: DriverContext): SingTask {
        val r = context.random
        val kept = blockKey?.takeIf { blockLeft > 0 }
        val key = kept ?: keyPicker.next(if (r.nextDouble() < MINOR_SHARE) Mode.MINOR else Mode.MAJOR)
        if (kept == null) {
            blockKey = key
            blockLeft = TirDriver.KEY_BLOCK
        }
        blockLeft--
        val tonic = key.tonicAtOrAbove(VOICE_FLOOR)
        val prefix = prefix(context.level)
        val pool = if (searches && context.level <= 1) key.mode.degrees.filter { it.number in STEADY } else key.mode.degrees
        val degree = ItemWeights.pick(r, pool) { ItemWeights.weight(prefix + it, context.history) }
        val reference = arranger.line(listOf(DegreeNote(Degree.ONE)), key, Voice.TONE, beatsEach = 1.5, tempoBpm = TirDriver.TEMPO, tonicFloor = tonic)
        val scale = if (kept == null) {
            val up = key.mode.degrees.map { DegreeNote(it) } + DegreeNote(Degree.ONE, 1)
            arranger.line(up + DegreeNote(Degree.ONE), key, Voice.TONE, beatsEach = 0.4, tempoBpm = TirDriver.TEMPO, tonicFloor = tonic)
        } else {
            null
        }
        val audio = when {
            !withDrone -> scale?.then(reference, gapBeats = 0.5) ?: reference
            else -> scale
        }
        val subtitle = if (searches) "спой " + RuNames.interval(degree, accusative = true) else "спой ступень"
        return SingTask(
            itemKey = prefix + degree,
            prompt = Prompt(title = DegreeFormatter.degree(degree, notation), subtitle = subtitle, audio = audio, key = key),
            key = key, tonic = tonic, degree = degree,
            drone = if (withDrone) arranger.drone(key) else null,
            reference = reference,
        )
    }

    override fun evaluate(task: SingTask, answer: Answer): Evaluation {
        val expectedText = DegreeFormatter.degree(task.degree, notation)
        val (note, cents) = when (answer) {
            is Answer.Sung -> answer.note to answer.cents
            is Answer.NotePlayed -> answer.note to answer.cents
            else -> return Evaluation(
                correct = false, expectedText = expectedText,
                givenText = if (answer == Answer.Pass) "не знаю" else "—",
                correction = walkHome(task, task.tonic), taskHadSound = true,
                expectedCode = task.degree.toString(), givenCode = if (answer == Answer.Pass) "?" else "—",
            )
        }
        // Semitones from the nearest note of the asked degree to what was sung (−6..5).
        val off = Math.floorMod(note.value - task.tonic.value - task.degree.semitones + 6, 12) - 6
        val error = off * 100 + cents
        val correct = off == 0
        val sung = sungDegree(task, note)
        // Home in the octave that was sung: the answer is heard where the voice is.
        val home = MidiNote(note.value - off - task.degree.semitones)
        return Evaluation(
            correct = correct,
            expectedText = expectedText,
            givenText = DegreeFormatter.degree(sung, notation) + " · " + centsText(cents),
            correction = if (correct) null else walkHome(task, home),
            centsError = error,
            taskHadSound = true,
            confirmation = if (correct) walkHome(task, home) else null,
            expectedCode = task.degree.toString(),
            givenCode = sung.toString(),
        )
    }

    private fun prefix(level: Int) = if (searches) "$SEARCH_PREFIX$level:" else PREFIX

    /** The asked degree (in the octave of [home]) walking home to the tonic. */
    private fun walkHome(task: SingTask, home: MidiNote): Score {
        val start = DegreeNote(task.degree)
        return arranger.line(listOf(start) + Harmony.resolution(start, task.key.mode), task.key, Voice.TONE, beatsEach = 0.6, tempoBpm = TirDriver.TEMPO, tonicFloor = home)
    }

    /** What was sung, as a degree of this key (spelled as in the mode where it belongs to it). */
    private fun sungDegree(task: SingTask, note: MidiNote): Degree {
        val semis = Math.floorMod(note.value - task.tonic.value, 12)
        return task.key.mode.degrees.firstOrNull { it.semitones == semis } ?: Degree.canonical(semis)
    }

    companion object {
        const val PREFIX = "sing:L1:"

        /** «Пропевка» item keys: "pevka:L1:5". */
        const val SEARCH_PREFIX = "pevka:L"

        /** Level 1 of «Пропевка»: the steady degrees. */
        private val STEADY = setOf(1, 3, 5)

        /** Search mode: first-try median latency target, a little slower than «Спой ступень». */
        const val SEARCH_PROMOTION_TARGET_MS = 4_000L
        private const val MINOR_SHARE = 0.3

        /** C3: the reference tonic sits in C3–B3, comfortable for a low voice; a high one sings an octave up. */
        val VOICE_FLOOR = MidiNote(48)

        /** Median latency target for the level (from the start of listening to the first stable note). */
        const val PROMOTION_TARGET_MS = 3_000L

        /**
         * K-01: what was sung is the median of the steady part, not the first frames — a voice
         * scoops into a note. [hz] — clear frames from the onset on; the first quarter is dropped.
         */
        fun steadyPitch(hz: List<Double>, a4Hz: Double = DEFAULT_A4_HZ): PitchReading? {
            if (hz.isEmpty()) return null
            val steady = hz.drop(hz.size / 4).sorted()
            return MidiNote.nearest(steady[steady.size / 2], a4Hz)
        }

        fun centsText(cents: Double): String {
            val c = cents.roundToInt()
            return when {
                abs(c) < 5 -> "в точку"
                c > 0 -> "+$c ц"
                else -> "−${-c} ц"
            }
        }
    }
}
