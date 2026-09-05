plugins {
    `kotlin-dsl`
}

dependencies {
    compileOnly(libs.kotlin.gradleplugin)
}

gradlePlugin {
    plugins {
        register("cats") {
            id = "cats"
            implementationClass = "cats.CatsCompilerPlugin"
        }
    }
}
