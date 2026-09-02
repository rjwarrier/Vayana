plugins {
    id("vayana.android.feature")
}

android {
    namespace = "com.vayana.feature.notes"
}

dependencies {
    implementation(project(":core:database"))
}
