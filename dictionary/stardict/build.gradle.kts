plugins {
    id("vayana.android.library")
    id("vayana.android.hilt")
}

android {
    namespace = "com.vayana.dictionary.stardict"
}

dependencies {
    implementation(project(":dictionary:api"))
    implementation(project(":core:resources"))
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.xz)

    testImplementation(kotlin("test"))
    testImplementation(libs.org.json)
}
