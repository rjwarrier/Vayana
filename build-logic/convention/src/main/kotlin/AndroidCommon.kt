import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

/**
 * Compile SDK / min SDK / Java 17 / Kotlin JVM target — one place, per §0.5 layering rules.
 * AGP 9's `CommonExtension` is non-generic and dropped the `defaultConfig{}`/`compileOptions{}`
 * lambda sugar `ApplicationExtension` still has — set via property access instead (docs/DECISIONS.md).
 */
internal fun Project.configureAndroidCommon(extension: CommonExtension) {
    extension.apply {
        compileSdk = 37
        defaultConfig.minSdk = 26
        compileOptions.sourceCompatibility = JavaVersion.VERSION_17
        compileOptions.targetCompatibility = JavaVersion.VERSION_17
    }

    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
}
