plugins {
    id("vayana.android.feature")
}

android {
    namespace = "com.vayana.feature.notes"
}

dependencies {
    implementation(project(":core:database"))
    implementation(project(":core:filesystem"))
    implementation(libs.coil.compose)
    implementation(libs.coil.core)
}
