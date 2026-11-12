package com.kazaeza.muzykalka

import com.kazaeza.muzykalka.ui.orb.Gles
import com.kazaeza.muzykalka.ui.orb.InkRenderer
import com.kazaeza.muzykalka.ui.orb.OrbInput
import com.kazaeza.muzykalka.ui.orb.OrbRenderer
import com.kazaeza.muzykalka.ui.orb.SandRenderer
import com.kazaeza.muzykalka.ui.orb.ThreadRenderer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * O-38: there is no GPU in `check`, so the renderers are run against a stand-in that knows GLSL's rules the way a
 * driver would enforce them: a uniform the shader does not have, a uniform set on another program than the one in use,
 * a uniform the shader declares that was never set before a draw, and a texture that is read while it is drawn into
 * (the mistake of every ping-pong) are all recorded. The shaders themselves are compiled by
 * `scripts/orb_shaders_check.js` in a browser's WebGL 2.
 */
class OrbRenderersTest {
    private val gold = floatArrayOf(0.91f, 0.69f, 0.10f)

    private fun frames(make: (Gles, OrbInput) -> OrbRenderer, floats: Boolean = true): StrictGles {
        val gl = StrictGles(floats)
        val input = OrbInput()
        val r = make(gl, input)
        r.init(300, 300)
        input.pluck(1f, 0.3f, -0.7f)
        repeat(60) { r.draw(1.0 / 60) }
        input.setAnswer(7, 0.2f, 0.9f, 0.4f)
        input.pluck(0.8f, -0.4f, -0.5f)
        repeat(120) { r.draw(1.0 / 60) }
        input.setAnswer(1, 1f, 0.2f, 0.2f)
        repeat(30) { r.draw(1.0 / 60) }
        input.clearAnswer()
        repeat(60) { r.draw(1.0 / 60) }
        r.resize(200, 200)
        repeat(3) { r.draw(1.0 / 60) }
        r.release()
        return gl
    }

    private fun check(gl: StrictGles, draws: Int) {
        assertTrue("GL misuse:\n" + gl.problems.joinToString("\n"), gl.problems.isEmpty())
        assertTrue("only ${gl.draws} draw calls", gl.draws >= draws)
        assertEquals("every program, shader, texture and framebuffer that was made is freed", 0, gl.alive())
    }

    @Test
    fun `the thread draws every frame without misusing GL`() {
        val gl = frames({ g, i -> ThreadRenderer(g, i, gold) })
        check(gl, 270 * 5)
        assertTrue("the ribbon is uploaded each frame", gl.uploads >= 270)
    }

    @Test
    fun `the sand draws every frame without misusing GL`() {
        val gl = frames({ g, i -> SandRenderer(g, i, gold, 2000) })
        check(gl, 270 * 2)
        assertTrue("the grains are uploaded each frame", gl.uploads >= 270)
    }

    @Test
    fun `the ink solves the fluid every frame without misusing GL`() {
        val gl = frames({ g, i -> InkRenderer(g, i, gold) })
        check(gl, 270 * 30)
    }

    @Test
    fun `the ink says no where there are no float targets, so that the drop is painted instead`() {
        try {
            frames({ g, i -> InkRenderer(g, i, gold) }, floats = false)
            fail("the ink needs float render targets")
        } catch (e: IllegalStateException) {
            assertTrue(e.message.orEmpty().contains("float"))
        }
    }

    @Test
    fun `the stand-in is not blind to what a driver would refuse`() {
        val gl = StrictGles(true)
        val vs = gl.createShader(0x8B31).also { gl.shaderSource(it, "#version 300 es\nin vec2 aPos; void main(){ gl_Position=vec4(aPos,0.,1.); }"); gl.compileShader(it) }
        val fs = gl.createShader(0x8B30).also {
            gl.shaderSource(it, "#version 300 es\nuniform sampler2D uTex; uniform float k; out vec4 o; void main(){ o=texture(uTex,vec2(0.))*k; }")
            gl.compileShader(it)
        }
        val p = gl.createProgram()
        gl.attachShader(p, vs)
        gl.attachShader(p, fs)
        gl.bindAttribLocation(p, 0, "aPos")
        gl.bindAttribLocation(p, 1, "aMissing")
        gl.linkProgram(p)
        gl.useProgram(p)
        val tex = gl.genTexture()
        val fb = gl.genFramebuffer()
        gl.bindTexture(tex)
        gl.bindFramebuffer(fb)
        gl.framebufferTexture(tex)
        gl.uniform1i(gl.uniformLocation(p, "uTex"), 0)
        gl.uniform1i(gl.uniformLocation(p, "nope"), 0)
        gl.drawArrays(4, 0, 3) // reads the texture it draws into, and never set k
        val text = gl.problems.joinToString("\n")
        for (expected in listOf("aMissing", "no uniform nope", "never set", "being drawn into")) assertTrue("$expected in:\n$text", text.contains(expected))
    }

    @Test
    fun `a pluck waits in the queue until the renderer takes it, and the queue does not grow forever`() {
        val input = OrbInput()
        repeat(100) { input.pluck(1f, 0f, -0.7f) }
        var n = 0
        input.drain { n++ }
        assertTrue("took $n", n in 1..16)
        input.drain { fail("nothing is left") }
    }
}

/** A [Gles] that is a driver with no mercy: see [OrbRenderersTest]. */
private class StrictGles(private val floatTargets: Boolean) : Gles {
    val problems = ArrayList<String>()
    var draws = 0
    var uploads = 0

    private var next = 1
    private val shaders = HashMap<Int, Pair<Int, String>>()
    private val programs = HashMap<Int, ArrayList<Int>>()
    private val linked = HashSet<Int>()
    private val linkedSource = HashMap<Int, String>()
    private val textures = HashSet<Int>()
    private val framebuffers = HashSet<Int>()
    private val attachment = HashMap<Int, Int>() // framebuffer -> texture
    private val vaos = HashSet<Int>()
    private val buffers = HashSet<Int>()
    private var program = 0
    private var framebuffer = 0
    private var activeUnit = 0
    private val unitTexture = HashMap<Int, Int>()
    private var boundTexture = 0

    // uniform locations are program * 1000 + n; what each is called and what it was set to
    private val names = HashMap<Int, String>()
    private val setSamplers = HashMap<Int, Int>() // location -> unit
    private val setOnce = HashSet<Int>()

    fun alive(): Int = shaders.size + programs.size + textures.size + framebuffers.size

    private fun problem(text: String) {
        if (problems.size < 20) problems.add(text)
    }

    override fun createShader(type: Int): Int = next++.also { shaders[it] = type to "" }
    override fun shaderSource(shader: Int, source: String) {
        shaders[shader] = shaders.getValue(shader).first to source
    }

    override fun compileShader(shader: Int) {
        val src = shaders.getValue(shader).second
        if (!src.startsWith("#version 300 es")) problem("a shader without the version line")
        if (!src.contains("void main")) problem("a shader without main")
    }

    override fun shaderCompiled(shader: Int) = true
    override fun shaderLog(shader: Int) = ""
    override fun createProgram(): Int = next++.also { programs[it] = ArrayList() }
    override fun attachShader(program: Int, shader: Int) {
        programs.getValue(program).add(shader)
    }

    override fun bindAttribLocation(program: Int, index: Int, name: String) {
        val vs = programs.getValue(program).map { shaders.getValue(it) }.firstOrNull { it.first == VERTEX }?.second.orEmpty()
        if (!Regex("""\bin\s+(?:vec\d|float)\s+$name\b""").containsMatchIn(vs)) problem("attribute $name is not in the vertex shader")
    }

    override fun linkProgram(program: Int) {
        linked.add(program)
        // a driver keeps the code of a linked program, whatever happens to the shader objects afterwards
        linkedSource[program] = programs.getValue(program).joinToString("\n") { shaders[it]?.second ?: "" }
    }

    override fun programLinked(program: Int) = program in linked
    override fun programLog(program: Int) = ""
    override fun deleteShader(shader: Int) {
        shaders.remove(shader)
    }

    override fun deleteProgram(program: Int) {
        programs.remove(program)
    }

    override fun useProgram(program: Int) {
        if (program !in programs) problem("use of a program that is gone: $program")
        this.program = program
    }

    private fun sources(p: Int) = linkedSource[p].orEmpty()

    /** The uniforms a program declares, name → type. */
    private fun declared(p: Int): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        for (m in Regex("""uniform\s+(\w+)\s+([^;]+);""").findAll(sources(p))) {
            m.groupValues[2].split(',').map { it.trim() }.forEach { out[it] = m.groupValues[1] }
        }
        return out
    }

    override fun uniformLocation(program: Int, name: String): Int {
        if (name !in declared(program)) problem("program $program has no uniform $name")
        return program * 1000 + names.size.also { names[program * 1000 + it] = name }
    }

    private fun touch(location: Int, what: String) {
        if (location / 1000 != program) problem("uniform ${names[location]} ($what) is set on a program that is not in use")
        setOnce.add(location)
    }

    override fun uniform1i(location: Int, v: Int) {
        touch(location, "int")
        setSamplers[location] = v
    }

    override fun uniform1f(location: Int, v: Float) = touch(location, "float")
    override fun uniform2f(location: Int, a: Float, b: Float) = touch(location, "vec2")
    override fun uniform3f(location: Int, a: Float, b: Float, c: Float) = touch(location, "vec3")

    override fun genVertexArray(): Int = next++.also { vaos.add(it) }
    override fun bindVertexArray(vao: Int) = Unit
    override fun genBuffer(): Int = next++.also { buffers.add(it) }
    override fun bindBuffer(target: Int, buffer: Int) = Unit
    override fun bufferData(target: Int, bytes: Int, usage: Int) = Unit
    override fun bufferSubData(target: Int, data: FloatArray, count: Int) {
        uploads++
        if (count > data.size) problem("uploads $count floats from an array of ${data.size}")
        for (i in 0 until count) if (!data[i].isFinite()) {
            problem("a non-finite number in an upload (index $i)")
            break
        }
    }

    override fun bufferStatic(target: Int, data: FloatArray) = Unit
    override fun enableVertexAttribArray(index: Int) = Unit
    override fun vertexAttribPointer(index: Int, size: Int, stride: Int, offset: Int) = Unit

    override fun genTexture(): Int = next++.also { textures.add(it) }
    override fun bindTexture(texture: Int) {
        boundTexture = texture
        unitTexture[activeUnit] = texture
    }

    override fun activeTexture(unit: Int) {
        activeUnit = unit
    }

    override fun texParameteri(pname: Int, value: Int) = Unit
    override fun texImage2D(internalFormat: Int, width: Int, height: Int, format: Int, type: Int) {
        if (internalFormat == RGBA16F && !floatTargets) problem("a float texture where there are no float targets")
    }

    override fun generateMipmap() {
        if (boundTexture == 0) problem("mipmaps of no texture")
    }

    override fun genFramebuffer(): Int = next++.also { framebuffers.add(it) }
    override fun bindFramebuffer(framebuffer: Int) {
        this.framebuffer = framebuffer
    }

    override fun framebufferTexture(texture: Int) {
        attachment[framebuffer] = texture
    }

    override fun framebufferComplete() = true
    override fun deleteTexture(texture: Int) {
        textures.remove(texture)
    }

    override fun deleteFramebuffer(framebuffer: Int) {
        framebuffers.remove(framebuffer)
    }

    override fun viewport(x: Int, y: Int, w: Int, h: Int) {
        if (w <= 0 || h <= 0) problem("a viewport of $w x $h")
    }

    override fun clearColor(r: Float, g: Float, b: Float, a: Float) = Unit
    override fun clear() = Unit
    override fun enableBlend(on: Boolean) = Unit
    override fun blendFunc(src: Int, dst: Int) = Unit
    override fun blendFuncSeparate(srcRgb: Int, dstRgb: Int, srcA: Int, dstA: Int) = Unit

    override fun drawArrays(mode: Int, first: Int, count: Int) {
        draws++
        if (program == 0) {
            problem("a draw with no program")
            return
        }
        val declared = declared(program)
        val mine = names.filterKeys { it / 1000 == program }
        for ((name, type) in declared) {
            val loc = mine.entries.firstOrNull { it.value == name }?.key
            if (loc == null || loc !in setOnce) {
                problem("uniform $name ($type) of program $program was never set before a draw")
                continue
            }
            if (type == "sampler2D") {
                val tex = unitTexture[setSamplers[loc] ?: -1]
                if (tex == null || tex == 0) problem("sampler $name reads from an empty unit")
                if (tex != null && tex != 0 && attachment[framebuffer] == tex) problem("sampler $name reads the texture that is being drawn into")
            }
        }
    }

    override fun hasExtension(name: String) = floatTargets && name == "GL_EXT_color_buffer_float"
    override fun error() = 0

    private companion object {
        const val VERTEX = 0x8B31
        const val RGBA16F = 0x881A
    }
}
