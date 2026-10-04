plugins {
    id("vayana.android.feature")
}

android {
    namespace = "com.vayana.feature.notes"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:database"))
    implementation(project(":core:datastore"))
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.mlkit.text.recognition)
    implementation(project(":core:filesystem"))
    implementation(libs.coil.compose)
    implementation(libs.coil.core)
    testImplementation(kotlin("test"))
}
