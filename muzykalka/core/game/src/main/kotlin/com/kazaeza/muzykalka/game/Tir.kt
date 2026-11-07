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
import com.kazaeza.muzykalka.music.ScoreNote
import com.kazaeza.muzykalka.music.Voice
import kotlin.random.Random

/** What a Tir question asks for. */
enum class TirKind { NOTE, MELODY, CHORD, PROGRESSION }

/**
 * One Tir question. With a [drone] (single notes and melodies, ADR-017) the tonic sounds all
 * the time and the prompt audio is just the [question] (after a quick scale when the key has
 * just changed). Without one (chords) the prompt is the cadence followed by [question].
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
    /** Notes to hold under this question; null = no drone, the cadence sets the key. */
    override val drone: List<MidiNote>? = null,
    /** O-06: brought back from an earlier day's miss. Shown only AFTER the answer — it would be a hint. */
    val review: TirReview? = null,
    /** O-08: the chord held under the note instead of the tonic; the answer is still the degree in the key. */
    val over: ChordDegree? = null,
    /**
     * O-15 «Под нотой» (ADR-029): the reverse — [notes] holds the note on top, which is SHOWN, and
     * [chords] the chord droning under it, which is asked ([kind] = CHORD).
     */
    val underNote: Boolean = false,
    override val intro: Score? = null,
    override val introDrone: List<MidiNote>? = null,
) : DroneTask {
    override val replay: Score get() = question

    /** Taps the answer pad collects before the answer is submitted. */
    val answerLength: Int
        get() = when (kind) {
            TirKind.NOTE, TirKind.CHORD -> 1
            TirKind.MELODY -> notes.size
            TirKind.PROGRESSION -> chords.size - 1
        }
}

/** Why a question came back (O-06): missed [daysAgo] days ago, answered then as [confusedWith]. */
data class TirReview(val daysAgo: Int, val confusedWith: List<String>)

/**
 * Tir — sound → name (ticket G-02). Single notes and melodies are heard over a tonic drone in one
 * soft timbre (ADR-017): the key (random, rule 2) holds for [KEY_BLOCK] questions so the ear can
 * settle into «home», and every new key opens with a quick scale that sets major or minor.
 * Chords keep the old context: a fresh key and the cadence of its mode before every question.
 * A right answer is rewarded with the resolution to the tonic; a wrong one replays the question
 * and resolves it (rule 5).
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
    review: List<SpacedReview.Item> = emptyList(),
) : ModeDriver<TirTask> {

    /** O-06: earlier days' misses (keys from [reviewKey]) still waiting for a right answer today. */
    private val pendingReview = review.filter { it.key.startsWith(NOTE_GROUP) || it.key.startsWith(CHORD_GROUP) }.toMutableList()
    private val reviewAsks = HashMap<String, Int>()
    private var lastReview: Pair<String, String>? = null // review key → item key of the task that asked it

    override val accepts: Set<Class<out Answer>> = setOf(
        Answer.DegreeChoice::class.java,
        Answer.Formula::class.java,
        Answer.ChordChoice::class.java,
        Answer.ChordFormula::class.java,
        Answer.Pass::class.java,
    )

    /** The drone's key and how many questions it still has (state of the current round). */
    private var blockKey: Key? = null
    private var blockLevel = 0
    private var blockLeft = 0

    private fun pickMode(level: Int, r: Random): Mode = when (level) {
        1 -> Mode.MAJOR
        7 -> MODAL_LEVEL_MODES[r.nextInt(MODAL_LEVEL_MODES.size)]
        else -> if (r.nextDouble() < 0.3) Mode.MINOR else Mode.MAJOR
    }

    override fun nextTask(context: DriverContext): TirTask {
        settleReview(context.history)
        if (variant == Variant.TIR_OVER_CHORDS) return overChordTask(context)
        if (variant == Variant.TIR_UNDER_NOTE) return underNoteTask(context)
        val level = context.level.coerceIn(1, Levels.TIR.size)
        val r = context.random
        if (!usesDrone(level)) {
            val key = keyPicker.next(pickMode(level, r))
            val cadence = arranger.chords(Harmony.cadence(key.mode), key, beatsEach = 1.0, tempoBpm = TEMPO)
            return if (level == 5) chordTask(key, cadence, context) else progressionTask(key, cadence, r)
        }
        val kept = blockKey?.takeIf { blockLeft > 0 && blockLevel == level }
        val key = kept ?: keyPicker.next(pickMode(level, r))
        val newKey = kept == null
        if (newKey) {
            blockKey = key
            blockLevel = level
            blockLeft = KEY_BLOCK
        }
        blockLeft--
        val ctx = Context(key, key.tonicAtOrAbove(MidiNote(55)), arranger.drone(key), if (newKey) orientation(key) else null)
        return when (level) {
            1 -> noteTask(level, ctx, CORE, key.mode.degrees, context)
            2 -> noteTask(level, ctx, key.mode.degrees, key.mode.degrees, context)
            3 -> noteTask(level, ctx, Degree.CHROMATIC, Degree.CHROMATIC, context)
            4 -> melodyTask(ctx, r)
            else -> noteTask(level, ctx, key.mode.degrees, key.mode.degrees, context)
        }
    }

    /** The review task just answered: a right answer takes it off today's list. */
    private fun settleReview(history: List<AttemptRecord>) {
        val (key, itemKey) = lastReview ?: return
        lastReview = null
        val answer = history.lastOrNull() ?: return
        if (answer.itemKey == itemKey && answer.correct) pendingReview.removeAll { it.key == key }
    }

    /**
     * With [REVIEW_SHARE] chance, a pending review item of [group] that this question can ask
     * ([resolve] maps its code to a choice, null = not on this pad). Each item at most
     * [MAX_REVIEW_ASKS] times a round, so a stubborn one does not take the round over.
     */
    private fun <T> reviewPick(group: String, r: Random, resolve: (String) -> T?): Pair<T, SpacedReview.Item>? {
        if (pendingReview.isEmpty() || r.nextDouble() >= REVIEW_SHARE) return null
        val candidates = pendingReview.mapNotNull { item ->
            if (!item.key.startsWith(group) || (reviewAsks[item.key] ?: 0) >= MAX_REVIEW_ASKS) return@mapNotNull null
            resolve(item.key.removePrefix(group))?.let { it to item }
        }
        if (candidates.isEmpty()) return null
        // The front of the list (the freshest misses) first, but not always the very first one.
        val pick = candidates[r.nextInt(minOf(3, candidates.size))]
        reviewAsks[pick.second.key] = (reviewAsks[pick.second.key] ?: 0) + 1
        return pick
    }

    private fun reviewOf(item: SpacedReview.Item?, itemKey: String, show: (String) -> String?): TirReview? {
        item ?: return null
        lastReview = item.key to itemKey
        return TirReview(item.daysAgo, item.confusedWith.mapNotNull(show))
    }

    /** Where a drone question lives: its key, the tonic of its register, the drone, the intro. */
    private class Context(val key: Key, val tonic: MidiNote, val drone: List<MidiNote>, val intro: Score?)

    /** New key over the drone: the scale up to the octave and back to 1 — major or minor is set. */
    private fun orientation(key: Key): Score {
        val up = key.mode.degrees.map { DegreeNote(it) } + DegreeNote(Degree.ONE, 1)
        return arranger.line(up + DegreeNote(Degree.ONE), key, Voice.TONE, beatsEach = 0.4, tempoBpm = TEMPO, tonicFloor = key.tonicAtOrAbove(MidiNote(55)))
    }

    /**
     * [pool] is what is mostly asked, [pad] what can be answered. When the pad is wider than the
     * pool, [GUEST_SHARE] of the questions come from the rest of the pad.
     */
    private fun noteTask(level: Int, c: Context, pool: List<Degree>, pad: List<Degree>, ctx: DriverContext): TirTask {
        val key = c.key
        val tonic = c.tonic
        val prefix = if (level == 7) "tir:L7:${key.mode.name}:" else "tir:L$level:"
        val guests = pad.filter { d -> pool.none { it sameSoundAs d } }
        val from = if (guests.isNotEmpty() && ctx.random.nextDouble() < GUEST_SHARE) guests else pool
        val review = reviewPick(NOTE_GROUP, ctx.random) { code -> pad.firstOrNull { it.toString() == code } }
        val degree = review?.first ?: ItemWeights.pick(ctx.random, from) { ItemWeights.weight(prefix + it, ctx.history) }
        val octaves = listOf(-1, 0, 1).filter { (tonic.value + degree.semitones + 12 * it) in 50..79 }
        val note = DegreeNote(degree, octaves[ctx.random.nextInt(octaves.size)])
        val question = arranger.line(listOf(note), key, Voice.TONE, beatsEach = 2.0, tempoBpm = TEMPO, tonicFloor = tonic)
        return TirTask(
            itemKey = prefix + degree, prompt = prompt(key, c.intro, question), kind = TirKind.NOTE, level = level,
            key = key, tonic = tonic, notes = listOf(note), chords = emptyList(), noteChoices = pad,
            chordChoices = emptyList(), question = question, drone = c.drone,
            review = reviewOf(review?.second, prefix + degree) { code -> pad.firstOrNull { it.toString() == code }?.let { DegreeFormatter.degree(it, notation) } },
        )
    }

    /**
     * O-08 (RESEARCH §4 D): a degree's feeling depends on the chord under it (Arthur 2018), so a
     * «vibe» learnt over the drone alone may not carry into songs. The key is set as in Tir — a
     * block of [KEY_BLOCK] questions opening with the scale over I — then every question holds
     * another chord of the key under the note. Level 1: I, IV, V, vi (i, iv, v, ♭VI in minor);
     * level 2: every chord of the scale but vii°.
     */
    private fun overChordTask(context: DriverContext): TirTask {
        val level = context.level.coerceIn(1, Levels.TIR_CHORDS.size)
        val r = context.random
        val blockId = OVER_CHORDS_BLOCK + level
        val kept = blockKey?.takeIf { blockLeft > 0 && blockLevel == blockId }
        val key = kept ?: keyPicker.next(pickMode(2, r))
        val newKey = kept == null
        if (newKey) {
            blockKey = key
            blockLevel = blockId
            blockLeft = KEY_BLOCK
        }
        blockLeft--
        val steps = if (level == 1) CHORD_STEPS_L1 else CHORD_STEPS_L2
        val chord = Harmony.diatonicChord(key.mode, if (newKey) 0 else steps[r.nextInt(steps.size)])
        val tonic = key.tonicAtOrAbove(MidiNote(55))
        val pad = key.mode.degrees
        val prefix = "tir:chords:L$level:${ChordParser.formatRoman(chord)}:"
        val degree = ItemWeights.pick(r, pad) { ItemWeights.weight(prefix + it, context.history) }
        val octaves = listOf(-1, 0, 1).filter { (tonic.value + degree.semitones + 12 * it) in 55..79 }
        val note = DegreeNote(degree, octaves[r.nextInt(octaves.size)])
        val question = arranger.line(listOf(note), key, Voice.TONE, beatsEach = 2.0, tempoBpm = TEMPO, tonicFloor = tonic)
        return TirTask(
            itemKey = prefix + degree, prompt = prompt(key, if (newKey) orientation(key) else null, question), kind = TirKind.NOTE,
            level = level, key = key, tonic = tonic, notes = listOf(note), chords = emptyList(), noteChoices = pad,
            chordChoices = emptyList(), question = question, drone = arranger.chordDrone(chord, key), over = chord,
        )
    }

    /**
     * O-15 (ADR-029), the reverse of [overChordTask]: the same chord drone and note on top, but the
     * note's degree is on the screen and the chord is asked — «мелодия на 1: под ней I, IV или vi?».
     * The note is always a tone of the chord that at least one other chord of the pad shares, so
     * the pad cannot be narrowed to one answer by theory alone — only by ear. A new key is set by
     * its scale over the tonic drone ([TirTask.intro]); then the drone moves to the asked chord, so
     * the first chord of a block is not always I.
     */
    private fun underNoteTask(context: DriverContext): TirTask {
        val level = context.level.coerceIn(1, Levels.TIR_UNDER.size)
        val r = context.random
        val blockId = UNDER_NOTE_BLOCK + level
        val kept = blockKey?.takeIf { blockLeft > 0 && blockLevel == blockId }
        val key = kept ?: keyPicker.next(pickMode(2, r))
        val newKey = kept == null
        if (newKey) {
            blockKey = key
            blockLevel = blockId
            blockLeft = KEY_BLOCK
        }
        blockLeft--
        val pad = underNotePad(key.mode, level)
        val prefix = "tir:under:L$level:"
        val chord = ItemWeights.pick(r, pad) { ItemWeights.weight(prefix + ChordParser.formatRoman(it), context.history) }
        val shared = chordTones(chord, key.mode).filter { d -> pad.count { holds(it, d) } >= 2 }
        val degree = shared[r.nextInt(shared.size)]
        val tonic = key.tonicAtOrAbove(MidiNote(55))
        val octaves = listOf(-1, 0, 1).filter { (tonic.value + degree.semitones + 12 * it) in 55..79 }
        val note = DegreeNote(degree, octaves[r.nextInt(octaves.size)])
        val question = arranger.line(listOf(note), key, Voice.TONE, beatsEach = 2.0, tempoBpm = TEMPO, tonicFloor = tonic)
        return TirTask(
            itemKey = prefix + ChordParser.formatRoman(chord), prompt = Prompt(title = "?", audio = question, key = key),
            kind = TirKind.CHORD, level = level, key = key, tonic = tonic, notes = listOf(note), chords = listOf(chord),
            noteChoices = emptyList(), chordChoices = pad, question = question, drone = arranger.chordDrone(chord, key),
            underNote = true,
            intro = if (newKey) orientation(key) else null,
            introDrone = if (newKey) arranger.drone(key) else null,
        )
    }

    /** Chords in the soft drone timbre, low root + triad, [beatsEach] each (rewards over a drone). */
    private fun toneChords(chords: List<ChordDegree>, key: Key, beatsEach: Double): Score = Score(
        TEMPO,
        chords.flatMapIndexed { i, c ->
            arranger.chordDrone(c, key).map { ScoreNote(i * beatsEach, beatsEach * 0.95, Voice.TONE, it, 0.6f) }
        },
    )

    private fun melodyTask(c: Context, r: Random): TirTask {
        val key = c.key
        val tonic = c.tonic
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
        val question = arranger.line(notes, key, Voice.TONE, beatsEach = 0.9, tempoBpm = TEMPO, tonicFloor = tonic)
        return TirTask(
            itemKey = "tir:L4:len$length", prompt = prompt(key, c.intro, question), kind = TirKind.MELODY, level = 4,
            key = key, tonic = tonic, notes = notes, chords = emptyList(), noteChoices = key.mode.degrees,
            chordChoices = emptyList(), question = question, drone = c.drone,
        )
    }

    private fun chordTask(key: Key, cadence: Score, ctx: DriverContext): TirTask {
        val choices = (0..6).map { Harmony.diatonicChord(key.mode, it) }
        val prefix = "tir:L5:"
        val byCode = { code: String -> choices.firstOrNull { ChordParser.formatRoman(it) == code } }
        val review = reviewPick(CHORD_GROUP, ctx.random, byCode)
        val chord = review?.first ?: ItemWeights.pick(ctx.random, choices) { ItemWeights.weight(prefix + ChordParser.formatRoman(it), ctx.history) }
        val question = arranger.chords(listOf(chord), key, beatsEach = 2.0, tempoBpm = TEMPO)
        return TirTask(
            itemKey = prefix + ChordParser.formatRoman(chord), prompt = prompt(key, cadence, question), kind = TirKind.CHORD,
            level = 5, key = key, tonic = key.tonicAtOrAbove(MidiNote(55)), notes = emptyList(), chords = listOf(chord),
            noteChoices = emptyList(), chordChoices = choices, question = question,
            review = reviewOf(review?.second, prefix + ChordParser.formatRoman(chord)) { code -> byCode(code)?.let { DegreeFormatter.chord(it, notation) } },
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

    private fun prompt(key: Key, intro: Score?, question: Score) =
        Prompt(title = "?", subtitle = null, audio = intro?.then(question, gapBeats = 1.0) ?: question, key = key)

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
    private fun confirmation(task: TirTask): Score = when {
        // «Под нотой»: the chord that was under the note goes home.
        task.underNote -> toneChords(listOf(task.chords[0], Harmony.diatonicChord(task.key.mode, 0)), task.key, beatsEach = 0.9)
        else -> plainConfirmation(task)
    }

    private fun plainConfirmation(task: TirTask): Score = when (task.kind) {
        TirKind.NOTE, TirKind.MELODY -> {
            val last = task.notes.last()
            arranger.line(listOf(last) + Harmony.resolution(last, task.key.mode), task.key, task.voice, beatsEach = 0.5, tempoBpm = TEMPO, tonicFloor = task.tonic)
        }
        TirKind.CHORD -> arranger.chords(listOf(task.chords[0], Harmony.diatonicChord(task.key.mode, 0)), task.key, beatsEach = 0.75, tempoBpm = TEMPO)
        TirKind.PROGRESSION -> arranger.chords(listOf(Harmony.diatonicChord(task.key.mode, 0)), task.key, beatsEach = 1.0, tempoBpm = TEMPO)
    }

    /** Wrong answer (rule 5): hear the question again, then where it resolves. */
    private fun correction(task: TirTask): Score = when {
        // «Под нотой»: the note again over its chord, then that chord going home.
        task.underNote -> task.question.then(
            toneChords(listOf(task.chords[0], Harmony.diatonicChord(task.key.mode, 0)), task.key, beatsEach = 1.0),
            gapBeats = 0.25,
        )
        else -> plainCorrection(task)
    }

    private fun plainCorrection(task: TirTask): Score = when (task.kind) {
        TirKind.NOTE, TirKind.MELODY -> {
            val last = task.notes.last()
            val resolution = arranger.line(listOf(last) + Harmony.resolution(last, task.key.mode), task.key, task.voice, beatsEach = 0.75, tempoBpm = TEMPO, tonicFloor = task.tonic)
            task.question.then(resolution, gapBeats = 0.5)
        }
        TirKind.CHORD -> task.question.then(
            arranger.chords(listOf(Harmony.diatonicChord(task.key.mode, 0)), task.key, beatsEach = 1.0, tempoBpm = TEMPO),
            gapBeats = 0.25,
        )
        TirKind.PROGRESSION -> task.question
    }

    private val TirTask.voice: Voice get() = if (drone != null) Voice.TONE else Voice.PIANO

    companion object {
        const val TEMPO = 120.0

        /** Questions per drone key: long enough to settle into «home», short enough to stay random. */
        const val KEY_BLOCK = 8

        /** Single notes and melodies are heard over the drone; chords keep the cadence. */
        fun usesDrone(level: Int): Boolean = level != 5 && level != 6

        /** O-08: chords under the note (steps of the scale, 0 = I). */
        val CHORD_STEPS_L1 = listOf(0, 3, 4, 5)
        val CHORD_STEPS_L2 = listOf(0, 1, 2, 3, 4, 5)
        private const val OVER_CHORDS_BLOCK = 100
        private const val UNDER_NOTE_BLOCK = 200

        /** «Под нотой»: the chords that can be under the note on [level] (vii° never). */
        fun underNotePad(mode: Mode, level: Int): List<ChordDegree> =
            (if (level <= 1) CHORD_STEPS_L1 else CHORD_STEPS_L2).map { Harmony.diatonicChord(mode, it) }

        /** The triad's tones as degrees of [mode] (a tone outside the mode keeps its canonical name). */
        fun chordTones(chord: ChordDegree, mode: Mode): List<Degree> = chord.quality.intervals.take(3).map { i ->
            val semis = Math.floorMod(chord.root.semitones + i, 12)
            mode.degrees.firstOrNull { it.semitones == semis } ?: Degree.canonical(semis)
        }

        /** Whether [degree] is one of the triad tones of [chord]. */
        fun holds(chord: ChordDegree, degree: Degree): Boolean =
            chord.quality.intervals.take(3).any { Math.floorMod(chord.root.semitones + it, 12) == Math.floorMod(degree.semitones, 12) }

        /** O-06: while earlier days' misses wait, this share of questions comes from them. */
        const val REVIEW_SHARE = 0.7
        const val MAX_REVIEW_ASKS = 3
        private const val NOTE_GROUP = "note:"
        private const val CHORD_GROUP = "chord:"

        /**
         * Review key of a Tir attempt: single notes merge across levels 1–3 and 7 («note:♭3»), so a
         * degree missed on one level comes back on another; chords of level 5 are «chord:ii».
         * Melodies and progressions are not reviewed item by item (null).
         */
        fun reviewKey(itemKey: String): String? {
            val code = itemKey.substringAfterLast(':')
            return when {
                itemKey.startsWith("tir:L5:") -> CHORD_GROUP + code
                itemKey.startsWith("tir:L1:") || itemKey.startsWith("tir:L2:") || itemKey.startsWith("tir:L3:") || itemKey.startsWith("tir:L7:") -> NOTE_GROUP + code
                else -> null
            }
        }

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

        /** «Над аккордами»: a little slower than over the drone — the ear has to hold the key. */
        const val OVER_CHORDS_TARGET_MS = 3_000L

        /** «Под нотой»: a chord takes a moment longer to place than a single note. */
        const val UNDER_NOTE_TARGET_MS = 3_500L

        /** Median-latency targets for promotion, per level (ROADMAP G-02). */
        fun promotionTargetMs(level: Int): Long = when (level) {
            4 -> 5_000
            6 -> 8_000
            else -> 2_500
        }
    }
}
