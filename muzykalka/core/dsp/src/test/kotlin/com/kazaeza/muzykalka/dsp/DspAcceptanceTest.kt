package com.kazaeza.muzykalka.dsp

import com.kazaeza.muzykalka.music.MidiNote
import com.kazaeza.muzykalka.music.centsBetween
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Acceptance tests written BEFORE the implementation — the spec for tickets M-02/M-03/M-04.
 * Do not weaken thresholds without updating docs/ROADMAP.md and saying why in the commit message.
 */
class DspAcceptanceTest {

    private val guitarRange = 36..88 // C2 (drop C) .. E6 (24th fret high E)

    private fun detectMiddle(signal: FloatArray, detector: PitchDetector = McLeodPitchDetector()): PitchEstimate? {
        val offset = (signal.size - detector.windowSize) / 2
        return detector.detect(signal, offset)
    }

    @Test
    fun `MPM - sines across the guitar range within 3 cents`() {
        for (n in guitarRange) {
            val hz = MidiNote(n).frequency()
            val est = requireNotNull(detectMiddle(TestSignals.sine(hz, 0.2))) { "no pitch for midi $n" }
            assertTrue(abs(centsBetween(est.hz, hz)) < 3.0, "midi $n: ${est.hz} vs $hz")
            assertTrue(est.clarity > 0.95)
        }
    }

    @Test
    fun `MPM - sawtooth and plucks have no octave errors and stay within 5 cents`() {
        for (n in guitarRange) {
            val hz = MidiNote(n).frequency()
            for (signal in listOf(TestSignals.saw(hz, 0.2), TestSignals.pluck(hz, 0.4))) {
                val est = requireNotNull(detectMiddle(signal)) { "no pitch for midi $n" }
                assertTrue(abs(centsBetween(est.hz, hz)) < 5.0, "midi $n: ${est.hz} vs $hz")
            }
        }
    }

    @Test
    fun `MPM - voice with vibrato is tracked to within the vibrato depth`() {
        for (n in 45..76) { // A2..E5
            val hz = MidiNote(n).frequency()
            val est = requireNotNull(detectMiddle(TestSignals.voice(hz, 0.5, vibratoCents = 15.0)))
            assertTrue(abs(centsBetween(est.hz, hz)) < 20.0, "midi $n: ${est.hz} vs $hz")
        }
    }

    @Test
    fun `MPM - noise and silence give no pitch`() {
        assertNull(detectMiddle(TestSignals.whiteNoise(0.2)))
        assertNull(detectMiddle(FloatArray(SAMPLE_RATE / 5)))
    }

    @Test
    fun `MPM - pitch survives moderate room noise`() {
        val hz = MidiNote(52).frequency()
        val est = requireNotNull(detectMiddle(TestSignals.mix(TestSignals.pluck(hz, 0.4), TestSignals.whiteNoise(0.4, amp = 0.03))))
        assertTrue(abs(centsBetween(est.hz, hz)) < 8.0)
    }

    @Test
    fun `MPM - fast enough for real time (median under 1 ms per 2048 window)`() {
        val det = McLeodPitchDetector()
        val signal = TestSignals.pluck(110.0, 2.0)
        repeat(200) { det.detect(signal, (it * 97) % (signal.size - 2048)) } // warm-up
        val times = (0 until 300).map {
            val t0 = System.nanoTime()
            det.detect(signal, (it * 131) % (signal.size - 2048))
            System.nanoTime() - t0
        }.sorted()
        assertTrue(times[times.size / 2] < 1_000_000, "median ${times[times.size / 2]} ns")
    }

    /** Frames from a signal at hop 512, as the input engine will produce them. */
    private fun frames(signal: FloatArray, det: PitchDetector = McLeodPitchDetector(), hop: Int = 512): List<PitchFrame> =
        (0..(signal.size - det.windowSize) step hop).map { off ->
            var sum = 0.0
            for (i in off until off + det.windowSize) sum += signal[i] * signal[i]
            val rms = sqrt(sum / det.windowSize)
            PitchFrame(timeNanos = off * 1_000_000_000L / SAMPLE_RATE, estimate = det.detect(signal, off), rmsDb = 20 * log10(rms + 1e-12))
        }

    @Test
    fun `segmenter - one clean pluck gives exactly one onset with the right note`() {
        val seg = StableNoteSegmenter()
        val events = frames(TestSignals.pluck(MidiNote(54).frequency(), 1.0)).flatMap(seg::accept)
        val onsets = events.filterIsInstance<NoteEvent.Onset>()
        assertEquals(1, onsets.size, "$events")
        assertEquals(MidiNote(54), onsets.single().note)
    }

    @Test
    fun `segmenter - a fumbled search (short wrong notes) yields the wrong note FIRST`() {
        // Rule 4: "перебор не засчитывается". A 150 ms wrong note followed by the right one must
        // produce the wrong onset first, so the engine (which takes the first onset) marks it wrong.
        val wrong = TestSignals.pluck(MidiNote(53).frequency(), 0.15)
        val right = TestSignals.pluck(MidiNote(54).frequency(), 0.6)
        val seg = StableNoteSegmenter()
        val onsets = frames(wrong + right).flatMap(seg::accept).filterIsInstance<NoteEvent.Onset>()
        assertEquals(MidiNote(53), onsets.first().note)
    }

    @Test
    fun `segmenter - blips shorter than minHold are ignored`() {
        val blip = TestSignals.pluck(MidiNote(60).frequency(), 0.04)
        val seg = StableNoteSegmenter()
        val onsets = frames(blip + FloatArray(SAMPLE_RATE / 2)).flatMap(seg::accept).filterIsInstance<NoteEvent.Onset>()
        assertTrue(onsets.isEmpty(), "$onsets")
    }

    @Test
    fun `segmenter - re-plucking the same note gives a second onset`() {
        val hz = MidiNote(57).frequency()
        val seg = StableNoteSegmenter()
        val signal = TestSignals.decay(TestSignals.pluck(hz, 0.5), 0.12) + TestSignals.pluck(hz, 0.5, seed = 11)
        val onsets = frames(signal).flatMap(seg::accept).filterIsInstance<NoteEvent.Onset>()
        assertEquals(2, onsets.size, "$onsets")
        assertTrue(onsets.all { it.note == MidiNote(57) })
    }

    @Test
    fun `segmenter - onset is timestamped at the note start, not at confirmation`() {
        val silence = FloatArray(SAMPLE_RATE / 5) // 200 ms
        val seg = StableNoteSegmenter()
        val onset = frames(silence + TestSignals.pluck(MidiNote(50).frequency(), 0.6)).flatMap(seg::accept)
            .filterIsInstance<NoteEvent.Onset>().single()
        val startMs = onset.timeNanos / 1_000_000
        assertTrue(startMs in 150..215, "onset at $startMs ms")
    }

    @Test
    fun `fft - inverse of forward is identity`() {
        val fft = Fft(64)
        val re = DoubleArray(64) { kotlin.math.sin(it * 0.3) + it % 5 }
        val im = DoubleArray(64)
        val orig = re.copyOf()
        fft.forward(re, im)
        fft.inverse(re, im)
        for (i in 0 until 64) assertEquals(orig[i], re[i], 1e-9)
    }

    @Test
    fun `synth - every voice renders at the requested pitch (closed loop with MPM)`() {
        val synth = DefaultSynth()
        for (voice in listOf(com.kazaeza.muzykalka.music.Voice.GUITAR, com.kazaeza.muzykalka.music.Voice.PIANO, com.kazaeza.muzykalka.music.Voice.BASS, com.kazaeza.muzykalka.music.Voice.TONE)) {
            for (n in listOf(40, 45, 52, 57, 64, 69)) {
                if (voice == com.kazaeza.muzykalka.music.Voice.BASS && n > 57) continue
                val score = com.kazaeza.muzykalka.music.Score(60.0, listOf(com.kazaeza.muzykalka.music.ScoreNote(0.0, 1.0, voice, MidiNote(n))))
                val audio = synth.renderOffline(score)
                val est = requireNotNull(McLeodPitchDetector().detect(audio, SAMPLE_RATE / 4)) { "$voice $n silent" }
                assertTrue(abs(centsBetween(est.hz, MidiNote(n).frequency())) < 5.0, "$voice $n: ${est.hz}")
            }
        }
    }

    @Test
    fun `synth - output never clips even with dense chords`() {
        val synth = DefaultSynth()
        val notes = (0 until 16).map { com.kazaeza.muzykalka.music.ScoreNote(0.0, 2.0, com.kazaeza.muzykalka.music.Voice.PIANO, MidiNote(48 + it * 2), 1f) }
        val audio = synth.renderOffline(com.kazaeza.muzykalka.music.Score(60.0, notes))
        assertTrue(audio.all { it in -1f..1f })
    }

    private fun Synth.renderFrames(frames: Int): FloatArray {
        val out = FloatArray(frames)
        val block = FloatArray(256)
        var written = 0
        while (written < frames) {
            val n = minOf(256, frames - written)
            render(block, n)
            System.arraycopy(block, 0, out, written, n)
            written += n
        }
        return out
    }

    private fun rms(x: FloatArray, from: Int, to: Int): Double {
        var s = 0.0
        for (i in from until to) s += x[i] * x[i]
        return kotlin.math.sqrt(s / (to - from))
    }

    @Test
    fun `drone - sounds at the tonic, survives stopAll, fades out when cleared`() {
        val synth = DefaultSynth()
        val tonic = MidiNote(45)
        synth.setDrone(listOf(tonic.frequency(), (tonic + 12).frequency()))
        val settled = synth.renderFrames(SAMPLE_RATE)
        val est = requireNotNull(McLeodPitchDetector().detect(settled.copyOfRange(SAMPLE_RATE / 2, SAMPLE_RATE), SAMPLE_RATE / 4)) { "drone silent" }
        val pc = Math.floorMod(kotlin.math.round(MidiNote.fractional(est.hz)).toInt(), 12)
        assertEquals(tonic.pitchClass.value, pc, "drone pitch ${est.hz}")
        assertTrue(!synth.isIdle, "a sounding drone keeps the output busy")

        synth.stopAll()
        val afterStop = synth.renderFrames(SAMPLE_RATE / 2)
        assertTrue(rms(afterStop, 0, afterStop.size) > 0.5 * rms(settled, SAMPLE_RATE / 2, SAMPLE_RATE), "stopAll keeps the drone")

        synth.setDrone(emptyList())
        val faded = synth.renderFrames(3 * SAMPLE_RATE)
        assertTrue(rms(faded, faded.size - 4800, faded.size) < 1e-3, "drone fades out")
        assertTrue(synth.isIdle)
    }

    @Test
    fun `drone - changing the key crossfades without clicks`() {
        val synth = DefaultSynth()
        synth.setDrone(listOf(MidiNote(45).frequency(), MidiNote(57).frequency()))
        synth.renderFrames(SAMPLE_RATE)
        synth.setDrone(listOf(MidiNote(50).frequency(), MidiNote(62).frequency()))
        val change = synth.renderFrames(SAMPLE_RATE)
        val maxStep = (1 until change.size).maxOf { abs(change[it] - change[it - 1]) }
        assertTrue(maxStep < 0.1f, "sample jump $maxStep")
    }

    @Test
    fun `drone - with notes on top the output never clips`() {
        val synth = DefaultSynth()
        synth.setDrone(listOf(MidiNote(45).frequency(), MidiNote(57).frequency()))
        synth.renderFrames(SAMPLE_RATE / 2)
        val notes = (0 until 8).map { com.kazaeza.muzykalka.music.ScoreNote(0.0, 2.0, com.kazaeza.muzykalka.music.Voice.TONE, MidiNote(60 + it * 3), 1f) }
        val audio = synth.renderOffline(com.kazaeza.muzykalka.music.Score(60.0, notes))
        assertTrue(audio.all { it in -1f..1f })
    }
}
