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

/**
 * O-17: a chord in words. [role] is the job it does in the key («доминанта»), [quality] is how its
 * stack sounds («Мажорный + малая септима»), [line] hangs a feeling on the role. The tile says «V⁷»,
 * a tap plays it; these words keep «доминанта», «минорный» and «нона» from blurring together.
 */
data class ChordVibe(val role: String, val quality: String, val line: String)

object ChordVibes {
    fun of(chord: ChordDegree, mode: Mode): ChordVibe {
        val (role, line) = job(chord, mode)
        return ChordVibe(role, QUALITY.getValue(chord.quality), line)
    }

    /** «V⁷ — доминанта», the chord in the user's notation. */
    fun title(chord: ChordDegree, mode: Mode, notation: DegreeNotation): String =
        DegreeFormatter.chord(chord, notation) + " — " + of(chord, mode).role

    private val GUEST = "гость из другого лада" to "Не из этого лада: звучит необычно — прислушайся, куда он тянет."

    /** The job of [chord] by its root above the tonic and the third it carries; a stranger → [GUEST]. */
    private fun job(chord: ChordDegree, mode: Mode): Pair<String, String> {
        val set = chord.quality.pitchClassSet
        val major = 4 in set
        val minor = 3 in set
        val dominantSeventh = major && 10 in set
        val dim = minor && 6 in set
        val minorKey = mode.hasMinorThird
        return when (chord.root.semitones) {
            0 -> if (dominantSeventh) {
                "тоника" to "Дом, но с септимой: блюзовая, не даёт совсем успокоиться."
            } else {
                "тоника" to "Дом: покой и точка. Всё остальное тянет сюда."
            }
            1 -> "тёмный сосед дома" to "Полутоном выше дома: испанский холодок, тянет вниз к тонике."
            2 -> if (major) {
                "доминанта доминанты" to "Мажорный аккорд на второй ступени тянет к доминанте — как она сама, только на ступень выше."
            } else {
                "разбег к доминанте" to "Вторая ступень готовит доминанту: вторая → доминанта → дом, самый частый ход в песнях."
            }
            3 -> when {
                !major -> GUEST
                minorKey -> "параллельный мажор" to "Светлая сторона минора: просвет посреди печали, те же ноты, но дом сдвинут."
                else -> "гость из минора" to "Пониженная третья ступень: заём из минора, неожиданная тень посреди света."
            }
            4 -> when {
                major && !minorKey -> "доминанта к шестой" to "Мажорная третья ступень тянет к шестой — драматичный поворот."
                minor && !dim && !minorKey -> "сосед дома" to "Мягкая «серая зона»: две ноты общие с тоникой, но домом не кажется."
                else -> GUEST
            }
            5 -> when {
                minor && !dim && minorKey -> "субдоминанта" to "Минорная четвёртая: печальный шаг от дома, классика минора."
                minor && !dim -> "минорная субдоминанта" to "Заём из минора: горько-сладкий шаг от дома, свет становится серьёзным."
                major && minorKey -> "светлая четвёртая" to "Мажорная четвёртая в миноре: луч среди печали (дорийский цвет)."
                major -> "субдоминанта" to "Шаг от дома: светлый простор. Любит возвращаться домой («аминь»)."
                !dim -> "субдоминанта" to "Шаг от дома без терции: ни светлый, ни тёмный, открытый."
                else -> GUEST
            }
            6 -> "тритон от дома" to "Самая далёкая от дома: напряжённая, дрожащая, просит разрешения."
            7 -> when {
                dominantSeventh -> "доминанта" to "Сильнее всего тянет домой: внутри острая пара (терция и септима) просит разрешения." +
                    leadingToneNote(mode)
                major -> "доминанта" to "Яркий, открытый шаг к дому: тянет домой, но мягче, чем с септимой." + leadingToneNote(mode)
                minor && !dim -> "минорная доминанта" to "Доминанта без остроты: смотрит домой, но не торопит (натуральный минор, дорийский лад, блюз)."
                !dim -> "доминанта" to "Доминанта без терции: ни светлая, ни тёмная, открыто ведёт домой."
                else -> GUEST
            }
            8 -> when {
                !major -> GUEST
                minorKey -> "широкий свет" to "Шестая ступень минора: широкий, киношный аккорд — рассвет после печали."
                else -> "гость из минора" to "Заём из минора: драматичный, киношный поворот."
            }
            9 -> when {
                major && !minorKey -> "доминанта ко второй" to "Мажорная шестая тянет ко второй ступени — временная доминанта."
                minor && !dim && !minorKey -> "параллельный минор" to "Грусть рядом с домом: те же ноты, что у мажора, но центр на шестой ступени. Самый частый «грустный» аккорд мажора."
                else -> GUEST
            }
            10 -> when {
                dominantSeventh && minorKey -> "доминанта параллельного мажора" to
                    "Септаккорд на седьмой ступени минора тянет не домой, а к мажорной третьей ступени — в параллельный мажор."
                major && minorKey -> "седьмая ступень минора" to "Шаг на тон ниже дома: мягкий, естественный путь к тонике в миноре."
                major -> "рок-ход" to "Заём из миксолидийского: идёт домой без доминанты, рок-н-ролльный шаг на тон ниже."
                else -> GUEST
            }
            11 -> when {
                dim -> "ведущий тон" to "Острый, просится домой: как доминанта, у которой убрали основание."
                major -> "яркая седьмая" to "Гость из лидийского лада: сияющий, как сон."
                else -> GUEST
            }
            else -> GUEST
        }
    }

    /** In a minor mode without the leading tone the dominant's third is borrowed — worth one line. */
    private fun leadingToneNote(mode: Mode): String =
        if (mode.hasMinorThird && mode.degrees.none { it.semitones == 11 }) {
            " Её терция — ведущий тон, взятый из гармонического минора."
        } else {
            ""
        }

    private val QUALITY = mapOf(
        ChordQuality.MAJOR to "Мажорный: светлый.",
        ChordQuality.MINOR to "Минорный: мягко-печальный.",
        ChordQuality.DIMINISHED to "Уменьшённый: напряжённый, ни мажор ни минор.",
        ChordQuality.AUGMENTED to "Увеличенный: парящий, без опоры.",
        ChordQuality.SUS2 to "Sus2: вместо терции вторая, открытый и звонкий.",
        ChordQuality.SUS4 to "Sus4: вместо терции кварта, подвешенный.",
        ChordQuality.POWER to "Квинта: без терции, ни мажор ни минор.",
        ChordQuality.MAJOR6 to "Мажорный + шестая: тёплый, уютный.",
        ChordQuality.MINOR6 to "Минорный + шестая: печаль с лёгким светом.",
        ChordQuality.DOMINANT7 to "Мажорный + малая септима: с «зубами», просится дальше.",
        ChordQuality.MAJOR7 to "Мажорный + большая септима: мечтательный, парящий.",
        ChordQuality.MINOR7 to "Минорный + малая септима: мягкий, грустноватый.",
        ChordQuality.MINOR_MAJOR7 to "Минорный + большая септима: тревожный, «шпионский».",
        ChordQuality.HALF_DIMINISHED7 to "Полууменьшённый: тёмный, вопросительный.",
        ChordQuality.DIMINISHED7 to "Уменьшённый септаккорд: острый, как скрип двери.",
        ChordQuality.DOMINANT7_SUS4 to "Септима и кварта вместо терции: подвешенный, ждёт разрешения.",
        ChordQuality.ADD9 to "Мажорный + нона, без септимы: звонкий, воздушный.",
        ChordQuality.DOMINANT9 to "Нонаккорд: септима и нона, «джазовая» доминанта.",
        ChordQuality.MAJOR9 to "Maj9: большая септима и нона, роскошный и мечтательный.",
        ChordQuality.MINOR9 to "Минорный + септима + нона: бархатный.",
    )
}
