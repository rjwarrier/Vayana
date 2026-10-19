plugins {
    id("vayana.android.feature")
}

android {
    namespace = "com.vayana.feature.statistics"
}

dependencies {
    implementation(libs.coil.compose)
    implementation(project(":core:common"))
    implementation(project(":core:database"))
    implementation(project(":core:datastore"))
    implementation(project(":core:filesystem"))
    implementation(project(":dictionary:api"))
    testImplementation(kotlin("test"))
}
