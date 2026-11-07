package com.kazaeza.muzykalka.game

import com.kazaeza.muzykalka.music.BookEntry
import com.kazaeza.muzykalka.music.BookGenre
import com.kazaeza.muzykalka.music.Key
import com.kazaeza.muzykalka.music.KeyPicker
import com.kazaeza.muzykalka.music.SongBook
import kotlin.math.max
import kotlin.random.Random

enum class BlindPhase {
    /** The sheet in degrees is shown, the name is not. */
    PLAYING,

    /** «Как прошло?» */
    SELF_GRADE,

    /** The name is revealed with the tips. */
    RESULT,

    FINISHED,
}

/** One finished blind order. */
data class BlindResult(
    val entry: BookEntry,
    val key: Key,
    val self: SelfGrade,
    /** The only hint of this mode was taken: chords in letters. */
    val usedLetters: Boolean,
    val tips: Int,
    val playableNow: Boolean,
    val elapsedMs: Long,
)

/**
 * Ticket O-10: «Вслепую» — a random song from the user's likes (the [BookGenre.LIKED] shelf of the
 * songbook), in a random key, with its degrees on the screen and its NAME HIDDEN until the end.
 * Without the name there is no memory of how the song goes to lean on: the degrees are all there is,
 * so what is trained is name → place, not recall of the record. The reveal comes after the
 * self-grade, as the reward.
 *
 * The hint ladder (rule 3) keeps its order: this mode starts on [HintStep.DEGREES] and the one step
 * above it, [HintStep.FULL_SHEET] (chords in letters, rule 1), costs tips. Nothing is written to the
 * library (rule 7): the pool is the built-in shelf itself, so there is no FSRS card either — the
 * attempt goes to the statistics only.
 */
class BlindSession(
    pool: List<BookEntry>,
    history: List<AttemptRecord>,
    private val keyPicker: KeyPicker,
    random: Random,
    /** The session ends after the order that crosses it. */
    private val budgetMs: Long = 10 * 60_000L,
) {
    private val queue = ArrayDeque(order(pool, history, random))
    private var startedAtMs = -1L

    var phase: BlindPhase = BlindPhase.FINISHED
        private set
    var current: BookEntry? = null
        private set
    var key: Key? = null
        private set

    /** The step the screen is on: [HintStep.DEGREES], or [HintStep.FULL_SHEET] once letters were asked for. */
    var hint: HintStep = BASE_HINT
        private set
    var orderedAtMs: Long = 0
        private set
    var lastResult: BlindResult? = null
        private set
    val results = mutableListOf<BlindResult>()

    val remainingInQueue: Int get() = queue.size
    private var playedAtMs: Long = 0

    fun next(nowMs: Long): Boolean {
        if (startedAtMs < 0) startedAtMs = nowMs
        val budgetSpent = nowMs - startedAtMs >= budgetMs && results.isNotEmpty()
        val entry = if (budgetSpent) null else queue.removeFirstOrNull()
        if (entry == null) {
            phase = BlindPhase.FINISHED
            current = null
            return false
        }
        current = entry
        key = keyPicker.next(entry.song.mode)
        hint = BASE_HINT
        orderedAtMs = nowMs
        lastResult = null
        phase = BlindPhase.PLAYING
        return true
    }

    /** Rule 3: one step up, and there is only one — letters. */
    fun requestLetters(): HintStep {
        if (phase != BlindPhase.PLAYING) return hint
        hint = hint.next() ?: hint
        return hint
    }

    fun finishPlaying(nowMs: Long) {
        if (phase != BlindPhase.PLAYING) return
        playedAtMs = nowMs
        phase = BlindPhase.SELF_GRADE
    }

    fun grade(grade: SelfGrade): BlindResult {
        check(phase == BlindPhase.SELF_GRADE) { "Grade comes after playing" }
        val entry = requireNotNull(current)
        val letters = hint == HintStep.FULL_SHEET
        val result = BlindResult(
            entry = entry,
            key = requireNotNull(key),
            self = grade,
            usedLetters = letters,
            tips = BlindGrading.tips(grade, letters),
            playableNow = BlindGrading.playableNow(grade, letters),
            elapsedMs = max(0, playedAtMs - orderedAtMs),
        )
        results += result
        lastResult = result
        phase = BlindPhase.RESULT
        return result
    }

    val totalTips: Int get() = results.sumOf { it.tips }

    companion object {
        /** Blind orders start with the degrees already open. */
        val BASE_HINT = HintStep.DEGREES

        /** Songs of the likes shelf: the pool of this mode. */
        fun pool(): List<BookEntry> = SongBook.ENTRIES.filter { it.genre == BookGenre.LIKED }

        fun itemKey(entry: BookEntry) = "blind:${entry.id}"

        /**
         * Songs never asked first (shuffled), then the ones asked longest ago: with a hundred songs
         * the same one rarely comes back, and never before the rest have had their turn.
         */
        fun order(pool: List<BookEntry>, history: List<AttemptRecord>, random: Random): List<BookEntry> {
            val lastAsked = HashMap<String, Long>()
            history.forEach { a -> lastAsked.merge(a.itemKey, a.atEpochMs, ::maxOf) }
            return pool.shuffled(random).sortedBy { lastAsked[itemKey(it)] ?: Long.MIN_VALUE }
        }
    }
}

object BlindGrading {
    /** Cost of the letters over the base step, from the same ladder as the Taper's. */
    val LETTERS_COST: Int = HintStep.FULL_SHEET.tipCost - BlindSession.BASE_HINT.tipCost

    fun tips(self: SelfGrade, letters: Boolean): Int {
        val base = max(0, TaperGrading.BASE_TIP - if (letters) LETTERS_COST else 0)
        val factor = when (self) {
            SelfGrade.CLEAN -> 1.0
            SelfGrade.STUMBLED -> 0.5
            SelfGrade.FAILED -> 0.0
        }
        return (base * factor).toInt()
    }

    /** «С ходу» here: played clean off the degrees alone, no letters. */
    fun playableNow(self: SelfGrade, letters: Boolean): Boolean = self == SelfGrade.CLEAN && !letters

    /** The attempt row for the statistics: the song id is the item, the grade is what was given. */
    fun attempt(result: BlindResult, nowEpochMs: Long): AttemptRecord = AttemptRecord(
        variant = Variant.TAPER_BLIND,
        level = 1,
        itemKey = BlindSession.itemKey(result.entry),
        expected = result.entry.id,
        given = result.self.name,
        correct = result.self != SelfGrade.FAILED && !result.usedLetters,
        latencyMs = result.elapsedMs,
        centsError = null,
        hintLevel = if (result.usedLetters) HintStep.FULL_SHEET.ordinal else BlindSession.BASE_HINT.ordinal,
        keyLabel = result.key.toString(),
        atEpochMs = nowEpochMs,
    )
}
