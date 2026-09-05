plugins {
    alias(libs.plugins.android.library)
}

dependencies {
    implementation(project(":runtime"))
}

android {
    namespace = "cats"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
        consumerProguardFile("proguard-rules.pro")
    }
}
