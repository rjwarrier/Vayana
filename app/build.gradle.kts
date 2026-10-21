import java.io.FileInputStream
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
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
        versionCode = 5
        versionName = "0.93"
    }

    // One APK per CPU type, so a phone downloads only its own native code (text recognition's engine is ~7.5 MB of
    // it per type), plus a universal APK for anyone unsure which to take. ARM phones and tablets, and 64-bit x86
    // (emulators, Chromebooks); 32-bit x86 Android devices are gone (its copy of ML Kit's native code was 1.3 MB).
    // The APKs share a version code: they are installed from GitHub, not Google Play, and any of them updates any.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = true
        }
    }
    // ... and nothing for 32-bit x86, not even in the universal APK (the split list above doesn't reach it, and
    // ndk.abiFilters can't be combined with splits)
    packaging {
        jniLibs {
            excludes += "lib/x86/**"
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
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (releaseSigningConfigured) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
}

/**
 * Writes assets/build-info.properties: the build number (version code + local build date and time, e.g.
 * 3.20261013.2245) and build time. A task rather than a BuildConfig field, because the configuration cache would
 * freeze a timestamp taken while configuring; never up to date, so every build gets its own number.
 */
abstract class GenerateBuildInfo : DefaultTask() {
    @get:Input
    abstract val versionCode: Property<Int>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    init {
        outputs.upToDateWhen { false }
    }

    @TaskAction
    fun write() {
        val now = ZonedDateTime.now()
        val buildNumber = "${versionCode.get()}.${now.format(DateTimeFormatter.ofPattern("yyyyMMdd.HHmm"))}"
        outputDirectory.file("build-info.properties").get().asFile.writeText(
            "buildNumber=$buildNumber\nbuiltAt=${now.toInstant().toEpochMilli()}\n",
        )
    }
}

androidComponents {
    onVariants { variant ->
        val buildInfo = tasks.register<GenerateBuildInfo>("generate${variant.name.replaceFirstChar(Char::uppercase)}BuildInfo") {
            // one output per CPU type (ABI splits), all with the same version code
            versionCode.set(variant.outputs.first().versionCode.map { it })
        }
        variant.sources.assets?.addGeneratedSourceDirectory(buildInfo, GenerateBuildInfo::outputDirectory)
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
    implementation(project(":core:wear"))
    // The debug-only adb trigger for text recognition (OcrSpikeReceiver); the feature itself comes in with the reader.
    debugImplementation(project(":format:ocr"))
    testImplementation(kotlin("test"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:resources"))
    implementation(project(":core:common"))
    implementation(project(":core:database"))
    implementation(project(":core:filesystem"))
    implementation(project(":core:datastore"))
    implementation(project(":core:diagnostics"))
    implementation(project(":core:homelibrary"))
    implementation(project(":core:sync"))

    implementation(project(":feature:library"))
    implementation(project(":feature:notes"))
    implementation(project(":feature:statistics"))
    implementation(project(":feature:onboarding"))
    implementation(project(":feature:reader"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:search"))
    implementation(project(":feature:help"))
    implementation(project(":feature:reminders"))
    implementation(project(":feature:gutenberg"))
    implementation(project(":feature:opds"))

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
