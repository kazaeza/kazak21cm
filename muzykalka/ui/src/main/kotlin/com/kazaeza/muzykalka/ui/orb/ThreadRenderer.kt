package com.kazaeza.muzykalka.ui.orb

import android.opengl.GLES30
import com.kazaeza.muzykalka.music.OrbPhysics
import com.kazaeza.muzykalka.music.ThreadBody
import kotlin.math.atan2
import kotlin.math.exp
import kotlin.math.min

/**
 * O-38: THREAD on the GPU. The string ([ThreadBody.ribbon]) is drawn three times, wide and faint to thin and bright, with
 * light that adds up, into a layer that is dimmed a little each frame before the string is added again — the afterglow
 * of a phosphor screen; the ball (a dark glass with a halo) is drawn first and the layer laid over it.
 * [gold] is the colour of the tonic, the degree's own colour comes from [OrbInput.answerRgb].
 */
class ThreadRenderer(gl: Gles, input: OrbInput, private val gold: FloatArray) : OrbRenderer(gl, input) {
    private val body = ThreadBody()
    private val ribbon = FloatArray(body.ribbonSize)
    private val mixed = FloatArray(3)
    private var applied = -1
    private lateinit var full: Fullscreen
    private lateinit var ball: GlProgram
    private lateinit var line: GlProgram
    private lateinit var keep: GlProgram
    private lateinit var over: GlProgram
    private var vao = 0
    private var vbo = 0
    private var layers: PingPong? = null

    override fun init(w: Int, h: Int) {
        super.init(w, h)
        full = Fullscreen(gl)
        ball = GlProgram(gl, OrbShaders.VS_FULL, OrbShaders.FS_BALL)
        keep = GlProgram(gl, OrbShaders.VS_FULL, OrbShaders.FS_KEEP)
        over = GlProgram(gl, OrbShaders.VS_FULL, OrbShaders.FS_OVER)
        line = GlProgram(gl, OrbShaders.VS_LINE, OrbShaders.FS_LINE, listOf("aPos", "aNorm", "aInfo"))
        vao = gl.genVertexArray()
        vbo = gl.genBuffer()
        gl.bindVertexArray(vao)
        gl.bindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
        gl.bufferData(GLES30.GL_ARRAY_BUFFER, ribbon.size * 4, GLES30.GL_DYNAMIC_DRAW)
        val stride = ThreadBody.FLOATS_PER_VERTEX * 4
        gl.enableVertexAttribArray(0)
        gl.vertexAttribPointer(0, 2, stride, 0)
        gl.enableVertexAttribArray(1)
        gl.vertexAttribPointer(1, 2, stride, 8)
        gl.enableVertexAttribArray(2)
        gl.vertexAttribPointer(2, 3, stride, 16)
        makeLayers()
    }

    private fun makeLayers() {
        layers?.release()
        layers = PingPong(
            Target(gl, width, height, GLES30.GL_RGBA8, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE),
            Target(gl, width, height, GLES30.GL_RGBA8, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE),
        )
    }

    override fun resize(w: Int, h: Int) {
        if (w == width && h == height) return
        super.resize(w, h)
        makeLayers()
    }

    private fun sync() {
        input.drain { p -> body.pluck(p.strength, atan2(p.y, p.x)) }
        val s = input.answerSemitone
        if (s != applied) {
            applied = s
            if (s < 0) body.clear() else body.answer(s)
        }
    }

    override fun draw(dt: Double) {
        sync()
        body.step(dt)
        body.ribbon(ribbon)
        val layers = checkNotNull(layers)
        val d = min(dt, 1.0 / 30)
        val fade = exp(-d / 0.085).toFloat()
        val k = 1f - fade
        val answered = body.semitone >= 0
        val rgb = input.answerRgb
        // the picture so far, dimmer, then the string added to it
        gl.bindFramebuffer(layers.write.fb)
        gl.viewport(0, 0, width, height)
        gl.enableBlend(false)
        keep.use()
        gl.activeTexture(0)
        gl.bindTexture(layers.read.tex)
        keep.i("uTex", 0)
        keep.f("mul", fade)
        keep.f("sub", 0.004f)
        full.draw()
        gl.enableBlend(true)
        gl.blendFunc(GLES30.GL_ONE, GLES30.GL_ONE)
        line.use()
        gl.bindVertexArray(vao)
        gl.bindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
        gl.bufferSubData(GLES30.GL_ARRAY_BUFFER, ribbon, ribbon.size)
        line.f3("cA", gold)
        line.f3("cB", if (answered) rgb else gold)
        line.f("lock", body.lockShown.toFloat())
        line.f("waves", body.q.toFloat())
        val fold = body.fold.toFloat()
        val dense = body.dense.toFloat()
        val energy = body.energy.toFloat()
        val vertices = (body.points + 1) * 2
        fun pass(width: Float, gain: Float, sharp: Float) {
            line.f("width", width)
            line.f("gain", gain * fold * k)
            line.f("sharp", sharp)
            gl.drawArrays(GLES30.GL_TRIANGLE_STRIP, 0, vertices)
        }
        pass(0.10f, (0.13f + 0.14f * energy) * dense, 3.2f)
        pass(0.032f, 0.40f * dense, 3.0f)
        pass(0.0085f, 1.2f, 2.2f)
        // the ball, then the afterglow laid over it as light
        gl.enableBlend(false)
        gl.bindFramebuffer(0)
        gl.viewport(0, 0, width, height)
        for (i in 0..2) mixed[i] = if (answered) 0.5f * (rgb[i] + gold[i]) else gold[i]
        ball.use()
        ball.f3("tint", mixed)
        ball.f("energy", energy)
        ball.f("wall", OrbPhysics.RADIUS.toFloat())
        ball.f2("res", width.toFloat(), height.toFloat())
        full.draw()
        gl.enableBlend(true)
        gl.blendFuncSeparate(GLES30.GL_ONE, GLES30.GL_ONE, GLES30.GL_ONE, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        over.use()
        gl.activeTexture(0)
        gl.bindTexture(layers.write.tex)
        over.i("uTex", 0)
        full.draw()
        gl.enableBlend(false)
        layers.swap()
    }

    override fun release() {
        layers?.release()
        layers = null
        if (this::ball.isInitialized) {
            ball.release()
            keep.release()
            over.release()
            line.release()
        }
    }
}
