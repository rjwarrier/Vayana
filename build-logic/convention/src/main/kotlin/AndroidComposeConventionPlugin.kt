import com.android.build.api.dsl.CommonExtension
import com.vayana.buildlogic.convention.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/** Wires the Compose compiler plugin + BOM onto any Android module (app or library). */
class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

            // extensions.getByType<CommonExtension<*, *, *, *, *, *>>() fails at runtime: the
            // reified/TypeOf lookup wants an exact generic-signature match, but the registered
            // extension is a concrete ApplicationExtension/LibraryExtension. The raw Class
            // overload does an isAssignableFrom check instead, which finds it — see docs/DECISIONS.md.
            extensions.getByType(CommonExtension::class.java).apply {
                buildFeatures.compose = true
            }

            dependencies {
                val bom = libs.findLibrary("compose-bom").get()
                add("implementation", platform(bom))
                add("androidTestImplementation", platform(bom))
                add("implementation", libs.findLibrary("compose-ui").get())
                add("implementation", libs.findLibrary("compose-ui-graphics").get())
                add("implementation", libs.findLibrary("compose-ui-tooling-preview").get())
                add("implementation", libs.findLibrary("compose-material3").get())
                add("debugImplementation", libs.findLibrary("compose-ui-tooling").get())
                add("debugImplementation", libs.findLibrary("compose-ui-test-manifest").get())
            }
        }
    }
}
