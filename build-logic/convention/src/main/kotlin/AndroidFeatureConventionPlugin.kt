import com.vayana.buildlogic.convention.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.project

/** A `:feature:*` module: Android library + Compose + Hilt + the core modules every feature needs. */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("vayana.android.library")
                apply("vayana.android.library.compose")
                apply("vayana.android.hilt")
            }

            dependencies {
                add("implementation", project(":core:designsystem"))
                add("implementation", project(":core:resources"))
                add("implementation", project(":core:common"))
                add("implementation", libs.findLibrary("androidx-lifecycle-viewmodel-compose").get())
                add("implementation", libs.findLibrary("androidx-navigation-compose").get())
                add("implementation", libs.findLibrary("hilt-navigation-compose").get())
                add("implementation", libs.findLibrary("kotlinx-coroutines-android").get())
                add("implementation", libs.findLibrary("compose-material-icons-extended").get())
            }
        }
    }
}
