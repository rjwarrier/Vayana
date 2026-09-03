plugins {
    id("vayana.android.feature")
}

android {
    namespace = "com.vayana.feature.notes"
}

dependencies {
    implementation(project(":core:database"))
    implementation(libs.coil.compose)
    implementation(libs.coil.core)
}
