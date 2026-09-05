import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.kotlin.dsl.withType

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
        testLogging {
            outputs.upToDateWhen { false } // always rerun tests

            events("skipped", "failed")

            // When performing Project Clean, Android Studio spawns multiple processes
            // if a gradle wrapper is not present in included builds.
            // If you get an error here because of that,
            // add an external tool that runs `$ProjectFileDir$/gradlew clean`
            addTestListener(object : TestListener {
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