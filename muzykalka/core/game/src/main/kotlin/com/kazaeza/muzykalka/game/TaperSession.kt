package com.kazaeza.muzykalka.game

import com.kazaeza.muzykalka.music.ChordDegree
import com.kazaeza.muzykalka.music.Degree
import com.kazaeza.muzykalka.music.Key
import com.kazaeza.muzykalka.music.KeyPicker
import com.kazaeza.muzykalka.music.Melody
import com.kazaeza.muzykalka.music.Song
import com.kazaeza.muzykalka.srs.CardState
import com.kazaeza.muzykalka.srs.Fsrs
import com.kazaeza.muzykalka.srs.Rating
import kotlin.math.max
import kotlin.random.Random

/** An orderable library item: a song or a riff, with its memory state. */
data class TaperItem(
    val id: String,
    val title: String,
    val song: Song? = null,
    val melody: Melody? = null,
    val card: CardState,
) {
    init {
        require((song == null) != (melody == null)) { "A Taper item is either a song or a melody" }
    }

    val mode get() = song?.mode ?: melody!!.mode
}

enum class TaperPhase {
    /** The order has been announced; the user is (about to start) playing. Hints available. */
    PLAYING,

    /** «Как прошло?» — self-assessment. */
    SELF_GRADE,

    /** «Что это было?» — the formula is entered in degrees. */
    FORMULA,

    /** Tips and the verdict for this order. */
    RESULT,

    /** Queue empty or budget spent. */
    FINISHED,
}

/** The result of one order, ready to be persisted. */
data class OrderResult(
    val item: TaperItem,
    val key: Key,
    val outcome: OrderOutcome,
    val rating: Rating,
    val newCard: CardState,
    val tips: Int,
    val playableNow: Boolean,
)

/**
 * The Taper loop (ticket G-03): order → play (optionally with hints, one step at a time) →
 * self-grade → «Что это было?» → FSRS. Pure: time comes in as arguments, the caller announces,
 * listens and persists.
 */
class TaperSession(
    items: List<TaperItem>,
    private val keyPicker: KeyPicker,
    private val fsrs: Fsrs,
    private val today: Int,
    private val random: Random,
    /** Minutes of the set segment; the session ends after the order that crosses it. */
    private val budgetMs: Long = 8 * 60_000L,
    /** Exams forbid hints. */
    val hintsAllowed: Boolean = true,
) {
    private val queue = ArrayDeque(order(items, today, random))
    private var startedAtMs = -1L

    var phase: TaperPhase = TaperPhase.FINISHED
        private set
    var current: TaperItem? = null
        private set
    var key: Key? = null
        private set
    var hint: HintStep = HintStep.NONE
        private set
    var orderedAtMs: Long = 0
        private set
    var startLatencyMs: Long? = null
        private set
    var selfGrade: SelfGrade? = null
        private set
    var lastResult: OrderResult? = null
        private set
    val results = mutableListOf<OrderResult>()

    val remainingInQueue: Int get() = queue.size
    val isEmpty: Boolean get() = current == null && queue.isEmpty()

    /** Starts the next order, or finishes. Returns false when there is nothing (left) to order. */
    fun next(nowMs: Long): Boolean {
        if (startedAtMs < 0) startedAtMs = nowMs
        val budgetSpent = nowMs - startedAtMs >= budgetMs && results.isNotEmpty()
        val item = if (budgetSpent) null else queue.removeFirstOrNull()
        if (item == null) {
            phase = TaperPhase.FINISHED
            current = null
            return false
        }
        current = item
        key = keyPicker.next(item.mode)
        hint = HintStep.NONE
        orderedAtMs = nowMs
        startLatencyMs = null
        selfGrade = null
        lastResult = null
        phase = TaperPhase.PLAYING
        return true
    }

    /** First stable note after the order: measures the start (10 s window). */
    fun onFirstNote(nowMs: Long) {
        if (phase != TaperPhase.PLAYING || startLatencyMs != null) return
        val latency = nowMs - orderedAtMs
        if (latency in 0..TaperGrading.START_WINDOW_MS) startLatencyMs = latency
    }

    /** Rule 3: one step up the ladder; returns the new step (unchanged at the top or when forbidden). */
    fun requestHint(): HintStep {
        if (phase != TaperPhase.PLAYING || !hintsAllowed) return hint
        hint = hint.next() ?: hint
        return hint
    }

    fun finishPlaying() {
        if (phase == TaperPhase.PLAYING) phase = TaperPhase.SELF_GRADE
    }

    fun grade(grade: SelfGrade) {
        if (phase != TaperPhase.SELF_GRADE) return
        selfGrade = grade
        phase = TaperPhase.FORMULA
    }

    /** «Что это было?» for a song. */
    fun submitChords(chords: List<ChordDegree>): OrderResult {
        val song = requireNotNull(current?.song) { "Current item is not a song" }
        return complete(TaperGrading.formulaAccuracy(TaperGrading.expectedFormula(song), chords))
    }

    /** «Что это было?» for a riff. */
    fun submitDegrees(degrees: List<Degree>): OrderResult {
        val melody = requireNotNull(current?.melody) { "Current item is not a melody" }
        return complete(TaperGrading.melodyAccuracy(melody.notes.map { it.degree }, degrees))
    }

    private fun complete(accuracy: Double): OrderResult {
        check(phase == TaperPhase.FORMULA) { "Formula is entered after the self-grade" }
        val item = requireNotNull(current)
        val outcome = OrderOutcome(item.id, hint, requireNotNull(selfGrade), accuracy, startLatencyMs)
        val rating = TaperGrading.rating(outcome)
        val result = OrderResult(
            item = item,
            key = requireNotNull(key),
            outcome = outcome,
            rating = rating,
            newCard = fsrs.review(item.card, rating, today),
            tips = TaperGrading.tips(outcome),
            playableNow = TaperGrading.playableNow(outcome),
        )
        results += result
        lastResult = result
        phase = TaperPhase.RESULT
        return result
    }

    val totalTips: Int get() = results.sumOf { it.tips }

    companion object {
        /**
         * Queue order: most overdue first, then new items, then the rest by lowest stability.
         * Ties are shuffled so the same songs don't always open the evening.
         */
        fun order(items: List<TaperItem>, today: Int, random: Random): List<TaperItem> =
            items.shuffled(random).sortedWith(
                compareBy<TaperItem> { if (it.card.isNew) 1 else if (it.card.isDue(today)) 0 else 2 }
                    .thenBy { it.card.dueDay ?: Int.MAX_VALUE }
                    .thenBy { it.card.stability ?: 0.0 },
            )

        /** Items due today (new items included), in queue order. */
        fun due(items: List<TaperItem>, today: Int, random: Random): List<TaperItem> =
            order(items.filter { it.card.isDue(today) }, today, random)

        fun budgetMs(minutes: Double): Long = max(60_000L, (minutes * 60_000).toLong())
    }
}
