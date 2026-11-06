package com.kazaeza.muzykalka

import android.app.Application

class MuzykalkaApp : Application() {
    /** Manual DI root. Everything heavy is lazy: nothing touches audio or disk until a screen asks. */
    val graph: AppGraph by lazy { AppGraph(this) }
}
