package com.kazaeza.muzykalka.music

/** O-57 (ADR-056): what a hint is made of — the feel of a degree in words, or a song everyone knows. */
enum class HintStyle(val ru: String) {
    FEEL("Ощущение"),
    SONGS("Песни"),
}

/**
 * O-57 (ADR-056), reworked in O-64 (ADR-063): the opening of a song everyone knows, written in degrees of its own key and
 * with its own rhythm, leaning on [degree]. The notes that ARE the degree ([targets], indices into [melody]) are what has
 * to be remembered: they sound louder and fuller than the rest, the screen marks them, [cue] says in words where they are
 * («to YOU»), and after the tune the first of them is heard once more, alone and long — the echo that the ear keeps.
 *
 * Only an opening (a phrase, up to [MAX_NOTES] notes), never a whole tune, and only degrees and durations — no pitch.
 * This is the one place where known songs ship with the app: the user asked for it (ADR-056); the own anchors of
 * [DegreeAnchor] stay as they were and go first.
 */
data class SongHint(
    val degree: Degree,
    val title: String,
    val melody: List<DegreeNote>,
    /** The length of each note in beats (a quarter = 1). */
    val beats: List<Double>,
    val targets: Set<Int>,
    /** Where in the tune the degree is, in words: «на слоге «LIT-tle»», «последняя, долгая». */
    val cue: String,
) {
    init {
        require(melody.size in MIN_NOTES..MAX_NOTES) { "a hint is $MIN_NOTES–$MAX_NOTES notes, not ${melody.size}" }
        require(beats.size == melody.size && beats.all { it > 0.0 }) { "every note has a length" }
        require(targets.isNotEmpty() && targets.all { it in melody.indices }) { "the notes to remember are in the tune" }
        require(targets.all { melody[it].degree sameSoundAs degree }) { "the marked notes are $degree" }
    }

    val melodyText: String get() = melody.joinToString(" ")

    /** «как в «…»» — the line a hint reads, the same shape as an own anchor's. */
    val line: String get() = "как в «$title»"

    /** The first of the notes to remember: what the echo plays. */
    val target: DegreeNote get() = melody[targets.min()]

    /**
     * The tune in [key] over the tonic drone, in its rhythm: the marked notes accented (louder and held), the others
     * lighter and a touch detached; then a breath, and the first marked note once more, alone and long.
     */
    fun score(key: Key): Score {
        val tonic = key.tonicAtOrAbove(FLOOR)
        var at = 0.0
        val notes = melody.mapIndexed { i, n ->
            val accent = i in targets
            val length = beats[i]
            ScoreNote(at, length * if (accent) 1.0 else DETACHED, Voice.TONE, key.midiOf(n, tonic), if (accent) ACCENT else SOFT).also { at += length }
        }
        val echo = ScoreNote(at + ECHO_GAP_BEATS, ECHO_BEATS, Voice.TONE, key.midiOf(target, tonic), ACCENT)
        return Score(TEMPO, notes + echo)
    }

    companion object {
        const val MIN_NOTES = 3
        const val MAX_NOTES = 8
        const val TEMPO = 100.0
        const val ACCENT = 1.0f
        const val SOFT = 0.42f
        private const val DETACHED = 0.8
        private const val ECHO_GAP_BEATS = 0.75
        private const val ECHO_BEATS = 2.5
        private val FLOOR = MidiNote(55)

        /**
         * One tune, written as tokens: a degree as in [DegreeNote.parse], «*» before it — a note to remember, «/2» after
         * it — its length in beats (1 when left out). «*6/2» is a held 6 to remember.
         */
        fun parse(degree: String, title: String, tune: String, cue: String): SongHint {
            val tokens = tune.trim().split(Regex("\\s+"))
            val targets = HashSet<Int>()
            val notes = ArrayList<DegreeNote>()
            val beats = ArrayList<Double>()
            tokens.forEachIndexed { i, raw ->
                var t = raw
                if (t.startsWith("*")) {
                    targets += i
                    t = t.drop(1)
                }
                val parts = t.split('/')
                notes += DegreeNote.parse(parts[0])
                beats += parts.getOrNull(1)?.toDouble() ?: 1.0
            }
            return SongHint(Degree.parse(degree), title, notes, beats, targets, cue)
        }

        /**
         * One tune per sound, each a different song, so that no two sounds are hung on the same one. Written from
         * memory (ADR-063): to be checked by ear; a sound without a tune keeps the words.
         */
        val ALL: List<SongHint> = listOf(
            parse("♭2", "Челюсти", "1/1.5 *♭2/1.5 1/0.75 *♭2/0.75 1/0.5 *♭2/0.5 1/0.5 *♭2/0.5", "верхняя из двух нот, что подкрадываются"),
            parse("2", "Братец Яков", "1 *2 3 1 1 *2 3 1", "на слоге «-тец»: «бра-ТЕЦ Я-ков»"),
            parse("♭3", "Smoke on the Water", "1 *♭3 4/1.5 1/1 *♭3 ♭5/0.5 4/2", "вторая нота риффа"),
            parse("3", "Когда святые маршируют", "1 *3 4 5/3", "«Oh WHEN the saints» — вторая нота"),
            parse("4", "Ода к радости", "3 3 *4 5 5 *4 3 2", "третья нота — первый шаг вверх"),
            parse("♯4", "Симпсоны", "1/1.5 3 *♯4 6/0.5 5/2", "третья нота — самая «кривая»"),
            parse("5", "Звёздные войны", "1/2 *5/2 4/0.34 3/0.33 2/0.33 1'/2 5", "вторая долгая нота — прыжок вверх"),
            parse("♭6", "Seven Nation Army", "1/1.5 1/0.5 ♭3/0.75 1/0.75 ♭7,/0.5 *♭6,/2 5,/2", "долгая нижняя нота перед последней"),
            parse("6", "Twinkle, twinkle", "1 1 5 5 *6 *6 5/2", "«LIT-TLE star» — где мелодия поднялась ещё выше"),
            parse("♭7", "Миссия невыполнима", "1/1.5 1/1.5 ♭3 4 1/1.5 1/1.5 *♭7,/1 7,/1", "предпоследняя — на тон ниже тоники"),
            parse("7", "С днём рождения", "5/0.75 5/0.25 6 5 1' *7/2", "последняя, долгая: «to YOU»"),
        )

        /** The hints that lean on the sound [semitones] above the tonic (0..11), the first one is the main. */
        fun forSemitone(semitones: Int): List<SongHint> {
            val s = Math.floorMod(semitones, 12)
            return ALL.filter { it.degree.semitones == s }
        }

        fun forDegree(degree: Degree): SongHint? = forSemitone(degree.semitones).firstOrNull()
    }
}
