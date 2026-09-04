plugins {
    id("vayana.android.feature")
}

android {
    namespace = "com.vayana.feature.reader"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":reader:engine-api"))
    implementation(project(":reader:engine-web"))
    implementation(project(":core:database"))
    implementation(project(":core:datastore"))
    implementation(project(":core:filesystem"))
    implementation(project(":dictionary:api"))
    implementation(project(":dictionary:stardict"))
}
