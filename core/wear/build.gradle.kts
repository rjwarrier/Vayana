plugins { id("vayana.android.library") }

android { namespace = "com.vayana.core.wear" }

dependencies {
    api(project(":core:common"))
    api("com.google.android.gms:play-services-wearable:20.0.1")
    api(libs.androidx.work.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(kotlin("test"))
    testImplementation(libs.org.json)
}
