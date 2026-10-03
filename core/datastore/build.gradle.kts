plugins {
    id("vayana.android.library")
    id("vayana.android.hilt")
}

android {
    namespace = "com.vayana.core.datastore"
}

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":core:resources"))
    implementation(libs.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(kotlin("test"))
    testImplementation(libs.org.json)
}
