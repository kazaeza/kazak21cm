package com.kazaeza.muzykalka.game

import com.kazaeza.muzykalka.music.Arranger
import com.kazaeza.muzykalka.music.ChordDegree
import com.kazaeza.muzykalka.music.Harmony
import com.kazaeza.muzykalka.music.Key
import com.kazaeza.muzykalka.music.KeyPicker
import com.kazaeza.muzykalka.music.MidiNote
import com.kazaeza.muzykalka.music.Score
import com.kazaeza.muzykalka.music.Song
import com.kazaeza.muzykalka.music.SongOpenings

/** A song of the library as the quiz sees it. [learning]: it is in the repeats, not on the shelf of the book. */
data class GuessSong(val id: String, val song: Song, val learning: Boolean = false)

/** One answer on the screen: a song and the chords it opens with ([opening] is shown as degrees, never as letters). */
data class SongOption(val id: String, val title: String, val artist: String?, val opening: List<ChordDegree>) {
    /** The opening in degrees of the song's own key, spelled one way: the code stored in the statistics. */
    val signature: String get() = SongOpenings.signature(opening)

    /** Equal for openings the ear cannot tell apart, whatever the key they are written in. */
    val shape: String get() = SongOpenings.shape(opening)
}

/**
 * One question of «Чья гармония?»: the cadence sets the key, then [question] — the opening of [answer] — is played,
 * and one of [options] is picked. No drone: these are chords, the cadence is their home (as on levels 5–6 of the Tir).
 */
data class SongGuessTask(
    override val itemKey: String,
    override val prompt: Prompt,
    val level: Int,
    val key: Key,
    val answer: SongOption,
    val options: List<SongOption>,
    val question: Score,
    /** Levels 1–2: every option shows its opening in degrees. Level 3: the titles alone. */
    val showsDegrees: Boolean,
    /** What the song does next — played as the reward for a right answer. */
    val goesOn: List<ChordDegree>,
) : DroneTask {
    override val drone: List<MidiNote>? get() = null
    override val replay: Score get() = question
}

/**
 * O-43 «Чья гармония?» (ADR-042): sound → name for whole progressions, with the user's own songs as the names. The
 * opening of a library song — its first [OPENING] chord changes — is played in a random key (rule 2) after the cadence
 * of its mode, and four songs are offered. No two options open with the same loop (in any key), so the answer can always be heard;
 * the other three are songs of the same kind of mode where the library has them.
 *
 * Level 1: the options show their chords in degrees and start differently — the first chord already tells.
 * Level 2: the options are the closest relatives — the same start, the difference is further on.
 * Level 3: the titles alone; the songs in learning are asked when there are enough of them.
 * A right answer is rewarded with the song going on; a wrong one hears the opening again and its way home (rule 5).
 */
class SongGuessDriver(
    songs: List<GuessSong>,
    private val keyPicker: KeyPicker,
    private val arranger: Arranger = Arranger(),
) : ModeDriver<SongGuessTask> {
    override val variant: Variant = Variant.TIR_SONG

    override val accepts: Set<Class<out Answer>> = setOf(Answer.SongChoice::class.java, Answer.Pass::class.java)

    private class Entry(val source: GuessSong, val option: SongOption, val goesOn: List<ChordDegree>) {
        val minorish: Boolean get() = source.song.mode.hasMinorThird
    }

    private val entries: List<Entry> = songs.mapNotNull { s -> entryOf(s) }

    /** Songs that open with the same loop are one question, and never meet on one screen. */
    private val byShape: Map<String, List<Entry>> = entries.groupBy { it.option.shape }

    /** Openings of the songs in learning; level 3 asks these when they make a quiz by themselves. */
    private val learningShapes: Set<String> = entries.filter { it.source.learning }.map { it.option.shape }.toSet()

    private var lastShape: String? = null

    init {
        require(byShape.size >= OPTIONS) { "The quiz needs $OPTIONS songs that open differently, got ${byShape.size}" }
    }

    override fun nextTask(context: DriverContext): SongGuessTask {
        val level = context.level.coerceIn(1, Levels.TIR_SONG.size)
        val r = context.random
        val ownOnly = level == 3 && learningShapes.size >= OPTIONS
        val askable = if (ownOnly) learningShapes.sorted() else byShape.keys.sorted()
        // levels 1–2 learn progressions, level 3 learns songs: that is what the statistics and the weights are kept by
        fun keyOf(e: Entry) = "song:L$level:" + if (level == 3) e.option.id else e.option.signature
        fun pool(shape: String) = byShape.getValue(shape).let { all -> if (ownOnly) all.filter { it.source.learning } else all }
        val shape = ItemWeights.pick(r, askable, avoid = lastShape) { sh -> pool(sh).maxOf { ItemWeights.weight(keyOf(it), context.history) } }
        lastShape = shape
        val asked = pool(shape).let { it[r.nextInt(it.size)] }
        // one song for every other loop: of the asked song's kind of mode where there is one, so that «it is minor,
        // so not this one» does not answer the question
        val others = byShape.keys.filter { it != shape }.sorted().shuffled(r).map { sh ->
            val group = byShape.getValue(sh)
            val kin = group.filter { it.minorish == asked.minorish }.ifEmpty { group }
            kin[r.nextInt(kin.size)]
        }
        val near: (Entry) -> Int = { SongOpenings.commonPrefix(it.option.opening, asked.option.opening) }
        val kinFirst = compareByDescending<Entry> { it.minorish == asked.minorish }
        val distractors = when (level) {
            1 -> others.sortedWith(kinFirst.thenBy(near)) // stable: the shuffle above breaks the ties
            2 -> others.sortedWith(kinFirst.thenByDescending(near))
            else -> others.sortedWith(kinFirst)
        }.take(OPTIONS - 1)
        val key = keyPicker.next(asked.source.song.mode)
        val cadence = arranger.chords(Harmony.cadence(key.mode), key, beatsEach = 1.0, tempoBpm = CADENCE_TEMPO)
        val question = arranger.chords(asked.option.opening, key, beatsEach = CHORD_BEATS, tempoBpm = TEMPO)
        return SongGuessTask(
            itemKey = keyOf(asked),
            // the cadence has its own tempo; `then` keeps the first score's, so the question is laid out in its beats
            prompt = Prompt(title = "?", audio = cadence.then(retimed(question, CADENCE_TEMPO), gapBeats = 1.5), key = key),
            level = level, key = key, answer = asked.option,
            options = (distractors.map { it.option } + asked.option).shuffled(r),
            question = question, showsDegrees = level < 3, goesOn = asked.goesOn,
        )
    }

    override fun evaluate(task: SongGuessTask, answer: Answer): Evaluation {
        val (correct, givenText, givenCode) = when (answer) {
            is Answer.SongChoice -> {
                val chosen = task.options.firstOrNull { it.id == answer.id }
                Triple(answer.id == task.answer.id, chosen?.title ?: "—", chosen?.signature ?: "—")
            }
            Answer.Pass -> Triple(false, "не знаю", "?")
            else -> Triple(false, "—", "—")
        }
        val home = Harmony.diatonicChord(task.key.mode, 0)
        return Evaluation(
            correct = correct,
            expectedText = task.answer.title,
            givenText = givenText,
            // rule 5: the opening again, quicker, and where it comes home
            correction = if (correct) null else arranger.chords(task.answer.opening + home, task.key, beatsEach = REVIEW_BEATS, tempoBpm = TEMPO),
            taskHadSound = true,
            // the reward: the song goes on
            confirmation = if (!correct) null else arranger.chords(task.goesOn.ifEmpty { listOf(home) }, task.key, beatsEach = REVIEW_BEATS, tempoBpm = TEMPO),
            expectedCode = task.answer.signature,
            givenCode = givenCode,
        )
    }

    companion object {
        /** Chord changes of a song that are played as the question. */
        const val OPENING = 4

        /** Changes after the opening that are played as the reward. */
        const val GOES_ON = 4

        /** A song with fewer changes than this has no opening to tell it by. */
        const val MIN_CHANGES = 3
        const val OPTIONS = 4
        const val TEMPO = 100.0
        const val CADENCE_TEMPO = 120.0

        /** Beats a chord of the question is held, and of the quicker replay and reward. */
        const val CHORD_BEATS = 2.0
        const val REVIEW_BEATS = 1.0

        /** A progression takes longer to place than a note: the median that opens the next level. */
        const val PROMOTION_TARGET_MS = 8_000L

        private fun entryOf(s: GuessSong): Entry? {
            val changes = SongOpenings.changes(s.song, OPENING + GOES_ON)
            if (changes.size < MIN_CHANGES) return null
            val opening = changes.take(OPENING)
            return Entry(s, SongOption(s.id, s.song.title, s.song.artist, opening), changes.drop(OPENING))
        }

        /** Whether [songs] make a quiz: at least [OPTIONS] of them open differently. */
        fun playable(songs: List<GuessSong>): Boolean =
            songs.mapNotNull { entryOf(it)?.option?.shape }.toSet().size >= OPTIONS

        /** [score] with its beats stretched so that it sounds the same at [tempoBpm] — to join scores of two tempos. */
        fun retimed(score: Score, tempoBpm: Double): Score {
            val k = tempoBpm / score.tempoBpm
            return Score(tempoBpm, score.notes.map { it.copy(startBeat = it.startBeat * k, durationBeats = it.durationBeats * k) })
        }
    }
}
