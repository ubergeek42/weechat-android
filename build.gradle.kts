import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import org.gradle.api.plugins.JavaPluginExtension

defaultTasks("assembleDebug")

buildscript {
    dependencies {
        classpath(libs.aspectj.tools)
        classpath(libs.aspectjpipeline)
    }
}

subprojects {
    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
    }

    plugins.withType<JavaBasePlugin> {
        extensions.configure<JavaPluginExtension> {
            toolchain.languageVersion = JavaLanguageVersion.of(21)
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform()                      // aka JUnit 5

        testLogging {
            outputs.upToDateWhen { false }      // always rerun tests

            events("skipped", "failed")

            // https://github.com/gradle/gradle/issues/5431
            // https://github.com/gradle/kotlin-dsl-samples/issues/836#issuecomment-384206237
            addTestListener(object : TestListener {
                override fun beforeSuite(suite: TestDescriptor) {}
                override fun beforeTest(testDescriptor: TestDescriptor) {}
                override fun afterTest(testDescriptor: TestDescriptor, result: TestResult) {}
                override fun afterSuite(suite: TestDescriptor, result: TestResult) {
                    // print only the bottom-level test result information
                    if (suite.className == null) return

                    val details = if (result.skippedTestCount > 0 || result.failedTestCount > 0) {
                        ": ${result.successfulTestCount} successes, " +
                                "${result.failedTestCount} failures, " +
                                "${result.skippedTestCount} skipped"
                    } else {
                        ""
                    }

                    println("${suite.displayName}: ${result.resultType} " +
                            "(${result.testCount} tests$details)")
                }
            })
        }
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp) apply false

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