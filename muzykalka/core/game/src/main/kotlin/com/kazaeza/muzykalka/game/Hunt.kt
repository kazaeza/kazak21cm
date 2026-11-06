package com.kazaeza.muzykalka.game

import com.kazaeza.muzykalka.music.Accidental
import com.kazaeza.muzykalka.music.Degree
import com.kazaeza.muzykalka.music.DegreeFormatter
import com.kazaeza.muzykalka.music.DegreeNotation
import com.kazaeza.muzykalka.music.FretPosition
import com.kazaeza.muzykalka.music.Fretboard
import com.kazaeza.muzykalka.music.Letter
import com.kazaeza.muzykalka.music.MidiNote
import com.kazaeza.muzykalka.music.NoteName
import com.kazaeza.muzykalka.music.NoteNaming
import com.kazaeza.muzykalka.music.PitchClass
import com.kazaeza.muzykalka.music.RuNames
import com.kazaeza.muzykalka.music.Score
import com.kazaeza.muzykalka.music.ScoreNote
import com.kazaeza.muzykalka.music.Voice
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

enum class HuntKind { FIND, ROOT, DEGREE, REVERSE, OCTAVES }

/**
 * One Hunt question.
 *  - FIND/ROOT/DEGREE: play a note on [string]; any of [acceptable] counts (pitch AND octave must
 *    exist on that string; the string itself is on the honour system).
 *  - REVERSE: a dot at [position]; name it (no guitar).
 *  - OCTAVES: play [pitchClass] in every octave of the neck; [found] so far of [total].
 */
data class HuntTask(
    override val itemKey: String,
    override val prompt: Prompt,
    val kind: HuntKind,
    val string: Int?,
    val pitchClass: PitchClass,
    val name: NoteName,
    val acceptable: Set<MidiNote>,
    val degree: Degree? = null,
    val rootName: NoteName? = null,
    val position: FretPosition? = null,
    val found: Set<MidiNote> = emptySet(),
    val total: Int = 0,
) : Task

/**
 * Hunt — name ↔ place (ticket G-01). Levels: 1 = strings 6–5, 2 = strings 4–3, 3 = whole neck,
 * 4 = degrees from the root you just played. The timer shrinks with every right answer.
 */
class HuntDriver(
    override val variant: Variant,
    private val board: Fretboard,
    private val naming: NoteNaming = NoteNaming.LATIN,
    private val notation: DegreeNotation = DegreeNotation.ARABIC,
    /** Exams use a fixed deadline instead of the shrinking timer. */
    private val adaptiveTimer: Boolean = true,
) : ModeDriver<HuntTask> {

    override val accepts: Set<Class<out Answer>> = setOf(Answer.NotePlayed::class.java, Answer.NoteNamed::class.java)

    private var limitMs = START_LIMIT_MS
    private var previousKey: String? = null
    private var lastRoot: MidiNote? = null
    private var lastRootName: NoteName? = null
    private var octavePc: PitchClass? = null
    private var octaveFound = mutableSetOf<MidiNote>()
    private var octaveTasks = 0

    override fun nextTask(context: DriverContext): HuntTask {
        limitLevel = context.level
        val task = when (variant) {
            Variant.HUNT_REVERSE -> reverseTask(context)
            Variant.HUNT_ALL_OCTAVES -> octavesTask(context)
            else -> if (context.level >= 4) degreeLevelTask(context) else findTask(context, HuntKind.FIND)
        }
        previousKey = task.itemKey
        return task
    }

    private fun stringsFor(level: Int): List<Int> = when (level) {
        1 -> listOf(6, 5).filter { it in board.strings }
        2 -> listOf(4, 3).filter { it in board.strings }
        else -> board.strings.toList()
    }

    private fun findTask(ctx: DriverContext, kind: HuntKind): HuntTask {
        val strings = if (kind == HuntKind.ROOT) listOf(6, 5) else stringsFor(ctx.level)
        val items = strings.flatMap { s -> (0..11).map { s to it } }
        val prefix = if (kind == HuntKind.ROOT) "hunt4:root" else "hunt"
        val (string, pc) = ItemWeights.pick(ctx.random, items, avoid = items.firstOrNull { "$prefix:s${it.first}:pc${it.second}" == previousKey }) {
            ItemWeights.weight("$prefix:s${it.first}:pc${it.second}", ctx.history)
        }
        val name = spell(PitchClass.of(pc), ctx.random)
        val acceptable = board.notesOf(PitchClass.of(pc), string).toSet()
        val title = RuNames.note(name, naming)
        val subtitle = if (kind == HuntKind.ROOT) "корень · ${RuNames.stringOrdinal(string)} струна" else "${RuNames.stringOrdinal(string)} струна"
        return HuntTask(
            itemKey = "$prefix:s$string:pc$pc", prompt = Prompt(title, subtitle), kind = kind, string = string,
            pitchClass = PitchClass.of(pc), name = name, acceptable = acceptable,
        )
    }

    private fun degreeLevelTask(ctx: DriverContext): HuntTask {
        val root = lastRoot
        val rootName = lastRootName
        if (root == null || rootName == null) return findTask(ctx, HuntKind.ROOT)
        val degrees = Degree.CHROMATIC.drop(1)
        val items = board.strings.flatMap { s -> degrees.map { s to it } }
        val (string, degree) = ItemWeights.pick(ctx.random, items) { ItemWeights.weight("hunt4:${it.second}:s${it.first}", ctx.history) }
        val pc = root.pitchClass + degree.semitones
        val name = spell(pc, ctx.random)
        val rootLabel = RuNames.note(rootName, NoteNaming.SOLFEGE_RU)
        return HuntTask(
            itemKey = "hunt4:$degree:s$string",
            prompt = Prompt(DegreeFormatter.degree(degree, notation), "от $rootLabel · ${RuNames.stringOrdinal(string)} струна"),
            kind = HuntKind.DEGREE, string = string, pitchClass = pc, name = name,
            acceptable = board.notesOf(pc, string).toSet(), degree = degree, rootName = rootName,
        )
    }

    private fun reverseTask(ctx: DriverContext): HuntTask {
        val strings = stringsFor(ctx.level)
        val maxFret = if (ctx.level >= 3) min(board.fretCount, 15) else 12
        val items = strings.flatMap { s -> (0..maxFret).map { FretPosition(s, it) } }
        val pos = ItemWeights.pick(ctx.random, items, avoid = items.firstOrNull { "rhunt:s${it.string}:f${it.fret}" == previousKey }) {
            ItemWeights.weight("rhunt:s${it.string}:f${it.fret}", ctx.history)
        }
        val note = board.noteAt(pos)
        val name = spell(note.pitchClass, ctx.random)
        return HuntTask(
            itemKey = "rhunt:s${pos.string}:f${pos.fret}", prompt = Prompt("?", "${RuNames.stringOrdinal(pos.string)} струна · ${pos.fret} лад"),
            kind = HuntKind.REVERSE, string = pos.string, pitchClass = note.pitchClass, name = name,
            acceptable = setOf(note), position = pos,
        )
    }

    private fun octavesTask(ctx: DriverContext): HuntTask {
        var pc = octavePc
        val allNotes = { p: PitchClass -> board.strings.flatMap { board.notesOf(p, it) }.toSortedSet() }
        if (pc == null || octaveFound.containsAll(allNotes(pc)) || octaveTasks >= OCTAVE_TASKS_MAX) {
            val pcs = PitchClass.ALL
            pc = ItemWeights.pick(ctx.random, pcs, avoid = octavePc) { ItemWeights.weight("oct:pc${it.value}", ctx.history) }
            octavePc = pc
            octaveFound = mutableSetOf()
            octaveTasks = 0
        }
        octaveTasks++
        val notes = allNotes(pc)
        val name = spell(pc, ctx.random)
        return HuntTask(
            itemKey = "oct:pc${pc.value}",
            prompt = Prompt(RuNames.note(name, naming), "все октавы · найдено ${octaveFound.size} из ${notes.size}"),
            kind = HuntKind.OCTAVES, string = null, pitchClass = pc, name = name,
            acceptable = notes - octaveFound, found = octaveFound.toSet(), total = notes.size,
        )
    }

    override fun timeLimitMs(task: HuntTask, context: DriverContext): Long? = when {
        task.kind == HuntKind.OCTAVES -> OCTAVE_WINDOW_MS / max(1, task.total)
        !adaptiveTimer -> EXAM_LIMIT_MS
        task.kind == HuntKind.REVERSE -> limitMs + 1_500
        else -> limitMs
    }

    override fun evaluate(task: HuntTask, answer: Answer): Evaluation {
        val expectedNote = task.acceptable.minOrNull()
        val correctionScore = expectedNote?.let { Score(90.0, listOf(ScoreNote(0.0, 2.0, Voice.GUITAR, it, 0.9f))) }
        val expectedText = buildString {
            append(RuNames.note(task.name, naming))
            if (task.kind == HuntKind.REVERSE) {
                append(" · ").append(RuNames.note(task.name, NoteNaming.SOLFEGE_RU))
            } else if (task.string != null && expectedNote != null) {
                val fret = expectedNote.value - board.tuning.open(task.string).value
                append(" · ").append(task.string).append("-я, ").append(fret).append(" лад")
            }
        }
        val expectedCode = when (task.kind) {
            HuntKind.REVERSE -> task.name.toString()
            HuntKind.DEGREE -> task.degree.toString()
            else -> task.name.toString()
        }
        val result = when (answer) {
            is Answer.NotePlayed -> {
                val ok = answer.note in task.acceptable
                val given = RuNames.note(spell(answer.note.pitchClass, null, prefer = task.name), naming) + answer.note.octave
                Triple(ok, given, given) to answer.cents
            }
            is Answer.NoteNamed -> {
                val ok = answer.name.pitchClass == task.pitchClass
                Triple(ok, RuNames.note(answer.name, naming), answer.name.toString()) to null
            }
            else -> Triple(false, "время", "timeout") to null
        }
        val (triple, cents) = result
        val (correct, givenText, givenCode) = triple
        if (adaptiveTimer) {
            limitMs = if (correct) max(minLimit(), (limitMs * 0.93).toLong()) else min(START_LIMIT_MS, (limitMs * 1.2).toLong())
        }
        when (task.kind) {
            HuntKind.ROOT -> if (correct && answer is Answer.NotePlayed) {
                lastRoot = answer.note
                lastRootName = task.name
            } else {
                lastRoot = null
            }
            HuntKind.DEGREE -> if (!correct) lastRoot = null
            HuntKind.OCTAVES -> if (correct && answer is Answer.NotePlayed) octaveFound += answer.note
            else -> Unit
        }
        return Evaluation(
            correct = correct,
            expectedText = expectedText,
            givenText = givenText,
            correction = if (correct) null else correctionScore,
            centsError = cents,
            taskHadSound = false,
            expectedCode = expectedCode,
            givenCode = givenCode,
        )
    }

    /** Timer floor: 1.2 s on two strings, 2 s on the whole neck and for degrees (ROADMAP G-01). */
    private fun minLimit() = if (limitLevel >= 3) 2_000L else 1_200L

    private var limitLevel = 1

    /** Spelling for a pitch class: naturals as is, black keys as ♯ or ♭ at random (both must be learnt). */
    private fun spell(pc: PitchClass, random: Random?, prefer: NoteName? = null): NoteName {
        val natural = Letter.entries.firstOrNull { it.naturalPitchClass == pc.value }
        if (natural != null) return NoteName(natural)
        if (prefer != null && prefer.pitchClass == pc) return prefer
        val sharp = NoteName(Letter.entries.first { it.naturalPitchClass == pc.value - 1 }, Accidental.SHARP)
        val flat = NoteName(Letter.entries.first { it.naturalPitchClass == pc.value + 1 }, Accidental.FLAT)
        return if (random == null || random.nextBoolean()) sharp else flat
    }

    companion object {
        const val START_LIMIT_MS = 5_000L
        const val EXAM_LIMIT_MS = 6_000L
        const val OCTAVE_WINDOW_MS = 15_000L
        private const val OCTAVE_TASKS_MAX = 12

        fun promotionTargetMs(level: Int): Long = when (level) {
            1 -> 1_000
            2 -> 1_200
            else -> 2_500
        }

        /** All 12 note names for the reverse-hunt pad: naturals and "C♯/D♭" pairs as sharps. */
        val PAD: List<NoteName> = (0..11).map { pc ->
            Letter.entries.firstOrNull { it.naturalPitchClass == pc }?.let { NoteName(it) }
                ?: NoteName(Letter.entries.first { it.naturalPitchClass == pc - 1 }, Accidental.SHARP)
        }
    }
}
