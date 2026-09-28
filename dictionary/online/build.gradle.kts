plugins {
    id("vayana.android.library")
    id("vayana.android.hilt")
}

android {
    namespace = "com.vayana.dictionary.online"
}

dependencies {
    implementation(project(":dictionary:api"))
    implementation(project(":core:common"))
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(kotlin("test"))
    testImplementation(libs.org.json)
}
