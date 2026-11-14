package com.kazaeza.muzykalka.game

import com.kazaeza.muzykalka.music.Arranger
import com.kazaeza.muzykalka.music.ChordDegree
import com.kazaeza.muzykalka.music.ChordParser
import com.kazaeza.muzykalka.music.DegreeFormatter
import com.kazaeza.muzykalka.music.DegreeNotation
import com.kazaeza.muzykalka.music.Key
import com.kazaeza.muzykalka.music.KeyPicker
import com.kazaeza.muzykalka.music.Mode
import com.kazaeza.muzykalka.music.Score
import com.kazaeza.muzykalka.music.Section

/**
 * One question of «Формула вслух»: the song and one of its sections are named; the user says the section's degrees
 * (aloud or silently), opens the answer and says how it went. [sound] is the section in [key], heard after the answer.
 */
data class FormulaTask(
    override val itemKey: String,
    override val prompt: Prompt,
    val songId: String,
    val title: String,
    val artist: String?,
    val mode: Mode,
    /** The form of the whole song, e.g. «AABA» — where the section stands. */
    val form: String,
    val section: Section,
    val key: Key,
    val sound: Score,
) : Task

/**
 * F-08 «Формула вслух» (ADR-046): name → formula, without the guitar — a song of the library and one of its sections
 * are named, the degrees are recalled, the answer is opened and self-graded (as «Ходы» are, ADR-014). After the grade
 * the section sounds in a random key, so the ear checks what the memory said (rule 5 for «не вспомнил»).
 *
 * The songs in learning are asked when there are any — a song on the shelf was never learnt, there is nothing to
 * recall. Self-graded and silent until the answer: it changes no FSRS card (only a played order does).
 */
class FormulaDriver(
    songs: List<GuessSong>,
    private val keyPicker: KeyPicker,
    private val notation: DegreeNotation = DegreeNotation.ROMAN,
    private val arranger: Arranger = Arranger(),
) : ModeDriver<FormulaTask> {
    override val variant: Variant = Variant.FORMULA_ALOUD

    override val accepts: Set<Class<out Answer>> = setOf(Answer.SelfReport::class.java, Answer.Pass::class.java)

    private class Item(val source: GuessSong, val section: Section) {
        val key: String get() = "formula:${source.id}:${section.label}"
    }

    private val items: List<Item> = songs.sortedBy { it.id }.let { all -> all.filter { it.learning }.ifEmpty { all } }
        .flatMap { s -> s.song.sections.filter { sec -> sec.bars.any { it.chords.isNotEmpty() } }.map { Item(s, it) } }
    private var last: Item? = null

    init {
        require(items.isNotEmpty()) { "«Формула вслух» needs a song with a section" }
    }

    override fun nextTask(context: DriverContext): FormulaTask {
        val item = ItemWeights.pick(context.random, items, avoid = last) { weight(it.key, context.history) }
        last = item
        val song = item.source.song
        val key = keyPicker.next(song.mode)
        val chords = item.section.bars.flatMap { it.chords }.take(MAX_CHORDS)
        return FormulaTask(
            itemKey = item.key,
            prompt = Prompt(title = song.title, subtitle = "часть ${item.section.label}", audio = null, key = key),
            songId = item.source.id, title = song.title, artist = song.artist, mode = song.mode, form = song.formString,
            section = item.section, key = key,
            sound = arranger.chords(chords, key, beatsEach = 2.0, tempoBpm = TEMPO),
        )
    }

    /** Thinking time is not a skill signal: unseen sections first, then the ones that were not recalled. */
    private fun weight(key: String, history: List<AttemptRecord>): Double {
        val mine = history.filter { it.itemKey == key }.takeLast(10)
        if (mine.isEmpty()) return 3.0
        return 1.0 + 3.0 * mine.count { !it.correct } / mine.size
    }

    fun formula(section: Section): String =
        section.bars.joinToString(" | ") { bar -> bar.chords.joinToString(" ") { DegreeFormatter.chord(it, notation) } }

    override fun evaluate(task: FormulaTask, answer: Answer): Evaluation {
        val grade = (answer as? Answer.SelfReport)?.grade ?: SelfGrade.FAILED
        val correct = grade != SelfGrade.FAILED
        return Evaluation(
            correct = correct,
            expectedText = formula(task.section),
            givenText = grade.ru,
            correction = if (correct) null else task.sound,
            taskHadSound = true,
            confirmation = if (correct) task.sound else null,
            expectedCode = task.section.bars.joinToString("|") { bar -> bar.chords.joinToString(" ") { c: ChordDegree -> ChordParser.formatRoman(c) } },
            givenCode = grade.name,
        )
    }

    companion object {
        const val TEMPO = 100.0

        /** A long section is not played whole after the answer: the first chords are enough to check by ear. */
        const val MAX_CHORDS = 16

        /** Self-graded: promotion (there is one level) would look at accuracy, not speed. */
        const val PROMOTION_TARGET_MS = 120_000L

        fun playable(songs: List<GuessSong>): Boolean = songs.any { s -> s.song.sections.any { sec -> sec.bars.any { it.chords.isNotEmpty() } } }
    }
}
