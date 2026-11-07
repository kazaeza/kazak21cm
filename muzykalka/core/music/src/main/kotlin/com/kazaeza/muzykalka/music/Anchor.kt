package com.kazaeza.muzykalka.music

/**
 * Ticket O-09 (RESEARCH §4 E): an anchor — the opening of a song the user knows, written in
 * degrees, that leans on [degree]: «5 — как начало …». Anchor songs with singing lifted blocked
 * practice to the level of interleaving (Wong, Chen & Lim 2021). Anchors are typed in by the user
 * only — no melody of anyone's song ships with the app (rule 7, ADR-013) — and are shown as a
 * hint after an answer, never as the answer.
 */
data class DegreeAnchor(val degree: Degree, val title: String, val melody: List<DegreeNote>) {
    init {
        require(title.isNotBlank() && title.length <= MAX_TITLE) { "title" }
        require(melody.size in 1..MAX_NOTES) { "melody length" }
        require(melody.any { it.degree sameSoundAs degree }) { "the melody must lean on $degree" }
    }

    /** "5 5 6 5 1' 7" — the same text the user typed (canonical spelling). */
    val melodyText: String get() = melody.joinToString(" ")

    /** The opening in [key], in a singable register; played over the tonic drone. */
    fun score(key: Key, arranger: Arranger = Arranger()): Score =
        arranger.line(melody, key, Voice.TONE, beatsEach = BEATS_EACH, tempoBpm = TEMPO, tonicFloor = FLOOR)

    companion object {
        const val MAX_TITLE = 60
        const val MAX_NOTES = 16
        const val TEMPO = 96.0
        private const val BEATS_EACH = 0.5

        /** G3: the tonic sits in G3–F♯4, the middle of a voice and of a guitar. */
        private val FLOOR = MidiNote(55)

        /** User input → anchor, or a Russian reason it cannot be an anchor of [degree]. */
        fun parse(degree: Degree, title: String, melody: String, notation: DegreeNotation = DegreeNotation.ARABIC): Result<DegreeAnchor> {
            val name = title.trim()
            if (name.isEmpty()) return failure("Напиши, что это за песня")
            if (name.length > MAX_TITLE) return failure("Название длиннее $MAX_TITLE знаков")
            val notes = runCatching { DegreeNote.parseLine(melody) }.getOrNull()
                ?: return failure("Не разобрал ступени: пиши через пробел, например «5 5 6 5 1' 7»")
            if (notes.isEmpty()) return failure("Запиши начало мелодии ступенями")
            if (notes.size > MAX_NOTES) return failure("Хватит начала: не больше $MAX_NOTES нот")
            if (notes.none { it.degree sameSoundAs degree }) {
                return failure("В мелодии нет ступени ${DegreeFormatter.degree(degree, notation)} — якорь должен на неё опираться")
            }
            return Result.success(DegreeAnchor(degree, name, notes))
        }

        private fun failure(text: String): Result<DegreeAnchor> = Result.failure(IllegalArgumentException(text))
    }
}
