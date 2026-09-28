plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.vayana.baselineprofile"
    compileSdk = 37

    defaultConfig {
        minSdk = 28
        targetSdk = 37
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // The profile is taken from a debuggable build on an emulator; both are fine for collection, not timing.
        testInstrumentationRunnerArguments["androidx.benchmark.suppressErrors"] = "DEBUGGABLE,EMULATOR"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    targetProjectPath = ":app"
    // Macrobenchmark drives Vayana from outside: the test runs in its own process, not inside the app it stops.
    experimentalProperties["android.experimental.self-instrumenting"] = true

    // openFd needs the sample book stored uncompressed in the APK.
    androidResources {
        noCompress += "epub"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

// Records Vayana's baseline profile against the debug build (same class and method names as before R8, which
// rewrites the profile for release). Run on an emulator only - it installs and uninstalls the app:
//   ANDROID_SERIAL=emulator-5554 ./gradlew :baselineprofile:connectedDebugAndroidTest
// then copy the generated profile to app/src/main/baseline-prof.txt (see docs/BASELINE_PROFILE.md).

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
