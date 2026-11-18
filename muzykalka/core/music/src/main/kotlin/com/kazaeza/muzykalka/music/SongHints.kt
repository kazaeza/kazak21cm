package com.kazaeza.muzykalka.music

/** O-57 (ADR-056): what a hint is made of — the feel of a degree in words, or a song everyone knows. */
enum class HintStyle(val ru: String) {
    FEEL("Ощущение"),
    SONGS("Песни"),
}

/**
 * O-57 (ADR-056): a few notes of a song that everyone knows, written in degrees of its own key. They lean on [degree]:
 * «6 — как в «С днём рождения»». Played over the tonic drone after a miss, they hang the sound on a tune instead of a word.
 *
 * Only the first notes (3–4) are here, never a whole tune, and only degrees — no pitch, no rhythm beyond an even beat.
 * This is the one place where known songs ship with the app: the user asked for it (ADR-056), the own anchors
 * of [DegreeAnchor] stay as they were and go first.
 */
data class SongHint(val degree: Degree, val title: String, val melody: List<DegreeNote>) {
    init {
        require(melody.size in MIN_NOTES..MAX_NOTES) { "a hint is $MIN_NOTES–$MAX_NOTES notes, not ${melody.size}" }
        require(melody.any { it.degree sameSoundAs degree }) { "the melody must lean on $degree" }
    }

    val melodyText: String get() = melody.joinToString(" ")

    /** «как в «…»» — the line a hint reads, the same shape as an own anchor's. */
    val line: String get() = "как в «$title»"

    /** The notes in [key], in a singable register — played over the tonic drone, an even beat. */
    fun score(key: Key, arranger: Arranger = Arranger()): Score =
        arranger.line(melody, key, Voice.TONE, beatsEach = BEATS_EACH, tempoBpm = TEMPO, tonicFloor = FLOOR)

    companion object {
        const val MIN_NOTES = 3
        const val MAX_NOTES = 4
        const val TEMPO = 96.0
        private const val BEATS_EACH = 0.75
        private val FLOOR = MidiNote(55)

        private fun hint(degree: String, title: String, melody: String) =
            SongHint(Degree.parse(degree), title, DegreeNote.parseLine(melody))

        /**
         * One or two per sound, the tunes first for the sounds that are the hardest to call by a word. The first of
         * each is the one a hint shows; the rest are what a second miss in a row can offer. A sound without a tune
         * keeps the word (nothing is made up for it).
         */
        val ALL: List<SongHint> = listOf(
            hint("♭2", "Челюсти", "1 ♭2 1 ♭2"),
            hint("2", "Братец Яков", "1 2 3 1"),
            hint("♭3", "Seven Nation Army", "1 1 ♭3 1"),
            hint("3", "Когда святые маршируют", "1 3 4 5"),
            hint("3", "Ода к радости", "3 3 4 5"),
            hint("4", "Ода к радости", "3 3 4 5"),
            hint("♯4", "Симпсоны", "1 3 ♯4"),
            hint("5", "Twinkle, twinkle", "1 1 5 5"),
            hint("♭6", "Seven Nation Army", "1 ♭7, ♭6, 5,"),
            hint("6", "С днём рождения", "5 5 6 5"),
            hint("♭7", "Seven Nation Army", "♭3 1 ♭7,"),
            hint("7", "С днём рождения", "6 5 1' 7"),
        )

        /** The hints that lean on the sound [semitones] above the tonic (0..11), the first one is the main. */
        fun forSemitone(semitones: Int): List<SongHint> {
            val s = Math.floorMod(semitones, 12)
            return ALL.filter { it.degree.semitones == s }
        }

        fun forDegree(degree: Degree): SongHint? = forSemitone(degree.semitones).firstOrNull()
    }
}
