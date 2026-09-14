plugins {
    id("vayana.android.library")
    id("vayana.android.hilt")
}

android {
    namespace = "com.vayana.core.filesystem"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:database"))
    implementation(libs.androidx.documentfile)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(kotlin("test"))
}
