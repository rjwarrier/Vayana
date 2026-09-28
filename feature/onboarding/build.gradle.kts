plugins {
    id("vayana.android.feature")
}

android {
    namespace = "com.vayana.feature.onboarding"
}

dependencies {
    implementation(project(":core:datastore"))
    implementation(project(":core:sync"))
    implementation(project(":core:resources"))
    implementation(libs.androidx.activity.compose)
}
