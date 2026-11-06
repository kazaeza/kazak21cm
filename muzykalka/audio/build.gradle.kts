// Android audio I/O: AudioTrack output, AudioRecord input + pitch pipeline, TTS, device routing.
plugins {
    alias(libs.plugins.muzykalka.android.library)
}

android {
    namespace = "com.kazaeza.muzykalka.audio"
}

dependencies {
    api(projects.core.dsp)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
}
