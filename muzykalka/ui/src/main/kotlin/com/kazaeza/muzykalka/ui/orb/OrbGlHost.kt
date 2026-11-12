package com.kazaeza.muzykalka.ui.orb

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Process
import android.util.Log
import android.view.Choreographer
import android.view.TextureView
import android.view.View
import com.kazaeza.muzykalka.music.OrbBody
import kotlin.math.min

private const val TAG = "OrbGl"

/** The longest step the picture is advanced by: after a hitch it does not jump, it slows down. */
private const val MAX_STEP = 0.05

/**
 * O-38 (ADR-040): one OpenGL ES 3.0 context on a thread of its own, drawing into a [SurfaceTexture] (a TextureView with
 * a see-through background) once per screen frame — the body of the sphere is a renderer that [make] builds there.
 * If anything fails (no ES 3.0, a shader that does not compile, no float targets, a lost surface) [onFailed] is
 * called on the main thread, once, and the thread stops; the caller then paints the old drop instead.
 */
class OrbGlHost(
    private val make: (Gles) -> OrbRenderer,
    private val onFailed: (Throwable) -> Unit,
) {
    private val thread = HandlerThread("orb-gl", Process.THREAD_PRIORITY_DISPLAY).also { it.start() }
    private val handler = Handler(thread.looper)
    private val main = Handler(Looper.getMainLooper())

    // everything below is touched on the GL thread only
    private var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var context: EGLContext = EGL14.EGL_NO_CONTEXT
    private var surface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var renderer: OrbRenderer? = null
    private var alive = false
    private var shown = true
    private var lastNanos = 0L

    @Volatile
    private var stopped = false

    private val frame = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            val r = renderer ?: return
            if (!alive || !shown) return
            val dt = if (lastNanos == 0L) 1.0 / 60 else min((frameTimeNanos - lastNanos) / 1e9, MAX_STEP)
            lastNanos = frameTimeNanos
            try {
                r.draw(dt)
                check(EGL14.eglSwapBuffers(display, surface)) { "eglSwapBuffers: 0x${Integer.toHexString(EGL14.eglGetError())}" }
            } catch (t: Throwable) {
                fail(t)
                return
            }
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    fun start(texture: SurfaceTexture, w: Int, h: Int) {
        handler.post {
            if (stopped) return@post
            try {
                setUp(texture)
                val r = make(AndroidGles())
                renderer = r
                r.init(w, h)
                alive = true
                lastNanos = 0L
                if (shown) Choreographer.getInstance().postFrameCallback(frame)
            } catch (t: Throwable) {
                fail(t)
            }
        }
    }

    fun resize(w: Int, h: Int) {
        handler.post { if (alive) runCatching { renderer?.resize(w, h) }.onFailure { fail(it) } }
    }

    /** Draws only while the screen is in view: nothing runs for a sphere that nobody sees. */
    fun setShown(on: Boolean) {
        handler.post {
            if (shown == on) return@post
            shown = on
            Choreographer.getInstance().removeFrameCallback(frame)
            if (on && alive) {
                lastNanos = 0L
                Choreographer.getInstance().postFrameCallback(frame)
            }
        }
    }

    /** Stops the thread and frees the context; returns when it is done (the surface may be taken away afterwards). */
    fun stop() {
        if (stopped) return
        stopped = true
        handler.post { tearDown() }
        thread.quitSafely()
        runCatching { thread.join(JOIN_MS) }
    }

    private fun fail(t: Throwable) {
        Log.w(TAG, "the sphere cannot be drawn with OpenGL: ${t.message}")
        tearDown()
        if (!stopped) main.post { onFailed(t) }
    }

    private fun setUp(texture: SurfaceTexture) {
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        check(display != EGL14.EGL_NO_DISPLAY) { "no EGL display" }
        val version = IntArray(2)
        check(EGL14.eglInitialize(display, version, 0, version, 1)) { "eglInitialize" }
        val want = intArrayOf(
            EGL14.EGL_RENDERABLE_TYPE, EGLExt.EGL_OPENGL_ES3_BIT_KHR,
            EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_DEPTH_SIZE, 0, EGL14.EGL_STENCIL_SIZE, 0,
            EGL14.EGL_NONE,
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val count = IntArray(1)
        check(EGL14.eglChooseConfig(display, want, 0, configs, 0, 1, count, 0) && count[0] > 0) { "no ES 3.0 config" }
        val config = checkNotNull(configs[0])
        context = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT, intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE), 0)
        check(context != EGL14.EGL_NO_CONTEXT) { "no ES 3.0 context" }
        surface = EGL14.eglCreateWindowSurface(display, config, texture, intArrayOf(EGL14.EGL_NONE), 0)
        check(surface != EGL14.EGL_NO_SURFACE) { "no window surface" }
        check(EGL14.eglMakeCurrent(display, surface, surface, context)) { "eglMakeCurrent" }
    }

    private fun tearDown() {
        alive = false
        Choreographer.getInstance().removeFrameCallback(frame)
        runCatching { renderer?.release() }
        renderer = null
        if (display != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            if (surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface)
            if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context)
            EGL14.eglTerminate(display)
        }
        display = EGL14.EGL_NO_DISPLAY
        surface = EGL14.EGL_NO_SURFACE
        context = EGL14.EGL_NO_CONTEXT
    }

    private companion object {
        const val JOIN_MS = 1000L
    }
}

/** What the process has learned about this device: a body that failed once is not tried again until the app restarts. */
object OrbGlSupport {
    private val failed = HashSet<OrbBody>()

    /** The device claims OpenGL ES 3.0 (every phone since Android 5 does; an emulator may not). */
    fun available(context: Context): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager ?: return false
        return am.deviceConfigurationInfo.reqGlEsVersion >= ES3
    }

    @Synchronized
    fun failed(body: OrbBody): Boolean = body in failed

    @Synchronized
    fun markFailed(body: OrbBody) {
        failed.add(body)
    }

    private const val ES3 = 0x30000
}

/** The renderer for [body]; [OrbBody.DROP] is not drawn with OpenGL. */
fun createRenderer(body: OrbBody, gl: Gles, input: OrbInput, gold: FloatArray): OrbRenderer = when (body) {
    OrbBody.THREAD -> ThreadRenderer(gl, input, gold)
    OrbBody.INK -> InkRenderer(gl, input, gold)
    OrbBody.SAND -> SandRenderer(gl, input, gold)
    OrbBody.DROP -> error("the drop is painted by LensOrb")
}

/**
 * The see-through TextureView the [OrbGlHost] draws on. It builds the host when its surface appears, and stops it
 * (synchronously) when the surface is taken away; it is not drawn while the window is hidden.
 */
class OrbSurfaceView(
    context: Context,
    private val make: (Gles) -> OrbRenderer,
    private val onFailed: (Throwable) -> Unit,
) : TextureView(context), TextureView.SurfaceTextureListener {
    private var host: OrbGlHost? = null

    init {
        isOpaque = false
        surfaceTextureListener = this
    }

    private fun inView() = isShown && windowVisibility == View.VISIBLE

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        val h = OrbGlHost(make, onFailed)
        host = h
        h.setShown(inView())
        h.start(surface, width, height)
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        host?.resize(width, height)
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        host?.stop()
        host = null
        return true
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        host?.setShown(inView())
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        host?.setShown(inView())
    }
}
