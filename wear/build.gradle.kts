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
        versionCode = 1000002
        versionName = "0.87"
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
        release { if (signingFile.exists()) signingConfig = signingConfigs.getByName("release") }
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
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation("androidx.test:runner:1.7.0")
}
