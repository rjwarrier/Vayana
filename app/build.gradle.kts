import java.io.FileInputStream
import java.util.Properties

plugins {
    id("vayana.android.application")
    id("vayana.android.library.compose")
    id("vayana.android.hilt")
    alias(libs.plugins.kotlin.serialization)
}

val releaseKeystorePropertiesFile = providers.gradleProperty("VAYANA_KEYSTORE_PROPERTIES")
    .orElse(providers.environmentVariable("VAYANA_KEYSTORE_PROPERTIES"))
    .orNull
    ?.let(::file)
    ?: rootProject.file("keystore.properties")
val releaseKeystoreProperties = Properties().apply {
    if (releaseKeystorePropertiesFile.exists()) {
        FileInputStream(releaseKeystorePropertiesFile).use(::load)
    }
}
val releaseSigningConfigured = releaseKeystorePropertiesFile.exists()

android {
    namespace = "com.vayana.app"

    // Lists the translated languages (values-*) for Android 13+ per-app language settings.
    androidResources {
        generateLocaleConfig = true
        // Only the languages Vayana is translated into (keep in step with AppLanguage.Supported); the libraries'
        // translations into dozens more would otherwise ship in resources.arsc for nothing.
        localeFilters += listOf("en", "es", "pt", "ru", "de", "fr", "it", "ml", "ta")
    }

    defaultConfig {
        applicationId = "com.vayana.app"
        versionCode = 2
        versionName = "0.87"
        // ARM phones and tablets, plus 64-bit x86 (emulators, Chromebooks). 32-bit x86 Android devices are gone;
        // its copy of ML Kit's native code was 1.3 MB.
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    signingConfigs {
        if (releaseSigningConfigured) {
            create("release") {
                storeFile = file(releaseKeystoreProperties.getProperty("storeFile"))
                storePassword = releaseKeystoreProperties.getProperty("storePassword")
                keyAlias = releaseKeystoreProperties.getProperty("keyAlias")
                keyPassword = releaseKeystoreProperties.getProperty("keyPassword")
                storeType = releaseKeystoreProperties.getProperty("storeType", "PKCS12")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (releaseSigningConfigured) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
}

val verifyReleaseSigning = tasks.register("verifyReleaseSigning") {
    group = "verification"
    description = "Fails release packaging when no release signing configuration is available."
    inputs.property("releaseSigningConfigured", releaseSigningConfigured)
    doLast {
        check(inputs.properties["releaseSigningConfigured"] == true) {
            "Release signing is required. Set VAYANA_KEYSTORE_PROPERTIES to a signing properties " +
                "file, or add keystore.properties at the project root."
        }
    }
}

tasks.configureEach {
    if (name in setOf("packageRelease", "packageReleaseBundle", "packageReleaseUniversalApk", "signReleaseBundle")) {
        dependsOn(verifyReleaseSigning)
    }
}

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":core:resources"))
    implementation(project(":core:common"))
    implementation(project(":core:database"))
    implementation(project(":core:filesystem"))
    implementation(project(":core:datastore"))
    implementation(project(":core:diagnostics"))

    implementation(project(":feature:library"))
    implementation(project(":feature:notes"))
    implementation(project(":feature:statistics"))
    implementation(project(":feature:onboarding"))
    implementation(project(":feature:reader"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:search"))
    implementation(project(":feature:help"))
    implementation(project(":feature:reminders"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.material3.adaptive)
    implementation(libs.compose.material3.adaptive.layout)
    implementation(libs.compose.material3.adaptive.navigation)
    implementation(libs.kotlinx.serialization.core)
    // Installs app/src/main/baseline-prof.txt on first launch, including for APKs sideloaded from GitHub releases.
    implementation(libs.androidx.profileinstaller)
}
