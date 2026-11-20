package com.kazaeza.muzykalka.dsp

import com.kazaeza.muzykalka.music.Arranger
import com.kazaeza.muzykalka.music.ChordDegree
import com.kazaeza.muzykalka.music.Key
import com.kazaeza.muzykalka.music.Mode
import com.kazaeza.muzykalka.music.NoteName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * O-73: a developer's tool, not a test — renders the guitar to WAV files for listening and for the spectrogram script. It
 * runs only when GUITAR_RENDER_DIR names a folder.
 */
@EnabledIfEnvironmentVariable(named = "GUITAR_RENDER_DIR", matches = ".+")
class GuitarRender {
    private val key = Key(NoteName.parse("C"), Mode.MAJOR)

    private fun wav(file: File, audio: FloatArray, sr: Int = SAMPLE_RATE) {
        val data = ByteBuffer.allocate(audio.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        audio.forEach { data.putShort((it.coerceIn(-1f, 1f) * 32767).toInt().toShort()) }
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray()).putInt(36 + data.capacity()).put("WAVEfmt ".toByteArray()).putInt(16)
        header.putShort(1).putShort(1).putInt(sr).putInt(sr * 2).putShort(2).putShort(16)
        header.put("data".toByteArray()).putInt(data.capacity())
        file.writeBytes(header.array() + data.array())
    }

    @Test
    fun render() {
        val dir = File(System.getenv("GUITAR_RENDER_DIR")).also { it.mkdirs() }
        val tag = System.getenv("GUITAR_RENDER_TAG") ?: "new"
        val arranger = Arranger()
        for (c in listOf("I", "IV", "V", "vi", "V7", "ii")) {
            wav(File(dir, "$tag-$c.wav"), DefaultSynth().renderOffline(arranger.strum(ChordDegree.parse(c), key), tailSeconds = 1.5))
        }
        // the four chords of a song in a row, one strum each, as they would be heard
        val synth = DefaultSynth()
        val parts = listOf("I", "V", "vi", "IV").map { synth.renderOffline(arranger.strum(ChordDegree.parse(it), key), tailSeconds = 0.0) }
        wav(File(dir, "$tag-progression.wav"), parts.reduce { a, b -> a + b })
        // O-78: the transfer check's band-like texture: the loop twice, guitar + bass + drums
        val loop = listOf("I", "V", "vi", "IV").map(ChordDegree::parse)
        wav(File(dir, "$tag-groove.wav"), DefaultSynth().renderOffline(arranger.groove(loop + loop, key, beatsEach = 2.0, tempoBpm = 100.0), tailSeconds = 1.0))
    }
}
