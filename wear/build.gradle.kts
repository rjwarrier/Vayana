import java.util.Properties

plugins { id("vayana.android.application") }

val signingFile = providers.gradleProperty("VAYANA_KEYSTORE_PROPERTIES")
    .orElse(providers.environmentVariable("VAYANA_KEYSTORE_PROPERTIES")).orNull?.let(::file)
    ?: rootProject.file("keystore.properties")
val signing = Properties().apply { if (signingFile.exists()) signingFile.inputStream().use(::load) }

android {
    namespace = "com.vayana.wear"
    defaultConfig {
        // Data Layer requires the phone package identity and signing key.
        applicationId = "com.vayana.app"
        minSdk = 30
        versionCode = 3
        versionName = "0.90"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    signingConfigs {
        if (signingFile.exists()) create("release") {
            storeFile = rootProject.file("app").resolve(signing.getProperty("storeFile"))
            storePassword = signing.getProperty("storePassword")
            keyAlias = signing.getProperty("keyAlias")
            keyPassword = signing.getProperty("keyPassword")
            storeType = signing.getProperty("storeType", "PKCS12")
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            if (signingFile.exists()) signingConfig = signingConfigs.getByName("release")
        }
    }
}
val verifyReleaseSigning = tasks.register("verifyReleaseSigning") {
    inputs.property("configured", signingFile.exists())
    doLast { check(inputs.properties["configured"] == true) { "Use the phone's VAYANA_KEYSTORE_PROPERTIES for watch release signing." } }
}
tasks.configureEach {
    if (name in setOf("packageRelease", "packageReleaseBundle", "packageReleaseUniversalApk", "signReleaseBundle")) {
        dependsOn(verifyReleaseSigning)
    }
}
dependencies {
    implementation(project(":core:wear"))
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)
    implementation("androidx.wear:wear-ongoing:1.1.0")
    implementation("androidx.wear:wear:1.4.0")
    implementation("androidx.activity:activity:1.13.0")
    implementation("androidx.wear.watchface:watchface-complications-data-source:1.3.0")
    implementation("androidx.wear.tiles:tiles:1.5.0")
    implementation("androidx.wear.protolayout:protolayout:1.3.0")
    implementation("com.google.guava:guava:33.4.8-android")
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation("androidx.test:runner:1.7.0")
}
