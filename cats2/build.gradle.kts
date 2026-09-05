plugins {
    `embedded-kotlin` apply false
    `kotlin-dsl` apply false
    alias(libs.plugins.android.library) apply false
    id("common")
}

subprojects {
    group = "cats"
    version = "unspecified"
}
