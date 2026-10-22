plugins {
    id("vayana.android.feature")
}

android {
    namespace = "com.vayana.feature.search"
}

dependencies {
    implementation(project(":core:database"))
    implementation(project(":core:datastore"))
    implementation(project(":core:filesystem"))
    implementation(project(":format:epub"))
    implementation(project(":format:ocr"))
    implementation(libs.coil.compose)
    implementation(libs.coil.core)

    testImplementation(kotlin("test"))
    testImplementation(libs.room.runtime)
    testImplementation("org.robolectric:robolectric:4.13")
}
