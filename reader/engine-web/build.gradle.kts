plugins {
    id("vayana.android.library")
}

android {
    namespace = "com.vayana.reader.web"
}

dependencies {
    api(project(":reader:engine-api"))
    implementation(project(":core:common"))
    implementation(libs.androidx.webkit)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit5.jupiter)
    testRuntimeOnly(libs.junit5.platform.launcher)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
