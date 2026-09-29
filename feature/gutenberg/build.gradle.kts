plugins {
    id("vayana.android.feature")
}

android {
    namespace = "com.vayana.feature.gutenberg"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:database"))
    testImplementation(kotlin("test"))
}
