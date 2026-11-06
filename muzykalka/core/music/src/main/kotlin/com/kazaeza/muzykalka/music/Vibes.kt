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

    /** «путь домой: 6 7 1» — the resolution the app plays, in the user's notation. */
    fun pathHome(degree: Degree, mode: Mode, notation: DegreeNotation): String {
        val path = Harmony.resolution(DegreeNote(degree), mode)
        return if (path.isEmpty()) "уже дома" else (listOf(DegreeNote(degree)) + path).joinToString(" → ") { DegreeFormatter.degree(it.degree, notation) }
    }
}
