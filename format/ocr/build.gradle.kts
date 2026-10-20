plugins {
    id("vayana.android.library")
}

android {
    namespace = "com.vayana.format.ocr"
}

dependencies {
    implementation(libs.tesseract4android)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.core.ktx)
    testImplementation(libs.junit5.jupiter)
    testImplementation(libs.org.json)
    testRuntimeOnly(libs.junit5.platform.launcher)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
