package com.kazaeza.muzykalka.music

/**
 * O-82 (ADR-078): what strikes a chord where the app plays one by itself — the tap on a chord of a song sheet, of the
 * position map and of the steps, and the band of the transfer check and of the bass line. The piano is the default:
 * it was the user's pick («пианинка красиво звучит»); the guitar stays as a choice on the settings page.
 * The loops over the tonic are played on the piano whatever is chosen (their chords carry no bass of their own).
 */
enum class ChordInstrument(val ru: String) {
    PIANO("пианино"),
    GUITAR("гитара"),
}
