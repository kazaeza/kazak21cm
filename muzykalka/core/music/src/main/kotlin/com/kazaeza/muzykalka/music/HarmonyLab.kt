package com.kazaeza.muzykalka.music

/**
 * O-48 «Созвучия» (ADR-047): what two sounds are to each other beyond any key — the body of an interval, before its
 * role in a scale. [ratio] is the just ratio of the upper to the lower sound, [word] and [line] the feeling to hang
 * on it, [rough] how restless it is (0 = one sound, 1 = the tightest rub).
 */
data class DyadVibe(val semitones: Int, val ratio: Pair<Int, Int>, val word: String, val line: String, val rough: Float) {
    /**
     * Why it sounds the way it does: the first harmonics the two sounds share. For 3:2 the lower sound's 3rd harmonic
     * IS the upper sound's 2nd — they meet at once; for 16:15 nothing meets until the 16th, and all before it rubs.
     */
    val meeting: String
        get() = when {
            ratio.first == ratio.second -> "Все гармоники общие: это один звук."
            ratio.second == 1 -> "Каждая гармоника верхнего звука уже есть в нижнем: ${ratio.first}-я нижнего — его основной тон."
            ratio.first <= EARLY -> "Встречаются рано: ${ratio.first}-я гармоника нижнего звука — это ${ratio.second}-я верхнего. Чем раньше встреча, тем глаже."
            else -> "Встречаются поздно: только ${ratio.first}-я гармоника нижнего совпадает с ${ratio.second}-й верхнего, а до того они трутся."
        }

    /**
     * O-52: the harmonics of the two sounds that are one and the same — pairs (n-th of the lower, k-th of the upper) up
     * to the [limit]-th harmonic of the lower sound. A fifth: (3, 2), (6, 4), (9, 6)…
     */
    fun meetings(limit: Int = HARMONICS): List<Pair<Int, Int>> {
        val (p, q) = ratio
        return (1..limit).mapNotNull { n -> if (n * q % p == 0) n to n * q / p else null }
    }

    /**
     * O-52: the harmonics that almost meet and so rub — pairs (n, k) closer than [RUB] of their pitch but not the
     * same, among the first [limit] harmonics of the lower sound (the loud ones: higher up everything is close to
     * something, and too quiet to matter). A fifth has none, a major third one, a minor second nothing else.
     */
    fun rubs(limit: Int = RUB_HARMONICS): List<Pair<Int, Int>> {
        val (p, q) = ratio
        val out = ArrayList<Pair<Int, Int>>()
        for (n in 1..limit) {
            var k = 1
            while (k * p <= (limit + 1) * q) {
                // the k-th harmonic of the upper sound stands at k·p/q of the lower fundamental
                val gap = kotlin.math.abs(k * p - n * q).toDouble() / (n * q)
                if (gap > 0.0 && gap < RUB) out += n to k
                k++
            }
        }
        return out
    }

    companion object {
        private const val EARLY = 6

        /** How many harmonics of the lower sound the picture shows. */
        const val HARMONICS = 16

        /** The harmonics loud enough for their rubbing to be heard. */
        const val RUB_HARMONICS = 6

        /** Two harmonics closer than this share of their pitch beat against each other (a just semitone is 1/15). */
        const val RUB = 0.07
    }
}

object Dyads {
    /** The octave: asked for with [of] 12. Inside the octave [Overtones.RATIO] gives the ratio. */
    const val OCTAVE = 12

    private val WORDS: List<Pair<String, String>> = listOf(
        "слияние" to "Один и тот же звук: спорить не с кем.",
        "трение" to "Вплотную: звуки бьются друг о друга. Теснее не бывает.",
        "шероховатость" to "Рядом, но не вместе: лёгкая рябь, хочется разойтись.",
        "тёплая тень" to "Мягко и сумрачно. Это она делает аккорд минорным.",
        "тёплый свет" to "Мягко и ясно. Это она делает аккорд мажорным.",
        "открытость" to "Чисто и чуть подвешенно: вопрос без нажима.",
        "беспокойство" to "Ровно пол-октавы: ни туда, ни сюда. Просит выхода — на этом держится доминанта.",
        "каркас" to "Пусто и прочно: звуки почти сливаются. Скелет любого аккорда.",
        "горчинка" to "Широко и с тоской: большая терция, перевёрнутая вверх дном.",
        "простор" to "Широко и светло: малая терция, перевёрнутая вверх дном.",
        "терпкость" to "Не режет, но и не стоит: тянет вниз. С ней аккорд становится септаккордом.",
        "острота" to "Полтона до октавы: режет и рвётся вверх.",
    )

    /** The interval of [semitones] (0..12) between two sounds. */
    fun of(semitones: Int): DyadVibe {
        require(semitones in 0..OCTAVE) { "An interval of the lab is 0..12 semitones, got $semitones" }
        if (semitones == OCTAVE) return DyadVibe(OCTAVE, 2 to 1, "тот же звук, выше", "Вдвое чаще — и ухо слышит ту же ноту: с октавы всё начинается заново.", 0f)
        val (word, line) = WORDS[semitones]
        return DyadVibe(semitones, Overtones.ratio(semitones), word, line, Overtones.tension(semitones))
    }
}

/** O-48: the middle voice of the lab's chord — the third, or what stands in its place. */
enum class LabThird(val degree: Degree?) {
    NONE(null), SUS2(Degree(2)), MINOR(Degree(3, Accidental.FLAT)), MAJOR(Degree(3)), SUS4(Degree(4))
}

enum class LabFifth(val degree: Degree) {
    DIMINISHED(Degree(5, Accidental.FLAT)), PERFECT(Degree(5)), AUGMENTED(Degree(5, Accidental.SHARP))
}

/** The voice on top: nothing, the sixth, or one of the sevenths. */
enum class LabTop(val degree: Degree?) {
    NONE(null), SIXTH(Degree(6)), MINOR7(Degree(7, Accidental.FLAT)), MAJOR7(Degree(7))
}

/** Where a chord of the lab wants to go: [line] says it in words; [target] is the chord it goes to, null when it stands. */
data class LabPull(val line: String, val target: List<Int>?)

/**
 * O-48: a chord put together by hand — the root always sounds, the third, the fifth and the top voice are switched
 * one at a time. At most four sounds, as the drone bank holds. Pure: semitones above the root, no key.
 */
data class LabChord(val third: LabThird = LabThird.MAJOR, val fifth: LabFifth = LabFifth.PERFECT, val top: LabTop = LabTop.NONE) {
    /** The tones as degrees above the root, low to high: 1, ♭3, 5, ♭7. */
    val tones: List<Degree> get() = listOfNotNull(Degree.ONE, third.degree, fifth.degree, top.degree)

    /** Semitones above the root, low to high. */
    val intervals: List<Int> get() = tones.map { it.semitones }

    /** The chord's name when it has one (a sus2 with a ♯5 has none: the tones speak for themselves). */
    val quality: ChordQuality? get() = ChordQuality.fromPitchClassSet(intervals.toSet())

    /**
     * O-52: the steps between neighbouring tones, in semitones — the chord as a stack: 4 and 3 for a major triad,
     * 3 and 4 for a minor one. What the ear takes for «major» is the order of the two thirds.
     */
    val stack: List<Int> get() = intervals.zipWithNext { a, b -> b - a }

    /** How restless the whole chord is: the mean roughness of every pair of its tones, 0..1. */
    val rough: Float
        get() = intervals.flatMapIndexed { i, a -> intervals.drop(i + 1).map { b -> Overtones.tension(b - a) } }.let { if (it.isEmpty()) 0f else it.average().toFloat() }

    /** The tritone inside, if any: the pair of tones six semitones apart — the restless core of a chord. */
    val tritone: Pair<Degree, Degree>?
        get() = tones.flatMap { a -> tones.map { b -> a to b } }.firstOrNull { (a, b) -> b.semitones - a.semitones == 6 }

    /**
     * Where it pulls. A hanging fourth falls into the third, a second rises to it; a major third with a minor
     * seventh is a dominant and goes to the chord a fourth above (the tritone opens: 3 up a half step, ♭7 down);
     * a diminished triad slides up a half step; an augmented fifth goes on up. The rest stands — its pull comes from
     * its place in a key, not from its body.
     */
    val pull: LabPull
        get() = when {
            third == LabThird.SUS4 -> LabPull("Кварта висит над терцией и хочет упасть в неё: 4 → 3.", copy(third = LabThird.MAJOR).intervals)
            third == LabThird.SUS2 -> LabPull("Секунда тянется вверх, в терцию: 2 → 3.", copy(third = LabThird.MAJOR).intervals)
            third == LabThird.MAJOR && top == LabTop.MINOR7 -> LabPull(
                "Внутри тритон — терция и малая септима. Терция уходит на полтона вверх, септима — на полтона вниз, и выходит аккорд квартой выше. Так доминанта возвращается домой.",
                listOf(5, 9, 12),
            )
            fifth == LabFifth.DIMINISHED && third == LabThird.MINOR -> LabPull(
                "Уменьшённый не стоит на месте: голоса сползают на полтона вверх — в аккорд полутоном выше.",
                listOf(1, 5, 8),
            )
            fifth == LabFifth.AUGMENTED -> LabPull("Увеличенная квинта не держится: её тянет ещё на полтона вверх.", listOfNotNull(0, third.degree?.semitones, 9))
            top == LabTop.MAJOR7 -> LabPull("Большая септима остра, но аккорд стоит: это краска, а не тяга.", null)
            top == LabTop.MINOR7 -> LabPull("Мягкий и неустойчивый совсем чуть-чуть: его обычно ведут в аккорд квартой выше, но сам он не просится.", null)
            else -> LabPull("Стоит и никуда не тянет. Тяга появится от места в ладу: тот же аккорд на V ступени запросится домой.", null)
        }
}

/** O-60: a chord of the lab with the name a musician gives it — one press puts all three voices in place. */
data class LabPreset(val ru: String, val chord: LabChord)

/** O-60: the chords the lab knows by name — the presets, and what a puzzle is made of. */
object LabPresets {
    val ALL: List<LabPreset> = listOf(
        LabPreset("мажор", LabChord(LabThird.MAJOR, LabFifth.PERFECT, LabTop.NONE)),
        LabPreset("минор", LabChord(LabThird.MINOR, LabFifth.PERFECT, LabTop.NONE)),
        LabPreset("sus4", LabChord(LabThird.SUS4, LabFifth.PERFECT, LabTop.NONE)),
        LabPreset("sus2", LabChord(LabThird.SUS2, LabFifth.PERFECT, LabTop.NONE)),
        LabPreset("доминантсептаккорд", LabChord(LabThird.MAJOR, LabFifth.PERFECT, LabTop.MINOR7)),
        LabPreset("минорный септ", LabChord(LabThird.MINOR, LabFifth.PERFECT, LabTop.MINOR7)),
        LabPreset("мажорный септ", LabChord(LabThird.MAJOR, LabFifth.PERFECT, LabTop.MAJOR7)),
        LabPreset("уменьшённый", LabChord(LabThird.MINOR, LabFifth.DIMINISHED, LabTop.NONE)),
        LabPreset("увеличенный", LabChord(LabThird.MAJOR, LabFifth.AUGMENTED, LabTop.NONE)),
    )

    /** The name of [chord] if it is one of the presets. */
    fun nameOf(chord: LabChord): String? = ALL.firstOrNull { it.chord == chord }?.ru

    /** The chords a puzzle can ask: the first of the list first, so that a beginner meets the plain ones. */
    fun puzzlePool(level: Int): List<LabChord> = ALL.take(if (level <= 1) 2 else if (level == 2) 5 else ALL.size).map { it.chord }
}

/** O-48: how the lab's sounds are laid out for the drone bank (at most four notes, low to high). */
object LabSound {
    /** The two sounds of an interval of [semitones] (0..12) above [lower]; a unison is one sound. */
    fun dyad(lower: MidiNote, semitones: Int): List<MidiNote> = listOf(lower, lower + semitones).distinct()

    /**
     * A chord given by [intervals] above [root] (semitones, low to high; the first is its own bass): the bass low,
     * the chord an octave above it. Four sounds at most — a four-note chord gives up the doubled bass.
     */
    fun chord(root: MidiNote, intervals: List<Int>): List<MidiNote> {
        val bass = root + intervals.first()
        val upper = intervals.map { root + 12 + it }
        return (listOf(bass) + if (intervals.size >= 4) upper.drop(1) else upper).distinct().sortedBy { it.value }
    }

    /**
     * O-49: [chord] of a key whose tonic is [tonic], held over that tonic — the home tone low, the triad above it. The
     * chords on the upper degrees come down an octave, so all seven sit in one register and only their place differs.
     */
    fun inKey(tonic: MidiNote, chord: ChordDegree): List<MidiNote> {
        val up = Math.floorMod(chord.root.semitones, 12)
        val base = tonic + 12 + if (up > 7) up - 12 else up
        return (listOf(tonic) + chord.quality.intervals.take(3).map { base + it }).distinct().sortedBy { it.value }
    }

    /** The other steps of [mode] (0 = I) whose chord has the same body as the chord on [step]. */
    fun sameBody(mode: Mode, step: Int): List<Int> {
        val quality = Harmony.diatonicChord(mode, step).quality
        return (0..6).filter { it != step && Harmony.diatonicChord(mode, it).quality == quality }
    }
}
