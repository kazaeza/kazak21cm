package com.kazaeza.muzykalka.game

import com.kazaeza.muzykalka.music.Arranger
import com.kazaeza.muzykalka.music.ChordDegree
import com.kazaeza.muzykalka.music.ChordParser
import com.kazaeza.muzykalka.music.Degree
import com.kazaeza.muzykalka.music.DegreeFormatter
import com.kazaeza.muzykalka.music.DegreeNotation
import com.kazaeza.muzykalka.music.DegreeNote
import com.kazaeza.muzykalka.music.Harmony
import com.kazaeza.muzykalka.music.Key
import com.kazaeza.muzykalka.music.KeyPicker
import com.kazaeza.muzykalka.music.MidiNote
import com.kazaeza.muzykalka.music.Mode
import com.kazaeza.muzykalka.music.Score
import kotlin.random.Random

/** What a Tir question asks for. */
enum class TirKind { NOTE, MELODY, CHORD, PROGRESSION }

/**
 * One Tir question. The prompt audio is the cadence followed by [question].
 * For PROGRESSION the first chord (always the tonic) is given; the user names chords 2..4.
 */
data class TirTask(
    override val itemKey: String,
    override val prompt: Prompt,
    val kind: TirKind,
    val level: Int,
    val key: Key,
    val tonic: MidiNote,
    val notes: List<DegreeNote>,
    val chords: List<ChordDegree>,
    val noteChoices: List<Degree>,
    val chordChoices: List<ChordDegree>,
    val question: Score,
) : Task {
    /** Taps the answer pad collects before the answer is submitted. */
    val answerLength: Int
        get() = when (kind) {
            TirKind.NOTE, TirKind.CHORD -> 1
            TirKind.MELODY -> notes.size
            TirKind.PROGRESSION -> chords.size - 1
        }
}

/**
 * Tir — sound → name (ticket G-02). Every question: a fresh random key (rule 2), the cadence of its
 * mode, then the question. A right answer is rewarded with the resolution to the tonic; a wrong one
 * replays the question and resolves it (rule 5).
 *
 * Anti-guessing (ADR-015): questions are independent — the same degree may come twice in a row, so
 * «не похоже на прошлый — значит iii» stops working; level 1 shows all seven keys and slips in
 * «guests» from the rest of the scale, so «не тоника — значит 3 или 5» stops working too; the
 * register jumps by octaves from level 1; «Не знаю» ([Answer.Pass]) is an honest way out.
 */
class TirDriver(
    private val keyPicker: KeyPicker,
    private val notation: DegreeNotation = DegreeNotation.ARABIC,
    override val variant: Variant = Variant.TIR_CLASSIC,
    private val arranger: Arranger = Arranger(),
) : ModeDriver<TirTask> {

    override val accepts: Set<Class<out Answer>> = setOf(
        Answer.DegreeChoice::class.java,
        Answer.Formula::class.java,
        Answer.ChordChoice::class.java,
        Answer.ChordFormula::class.java,
        Answer.Pass::class.java,
    )

    override fun nextTask(context: DriverContext): TirTask {
        val level = context.level.coerceIn(1, Levels.TIR.size)
        val r = context.random
        val mode = when (level) {
            1 -> Mode.MAJOR
            7 -> MODAL_LEVEL_MODES[r.nextInt(MODAL_LEVEL_MODES.size)]
            else -> if (r.nextDouble() < 0.3) Mode.MINOR else Mode.MAJOR
        }
        val key = keyPicker.next(mode)
        val tonic = key.tonicAtOrAbove(MidiNote(55))
        val cadence = arranger.chords(Harmony.cadence(mode), key, beatsEach = 1.0, tempoBpm = TEMPO)
        return when (level) {
            1 -> noteTask(level, key, tonic, cadence, CORE, mode.degrees, context)
            2 -> noteTask(level, key, tonic, cadence, mode.degrees, mode.degrees, context)
            3 -> noteTask(level, key, tonic, cadence, Degree.CHROMATIC, Degree.CHROMATIC, context)
            4 -> melodyTask(key, tonic, cadence, r)
            5 -> chordTask(key, cadence, context)
            6 -> progressionTask(key, cadence, r)
            else -> noteTask(level, key, tonic, cadence, mode.degrees, mode.degrees, context)
        }
    }

    /**
     * [pool] is what is mostly asked, [pad] what can be answered. When the pad is wider than the
     * pool, [GUEST_SHARE] of the questions come from the rest of the pad.
     */
    private fun noteTask(level: Int, key: Key, tonic: MidiNote, cadence: Score, pool: List<Degree>, pad: List<Degree>, ctx: DriverContext): TirTask {
        val prefix = if (level == 7) "tir:L7:${key.mode.name}:" else "tir:L$level:"
        val guests = pad.filter { d -> pool.none { it sameSoundAs d } }
        val from = if (guests.isNotEmpty() && ctx.random.nextDouble() < GUEST_SHARE) guests else pool
        val degree = ItemWeights.pick(ctx.random, from) { ItemWeights.weight(prefix + it, ctx.history) }
        val octaves = listOf(-1, 0, 1).filter { (tonic.value + degree.semitones + 12 * it) in 50..79 }
        val note = DegreeNote(degree, octaves[ctx.random.nextInt(octaves.size)])
        val question = arranger.line(listOf(note), key, beatsEach = 1.5, tempoBpm = TEMPO, tonicFloor = tonic)
        return TirTask(
            itemKey = prefix + degree, prompt = prompt(key, cadence, question), kind = TirKind.NOTE, level = level,
            key = key, tonic = tonic, notes = listOf(note), chords = emptyList(), noteChoices = pad,
            chordChoices = emptyList(), question = question,
        )
    }

    private fun melodyTask(key: Key, tonic: MidiNote, cadence: Score, r: Random): TirTask {
        val length = 2 + r.nextInt(4)
        var idx = r.nextInt(7)
        val indices = mutableListOf(idx)
        while (indices.size < length) {
            val step = when (r.nextInt(10)) {
                in 0..2 -> 1
                in 3..5 -> -1
                6 -> 2
                7 -> -2
                8 -> 3
                else -> -3
            }
            idx = (idx + step).coerceIn(-3, 10)
            if (idx != indices.last()) indices += idx
        }
        val notes = indices.map { i -> DegreeNote(key.mode.degrees[Math.floorMod(i, 7)], Math.floorDiv(i, 7)) }
        val question = arranger.line(notes, key, beatsEach = 0.8, tempoBpm = TEMPO, tonicFloor = tonic)
        return TirTask(
            itemKey = "tir:L4:len$length", prompt = prompt(key, cadence, question), kind = TirKind.MELODY, level = 4,
            key = key, tonic = tonic, notes = notes, chords = emptyList(), noteChoices = key.mode.degrees,
            chordChoices = emptyList(), question = question,
        )
    }

    private fun chordTask(key: Key, cadence: Score, ctx: DriverContext): TirTask {
        val choices = (0..6).map { Harmony.diatonicChord(key.mode, it) }
        val prefix = "tir:L5:"
        val chord = ItemWeights.pick(ctx.random, choices) { ItemWeights.weight(prefix + ChordParser.formatRoman(it), ctx.history) }
        val question = arranger.chords(listOf(chord), key, beatsEach = 2.0, tempoBpm = TEMPO)
        return TirTask(
            itemKey = prefix + ChordParser.formatRoman(chord), prompt = prompt(key, cadence, question), kind = TirKind.CHORD,
            level = 5, key = key, tonic = key.tonicAtOrAbove(MidiNote(55)), notes = emptyList(), chords = listOf(chord),
            noteChoices = emptyList(), chordChoices = choices, question = question,
        )
    }

    private fun progressionTask(key: Key, cadence: Score, r: Random): TirTask {
        val moves = if (key.mode.hasMinorThird) MINOR_MOVES else MAJOR_MOVES
        val steps = mutableListOf(0)
        while (steps.size < 4) {
            val options = moves.getValue(steps.last()).filter { it != steps.last() }
            steps += options[r.nextInt(options.size)]
        }
        val chords = steps.map { Harmony.diatonicChord(key.mode, it) }
        val choices = (0..6).map { Harmony.diatonicChord(key.mode, it) }
        val question = arranger.chords(chords, key, beatsEach = 1.5, tempoBpm = TEMPO)
        return TirTask(
            itemKey = "tir:L6:" + chords.joinToString("-") { ChordParser.formatRoman(it) }, prompt = prompt(key, cadence, question),
            kind = TirKind.PROGRESSION, level = 6, key = key, tonic = key.tonicAtOrAbove(MidiNote(55)), notes = emptyList(),
            chords = chords, noteChoices = emptyList(), chordChoices = choices, question = question,
        )
    }

    private fun prompt(key: Key, cadence: Score, question: Score) =
        Prompt(title = "?", subtitle = null, audio = cadence.then(question, gapBeats = 0.5), key = key)

    override fun evaluate(task: TirTask, answer: Answer): Evaluation {
        val expectedText: String
        val expectedCode: String
        when (task.kind) {
            TirKind.NOTE, TirKind.MELODY -> {
                expectedText = task.notes.joinToString(" ") { DegreeFormatter.degree(it.degree, notation) }
                expectedCode = task.notes.joinToString(" ") { it.degree.toString() }
            }
            TirKind.CHORD -> {
                expectedText = DegreeFormatter.chord(task.chords[0], notation)
                expectedCode = ChordParser.formatRoman(task.chords[0])
            }
            TirKind.PROGRESSION -> {
                expectedText = task.chords.drop(1).joinToString(" ") { DegreeFormatter.chord(it, notation) }
                expectedCode = task.chords.drop(1).joinToString(" ") { ChordParser.formatRoman(it) }
            }
        }
        val (correct, givenText, givenCode) = when (answer) {
            is Answer.DegreeChoice -> Triple(
                task.kind == TirKind.NOTE && answer.degree sameSoundAs task.notes[0].degree,
                DegreeFormatter.degree(answer.degree, notation),
                answer.degree.toString(),
            )
            is Answer.Formula -> Triple(
                task.kind == TirKind.MELODY && answer.notes.size == task.notes.size &&
                    answer.notes.zip(task.notes).all { (a, e) -> a.degree sameSoundAs e.degree },
                answer.notes.joinToString(" ") { DegreeFormatter.degree(it.degree, notation) },
                answer.notes.joinToString(" ") { it.degree.toString() },
            )
            is Answer.ChordChoice -> Triple(
                task.kind == TirKind.CHORD && sameChord(answer.chord, task.chords[0]),
                DegreeFormatter.chord(answer.chord, notation),
                ChordParser.formatRoman(answer.chord),
            )
            is Answer.ChordFormula -> Triple(
                task.kind == TirKind.PROGRESSION && answer.chords.size == task.chords.size - 1 &&
                    answer.chords.zip(task.chords.drop(1)).all { (a, e) -> sameChord(a, e) },
                answer.chords.joinToString(" ") { DegreeFormatter.chord(it, notation) },
                answer.chords.joinToString(" ") { ChordParser.formatRoman(it) },
            )
            Answer.Pass -> Triple(false, "не знаю", "?")
            else -> Triple(false, "—", "—")
        }
        return Evaluation(
            correct = correct,
            expectedText = expectedText,
            givenText = givenText,
            correction = if (correct) null else correction(task),
            taskHadSound = true,
            confirmation = if (correct) confirmation(task) else null,
            expectedCode = expectedCode,
            givenCode = givenCode,
        )
    }

    private fun sameChord(a: ChordDegree, b: ChordDegree) = a.root sameSoundAs b.root && a.quality == b.quality

    /** Correct answer: the musical reward — the last note walks home to the tonic. */
    private fun confirmation(task: TirTask): Score = when (task.kind) {
        TirKind.NOTE, TirKind.MELODY -> {
            val last = task.notes.last()
            arranger.line(listOf(last) + Harmony.resolution(last, task.key.mode), task.key, beatsEach = 0.5, tempoBpm = TEMPO, tonicFloor = task.tonic)
        }
        TirKind.CHORD -> arranger.chords(listOf(task.chords[0], Harmony.diatonicChord(task.key.mode, 0)), task.key, beatsEach = 0.75, tempoBpm = TEMPO)
        TirKind.PROGRESSION -> arranger.chords(listOf(Harmony.diatonicChord(task.key.mode, 0)), task.key, beatsEach = 1.0, tempoBpm = TEMPO)
    }

    /** Wrong answer (rule 5): hear the question again, then where it resolves. */
    private fun correction(task: TirTask): Score = when (task.kind) {
        TirKind.NOTE, TirKind.MELODY -> {
            val last = task.notes.last()
            val resolution = arranger.line(listOf(last) + Harmony.resolution(last, task.key.mode), task.key, beatsEach = 0.75, tempoBpm = TEMPO, tonicFloor = task.tonic)
            task.question.then(resolution, gapBeats = 0.5)
        }
        TirKind.CHORD -> task.question.then(
            arranger.chords(listOf(Harmony.diatonicChord(task.key.mode, 0)), task.key, beatsEach = 1.0, tempoBpm = TEMPO),
            gapBeats = 0.25,
        )
        TirKind.PROGRESSION -> task.question
    }

    companion object {
        const val TEMPO = 120.0

        /** Level 1 asks mostly these; the other scale degrees come as guests. */
        val CORE = listOf(Degree.ONE, Degree.THREE, Degree.FIVE)
        const val GUEST_SHARE = 0.25
        val MODAL_LEVEL_MODES = listOf(Mode.DORIAN, Mode.PHRYGIAN, Mode.HARMONIC_MINOR)

        /** Functional moves between diatonic steps (0 = I … 6 = vii°). */
        private val MAJOR_MOVES = mapOf(
            0 to listOf(3, 4, 5, 1, 2), 1 to listOf(4, 6, 3), 2 to listOf(5, 3), 3 to listOf(4, 0, 1, 5),
            4 to listOf(0, 5, 3), 5 to listOf(1, 3, 4), 6 to listOf(0, 2),
        )
        private val MINOR_MOVES = mapOf(
            0 to listOf(3, 5, 6, 2, 4), 1 to listOf(4, 0), 2 to listOf(5, 3, 6), 3 to listOf(0, 6, 4, 5),
            4 to listOf(0, 5), 5 to listOf(6, 2, 3), 6 to listOf(2, 0, 5),
        )

        /** Median-latency targets for promotion, per level (ROADMAP G-02). */
        fun promotionTargetMs(level: Int): Long = when (level) {
            4 -> 5_000
            6 -> 8_000
            else -> 2_500
        }
    }
}
