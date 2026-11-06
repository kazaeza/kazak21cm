// Game rules: modes, rounds, scoring, hint ladder, set planner, metrics. Pure Kotlin, deterministic.
plugins {
    alias(libs.plugins.muzykalka.jvm.library)
}

dependencies {
    api(projects.core.music)
    api(projects.core.srs)
}
