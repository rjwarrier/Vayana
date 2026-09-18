plugins {
    id("vayana.android.feature")
}

android {
    namespace = "com.vayana.feature.library"
}

dependencies {
    implementation(project(":core:backup"))
    implementation(project(":core:database"))
    implementation(project(":core:datastore"))
    implementation(project(":core:diagnostics"))
    implementation(project(":core:filesystem"))
    implementation(project(":core:sync"))
    implementation(project(":format:epub"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.documentfile)
    implementation(libs.coil.compose)
    implementation(libs.coil.core)
    implementation(libs.mlkit.language.id)
    testImplementation(kotlin("test"))
    testImplementation(libs.org.json)
    testImplementation(libs.room.runtime)
    testImplementation("org.robolectric:robolectric:4.13")
}
