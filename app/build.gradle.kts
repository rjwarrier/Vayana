plugins {
    id("vayana.android.application")
    id("vayana.android.library.compose")
    id("vayana.android.hilt")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.vayana.app"

    defaultConfig {
        applicationId = "com.vayana.app"
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":core:resources"))
    implementation(project(":core:common"))
    implementation(project(":core:datastore"))

    implementation(project(":feature:library"))
    implementation(project(":feature:notes"))
    implementation(project(":feature:statistics"))
    implementation(project(":feature:reader"))
    implementation(project(":feature:settings"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.kotlinx.serialization.core)
}
