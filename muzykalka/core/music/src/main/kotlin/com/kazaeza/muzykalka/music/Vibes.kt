package com.kazaeza.muzykalka.music

/**
 * The character of a degree heard against its tonic: one word to hang the sound on and one line
 * that describes the feeling. Recognising degrees «по вайбу» means recognising these feelings,
 * not measuring distances from the previous note.
 */
data class DegreeVibe(val word: String, val line: String)

/** Russian «vibe» words per chromatic step (enharmonic spellings share a vibe). */
object DegreeVibes {
    private val BY_SEMITONE = listOf(
        DegreeVibe("дом", "Покой и точка. Сюда хочет вернуться всё остальное."),
        DegreeVibe("тень", "Тёмная и тесная, вплотную к дому — испанский холодок."),
        DegreeVibe("вопрос", "Шаг из дома: лёгкая, подвешенная, будто фраза не закончена."),
        DegreeVibe("грусть", "Минорная терция: мягкая печаль. В мажоре — блюзовая «синяя» нота."),
        DegreeVibe("свет", "Мажорная терция: тёплая, улыбчивая, спокойная. Делает лад мажорным."),
        DegreeVibe("тяжесть", "Зависла и давит вниз, как «аминь» в конце гимна."),
        DegreeVibe("тревога", "Самая далёкая от дома: висит в воздухе — мечтательно или зловеще."),
        DegreeVibe("опора", "Открытая и сильная, как фанфара. Устойчиво, но ещё не дома."),
        DegreeVibe("драма", "Тёмная и тяжёлая — киношная трагедия."),
        DegreeVibe("нежность", "Мягкая, ностальгическая — тёплая тоска."),
        DegreeVibe("блюз", "Расслабленная, рок-н-ролльная; домой не спешит."),
        DegreeVibe("напряжение", "Острая, на цыпочках, на полтона ниже дома — рвётся вверх."),
    )

    fun of(degree: Degree): DegreeVibe = BY_SEMITONE[degree.semitones]

    /**
     * O-08 (RESEARCH §4 D): the same degree feels different over another chord. A chord tone rests
     * on it («над IV — опора: терция»); any other degree leans to the nearest chord tone, down
     * when both are as near («над IV — тянет вниз к 4»).
     */
    fun overChord(degree: Degree, chord: ChordDegree, mode: Mode, notation: DegreeNotation): String {
        val name = DegreeFormatter.chord(chord, notation)
        val tones = chord.quality.intervals.take(4).map { Math.floorMod(it, 12) }
        val rel = Math.floorMod(degree.semitones - chord.root.semitones, 12)
        when (tones.indexOf(rel)) {
            0 -> return "над $name — опора: корень аккорда"
            1 -> return "над $name — опора: терция аккорда"
            2 -> return "над $name — опора: квинта аккорда"
            3 -> return "над $name — опора: септима аккорда"
        }
        val down = tones.minOf { Math.floorMod(rel - it, 12) }
        val up = tones.minOf { Math.floorMod(it - rel, 12) }
        val goesDown = down <= up
        val to = Math.floorMod(degree.semitones + if (goesDown) -down else up, 12)
        val target = mode.degrees.firstOrNull { it.semitones == to } ?: Degree.canonical(to)
        return "над $name — тянет ${if (goesDown) "вниз" else "вверх"} к ${DegreeFormatter.degree(target, notation)}"
    }

    /** «путь домой: 6 7 1» — the resolution the app plays, in the user's notation. */
    fun pathHome(degree: Degree, mode: Mode, notation: DegreeNotation): String {
        val path = Harmony.resolution(DegreeNote(degree), mode)
        return if (path.isEmpty()) "уже дома" else (listOf(DegreeNote(degree)) + path).joinToString(" → ") { DegreeFormatter.degree(it.degree, notation) }
    }
}
