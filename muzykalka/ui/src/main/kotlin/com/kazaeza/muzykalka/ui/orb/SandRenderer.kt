package com.kazaeza.muzykalka.ui.orb

import android.opengl.GLES30
import com.kazaeza.muzykalka.music.OrbPhysics
import com.kazaeza.muzykalka.music.SandBody
import kotlin.math.max

/** O-38: SAND on the GPU: the plate (dark metal with a lit rim), then the grains as points whose light adds up. */
class SandRenderer(gl: Gles, input: OrbInput, private val gold: FloatArray, grains: Int = SandBody.SAND_GRAINS) : OrbRenderer(gl, input) {
    private val body = SandBody(grains)
    private var applied = -1
    private lateinit var full: Fullscreen
    private lateinit var plate: GlProgram
    private lateinit var sand: GlProgram
    private var vao = 0
    private var vbo = 0

    override fun init(w: Int, h: Int) {
        super.init(w, h)
        full = Fullscreen(gl)
        plate = GlProgram(gl, OrbShaders.VS_FULL, OrbShaders.FS_PLATE)
        sand = GlProgram(gl, OrbShaders.VS_SAND, OrbShaders.FS_SAND, listOf("aPos", "aHeat"))
        vao = gl.genVertexArray()
        vbo = gl.genBuffer()
        gl.bindVertexArray(vao)
        gl.bindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
        gl.bufferData(GLES30.GL_ARRAY_BUFFER, body.out.size * 4, GLES30.GL_DYNAMIC_DRAW)
        gl.enableVertexAttribArray(0)
        gl.vertexAttribPointer(0, 2, 12, 0)
        gl.enableVertexAttribArray(1)
        gl.vertexAttribPointer(1, 1, 12, 8)
    }

    private fun sync() {
        input.drain { body.pluck(it.strength) }
        val s = input.answerSemitone
        if (s != applied) {
            applied = s
            if (s < 0) body.clear() else body.answer(s)
        }
    }

    override fun draw(dt: Double) {
        sync()
        body.step(dt)
        gl.bindFramebuffer(0)
        gl.viewport(0, 0, width, height)
        gl.enableBlend(false)
        val tint = if (body.semitone < 0) gold else input.answerRgb
        plate.use()
        plate.f3("tint", tint)
        plate.f("energy", body.energy.toFloat())
        plate.f("wall", OrbPhysics.RADIUS.toFloat())
        plate.f2("res", width.toFloat(), height.toFloat())
        full.draw()
        // the grains add their light to the plate's; the alpha of the plate stays
        gl.enableBlend(true)
        gl.blendFuncSeparate(GLES30.GL_ONE, GLES30.GL_ONE, GLES30.GL_ZERO, GLES30.GL_ONE)
        sand.use()
        sand.f("size", max(1.5f, width / 300f))
        sand.f3("tint", tint)
        gl.bindVertexArray(vao)
        gl.bindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
        gl.bufferSubData(GLES30.GL_ARRAY_BUFFER, body.out, body.out.size)
        gl.drawArrays(GLES30.GL_POINTS, 0, body.count)
        gl.enableBlend(false)
    }

    override fun release() {
        if (this::plate.isInitialized) {
            plate.release()
            sand.release()
        }
    }
}
