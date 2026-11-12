package com.kazaeza.muzykalka.ui.orb

import android.opengl.GLES30
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * O-38: what the screen tells a body, from any thread; the render thread reads it once a frame. Coordinates of a
 * pluck are the body's own: the unit disc, y up.
 */
class OrbInput {
    class Pluck(val strength: Float, val x: Float, val y: Float)

    private val plucks = ConcurrentLinkedQueue<Pluck>()

    /** The degree answered, in semitones above the tonic, or -1 while none is. */
    @Volatile
    var answerSemitone: Int = -1
        private set

    /** Its colour, linear-free sRGB 0..1 (the degree's own, or red for a wrong choice). */
    @Volatile
    var answerRgb: FloatArray = floatArrayOf(1f, 1f, 1f)
        private set

    fun pluck(strength: Float, x: Float, y: Float) {
        if (plucks.size < MAX_QUEUE) plucks.add(Pluck(strength, x, y))
    }

    fun setAnswer(semitone: Int, r: Float, g: Float, b: Float) {
        answerRgb = floatArrayOf(r, g, b)
        answerSemitone = semitone
    }

    fun clearAnswer() {
        answerSemitone = -1
    }

    /** Hands every pluck that has arrived to [each], oldest first. */
    fun drain(each: (Pluck) -> Unit) {
        while (true) each(plucks.poll() ?: return)
    }

    private companion object {
        const val MAX_QUEUE = 16
    }
}

/** One body on the GPU. All calls on the render thread, with the context current. */
abstract class OrbRenderer(protected val gl: Gles, protected val input: OrbInput) {
    protected var width = 1
    protected var height = 1

    /** Builds the programs and targets for a surface of [w] × [h] pixels. Throws if the device cannot do it. */
    open fun init(w: Int, h: Int) {
        width = w
        height = h
    }

    open fun resize(w: Int, h: Int) {
        width = w
        height = h
    }

    /** Advances [dt] seconds and draws one frame into the screen's buffer (premultiplied alpha). */
    abstract fun draw(dt: Double)

    abstract fun release()
}

/** A compiled and linked program; uniforms are looked up once by name. */
class GlProgram(private val gl: Gles, vertex: String, fragment: String, attribs: List<String> = listOf("aPos")) {
    val id: Int
    private val locations = HashMap<String, Int>()

    init {
        val vs = compile(GLES30.GL_VERTEX_SHADER, OrbShaders.PREFIX + vertex)
        val fs = compile(GLES30.GL_FRAGMENT_SHADER, OrbShaders.PREFIX + fragment)
        id = gl.createProgram()
        gl.attachShader(id, vs)
        gl.attachShader(id, fs)
        attribs.forEachIndexed { i, name -> gl.bindAttribLocation(id, i, name) }
        gl.linkProgram(id)
        check(gl.programLinked(id)) { "link: ${gl.programLog(id)}" }
        gl.deleteShader(vs)
        gl.deleteShader(fs)
    }

    private fun compile(type: Int, source: String): Int {
        val s = gl.createShader(type)
        gl.shaderSource(s, source)
        gl.compileShader(s)
        check(gl.shaderCompiled(s)) { "compile: ${gl.shaderLog(s)}\n$source" }
        return s
    }

    fun use() = gl.useProgram(id)

    private fun loc(name: String) = locations.getOrPut(name) { gl.uniformLocation(id, name) }

    fun i(name: String, v: Int) = gl.uniform1i(loc(name), v)
    fun f(name: String, v: Float) = gl.uniform1f(loc(name), v)
    fun f2(name: String, a: Float, b: Float) = gl.uniform2f(loc(name), a, b)
    fun f3(name: String, a: Float, b: Float, c: Float) = gl.uniform3f(loc(name), a, b, c)
    fun f3(name: String, rgb: FloatArray) = gl.uniform3f(loc(name), rgb[0], rgb[1], rgb[2])

    fun release() = gl.deleteProgram(id)
}

/** The triangle that covers the whole target. */
class Fullscreen(private val gl: Gles) {
    private val vao = gl.genVertexArray()
    private val vbo = gl.genBuffer()

    init {
        gl.bindVertexArray(vao)
        gl.bindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
        gl.bufferStatic(GLES30.GL_ARRAY_BUFFER, floatArrayOf(-1f, -1f, 3f, -1f, -1f, 3f))
        gl.enableVertexAttribArray(0)
        gl.vertexAttribPointer(0, 2, 0, 0)
    }

    fun draw() {
        gl.bindVertexArray(vao)
        gl.drawArrays(GLES30.GL_TRIANGLES, 0, 3)
    }
}

/** A texture that can be drawn into. [mip]: it has a chain of smaller copies (the dye of the ink is looked at blurred). */
class Target(private val gl: Gles, val w: Int, val h: Int, internalFormat: Int, format: Int, type: Int, mip: Boolean = false) {
    val tex = gl.genTexture()
    val fb = gl.genFramebuffer()

    init {
        gl.bindTexture(tex)
        gl.texParameteri(GLES30.GL_TEXTURE_MIN_FILTER, if (mip) GLES30.GL_LINEAR_MIPMAP_LINEAR else GLES30.GL_LINEAR)
        gl.texParameteri(GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        gl.texParameteri(GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        gl.texParameteri(GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        gl.texImage2D(internalFormat, w, h, format, type)
        gl.bindFramebuffer(fb)
        gl.framebufferTexture(tex)
        check(gl.framebufferComplete()) { "a ${w}x$h target (format $internalFormat) cannot be drawn into on this device" }
        gl.viewport(0, 0, w, h)
        gl.clearColor(0f, 0f, 0f, 1f)
        gl.clear()
        if (mip) gl.generateMipmap()
    }

    fun release() {
        gl.deleteTexture(tex)
        gl.deleteFramebuffer(fb)
    }
}

/** Two targets that take turns: read one, write the other, [swap]. */
class PingPong(val a0: Target, val b0: Target) {
    var read: Target = a0
        private set
    var write: Target = b0
        private set

    fun swap() {
        val t = read
        read = write
        write = t
    }

    fun release() {
        a0.release()
        b0.release()
    }
}
