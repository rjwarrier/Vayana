plugins {
    id("vayana.android.feature")
}

android {
    namespace = "com.vayana.feature.settings"
}

tasks.withType<Test>().configureEach {
    val schemas = rootProject.file("core/database/schemas/com.vayana.core.database.VayanaDatabase")
    inputs.dir(schemas)
    systemProperty("vayana.test.schemas", schemas.absolutePath)
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:database"))
    implementation(project(":core:datastore"))
    implementation(project(":core:diagnostics"))
    implementation(project(":core:filesystem"))
    implementation(project(":core:sync"))
    implementation(libs.room.runtime)
    testImplementation(kotlin("test"))
    testImplementation("org.robolectric:robolectric:4.13")
}
