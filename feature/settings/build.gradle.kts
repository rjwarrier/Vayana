plugins {
    id("vayana.android.feature")
}

android {
    namespace = "com.vayana.feature.settings"
}

dependencies {
    implementation(project(":core:datastore"))
}
