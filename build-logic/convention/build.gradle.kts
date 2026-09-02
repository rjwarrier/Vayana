plugins {
    `kotlin-dsl`
}

group = "com.vayana.buildlogic"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)
    compileOnly(libs.ksp.gradlePlugin)
    compileOnly(libs.hilt.gradlePlugin)
    compileOnly(libs.room.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "vayana.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "vayana.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidFeature") {
            id = "vayana.android.feature"
            implementationClass = "AndroidFeatureConventionPlugin"
        }
        register("androidLibraryCompose") {
            id = "vayana.android.library.compose"
            implementationClass = "AndroidComposeConventionPlugin"
        }
        register("androidHilt") {
            id = "vayana.android.hilt"
            implementationClass = "AndroidHiltConventionPlugin"
        }
        register("kotlinJvm") {
            id = "vayana.kotlin.jvm"
            implementationClass = "KotlinJvmConventionPlugin"
        }
        register("konsist") {
            id = "vayana.konsist"
            implementationClass = "KonsistConventionPlugin"
        }
    }
}
