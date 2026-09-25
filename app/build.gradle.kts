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

android {
    namespace = "com.vayana.app"

    defaultConfig {
        applicationId = "com.vayana.app"
        versionCode = 1
        versionName = "0.85"
    }

    signingConfigs {
        if (releaseKeystorePropertiesFile.exists()) {
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
            if (releaseKeystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
}

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":core:resources"))
    implementation(project(":core:common"))
    implementation(project(":core:database"))
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

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.material3.adaptive)
    implementation(libs.compose.material3.adaptive.layout)
    implementation(libs.compose.material3.adaptive.navigation)
    implementation(libs.kotlinx.serialization.core)
}
