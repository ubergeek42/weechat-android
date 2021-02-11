subprojects {
    repositories {
        mavenCentral()
        google()
        jcenter()
        maven { url = uri("https://jitpack.io") }
    }
}

// to print a sensible task graph, uncomment the following lines and run:
//   $ gradlew :app:assembleDebug taskTree --no-repeat
// plugins {
//     id("com.dorongold.task-tree") version "1.5"
// }

defaultTasks("assembleDebug")

repositories {
    google()
}

buildscript {
    repositories {
        google()
        jcenter()
    }

    dependencies {
        classpath("com.android.tools.build:gradle:4.1.0")
        classpath("org.aspectj:aspectjtools:1.9.6")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.4.10")
        classpath("org.jetbrains.kotlin:kotlin-serialization:1.4.10")
    }
}

subprojects {
    tasks.withType<Test> {
        testLogging {
            // always rerun tests
            outputs.upToDateWhen { false }

            events("skipped", "failed")

            // https://github.com/gradle/gradle/issues/5431
            addTestListener(object : TestListener {
                override fun beforeSuite(suite: TestDescriptor) {}
                override fun beforeTest(testDescriptor: TestDescriptor) {}
                override fun afterTest(testDescriptor: TestDescriptor, result: TestResult) {}
                override fun afterSuite(suite: TestDescriptor, result: TestResult) {
                    // print only the bottom-level test result information
                    if (suite.className == null) return

                    println("${suite.displayName}: ${result.resultType} " +
                            "(${result.testCount} tests: " +
                            "${result.successfulTestCount} successes, " +
                            "${result.failedTestCount} failures, " +
                            "${result.skippedTestCount} skipped)")
                }
            })
        }
    }
}
