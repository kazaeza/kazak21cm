package com.kazaeza.muzykalka

import android.app.Application
import com.kazaeza.muzykalka.crash.CrashReporter

class MuzykalkaApp : Application() {
    /** Manual DI root. */
    val graph: AppGraph by lazy { AppGraph(this) }

    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)
    }
}
