package com.kazaeza.muzykalka.game

import com.kazaeza.muzykalka.music.Accidental
import com.kazaeza.muzykalka.music.Arranger
import com.kazaeza.muzykalka.music.Degree
import com.kazaeza.muzykalka.music.KeyPicker
import com.kazaeza.muzykalka.music.RuNames
import com.kazaeza.muzykalka.music.Voice
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

/** O-16 «Угадай про себя» (ADR-030). */
class ListenDeckTest {
    private fun deck(scope: ListenScope, seed: Int = 1) = ListenDeck(KeyPicker(Random(seed)), Random(seed), scope)

    @Test
    fun `over the tonic every degree of the mode is heard, the key holds for a block`() {
        val d = deck(ListenScope.DRONE)
        val items = List(TirDriver.KEY_BLOCK * 3) { d.next() }
        items.chunked(TirDriver.KEY_BLOCK).forEach { block ->
            assertTrue(block.first().newKey, "a block opens with the scale")
            assertTrue(block.drop(1).none { it.newKey })
            assertTrue(block.all { it.key == block.first().key })
            block.forEach { i ->
                assertNull(i.over)
                assertEquals(Arranger().drone(i.key), i.drone)
                assertTrue(i.key.mode.degrees.any { it sameSoundAs i.degree })
            }
        }
    }

    @Test
    fun `over chords the chord changes under the note and is part of the item`() {
        val d = deck(ListenScope.CHORDS, 4)
        val items = List(TirDriver.KEY_BLOCK) { d.next() }
        val allowed = TirDriver.CHORD_STEPS_L1.map { com.kazaeza.muzykalka.music.Harmony.diatonicChord(items.first().key.mode, it) }
        items.forEach { i ->
            val chord = requireNotNull(i.over)
            assertTrue(chord in allowed)
            assertEquals(Arranger().chordDrone(chord, i.key), i.drone)
        }
        assertTrue(items.map { it.over }.distinct().size > 1, "the harmony moves")
        val all = List(40) { deck(ListenScope.ALL_CHORDS, 9).next() }
        assertTrue(all.all { it.over != null })
    }

    @Test
    fun `the answer is the note walking home, said aloud`() {
        val d = deck(ListenScope.CHORDS, 2)
        repeat(20) {
            val i = d.next()
            assertTrue(i.answer.notes.isNotEmpty())
            assertTrue(i.answer.notes.all { it.voice == Voice.TONE }, "one soft timbre over the drone")
            assertEquals(RuNames.spokenDegree(i.degree), i.spoken)
            assertTrue(i.question.notes.isNotEmpty())
        }
    }

    @Test
    fun `degrees are read aloud as numbers`() {
        assertEquals("пять", RuNames.spokenDegree(Degree.FIVE))
        assertEquals("один", RuNames.spokenDegree(Degree.ONE))
        assertEquals("бемоль три", RuNames.spokenDegree(Degree.FLAT_THREE))
        assertEquals("диез четыре", RuNames.spokenDegree(Degree(4, Accidental.SHARP)))
        assertFalse(ListenDeck.DELAYS_MS.isEmpty())
        assertTrue(ListenDeck.DEFAULT_DELAY_MS in ListenDeck.DELAYS_MS)
    }
}
