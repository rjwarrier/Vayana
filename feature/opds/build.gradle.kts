plugins {
    id("vayana.android.feature")
}

android {
    namespace = "com.vayana.feature.opds"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:database"))
    testImplementation(kotlin("test"))
}
