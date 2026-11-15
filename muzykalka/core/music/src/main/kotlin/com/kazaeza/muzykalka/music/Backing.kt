package com.kazaeza.muzykalka.music

/**
 * One chord of a backing as it is played: which bar of which section (in the order the song is performed) it is in,
 * when it starts and how long it lasts, in beats from the start of the song.
 */
data class BackingStep(
    /** Index into the song's performance order (the form, not the list of sections). */
    val part: Int,
    val label: String,
    /** The bar inside the section, from 0. */
    val bar: Int,
    val chord: ChordDegree,
    val startBeat: Double,
    val beats: Double,
)

/**
 * X-03 «Под бэкинг» (ADR-052): a song of the library laid out in time — every bar of the form [BEATS_PER_BAR] beats
 * long, the chords of a bar sharing it equally. Pure: the plan and the score, no clock.
 */
object Backing {
    const val BEATS_PER_BAR = 4

    /** The whole song once through its form. */
    fun steps(song: Song): List<BackingStep> {
        val out = ArrayList<BackingStep>()
        var beat = 0.0
        song.performanceOrder.forEachIndexed { part, section ->
            section.bars.forEachIndexed { bar, b ->
                val each = BEATS_PER_BAR.toDouble() / b.chords.size
                b.chords.forEach { chord ->
                    out += BackingStep(part, section.label, bar, chord, beat, each)
                    beat += each
                }
            }
        }
        return out
    }

    fun lengthBeats(steps: List<BackingStep>): Double = steps.lastOrNull()?.let { it.startBeat + it.beats } ?: 0.0

    /** The step that sounds at [beat]; the last one after the end, the first before the start. */
    fun stepAt(steps: List<BackingStep>, beat: Double): Int = steps.indexOfLast { it.startBeat <= beat }.coerceAtLeast(0)

    /**
     * The backing itself: the chord held on the piano for as long as it lasts, the bass on every beat — enough to
     * play over, quiet enough to hear oneself.
     */
    fun score(steps: List<BackingStep>, key: Key, tempoBpm: Double, arranger: Arranger = Arranger()): Score {
        val notes = ArrayList<ScoreNote>()
        for (s in steps) {
            arranger.voicing(s.chord, key).forEach { notes += ScoreNote(s.startBeat, s.beats * 0.97, Voice.PIANO, it, 0.55f) }
            val bass = arranger.bassNote(s.chord, key)
            var b = 0.0
            while (b < s.beats - 1e-9) {
                notes += ScoreNote(s.startBeat + b, minOf(0.9, s.beats - b), Voice.BASS, bass, if (b == 0.0) 0.8f else 0.55f)
                b += 1.0
            }
        }
        return Score(tempoBpm, notes)
    }
}
