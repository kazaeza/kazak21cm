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
 * The mode stands on the top of the hint ladder, [HintStep.DEGREES], and has no hints above it:
 * chords in letters were dropped in O-13 (ADR-027). Nothing is written to the library (rule 7): the
 * pool is the built-in shelf itself, so there is no FSRS card either — the attempt goes to the
 * statistics only.
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
        orderedAtMs = nowMs
        lastResult = null
        phase = BlindPhase.PLAYING
        return true
    }

    fun finishPlaying(nowMs: Long) {
        if (phase != BlindPhase.PLAYING) return
        playedAtMs = nowMs
        phase = BlindPhase.SELF_GRADE
    }

    fun grade(grade: SelfGrade): BlindResult {
        check(phase == BlindPhase.SELF_GRADE) { "Grade comes after playing" }
        val entry = requireNotNull(current)
        val result = BlindResult(
            entry = entry,
            key = requireNotNull(key),
            self = grade,
            tips = BlindGrading.tips(grade),
            playableNow = BlindGrading.playableNow(grade),
            elapsedMs = max(0, playedAtMs - orderedAtMs),
        )
        results += result
        lastResult = result
        phase = BlindPhase.RESULT
        return result
    }

    val totalTips: Int get() = results.sumOf { it.tips }

    companion object {
        /** Blind orders show the degrees from the start, and nothing more is ever shown. */
        val BASE_HINT = HintStep.TOP

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
    fun tips(self: SelfGrade): Int {
        val base = TaperGrading.BASE_TIP
        val factor = when (self) {
            SelfGrade.CLEAN -> 1.0
            SelfGrade.STUMBLED -> 0.5
            SelfGrade.FAILED -> 0.0
        }
        return (base * factor).toInt()
    }

    /** «С ходу» here: played clean off the degrees alone. */
    fun playableNow(self: SelfGrade): Boolean = self == SelfGrade.CLEAN

    /** The attempt row for the statistics: the song id is the item, the grade is what was given. */
    fun attempt(result: BlindResult, nowEpochMs: Long): AttemptRecord = AttemptRecord(
        variant = Variant.TAPER_BLIND,
        level = 1,
        itemKey = BlindSession.itemKey(result.entry),
        expected = result.entry.id,
        given = result.self.name,
        correct = result.self != SelfGrade.FAILED,
        latencyMs = result.elapsedMs,
        centsError = null,
        hintLevel = BlindSession.BASE_HINT.ordinal,
        keyLabel = result.key.toString(),
        atEpochMs = nowEpochMs,
    )
}
