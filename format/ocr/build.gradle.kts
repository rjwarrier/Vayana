plugins {
    id("vayana.android.library")
}

android {
    namespace = "com.vayana.format.ocr"
}

dependencies {
    implementation(libs.tesseract4android)
    testImplementation(kotlin("test"))
}
