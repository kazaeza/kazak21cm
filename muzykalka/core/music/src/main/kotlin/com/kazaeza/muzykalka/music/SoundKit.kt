package com.kazaeza.muzykalka.music

/**
 * O-36: how the note that sings over the drone ([Voice.TONE]) sounds. [Voice.TONE] stays the role («the note
 * over the drone», ADR-017); this is the setting that says which instrument plays it. The game drivers know
 * nothing about it.
 */
enum class ToneTimbre {
    /**
     * O-39: «Стекло» — a choir of glass for the drone and a small bell with the breath of the same choir for the note,
     * both in one stereo hall. Unlike the others this is an instrument, not only a timbre: it also takes the drone.
     */
    GLASS,

    /** The 0.0.53 sound: three static harmonics and a clockwork vibrato. Kept so it can be had back. */
    CLASSIC,

    /** A rosewood bar over a tube: a knock of wood, then a clean note that dies by itself. */
    MARIMBA,

    /** A steel tine on a box: a «tink» and a long warm tone. */
    KALIMBA,

    /** A piano through felt: a dull hammer and a string that slowly breathes. */
    FELT,

    /** «Oo–oh» with the mouth nearly closed: scoops into the note, drifts, breathes. */
    HUM,
    ;

    /**
     * How long, in milliseconds, the microphone waits after the note ends before it listens again, or the tail is
     * taken for a sung note: the ring of a damped bar or string, or of the glass in its hall (which is the longest —
     * 0.8 s later it is 25–35 dB under the note, and the gate of the microphone does not hear that). 0 = it dies with the note.
     */
    val ringMs: Int get() = when (this) {
        GLASS -> 800
        MARIMBA, KALIMBA, FELT -> 400
        CLASSIC, HUM -> 0
    }

    /** True when the note keeps ringing after it ends ([ringMs] > 0). */
    val rings: Boolean get() = ringMs > 0

    /** True for the timbre that brings its own drone and hall ([GLASS]); the others sit over the classic drone. */
    val ownsDrone: Boolean get() = this == GLASS
}

/** O-36: a wooden tap on a button — wood on wood, minimal. Each is also played a little lower and softer for «back». */
enum class TapSound {
    /** A pair of sticks: 28 ms. */
    STICKS,

    /** A small wooden block: 44 ms, lower. */
    BLOCK,

    /** A short dry tick: 11 ms. */
    TICK,
}
