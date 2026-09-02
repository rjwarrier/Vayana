import com.vayana.buildlogic.convention.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType

/**
 * Architecture-rule tests (Konsist) — enforces §0 rules (no bare .dp/.sp/hex colors outside
 * `design/tokens`, no `!!`, etc.) as a JVM test rather than a custom Android Lint check.
 * See docs/DECISIONS.md for why Konsist was chosen over a hand-rolled lint rule module.
 */
class KonsistConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            dependencies {
                add("testImplementation", libs.findLibrary("konsist").get())
                add("testImplementation", libs.findLibrary("junit5-jupiter").get())
                add("testRuntimeOnly", libs.findLibrary("junit5-platform-launcher").get())
            }

            tasks.withType<Test>().configureEach {
                useJUnitPlatform()
            }
        }
    }
}
