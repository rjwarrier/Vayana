plugins {
    id("vayana.android.library")
    id("vayana.android.hilt")
}

android {
    namespace = "com.vayana.core.sync"
}

dependencies {
    implementation(project(":core:backup"))
    implementation(project(":core:common"))
    implementation(project(":core:database"))
    implementation(project(":core:datastore"))
    implementation(project(":core:diagnostics"))
    implementation(project(":core:filesystem"))

    implementation(libs.kotlinx.coroutines.android)

    testImplementation(kotlin("test"))
}
