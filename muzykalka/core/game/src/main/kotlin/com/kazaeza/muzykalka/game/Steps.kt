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

/** One progression of the ladder; [like] names a song that uses exactly this move. */
data class StepsPattern(val chords: List<ChordDegree>, val mode: Mode, val like: String? = null) {
    val code: String get() = chords.joinToString(" ") { ChordParser.formatRoman(it) }
}

/**
 * Taper «Ходы» content (ADR-014): progressions from the most banal (I–V) to ii–V–I with
 * sevenths. A level holds a handful of moves; the driver picks the weakest ones more often.
 */
object StepsLadder {
    private fun p(spec: String, mode: Mode = Mode.MAJOR, like: String? = null) =
        StepsPattern(spec.split(' ').map(ChordDegree::parse), mode, like)

    val LEVELS: List<List<StepsPattern>> = listOf(
        listOf(
            p("I V"), p("I IV"), p("V I"), p("IV I"), p("I V I"), p("I IV I"),
        ),
        listOf(
            p("I IV V", like = "Twist and Shout"), p("I IV V I"), p("ii V I"), p("I V IV I", like = "конец куплета «Let It Be»"),
            p("I vi IV"), p("I ii V I"),
        ),
        listOf(
            p("I V vi IV", like = "No Woman, No Cry"), p("I vi IV V", like = "Stand by Me"), p("vi IV I V"),
            p("I V ii IV"), p("I V vi iii IV I IV V", like = "Канон Пахельбеля"),
        ),
        listOf(
            p("i iv", Mode.MINOR), p("i v", Mode.MINOR), p("i iv i", Mode.MINOR), p("i bVII", Mode.MINOR, "A Horse with No Name"),
            p("i iv V i", Mode.MINOR), p("i bVII bVI V", Mode.MINOR, "Hit the Road Jack"), p("i bVI bIII bVII", Mode.MINOR, "Zombie"),
        ),
        listOf(
            p("ii7 V7 Imaj7"), p("Imaj7 vi7 ii7 V7"), p("iii7 VI7 ii7 V7"), p("iiø7 V7 i7", Mode.MINOR),
            p("I7 IV7 I7 V7", like = "блюз"),
        ),
    )
}

/** «Ходы»: the key and the degrees are shown; the user plays it on the guitar and says how it went. */
data class StepsTask(
    override val itemKey: String,
    override val prompt: Prompt,
    val level: Int,
    val key: Key,
    val pattern: StepsPattern,
    /** What the progression sounds like in this key — played right after the answer. */
    val sound: Score,
) : Task

/**
 * Taper «Ходы» (ADR-014). Name → place: «ii–V–I в ре мажоре» → the user plays it. No microphone:
 * chords are self-graded («получилось / не вышло»), and every answer is followed by the
 * progression itself so the ear checks the hands (rule 5).
 */
class StepsDriver(
    private val keyPicker: KeyPicker,
    private val notation: DegreeNotation = DegreeNotation.ROMAN,
    private val arranger: Arranger = Arranger(),
) : ModeDriver<StepsTask> {
    override val variant: Variant = Variant.TAPER_STEPS

    override val accepts: Set<Class<out Answer>> = setOf(Answer.SelfReport::class.java, Answer.Pass::class.java)

    override fun nextTask(context: DriverContext): StepsTask {
        val level = context.level.coerceIn(1, StepsLadder.LEVELS.size)
        val pool = StepsLadder.LEVELS[level - 1]
        val pattern = ItemWeights.pick(context.random, pool) { weight(itemKey(level, it), context.history) }
        val key = keyPicker.next(pattern.mode)
        val sound = arranger.chords(pattern.chords, key, beatsEach = 2.0, tempoBpm = TEMPO)
        return StepsTask(
            itemKey = itemKey(level, pattern),
            prompt = Prompt(title = formula(pattern), subtitle = pattern.like, audio = null, key = key),
            level = level, key = key, pattern = pattern, sound = sound,
        )
    }

    /** Playing time is not a skill signal here: unseen moves first, then the ones that failed. */
    private fun weight(key: String, history: List<AttemptRecord>): Double {
        val mine = history.filter { it.itemKey == key }.takeLast(10)
        if (mine.isEmpty()) return 3.0
        return 1.0 + 3.0 * mine.count { !it.correct } / mine.size
    }

    private fun itemKey(level: Int, p: StepsPattern) = "steps:L$level:" + p.chords.joinToString("-") { ChordParser.formatRoman(it) }

    fun formula(p: StepsPattern): String = p.chords.joinToString(" – ") { DegreeFormatter.chord(it, notation) }

    override fun evaluate(task: StepsTask, answer: Answer): Evaluation {
        val correct = answer is Answer.SelfReport && answer.grade != SelfGrade.FAILED
        val given = when (answer) {
            is Answer.SelfReport -> answer.grade
            else -> SelfGrade.FAILED
        }
        return Evaluation(
            correct = correct,
            expectedText = formula(task.pattern),
            givenText = given.ru,
            correction = if (correct) null else task.sound,
            taskHadSound = true,
            confirmation = if (correct) task.sound else null,
            expectedCode = task.pattern.code,
            givenCode = given.name,
        )
    }

    companion object {
        const val TEMPO = 110.0

        /** Self-graded and paced by the guitar: promotion looks at accuracy, not speed. */
        const val PROMOTION_TARGET_MS = 120_000L
    }
}
