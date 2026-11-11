package com.kazaeza.muzykalka

import android.graphics.RuntimeShader
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kazaeza.muzykalka.ui.effect.LENS_ORB_AGSL
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * O-26: the sphere is a shader in a string, so the compiler never sees it. A typo there would only show on the
 * phone — quietly, as the painted fallback sphere. Here real Skia compiles it.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class LensOrbShaderTest {
    @Test
    fun `the sphere's shader compiles`() {
        RuntimeShader(LENS_ORB_AGSL)
    }

    @Test
    fun `a broken shader is refused here, so the test above means something`() {
        assertThrows(IllegalArgumentException::class.java) { RuntimeShader("$LENS_ORB_AGSL\nnot a shader") }
    }
}
