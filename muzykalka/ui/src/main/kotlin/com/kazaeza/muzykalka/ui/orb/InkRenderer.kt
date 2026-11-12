package com.kazaeza.muzykalka.ui.orb

import android.opengl.GLES30
import com.kazaeza.muzykalka.music.InkScript
import com.kazaeza.muzykalka.music.InkScript.InkFrame
import com.kazaeza.muzykalka.music.OrbPhysics
import kotlin.math.min

/**
 * O-38: INK on the GPU — a real fluid (stable fluids: advect, vorticity, projection by 22 Jacobi sweeps) on a
 * [SIM]² grid with the dye on a [DYE]² one, in a glass ball whose wall nothing flows through. What goes into the
 * fluid each frame comes from [InkScript]; this class is the solver and the picture. Needs float render targets:
 * [init] throws where the device has none (the caller then paints another body).
 */
class InkRenderer(gl: Gles, input: OrbInput, private val gold: FloatArray) : OrbRenderer(gl, input) {
    private val script = InkScript()
    private val frame = InkFrame()
    private var applied = -1
    private val rgb = FloatArray(3)
    private lateinit var full: Fullscreen
    private val programs = HashMap<String, GlProgram>()
    private lateinit var vel: PingPong
    private lateinit var dye: PingPong
    private lateinit var prs: PingPong
    private lateinit var div: Target
    private lateinit var curl: Target
    private var unit = 0

    private fun program(name: String, fragment: String) {
        programs[name] = GlProgram(gl, OrbShaders.VS_SIM, OrbShaders.FS_SIM_HEAD + fragment)
    }

    override fun init(w: Int, h: Int) {
        super.init(w, h)
        check(gl.hasExtension("GL_EXT_color_buffer_float") || gl.hasExtension("GL_EXT_color_buffer_half_float")) {
            "no float render targets"
        }
        full = Fullscreen(gl)
        program("advect", OrbShaders.INK_ADVECT)
        program("splat", OrbShaders.INK_SPLAT)
        program("burst", OrbShaders.INK_BURST)
        program("curl", OrbShaders.INK_CURL)
        program("vort", OrbShaders.INK_VORT)
        program("div", OrbShaders.INK_DIV)
        program("pressure", OrbShaders.INK_PRESSURE)
        program("grad", OrbShaders.INK_GRAD)
        program("show", OrbShaders.INK_SHOW)
        fun target(n: Int, mip: Boolean = false) = Target(gl, n, n, GLES30.GL_RGBA16F, GLES30.GL_RGBA, GLES30.GL_HALF_FLOAT, mip)
        vel = PingPong(target(SIM), target(SIM))
        dye = PingPong(target(DYE, true), target(DYE, true))
        prs = PingPong(target(SIM), target(SIM))
        div = target(SIM)
        curl = target(SIM)
    }

    private fun use(name: String, n: Int): GlProgram {
        val p = checkNotNull(programs[name])
        p.use()
        unit = 0
        p.f2("texel", 1f / n, 1f / n)
        return p
    }

    private fun bind(p: GlProgram, name: String, t: Target) {
        gl.activeTexture(unit)
        gl.bindTexture(t.tex)
        p.i(name, unit)
        unit++
    }

    private fun render(t: Target?) {
        if (t == null) {
            gl.bindFramebuffer(0)
            gl.viewport(0, 0, width, height)
        } else {
            gl.bindFramebuffer(t.fb)
            gl.viewport(0, 0, t.w, t.h)
        }
        full.draw()
    }

    private fun splat(buf: PingPong, x: Float, y: Float, r: Float, g: Float, b: Float, radius: Float) {
        val p = use("splat", buf.read.w)
        bind(p, "uSrc", buf.read)
        p.f2("point", x, y)
        p.f3("color", r, g, b)
        p.f("radius", radius)
        render(buf.write)
        buf.swap()
    }

    private fun sync() {
        input.drain { script.pluck(it.strength, it.x, it.y) }
        val s = input.answerSemitone
        if (s != applied) {
            applied = s
            if (s < 0) script.clear() else script.answer(s)
        }
    }

    /** The colour of a dye splat: the tonic's four smokes, the answered degree's own, a bright puff for a pluck. */
    private fun dyeColour(role: Int, amount: Float, answered: Boolean) {
        val a = input.answerRgb
        when {
            role == InkScript.ROLE_BURST -> {
                rgb[0] = amount
                rgb[1] = 0.86f * amount
                rgb[2] = 0.6f * amount
            }
            role == InkScript.ROLE_JET -> for (i in 0..2) rgb[i] = a[i] * amount
            !answered && role == InkScript.ROLE_TONIC -> for (i in 0..2) rgb[i] = gold[i] * amount
            !answered -> for (i in 0..2) rgb[i] = SMOKE[role - InkScript.ROLE_TONIC][i] * amount
            role == InkScript.ROLE_TONIC -> for (i in 0..2) rgb[i] = gold[i] * 0.45f * amount
            else -> {
                val k = role - InkScript.ROLE_TONIC
                for (i in 0..2) rgb[i] = min(1f, a[i] * (0.55f + 0.25f * k) + if (k == 3 && i == 2) 0.25f else 0f) * amount
            }
        }
    }

    override fun draw(dt: Double) {
        sync()
        script.step(dt, frame)
        val answered = script.semitone >= 0
        val dtf = frame.dt
        val vtexel = 1f / SIM
        // the ink carries itself along
        var p = use("advect", SIM)
        bind(p, "uVel", vel.read)
        bind(p, "uSrc", vel.read)
        p.f("dt", dtf)
        p.f("diss", 0.25f)
        p.f2("vtexel", vtexel, vtexel)
        render(vel.write)
        vel.swap()
        // what the script puts in: pushes, puffs of ink, plucks
        for (i in 0 until frame.count) {
            when (frame.kind[i]) {
                InkFrame.VELOCITY -> splat(vel, frame.x[i], frame.y[i], frame.a[i], frame.b[i], 0f, frame.radius[i])
                InkFrame.DYE -> {
                    dyeColour(frame.role[i], frame.a[i], answered)
                    splat(dye, frame.x[i], frame.y[i], rgb[0], rgb[1], rgb[2], frame.radius[i])
                }
                InkFrame.BURST -> {
                    p = use("burst", SIM)
                    bind(p, "uSrc", vel.read)
                    p.f2("point", frame.x[i], frame.y[i])
                    p.f("power", frame.a[i])
                    p.f("r0", 0.085f)
                    p.f("w", 0.06f)
                    p.f("seed", frame.b[i])
                    render(vel.write)
                    vel.swap()
                }
            }
        }
        // swirl, then make the flow incompressible and keep it inside the wall
        p = use("curl", SIM)
        bind(p, "uVel", vel.read)
        render(curl)
        p = use("vort", SIM)
        bind(p, "uVel", vel.read)
        bind(p, "uCurl", curl)
        p.f("curl", 11f)
        p.f("dt", dtf)
        render(vel.write)
        vel.swap()
        p = use("div", SIM)
        bind(p, "uVel", vel.read)
        render(div)
        repeat(PRESSURE_SWEEPS) {
            p = use("pressure", SIM)
            bind(p, "uP", prs.read)
            bind(p, "uDiv", div)
            render(prs.write)
            prs.swap()
        }
        p = use("grad", SIM)
        bind(p, "uP", prs.read)
        bind(p, "uVel", vel.read)
        p.f("wall", OrbPhysics.RADIUS.toFloat())
        render(vel.write)
        vel.swap()
        // the dye follows the flow; its blurred copies are what the glass shows
        p = use("advect", DYE)
        bind(p, "uVel", vel.read)
        bind(p, "uSrc", dye.read)
        p.f("dt", dtf)
        p.f("diss", frame.dyeDissipation)
        p.f2("vtexel", vtexel, vtexel)
        render(dye.write)
        dye.swap()
        gl.bindTexture(dye.read.tex)
        gl.generateMipmap()
        p = use("show", DYE)
        bind(p, "uDye", dye.read)
        p.f("wall", OrbPhysics.RADIUS.toFloat())
        p.f("energy", frame.energy)
        p.f2("res", width.toFloat(), height.toFloat())
        gl.enableBlend(false)
        render(null)
    }

    override fun release() {
        programs.values.forEach { it.release() }
        programs.clear()
        if (this::vel.isInitialized) {
            vel.release()
            dye.release()
            prs.release()
            div.release()
            curl.release()
        }
    }

    private companion object {
        const val SIM = 192
        const val DYE = 512
        const val PRESSURE_SWEEPS = 22
        val SMOKE = arrayOf(
            floatArrayOf(0f, 0f, 0f), // the tonic's own (the gold) is taken from the constructor
            floatArrayOf(1.0f, 0.36f, 0.22f),
            floatArrayOf(0.62f, 0.3f, 1.0f),
            floatArrayOf(0.12f, 0.45f, 1.0f),
        )
    }
}
