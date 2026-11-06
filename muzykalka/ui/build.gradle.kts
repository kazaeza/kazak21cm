// Design system: theme, degree colours, big-touch components (hands are on the guitar).
plugins {
    alias(libs.plugins.muzykalka.android.library)
    alias(libs.plugins.muzykalka.android.compose)
}

android {
    namespace = "com.kazaeza.muzykalka.ui"
}

dependencies {
    api(projects.core.music)
}
