// Signal processing: pitch detection, note segmentation, synthesis. Pure Kotlin, no Android.
plugins {
    alias(libs.plugins.muzykalka.jvm.library)
}

dependencies {
    api(projects.core.music)
}
