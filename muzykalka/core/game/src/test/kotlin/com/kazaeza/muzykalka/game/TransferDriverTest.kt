package com.kazaeza.muzykalka.game

import com.kazaeza.muzykalka.music.Arranger
import com.kazaeza.muzykalka.music.ChordInstrument
import com.kazaeza.muzykalka.music.Voice
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

/** O-78 «Проверка переноса» (ADR-074): two textures alternate, nothing of it is planned or reviewed. */
class TransferDriverTest {
    private val guitar = Arranger(chordInstrument = ChordInstrument.GUITAR)

    private fun tasks(n: Int, seed: Int = 1, arranger: Arranger = guitar): List<LoopTask> {
        val d = TransferDriver(arranger = arranger)
        return List(n) { d.nextTask(DriverContext(Random(seed * 100 + it), 1, emptyList())) }
    }

    @Test
    fun `the two textures alternate, so six questions are three and three`() {
        for (seed in 1..6) {
            val ts = tasks(6, seed)
            assertEquals(3, ts.count { it.texture == Texture.SYNTH })
            assertEquals(3, ts.count { it.texture == Texture.REAL })
            ts.zipWithNext().forEach { (a, b) -> assertTrue(a.texture != b.texture) }
        }
    }

    @Test
    fun `the synth texture is the trainer's own - a hum, a scale, pad chords with the tonic held`() {
        val t = tasks(6).first { it.texture == Texture.SYNTH }
        assertTrue(t.drone!!.isNotEmpty())
        assertNotNull(t.intro)
        assertNotNull(t.introDrone)
        assertTrue(t.question.notes.none { it.voice == Voice.GUITAR || it.voice == Voice.KICK })
        assertTrue(t.itemKey.startsWith("transfer:synth:"))
    }

    @Test
    fun `the real texture is a band - guitar, bass and drums, no hum, the key set by a strummed cadence`() {
        val t = tasks(6).first { it.texture == Texture.REAL }
        assertNull(t.drone, "no drone")
        assertNull(t.introDrone)
        val voices = t.question.notes.map { it.voice }.toSet()
        assertTrue(Voice.GUITAR in voices && Voice.BASS in voices && Voice.KICK in voices && Voice.SNARE in voices && Voice.HAT in voices)
        assertTrue(Voice.TONE !in voices && Voice.PIANO !in voices)
        assertTrue(t.intro!!.notes.any { it.voice == Voice.GUITAR }, "the cadence is in the same texture")
        assertEquals(2 * t.loop.size * TransferDriver.REAL_BEATS, t.question.lengthBeats, 1.0)
        assertTrue(t.itemKey.startsWith("transfer:real:"))
    }

    @Test
    fun `O-82 - with the piano chosen the band comps on the piano, the bass and the drums stay`() {
        val t = tasks(6, arranger = Arranger(chordInstrument = ChordInstrument.PIANO)).first { it.texture == Texture.REAL }
        val voices = t.question.notes.map { it.voice }.toSet()
        assertTrue(Voice.PIANO in voices && Voice.BASS in voices && Voice.KICK in voices && Voice.SNARE in voices && Voice.HAT in voices)
        assertTrue(Voice.GUITAR !in voices && Voice.TONE !in voices)
        assertNull(t.drone, "still no hum")
        val intro = requireNotNull(t.intro)
        assertTrue(intro.notes.any { it.voice == Voice.PIANO } && intro.notes.none { it.voice == Voice.GUITAR }, "the cadence is in the same texture")
    }

    @Test
    fun `a new key every question, loops the other modes do not train, the asked place is not the first`() {
        val ts = tasks(24)
        ts.zipWithNext().forEach { (a, b) -> assertTrue(a.key.tonic.pitchClass != b.key.tonic.pitchClass) }
        val trained = (1..3).flatMap { l -> listOf(com.kazaeza.muzykalka.music.Mode.MAJOR, com.kazaeza.muzykalka.music.Mode.MINOR).flatMap { Loop3Driver.loops(it, l) } }.toSet()
        assertTrue(TransferDriver.LOOPS.none { it in trained }, "the loops of the check are not the loops of the lessons")
        ts.forEach { assertTrue(it.asked in 1..3 && it.answer in it.choices) }
    }

    @Test
    fun `answers are judged as in the loops, a miss hears the loop in the same texture, and it is not planned or reviewed`() {
        val d = TransferDriver(arranger = guitar)
        repeat(8) { seed ->
            val t = d.nextTask(DriverContext(Random(seed), 1, emptyList()))
            assertTrue(d.evaluate(t, Answer.ChordChoice(t.answer)).correct)
            val other = t.choices.first { it.root.semitones != t.answer.root.semitones }
            val wrong = d.evaluate(t, Answer.ChordChoice(other))
            assertFalse(wrong.correct)
            assertNotNull(wrong.correction, "rule 5")
            assertEquals(t.texture == Texture.REAL, wrong.correction!!.notes.any { it.voice == Voice.GUITAR })
            assertNull(TirDriver.reviewKey(t.itemKey))
        }
        // not weighted by what was answered before: the same history and a different one give the same question
        val history = List(40) { AttemptRecord(Variant.TIR_TRANSFER, 1, "transfer:synth:IV", "IV", "V", false, 900, null, 0, "C", 0) }
        val a = TransferDriver().nextTask(DriverContext(Random(5), 1, emptyList()))
        val b = TransferDriver().nextTask(DriverContext(Random(5), 1, history))
        assertEquals(a.loop, b.loop)
        assertEquals(a.asked, b.asked)
    }

    private fun rec(texture: String, correct: Boolean, at: Long) =
        AttemptRecord(Variant.TIR_TRANSFER, 1, "transfer:$texture:V", "V", if (correct) "V" else "IV", correct, 1000, null, 0, "C", at)

    @Test
    fun `the gap is accuracy over the drone minus accuracy in the band, by day, and is trusted only on enough answers`() {
        val day = 86_400_000L
        val attempts = buildList {
            repeat(10) { add(rec("synth", it < 9, it * 10L)) }
            repeat(10) { add(rec("real", it < 5, 20 + it * 10L)) }
            repeat(4) { add(rec("synth", true, day + it * 10L)) }
            repeat(4) { add(rec("real", it < 2, day + 100 + it * 10L)) }
            add(AttemptRecord(Variant.TIR_CLASSIC, 1, "tir:L1:5", "5", "5", true, 900, null, 0, "C", 5))
        }
        val t = Metrics.transfer(attempts) { (it / day).toInt() }
        assertEquals(14, t.synth!!.attempts)
        assertEquals(14, t.real!!.attempts)
        assertEquals(13.0 / 14 - 7.0 / 14, t.gap!!, 1e-9)
        assertEquals(listOf(0 to 0.9 - 0.5, 1 to 1.0 - 0.5), t.daily.map { it.first to it.second })
        assertEquals(1, t.lastDay)
        assertTrue(t.trusted)
        assertFalse(Metrics.transfer(attempts.take(5)) { 0 }.trusted)
        assertNull(Metrics.transfer(emptyList()) { 0 }.gap)
    }
}
