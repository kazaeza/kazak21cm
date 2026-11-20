package com.kazaeza.muzykalka.music

/**
 * O-73 (ADR-070): a chord as a hand holds it on the neck — the fret of every string from the lowest up, `null` where the
 * string is not struck. [frets] has one entry per string of the tuning.
 */
data class GuitarShape(val frets: List<Int?>) {
    /** The sounding notes, lowest string first. */
    fun notes(tuning: Tuning): List<MidiNote> {
        val open = tuning.openNotes.sortedBy { it.value }
        return frets.mapIndexedNotNull { i, f -> f?.let { open[i] + it } }
    }
}

/**
 * O-73 (ADR-070): the shapes a guitarist actually plays. Before, a strummed chord was a close triad stacked from G2 —
 * three or four strings inside an octave and a half, which no guitar plays and which reads as «midi». A real chord is five
 * or six strings over two octaves with the root and the fifth doubled, and it is that spread, with the open strings
 * ringing among the fretted ones, that makes a guitar chord a guitar chord.
 *
 * The shapes are not a table. They are searched: every way of putting the hand on the neck near some fret (a span of four
 * frets, the open strings too while the hand is low), kept only if the lowest string struck is the bass, every string
 * struck is a tone of the chord, all its tones are there (the fifth may go from a chord of four or more tones), and the
 * fingers suffice (a barre is one finger). The cheapest wins: low on the neck, many strings, few fingers, no doubled third.
 * That is what puts C at «x32010», F as the barre «133211» and G as «320003» without anyone writing them down.
 */
object GuitarVoicings {
    /** The highest fret the hand may start from (it then reaches [SPAN] frets above). */
    private const val MAX_ANCHOR = 9

    /** The frets above the first pressed one the hand reaches without moving. */
    private const val SPAN = 3

    /** Open strings may ring only while the hand is low on the neck. */
    private const val OPEN_TILL = 4

    private const val FINGERS = 4

    private val cache = HashMap<Triple<ChordQuality, Int, Int>, GuitarShape?>()

    /**
     * The shape of [chord] in [key] on a guitar in [tuning] (standard by default), or null where no hand holds it. The root is
     * the lowest string, or the slash bass when the chord has one.
     */
    fun shape(chord: ChordDegree, key: Key, tuning: Tuning = Tuning.STANDARD): GuitarShape? {
        val rootPc = key.pitchClassOf(chord.root).value
        val bassPc = key.pitchClassOf(chord.bass ?: chord.root).value
        if (tuning != Tuning.STANDARD) return search(chord.quality, rootPc, bassPc, tuning)
        return synchronized(cache) { cache.getOrPut(Triple(chord.quality, rootPc, bassPc)) { search(chord.quality, rootPc, bassPc, tuning) } }
    }

    private fun search(quality: ChordQuality, rootPc: Int, bassPc: Int, tuning: Tuning): GuitarShape? {
        val tones = quality.intervals.map { Math.floorMod(rootPc + it, 12) }.distinct()
        val fifth = Math.floorMod(rootPc + 7, 12)
        // a chord of four tones or more may do without its fifth: it is the first thing a hand drops
        val required = (if (tones.size >= 4) tones.filter { it != fifth } else tones).toSet() + bassPc
        val allowed = tones.toSet() + bassPc
        val open = tuning.openNotes.map { it.value }.sorted()
        val search = Search(open, rootPc, fifth, bassPc, required, allowed, if (tones.size <= 2) 3 else 4)
        for (anchor in 1..MAX_ANCHOR) search.run(anchor)
        return search.best?.let { shape -> GuitarShape(shape.map { if (it < 0) null else it }) }
    }

    private class Search(
        val open: List<Int>,
        val rootPc: Int,
        val fifth: Int,
        val bassPc: Int,
        val required: Set<Int>,
        val allowed: Set<Int>,
        val minStrings: Int,
    ) {
        private val n = open.size
        private val frets = IntArray(n) { -1 }
        private var anchor = 1
        var best: List<Int>? = null
        private var bestCost = Double.MAX_VALUE

        fun run(anchor: Int) {
            this.anchor = anchor
            descend(0, started = false)
        }

        private fun pcOf(string: Int, fret: Int) = (open[string] + fret) % 12

        private fun descend(string: Int, started: Boolean) {
            if (string == n) {
                judge()
                return
            }
            if (started) {
                // the hand stops somewhere: the rest of the strings are not struck
                for (s in string until n) frets[s] = -1
                judge()
            } else {
                frets[string] = -1
                descend(string + 1, started = false)
            }
            val options = if (anchor <= OPEN_TILL) intArrayOf(0, anchor, anchor + 1, anchor + 2, anchor + 3) else intArrayOf(anchor, anchor + 1, anchor + 2, anchor + 3)
            for (f in options) {
                val pc = pcOf(string, f)
                if (pc !in allowed) continue
                if (!started && pc != bassPc) continue
                frets[string] = f
                descend(string + 1, started = true)
            }
            frets[string] = -1
        }

        private fun judge() {
            var played = 0
            val present = HashMap<Int, Int>()
            var pressed = false
            for (s in 0 until n) {
                val f = frets[s]
                if (f < 0) continue
                played++
                present.merge(pcOf(s, f), 1, Int::plus)
                if (f == anchor) pressed = true
            }
            if (played < minStrings || !pressed || !present.keys.containsAll(required)) return

            val atAnchor = (0 until n).filter { frets[it] == anchor }
            val barre = atAnchor.size >= 2 && (atAnchor.first()..atAnchor.last()).all { frets[it] >= anchor }
            val fingers = (if (barre) 1 else atAnchor.size) + (0 until n).count { frets[it] > anchor }
            if (fingers > FINGERS) return

            val firstString = (0 until n).first { frets[it] >= 0 }
            val openRinging = (0 until n).count { frets[it] == 0 }
            val extraCopies = present.entries.filter { it.key != rootPc && it.key != fifth }.sumOf { it.value - 1 }
            val pitches = (0 until n).filter { frets[it] >= 0 }.map { open[it] + frets[it] }
            val unisons = pitches.size - pitches.toSet().size
            val pressedFrets = (0 until n).map { frets[it] }.filter { it > 0 }
            val reach = (pressedFrets.maxOrNull() ?: 0) - (pressedFrets.minOrNull() ?: 0)
            val cost = 0.35 * anchor +
                1.0 * (n - played) +
                1.5 * unisons +
                0.45 * fingers +
                (if (barre) 0.8 else 0.0) +
                0.3 * extraCopies +
                0.25 * reach -
                0.15 * openRinging +
                (if (firstString > 1) 0.6 else 0.0)
            if (cost < bestCost - 1e-9) {
                bestCost = cost
                best = frets.toList()
            }
        }
    }
}
