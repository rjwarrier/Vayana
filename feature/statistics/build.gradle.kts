plugins {
    id("vayana.android.feature")
}

android {
    namespace = "com.vayana.feature.statistics"
}

dependencies {
    implementation(project(":core:database"))
    implementation(project(":core:datastore"))
    implementation(project(":core:filesystem"))
    testImplementation(kotlin("test"))
}
