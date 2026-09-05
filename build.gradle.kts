import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask

defaultTasks("assembleDebug")

buildscript {
    dependencies {
        classpath(libs.aspectj.tools)
        classpath(libs.aspectjpipeline)
    }
}

subprojects {
    tasks.withType<Test> {
        useJUnitPlatform()                      // aka JUnit 5
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    id("common")

    // to print a sensible task graph, uncomment the following line and run:
    //   $ gradlew :app:assembleDebug taskTree --no-repeat
    //alias(libs.plugins.tasktree)
}

// This and below is the configuration for the Gradle Version Plugin,
// taken verbatim from the recommended configuration section its readme as of version 0.61.0
// See https://github.com/ben-manes/gradle-versions-plugin#a-recommended-configuration
fun String.isNonStable(): Boolean {
    val stableKeyword = listOf("RELEASE", "FINAL", "GA").any { uppercase().contains(it) }
    val regex = "^[0-9,.v-]+(-r|-jre|-android)?$".toRegex()
    val isStable = stableKeyword || regex.matches(this)
    return isStable.not()
}

tasks.named<DependencyUpdatesTask>("dependencyUpdates") {
    checkConstraints = true
    rejectVersionIf {
        (candidate.version.isNonStable() && !currentVersion.isNonStable()) ||
                !satisfiesDeclaredBound
    }
}

subprojects {
    tasks.all {
        if (name == "clean") {
            dependsOn(gradle.includedBuild("build-logic").task(":clean"))
            dependsOn(gradle.includedBuild("cats2").task(":gradle-plugin:clean"))
            dependsOn(gradle.includedBuild("cats2").task(":compiler-plugin:clean"))
            dependsOn(gradle.includedBuild("cats2").task(":runtime:clean"))
            dependsOn(gradle.includedBuild("cats2").task(":android-runtime:clean"))
        }
        if (name == "test") {
            dependsOn(gradle.includedBuild("cats2").task(":compiler-plugin:test"))
        }
    }
}

// Uncomment to verify IR and see the IR dumps.
// See https://kotlinlang.org/docs/custom-compiler-plugins.html#check-your-backend-plugin-code-for-problems
//tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
//    compilerOptions {
//        freeCompilerArgs.add("-Xverify-ir=error")
//
//        freeCompilerArgs.addAll(
//            "-Xphases-to-dump-before=ExternalPackageParentPatcherLowering", // Or ALL
//            "-Xdump-directory=${layout.buildDirectory.get().asFile}/ir-dumps"
//        )
//    }
//}