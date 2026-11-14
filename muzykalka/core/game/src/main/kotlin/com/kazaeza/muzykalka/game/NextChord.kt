package com.kazaeza.muzykalka.game

import com.kazaeza.muzykalka.music.Arranger
import com.kazaeza.muzykalka.music.ChordDegree
import com.kazaeza.muzykalka.music.ChordParser
import com.kazaeza.muzykalka.music.DegreeFormatter
import com.kazaeza.muzykalka.music.DegreeNotation
import com.kazaeza.muzykalka.music.Harmony
import com.kazaeza.muzykalka.music.Key
import com.kazaeza.muzykalka.music.KeyPicker
import com.kazaeza.muzykalka.music.MidiNote
import com.kazaeza.muzykalka.music.Mode
import com.kazaeza.muzykalka.music.Score
import com.kazaeza.muzykalka.music.SongOpenings

/**
 * One question of «Куда пойдёт?»: the song is named, its first chords ([shown]) are played after the cadence, and the
 * chord it goes to next ([answer]) is picked from [choices]. No drone: chords have the cadence for a home.
 */
data class NextChordTask(
    override val itemKey: String,
    override val prompt: Prompt,
    val level: Int,
    val key: Key,
    val songId: String,
    val title: String,
    val artist: String?,
    /** The chords that are played — and, on level 1, written. */
    val shown: List<ChordDegree>,
    val answer: ChordDegree,
    val choices: List<ChordDegree>,
    val question: Score,
    /** Level 1: the played chords are on the screen in degrees. Level 2: the title and the sound alone. */
    val showsDegrees: Boolean,
    /** What the song does after the answer — the reward goes on with it. */
    val goesOn: List<ChordDegree>,
) : DroneTask {
    override val drone: List<MidiNote>? get() = null
    override val replay: Score get() = question
}

/**
 * O-45 «Куда пойдёт?» (ADR-044): harmonic expectation on the user's own songs. A library song is named, the cadence
 * sets a random key (rule 2), the song's first [GIVEN] chord changes are played — and the next one is to be told
 * before it sounds. The title makes the answer definite: it is this song's way, heard ahead in the head, not a guess
 * at what chords usually do. Then the song is heard going there (rule 5 for a miss, the reward for a hit).
 *
 * Level 1: the played chords are written in degrees and four chords are offered. Level 2: nothing is written, and the
 * whole pad of the mode is offered. The answer is always on the pad exactly as the song has it (a V⁷ in a minor song
 * takes the place of the mode's own v).
 */
class NextChordDriver(
    songs: List<GuessSong>,
    private val keyPicker: KeyPicker,
    private val notation: DegreeNotation = DegreeNotation.ARABIC,
    private val arranger: Arranger = Arranger(),
) : ModeDriver<NextChordTask> {
    override val variant: Variant = Variant.TIR_NEXT

    override val accepts: Set<Class<out Answer>> = setOf(Answer.ChordChoice::class.java, Answer.Pass::class.java)

    private class Entry(val source: GuessSong, val changes: List<ChordDegree>)

    private val entries: List<Entry> = songs.mapNotNull(::entryOf).sortedBy { it.source.id }
    private var lastId: String? = null

    init {
        require(entries.size >= MIN_SONGS) { "The game needs $MIN_SONGS songs with $GIVEN chords and a next one, got ${entries.size}" }
    }

    override fun nextTask(context: DriverContext): NextChordTask {
        val level = context.level.coerceIn(1, Levels.TIR_NEXT.size)
        val r = context.random
        val prefix = "next:L$level:"
        val asked = ItemWeights.pick(r, entries, avoid = entries.firstOrNull { it.source.id == lastId }) { e ->
            // the songs in learning are the ones worth hearing ahead: twice as often
            ItemWeights.weight(prefix + e.source.id, context.history) * if (e.source.learning) LEARNING_BONUS else 1.0
        }
        lastId = asked.source.id
        val song = asked.source.song
        val shown = asked.changes.take(GIVEN)
        val answer = asked.changes[GIVEN]
        val pad = pad(song.mode, answer)
        val choices = if (level == 1) {
            // the chord that has just sounded cannot be the next change: it is not offered
            val others = pad.filter { !SongOpenings.sameChord(it, answer) && !SongOpenings.sameChord(it, shown.last()) }.shuffled(r).take(OPTIONS - 1)
            (others + answer).sortedBy { c -> pad.indexOfFirst { SongOpenings.sameChord(it, c) } }
        } else {
            pad
        }
        val key = keyPicker.next(song.mode)
        val cadence = arranger.chords(Harmony.cadence(key.mode), key, beatsEach = 1.0, tempoBpm = SongGuessDriver.CADENCE_TEMPO)
        val question = arranger.chords(shown, key, beatsEach = SongGuessDriver.CHORD_BEATS, tempoBpm = SongGuessDriver.TEMPO)
        return NextChordTask(
            itemKey = prefix + asked.source.id,
            prompt = Prompt(
                title = song.title,
                audio = cadence.then(SongGuessDriver.retimed(question, SongGuessDriver.CADENCE_TEMPO), gapBeats = 1.5),
                key = key,
            ),
            level = level, key = key, songId = asked.source.id, title = song.title, artist = song.artist,
            shown = shown, answer = answer, choices = choices, question = question, showsDegrees = level == 1,
            goesOn = asked.changes.drop(GIVEN + 1),
        )
    }

    override fun evaluate(task: NextChordTask, answer: Answer): Evaluation {
        val (correct, givenText, givenCode) = when (answer) {
            is Answer.ChordChoice -> Triple(
                SongOpenings.sameChord(answer.chord, task.answer),
                DegreeFormatter.chord(answer.chord, notation),
                ChordParser.formatRoman(answer.chord),
            )
            Answer.Pass -> Triple(false, "не знаю", "?")
            else -> Triple(false, "—", "—")
        }
        // the song as it really goes: the given chords, the answer, and a little further
        val way = arranger.chords(
            task.shown + task.answer + task.goesOn.take(if (correct) GOES_ON else 0), task.key,
            beatsEach = SongGuessDriver.REVIEW_BEATS, tempoBpm = SongGuessDriver.TEMPO,
        )
        return Evaluation(
            correct = correct,
            expectedText = DegreeFormatter.chord(task.answer, notation),
            givenText = givenText,
            correction = if (correct) null else way,
            taskHadSound = true,
            confirmation = if (correct) way else null,
            expectedCode = ChordParser.formatRoman(task.answer),
            givenCode = givenCode,
        )
    }

    companion object {
        /** Chord changes that are played before the question. */
        const val GIVEN = 3

        /** Changes after the answer that a right answer hears on. */
        const val GOES_ON = 2
        const val OPTIONS = 4
        const val MIN_SONGS = 3
        const val LEARNING_BONUS = 2.0
        const val PROMOTION_TARGET_MS = 8_000L

        private fun entryOf(s: GuessSong): Entry? =
            SongOpenings.changes(s.song, GIVEN + 1 + GOES_ON).takeIf { it.size > GIVEN }?.let { Entry(s, it) }

        fun playable(songs: List<GuessSong>): Boolean = songs.count { entryOf(it) != null } >= MIN_SONGS

        /**
         * The chords [answer] is picked among: the chords of [mode]'s scale (sevenths when the answer is one), with the
         * answer itself in the place of the scale's chord on its root — or added, when its root is outside the scale.
         */
        fun pad(mode: Mode, answer: ChordDegree): List<ChordDegree> {
            val sevenths = answer.quality.intervals.size >= 4
            val scale = (0..6).map { Harmony.diatonicChord(mode, it, sevenths) }
            val own = ChordDegree(answer.root, answer.quality)
            val at = scale.indexOfFirst { it.root.semitones == own.root.semitones }
            return if (at >= 0) scale.toMutableList().also { it[at] = own } else (scale + own).sortedBy { it.root.semitones }
        }
    }
}
