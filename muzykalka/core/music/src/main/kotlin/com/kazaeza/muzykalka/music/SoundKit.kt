package com.kazaeza.muzykalka.music

/**
 * O-36: how the note that sings over the drone ([Voice.TONE]) sounds. [Voice.TONE] stays the role («the note
 * over the drone», ADR-017); this is the setting that says which instrument plays it. The game drivers know
 * nothing about it.
 */
enum class ToneTimbre {
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
     * True when the note keeps ringing after it ends (a damped bar or string): the microphone must wait longer
     * before it listens again, or the tail is taken for a sung note.
     */
    val rings: Boolean get() = this == MARIMBA || this == KALIMBA || this == FELT
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
