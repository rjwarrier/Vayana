plugins {
    id("vayana.android.library")
    id("vayana.android.hilt")
}

android {
    namespace = "com.vayana.core.filesystem"
}

dependencies {
    implementation(project(":core:common"))
    implementation(libs.androidx.documentfile)
    implementation(libs.kotlinx.coroutines.android)
}
