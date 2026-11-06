package com.kazaeza.muzykalka.crash

import android.content.Context
import android.os.Build
import com.kazaeza.muzykalka.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Ticket R-02. No Play Console: an uncaught exception is written to filesDir/crash.txt and offered
 * for sharing on the next start. The system handler still runs afterwards.
 */
object CrashReporter {
    private const val FILE = "crash.txt"

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val header = buildString {
                    appendLine("Музыкалка ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                    appendLine("${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
                    appendLine(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))
                    appendLine("thread: ${thread.name}")
                    appendLine()
                }
                File(app.filesDir, FILE).writeText(header + sw.toString())
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    fun pending(context: Context): String? = File(context.filesDir, FILE).takeIf { it.exists() }?.readText()

    fun clear(context: Context) {
        File(context.filesDir, FILE).delete()
    }
}
