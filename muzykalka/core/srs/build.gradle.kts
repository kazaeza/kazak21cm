// Spaced repetition (FSRS-6, day granularity). Zero runtime dependencies.
plugins {
    alias(libs.plugins.muzykalka.jvm.library)
}

dependencies {
    testImplementation(libs.kotlinx.serialization.json) // only to read the golden-vector fixture
}
