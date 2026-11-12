package com.kazaeza.muzykalka.ui.orb

import android.opengl.GLES30
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * O-38 (ADR-040): the few OpenGL ES 3.0 calls the bodies of the sphere need, as an interface — so that the renderers
 * can be run in a test against a stand-in (there is no GPU in `check`) and the real one is a thin [AndroidGles].
 * All handles are plain ints (0 = none). Constants are the `GLES30.GL_*` ones (the compiler inlines them).
 */
interface Gles {
    fun createShader(type: Int): Int
    fun shaderSource(shader: Int, source: String)
    fun compileShader(shader: Int)
    fun shaderCompiled(shader: Int): Boolean
    fun shaderLog(shader: Int): String
    fun createProgram(): Int
    fun attachShader(program: Int, shader: Int)
    fun bindAttribLocation(program: Int, index: Int, name: String)
    fun linkProgram(program: Int)
    fun programLinked(program: Int): Boolean
    fun programLog(program: Int): String
    fun deleteShader(shader: Int)
    fun deleteProgram(program: Int)
    fun useProgram(program: Int)
    fun uniformLocation(program: Int, name: String): Int
    fun uniform1i(location: Int, v: Int)
    fun uniform1f(location: Int, v: Float)
    fun uniform2f(location: Int, a: Float, b: Float)
    fun uniform3f(location: Int, a: Float, b: Float, c: Float)

    fun genVertexArray(): Int
    fun bindVertexArray(vao: Int)
    fun genBuffer(): Int
    fun bindBuffer(target: Int, buffer: Int)
    fun bufferData(target: Int, bytes: Int, usage: Int)

    /** Replaces the first [count] floats of the bound buffer with [data]. */
    fun bufferSubData(target: Int, data: FloatArray, count: Int)

    /** Puts [data] (all of it) into the bound buffer, once. */
    fun bufferStatic(target: Int, data: FloatArray)
    fun enableVertexAttribArray(index: Int)
    fun vertexAttribPointer(index: Int, size: Int, stride: Int, offset: Int)

    fun genTexture(): Int
    fun bindTexture(texture: Int)
    fun activeTexture(unit: Int)
    fun texParameteri(pname: Int, value: Int)
    fun texImage2D(internalFormat: Int, width: Int, height: Int, format: Int, type: Int)
    fun generateMipmap()
    fun genFramebuffer(): Int
    fun bindFramebuffer(framebuffer: Int)
    fun framebufferTexture(texture: Int)
    fun framebufferComplete(): Boolean
    fun deleteTexture(texture: Int)
    fun deleteFramebuffer(framebuffer: Int)

    fun viewport(x: Int, y: Int, w: Int, h: Int)
    fun clearColor(r: Float, g: Float, b: Float, a: Float)
    fun clear()
    fun enableBlend(on: Boolean)
    fun blendFunc(src: Int, dst: Int)
    fun blendFuncSeparate(srcRgb: Int, dstRgb: Int, srcA: Int, dstA: Int)
    fun drawArrays(mode: Int, first: Int, count: Int)

    /** True if the driver lists [name] (e.g. "GL_EXT_color_buffer_float") among its extensions. */
    fun hasExtension(name: String): Boolean

    /** glGetError, for the log. */
    fun error(): Int
}

/** [Gles] on the real thing. Must be called on the thread that has the context current. */
class AndroidGles : Gles {
    private val ints = IntArray(1)
    private var scratch: FloatBuffer = ByteBuffer.allocateDirect(4 * 1024).order(ByteOrder.nativeOrder()).asFloatBuffer()

    private fun floats(data: FloatArray, count: Int): FloatBuffer {
        if (scratch.capacity() < count) scratch = ByteBuffer.allocateDirect(4 * count).order(ByteOrder.nativeOrder()).asFloatBuffer()
        scratch.clear()
        scratch.put(data, 0, count)
        scratch.flip()
        return scratch
    }

    override fun createShader(type: Int) = GLES30.glCreateShader(type)
    override fun shaderSource(shader: Int, source: String) = GLES30.glShaderSource(shader, source)
    override fun compileShader(shader: Int) = GLES30.glCompileShader(shader)
    override fun shaderCompiled(shader: Int): Boolean {
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, ints, 0)
        return ints[0] != 0
    }

    override fun shaderLog(shader: Int): String = GLES30.glGetShaderInfoLog(shader)
    override fun createProgram() = GLES30.glCreateProgram()
    override fun attachShader(program: Int, shader: Int) = GLES30.glAttachShader(program, shader)
    override fun bindAttribLocation(program: Int, index: Int, name: String) = GLES30.glBindAttribLocation(program, index, name)
    override fun linkProgram(program: Int) = GLES30.glLinkProgram(program)
    override fun programLinked(program: Int): Boolean {
        GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, ints, 0)
        return ints[0] != 0
    }

    override fun programLog(program: Int): String = GLES30.glGetProgramInfoLog(program)
    override fun deleteShader(shader: Int) = GLES30.glDeleteShader(shader)
    override fun deleteProgram(program: Int) = GLES30.glDeleteProgram(program)
    override fun useProgram(program: Int) = GLES30.glUseProgram(program)
    override fun uniformLocation(program: Int, name: String) = GLES30.glGetUniformLocation(program, name)
    override fun uniform1i(location: Int, v: Int) = GLES30.glUniform1i(location, v)
    override fun uniform1f(location: Int, v: Float) = GLES30.glUniform1f(location, v)
    override fun uniform2f(location: Int, a: Float, b: Float) = GLES30.glUniform2f(location, a, b)
    override fun uniform3f(location: Int, a: Float, b: Float, c: Float) = GLES30.glUniform3f(location, a, b, c)

    override fun genVertexArray(): Int {
        GLES30.glGenVertexArrays(1, ints, 0)
        return ints[0]
    }

    override fun bindVertexArray(vao: Int) = GLES30.glBindVertexArray(vao)
    override fun genBuffer(): Int {
        GLES30.glGenBuffers(1, ints, 0)
        return ints[0]
    }

    override fun bindBuffer(target: Int, buffer: Int) = GLES30.glBindBuffer(target, buffer)
    override fun bufferData(target: Int, bytes: Int, usage: Int) = GLES30.glBufferData(target, bytes, null, usage)
    override fun bufferSubData(target: Int, data: FloatArray, count: Int) = GLES30.glBufferSubData(target, 0, count * 4, floats(data, count))
    override fun bufferStatic(target: Int, data: FloatArray) = GLES30.glBufferData(target, data.size * 4, floats(data, data.size), GLES30.GL_STATIC_DRAW)
    override fun enableVertexAttribArray(index: Int) = GLES30.glEnableVertexAttribArray(index)
    override fun vertexAttribPointer(index: Int, size: Int, stride: Int, offset: Int) =
        GLES30.glVertexAttribPointer(index, size, GLES30.GL_FLOAT, false, stride, offset)

    override fun genTexture(): Int {
        GLES30.glGenTextures(1, ints, 0)
        return ints[0]
    }

    override fun bindTexture(texture: Int) = GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture)
    override fun activeTexture(unit: Int) = GLES30.glActiveTexture(GLES30.GL_TEXTURE0 + unit)
    override fun texParameteri(pname: Int, value: Int) = GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, pname, value)
    override fun texImage2D(internalFormat: Int, width: Int, height: Int, format: Int, type: Int) =
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, internalFormat, width, height, 0, format, type, null)

    override fun generateMipmap() = GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D)
    override fun genFramebuffer(): Int {
        GLES30.glGenFramebuffers(1, ints, 0)
        return ints[0]
    }

    override fun bindFramebuffer(framebuffer: Int) = GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, framebuffer)
    override fun framebufferTexture(texture: Int) =
        GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, texture, 0)

    override fun framebufferComplete(): Boolean = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER) == GLES30.GL_FRAMEBUFFER_COMPLETE
    override fun deleteTexture(texture: Int) {
        ints[0] = texture
        GLES30.glDeleteTextures(1, ints, 0)
    }

    override fun deleteFramebuffer(framebuffer: Int) {
        ints[0] = framebuffer
        GLES30.glDeleteFramebuffers(1, ints, 0)
    }

    override fun viewport(x: Int, y: Int, w: Int, h: Int) = GLES30.glViewport(x, y, w, h)
    override fun clearColor(r: Float, g: Float, b: Float, a: Float) = GLES30.glClearColor(r, g, b, a)
    override fun clear() = GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
    override fun enableBlend(on: Boolean) = if (on) GLES30.glEnable(GLES30.GL_BLEND) else GLES30.glDisable(GLES30.GL_BLEND)
    override fun blendFunc(src: Int, dst: Int) = GLES30.glBlendFunc(src, dst)
    override fun blendFuncSeparate(srcRgb: Int, dstRgb: Int, srcA: Int, dstA: Int) = GLES30.glBlendFuncSeparate(srcRgb, dstRgb, srcA, dstA)
    override fun drawArrays(mode: Int, first: Int, count: Int) = GLES30.glDrawArrays(mode, first, count)
    override fun hasExtension(name: String): Boolean = GLES30.glGetString(GLES30.GL_EXTENSIONS)?.split(' ')?.contains(name) == true
    override fun error(): Int = GLES30.glGetError()
}
