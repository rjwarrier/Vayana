plugins {
    id("vayana.android.library")
}

android {
    namespace = "com.vayana.core.resources"

    androidResources.enable = true
}

dependencies {
    api(libs.androidx.annotation)

    testImplementation(kotlin("test"))
}
