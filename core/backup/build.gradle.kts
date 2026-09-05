plugins {
    id("vayana.android.library")
    id("vayana.android.hilt")
}

android {
    namespace = "com.vayana.core.backup"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:database"))
    implementation(project(":core:datastore"))
    implementation(project(":core:filesystem"))

    implementation(libs.kotlinx.coroutines.android)

    testImplementation(kotlin("test"))
    testImplementation(libs.org.json)
}
