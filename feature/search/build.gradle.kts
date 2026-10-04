plugins {
    id("vayana.android.feature")
}

android {
    namespace = "com.vayana.feature.search"
}

dependencies {
    implementation(project(":core:database"))
    implementation(project(":core:datastore"))
    implementation(project(":core:filesystem"))
    implementation(project(":format:epub"))
    implementation(libs.coil.compose)
    implementation(libs.coil.core)

    testImplementation(kotlin("test"))
}
