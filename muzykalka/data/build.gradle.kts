// Persistence: Room (library, SRS cards, attempts, sessions, exams) + DataStore (settings) + backup.
plugins {
    alias(libs.plugins.muzykalka.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room)
}

android {
    namespace = "com.kazaeza.muzykalka.data"
}

room {
    // Exported schemas are committed. Every version bump needs a Migration + a migration test.
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    api(projects.core.music)
    api(projects.core.srs)
    api(projects.core.game)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.kotlinx.coroutines.test)
}
