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
    // material3-adaptive intentionally NOT added yet: 1.3.0 requires AGP 9.1+/compileSdk 37,
    // and nothing uses adaptive-pane layouts yet. Add it back (checking compatible versions
    // again first) when a milestone actually implements tablet/foldable panes — see docs/DECISIONS.md.
    implementation(libs.compose.material.icons.extended)
    implementation(libs.androidx.core.ktx)
    implementation(project(":core:common"))
}
