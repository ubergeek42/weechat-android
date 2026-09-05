import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `embedded-kotlin`
    kotlin("plugin.power-assert") version embeddedKotlinVersion
}

dependencies {
    implementation(libs.kotlin.compiler)
    testImplementation(project(":runtime"))
    testImplementation(libs.kotlinx.coroutines.core)
    testImplementation(kotlin("test"))
}

// This is a bit of a hacky way of injecting the compiler plugin into the test compilation pipeline.
// As we are not using the *gradle* plugin here, we need to pass the required option as well.
tasks.named<KotlinCompile>("compileTestKotlin") {
    pluginClasspath.from(tasks.named("jar"))
    pluginClasspath.from(configurations.runtimeClasspath)

    compilerOptions {
        freeCompilerArgs.addAll("-P", "plugin:cats:processTraceAndDebugCalls=true")
    }
}