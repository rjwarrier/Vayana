plugins {
    id("vayana.android.library")
    id("vayana.android.library.compose")
    id("vayana.konsist")
}

android {
    namespace = "com.vayana.core.designsystem"

    androidResources.enable = true
}

dependencies {
    // material3-adaptive is wired in :app (VayanaAppRoot) for the tablet nav-rail layout —
    // see docs/DECISIONS.md.
    implementation(libs.compose.material.icons.extended)
    implementation(libs.androidx.core.ktx)
    implementation(project(":core:common"))
    implementation(project(":core:resources"))
}
