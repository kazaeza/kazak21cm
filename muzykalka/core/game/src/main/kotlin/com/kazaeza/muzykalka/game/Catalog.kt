package com.kazaeza.muzykalka.game

/**
 * The six links of the triangle sound / name / place. Every drill trains one or two of them;
 * Progress shows a score per link. Four of six go through the NAME — the diagnosed gap.
 */
enum class Link(val ru: String) {
    NAME_TO_PLACE("имя → место"),
    PLACE_TO_NAME("место → имя"),
    SOUND_TO_NAME("звук → имя"),
    NAME_TO_SOUND("имя → звук"),
    PLACE_TO_SOUND("место → звук"),
    SOUND_TO_PLACE("звук → место"),
}

enum class GuitarNeed(val ru: String) {
    REQUIRED("нужна гитара"),
    OPTIONAL("можно без"),
    NONE("гитара не нужна"),
}

/** The six game modes from the plan. [since] = app version that ships the mode. */
enum class ModeId(
    val ru: String,
    val goal: String,
    val links: Set<Link>,
    val guitar: GuitarNeed,
    val since: String,
) {
    HUNT("Охота", "любая нота на грифе быстрее секунды", setOf(Link.NAME_TO_PLACE, Link.PLACE_TO_NAME), GuitarNeed.OPTIONAL, "0.1"),
    TIR("Тир", "слышишь ноту или аккорд — сразу знаешь ступень", setOf(Link.SOUND_TO_NAME), GuitarNeed.NONE, "0.1"),
    KAMERTON("Камертон", "слышать ноту до того, как ударил по струне", setOf(Link.NAME_TO_SOUND, Link.PLACE_TO_SOUND), GuitarNeed.OPTIONAL, "0.1"),
    PARROT("Попугай", "услышал — сыграл сразу, без поиска", setOf(Link.SOUND_TO_PLACE), GuitarNeed.REQUIRED, "0.1"),
    TAPER("Тапёр", "видишь ступени — играешь на гитаре в любой тональности", setOf(Link.NAME_TO_PLACE), GuitarNeed.REQUIRED, "0.1"),
    RECORDER("Магнитофон", "игра на ощущениях становится словарём", emptySet(), GuitarNeed.REQUIRED, "0.3"),
}

/** Concrete playable variants. Stats, levels and records are kept per variant. */
enum class Variant(val mode: ModeId, val ru: String, val guitar: GuitarNeed, val since: String) {
    HUNT_FIND(ModeId.HUNT, "Охота", GuitarNeed.REQUIRED, "0.1"),
    HUNT_ALL_OCTAVES(ModeId.HUNT, "Все октавы", GuitarNeed.REQUIRED, "0.1"),
    HUNT_REVERSE(ModeId.HUNT, "Обратная охота", GuitarNeed.NONE, "0.1"),
    TIR_CLASSIC(ModeId.TIR, "Тир", GuitarNeed.NONE, "0.1"),
    TIR_OVER_CHORDS(ModeId.TIR, "Над аккордами", GuitarNeed.NONE, "0.1"),
    /** O-15: the reverse — the note is shown, the chord droning under it is asked (ADR-029). */
    TIR_UNDER_NOTE(ModeId.TIR, "Под нотой", GuitarNeed.NONE, "0.1"),
    /** O-58: the tonic hums, a chord of the key is played over it — which step is it (ADR-057). The link before «Куда пойдёт?». */
    TIR_ON_TONIC(ModeId.TIR, "Аккорд над тоникой", GuitarNeed.NONE, "0.1"),
    /** O-62: only the bass of a chord is heard over the humming tonic — which chord is it (ADR-061). */
    TIR_BASS(ModeId.TIR, "Бас", GuitarNeed.NONE, "0.1"),
    /** O-62: a chord is given, the next one is asked — the smallest step of «Куда пойдёт?» (ADR-061). */
    TIR_TWO_CHORDS(ModeId.TIR, "Два аккорда", GuitarNeed.NONE, "0.1"),
    /** O-65: two sounds struck together — what is the upper one from the lower (ADR-064). No drone: the pair is the whole question. */
    TIR_DYAD(ModeId.TIR, "Созвучие", GuitarNeed.NONE, "0.1"),
    /** S-03: the two degrees taken for each other most often, until ten in a row are right (ADR-043). */
    TIR_DUEL(ModeId.TIR, "Дуэль", GuitarNeed.NONE, "0.1"),
    /** O-50: a chord on a pitch of chance — which quality is it (ADR-048). */
    TIR_COLOR(ModeId.TIR, "Цвет аккорда", GuitarNeed.NONE, "0.1"),
    /** O-76: one loop of chords in three keys in a row, the same chord of it asked each time — the degree does not move, the key does (ADR-073). */
    TIR_LOOP3(ModeId.TIR, "Петля в трёх тональностях", GuitarNeed.NONE, "0.1"),
    /** O-78: the weekly control round — the same question over the drone and in a band-like texture, to see the gap between them (ADR-074). */
    TIR_TRANSFER(ModeId.TIR, "Проверка переноса", GuitarNeed.NONE, "0.1"),
    /** O-75: a chord over the tonic — what does it DO (home, away, tension), and then what is it made of (ADR-072). */
    TIR_LAYERS(ModeId.TIR, "Слои аккорда", GuitarNeed.NONE, "0.1"),
    /** O-51: a scale over the drone — which mode is it (ADR-049). */
    TIR_MODE(ModeId.TIR, "Какой лад?", GuitarNeed.NONE, "0.1"),
    /** O-46: a chain of notes over the drone that grows by one with every right answer (ADR-045). */
    TIR_SIMON(ModeId.TIR, "Саймон", GuitarNeed.NONE, "0.1"),
    /** O-43: the opening chords of a library song are played — whose are they (ADR-042). */
    TIR_SONG(ModeId.TIR, "Чья гармония?", GuitarNeed.NONE, "0.1"),
    /** O-45: three chords of a library song are played — where does it go next (ADR-044). */
    TIR_NEXT(ModeId.TIR, "Куда пойдёт?", GuitarNeed.NONE, "0.1"),
    TIR_RADIO(ModeId.TIR, "Радио", GuitarNeed.NONE, "0.3"),
    /** O-14: «спой квинту» — search with the voice until it is found (ADR-028). */
    KAMERTON_SEARCH(ModeId.KAMERTON, "Пропевка", GuitarNeed.NONE, "0.1"),
    KAMERTON_SING(ModeId.KAMERTON, "Спой ступень", GuitarNeed.NONE, "0.1"),
    KAMERTON_PREDICT(ModeId.KAMERTON, "Предскажи лад", GuitarNeed.REQUIRED, "0.2"),
    KAMERTON_DIGITS(ModeId.KAMERTON, "Угадай по цифрам", GuitarNeed.NONE, "0.2"),
    /** PR-01, step 1: a degree sounds — find it on the guitar (ADR-051). */
    PARROT_ECHO(ModeId.PARROT, "Попугай", GuitarNeed.REQUIRED, "0.1"),
    PARROT_CALL_RESPONSE(ModeId.PARROT, "Перекличка", GuitarNeed.REQUIRED, "0.3"),
    TAPER_STEPS(ModeId.TAPER, "Ходы", GuitarNeed.REQUIRED, "0.1"),
    TAPER_ORDER(ModeId.TAPER, "Песни из библиотеки", GuitarNeed.REQUIRED, "0.1"),
    TAPER_BLIND(ModeId.TAPER, "Вслепую", GuitarNeed.REQUIRED, "0.1"),
    /** F-08: a song and its section are named — recall the degrees, open the answer, grade yourself (ADR-046). */
    FORMULA_ALOUD(ModeId.TAPER, "Формула вслух", GuitarNeed.NONE, "0.1"),
    RECORDER_FREE(ModeId.RECORDER, "Магнитофон", GuitarNeed.REQUIRED, "0.3"),
    DIALOG(ModeId.RECORDER, "Диалог", GuitarNeed.REQUIRED, "0.3"),
}

/**
 * Level ladders, verbatim from the plan. Index 0 = level 1. Promotion rule: see [LevelRules].
 * The content of each level is generated by the variant's driver.
 */
object Levels {
    val HUNT = listOf(
        "6-я и 5-я струны",
        "4-я и 3-я струны",
        "весь гриф",
        "ступени от корня",
    )
    val TIR = listOf(
        "1–3–5 (+ гости из лада)",
        "все 7 ступеней",
        "хроматика (♭2, ♭3, ♯4, ♭6, ♭7)",
        "мелодии из 2–5 нот",
        "аккорды",
        "ходы из 4 аккордов",
        "лады: дорийский, фригийский, гармонический минор",
    )
    /** «Над аккордами» (O-08): the degree in the key, heard over chords of the key instead of the tonic. */
    val TIR_CHORDS = listOf(
        "над I · IV · V · vi",
        "над всеми аккордами лада",
    )

    /** «Аккорд над тоникой» (O-58): the three chords everything starts with, then the rest of the key one by one. */
    val TIR_ON_TONIC = listOf(
        "I · IV · V",
        "+ vi",
        "+ ii · iii",
        "все аккорды лада",
    )

    /** «Бас» (O-62): the same widening as over the tonic — the bass of a chord names it. */
    val TIR_BASS = TIR_ON_TONIC

    /** «Два аккорда» (O-62): from the tonic to the two main chords, then to any, then between the main ones, then all. */
    val TIR_TWO_CHORDS = listOf(
        "из I: к IV или V",
        "из I: к любому",
        "между I · IV · V · vi",
        "любой — в любой",
    )

    /** «Созвучие» (O-65): the clearest pairs first, then the fourth and the sixths, then all; then all in any register. */
    val TIR_DYAD = listOf(
        "терции, квинта, октава",
        "+ кварта и сексты",
        "все созвучия",
        "все, в любом регистре",
    )

    /** «Под нотой» (O-15): which chord of the key is under the shown note. */
    val TIR_UNDER = listOf(
        "I · IV · V · vi",
        "все аккорды лада",
    )

    /** «Чья гармония?» (O-43): the written openings first, then the titles alone. */
    val TIR_SONG = listOf(
        "разные начала",
        "похожие начала",
        "только названия",
    )

    /** «Цвет аккорда» (O-50): the two colours first, then the other triads, the sevenths, and all of them. */
    val TIR_COLOR = listOf("мажор или минор", "все трезвучия", "септаккорды", "всё вместе")

    /** «Петля в трёх тональностях» (O-76): the four open keys and five chords, then any key, then minor loops and the whole key. */
    val TIR_LOOP3 = listOf("открытые тональности: C G D A", "любые тональности", "минор и все аккорды")

    /** «Проверка переноса» (O-78): one level — a control, not a ladder. */
    val TIR_TRANSFER = listOf("раз в неделю: дрон и живая фактура")

    /** «Слои аккорда» (O-75): the job first, on triads of a major key; then sevenths and minor; then the job and the colour of one chord. */
    val TIR_LAYERS = listOf("что делает: дом, уход, напряжение", "+ септаккорды и минор", "два слоя: что делает, потом из чего")

    /** «Какой лад?» (O-51): the two colours, then the modes that differ from them by one degree. */
    val TIR_MODE = listOf("мажор или минор", "+ дорийский, миксолидийский", "+ фригийский, лидийский", "+ гармонический минор")

    /**
     * «Саймон» (O-46, O-59): the five steady degrees, slowly and from the tonic; the whole scale with leaps; then without
     * the tonic first and faster — the ear that thinks «выше, чем была» has to hold the notes against home instead.
     */
    val TIR_SIMON = listOf("пять ступеней, не спеша", "все семь, с прыжками", "без опоры, быстро")

    /** «Куда пойдёт?» (O-45): with the chords written and four answers, then by ear on the whole pad. */
    val TIR_NEXT = listOf("ступени видны", "на слух")

    /** Taper «Ходы»: progressions from the most banal up (see [StepsLadder]). */
    val STEPS = listOf(
        "два аккорда: I–V, I–IV",
        "три аккорда: I–IV–V, ii–V–I",
        "четыре аккорда: поп-квадраты",
        "минор: i–iv, i–v, андалузская",
        "джаз: септаккорды, ii–V–I",
    )
    val KAMERTON = listOf("ступени от тоники", "интервалы от любой ступени", "мелодии из 3–8 нот")

    /** «Пропевка» (O-14): the steady degrees first, then the whole mode. */
    val KAMERTON_SEARCH = listOf("опоры: 1, 3, 5", "все ступени лада")
    /** «Попугай», step 1 (ADR-051): one note at a time; the phrases of [PARROT] come after it. */
    val ECHO = listOf("одна нота: 1, 3, 5", "одна нота: весь лад")
    val PARROT = listOf("шаги в одной позиции", "скачки", "смена позиции", "6 нот", "в темп с бэкингом")

    fun of(mode: ModeId): List<String> = when (mode) {
        ModeId.HUNT -> HUNT
        ModeId.TIR -> TIR
        ModeId.KAMERTON -> KAMERTON
        ModeId.PARROT -> PARROT
        ModeId.TAPER -> STEPS
        ModeId.RECORDER -> emptyList()
    }
}

/**
 * Promotion: over the last [Pace.window] attempts at the current level, accuracy ≥ [MIN_ACCURACY] and
 * median latency ≤ the variant's target, on at least [Pace.minDays] different days. Demotion never happens automatically (no punishment
 * loops); the user can pick any unlocked level.
 */
object LevelRules {
    const val WINDOW = 30
    const val MIN_ACCURACY = 0.9
    const val DAY_MS = 86_400_000L

    /** How much evidence a promotion needs: the last [window] answers, spread over at least [minDays] days. */
    data class Pace(val window: Int = WINDOW, val minDays: Int = 1)

    val QUICK = Pace()

    /**
     * O-27 (ADR-037): two clean rounds in one evening are luck or a good day, not a skill. The Tir climbs on
     * 60 answers from at least three different days.
     */
    val SLOW = Pace(window = 60, minDays = 3)

    /** Accurate and fast enough over the last [window] answers — on whatever days they were given. */
    fun steady(recent: List<AttemptRecord>, targetMedianLatencyMs: Long, window: Int = WINDOW): Boolean {
        if (recent.size < window) return false
        val last = recent.takeLast(window)
        val acc = last.count { it.correct }.toDouble() / last.size
        val median = last.map { it.latencyMs }.sorted()[last.size / 2]
        return acc >= MIN_ACCURACY && median <= targetMedianLatencyMs
    }

    /** [dayOf] turns an attempt's epoch ms into a day number (the caller knows the user's zone and day start). */
    fun shouldPromote(
        recent: List<AttemptRecord>,
        targetMedianLatencyMs: Long,
        pace: Pace = QUICK,
        dayOf: (Long) -> Long = { it.floorDiv(DAY_MS) },
    ): Boolean = steady(recent, targetMedianLatencyMs, pace.window) &&
        recent.takeLast(pace.window).map { dayOf(it.atEpochMs) }.distinct().size >= pace.minDays
}
