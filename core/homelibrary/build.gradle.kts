plugins {
    id("vayana.android.library")
    id("vayana.android.hilt")
}

android {
    namespace = "com.vayana.core.homelibrary"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:database"))
    implementation(project(":core:datastore"))
    implementation(project(":core:diagnostics"))
    implementation(project(":core:filesystem"))
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation("org.robolectric:robolectric:4.13")
}
