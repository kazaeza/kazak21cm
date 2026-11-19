package com.kazaeza.muzykalka.music

/**
 * O-67 (ADR-065): the three jobs a chord can have in a key — the meaning of a chord, before its name. Every chord of a
 * song is at home, has left home, or wants back; the answer screen shows which, and where the chord pulls.
 */
enum class ChordFunction(val ru: String, val line: String) {
    HOME("Дом", "Покой: можно остаться, можно уйти куда угодно."),
    AWAY("Уход", "Шаг из дома: простор, движение, ещё не тревога."),
    TENSION("Напряжение", "Хочет обратно: висит, пока не разрешится."),
    ;

    companion object {
        /** The job of [chord] in [mode], by its root above the tonic and the third it carries. */
        fun of(chord: ChordDegree, mode: Mode): ChordFunction {
            val set = chord.quality.pitchClassSet
            val major = 4 in set
            val minorKey = mode.hasMinorThird
            return when (chord.root.semitones) {
                0 -> HOME
                2 -> if (major) TENSION else AWAY
                3 -> if (minorKey) HOME else AWAY
                4 -> if (major) TENSION else HOME
                5, 1, 8 -> AWAY
                9 -> if (minorKey) AWAY else HOME
                10 -> AWAY
                else -> TENSION // 5th, 7th, the tritone
            }
        }

        /**
         * Where [chord] most often goes next in [mode]: tension — to the chord it resolves to, a step away — on to
         * the tension or home, home — nowhere in particular (an empty list).
         */
        fun pullsTo(chord: ChordDegree, mode: Mode): List<ChordDegree> {
            val home = ChordDegree.parse(if (mode.hasMinorThird) "i" else "I")
            return when (of(chord, mode)) {
                HOME -> emptyList()
                AWAY -> listOf(ChordDegree.parse("V"), home)
                TENSION -> when (chord.root.semitones) {
                    2 -> listOf(ChordDegree.parse("V"))
                    4 -> listOf(ChordDegree.parse(if (mode.hasMinorThird) "♭VI" else "vi"))
                    else -> listOf(home)
                }
            }
        }
    }
}
