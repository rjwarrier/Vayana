plugins {
    id("vayana.android.feature")
}

android {
    namespace = "com.vayana.feature.onboarding"
}

dependencies {
    implementation(project(":core:datastore"))
}
