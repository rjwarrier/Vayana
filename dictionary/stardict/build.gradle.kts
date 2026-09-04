plugins {
    id("vayana.android.library")
    id("vayana.android.hilt")
}

android {
    namespace = "com.vayana.dictionary.stardict"
}

dependencies {
    implementation(project(":dictionary:api"))
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(kotlin("test"))
}
