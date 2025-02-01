plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("kapt")
}

dependencies {
    implementation(project(":cats"))
    implementation(project(":relay"))

    // these two are required for logging within the relay module. todo remove?
    implementation(libs.slf4j.api)
    implementation(libs.androidlogger)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.legacy.support.v4)
    implementation(libs.androidx.annotation) // For @Nullable/@NonNull
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.emoji2)
    implementation(libs.androidx.preference.ktx)  // preference fragment & al
    implementation(libs.androidx.legacy.preference.v14) // styling for the fragment
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.common.java8)
    implementation(libs.androidx.sharetarget)

    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.glide.glide)
    kapt(libs.glide.compiler)
    implementation(libs.okhttp)

    implementation(libs.androidx.room.runtime)
    annotationProcessor(libs.androidx.room.compiler)
    kapt(libs.androidx.room.compiler)

    implementation(libs.snakeyaml)

    implementation(libs.bouncycastle.pkix)

    // needed for thread-safe date formatting as SimpleDateFormat isn"t thread-safe
    // the alternatives, including apache commons and threetenabp, seem to be much slower
    // todo perhaps replace with core library desugaring, if it"s fast
    implementation(libs.joda)

    implementation(libs.eventbus)

    debugImplementation(libs.aspectj.rt)
    debugImplementation(libs.leakcanary)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.junit.jupiter.params)
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

android {
    namespace = "com.ubergeek42.WeechatAndroid"
    compileSdk = 34

    defaultConfig {
        versionCode = 1_10_00
        versionName = "1.10"

        minSdk = 21
        targetSdk = 34
        buildConfigField("String", "VERSION_BANNER", "\"" + versionBanner() + "\"")

        vectorDrawables.useSupportLibrary = true

        javaCompileOptions {
            annotationProcessorOptions {
                arguments["room.schemaLocation"] = "$projectDir/schemas"
                arguments["room.incremental"] = "true"
            }
        }
    }

    signingConfigs {
        create("dev") {
            try {
                storeFile = file(project.properties["devStorefile"] as String)
                storePassword = project.properties["devStorePassword"] as String
                keyAlias = project.properties["devKeyAlias"] as String
                keyPassword = project.properties["devKeyPassword"] as String
            } catch (_: Exception) {
                project.logger.warn("WARNING: Set the values devStorefile, devStorePassword, " +
                        "devKeyAlias, and devKeyPassword " +
                        "in ~/.gradle/gradle.properties to sign the release.")
            }
        }
    }

    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }

        getByName("release") {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"),
                          "proguard-rules.pro",
                          "../cats/proguard-rules.pro")
            // kotlinx-coroutines-core debug-only artifact
            // see https://github.com/Kotlin/kotlinx.coroutines#avoiding-including-the-debug-infrastructure-in-the-resulting-apk
            packaging {
                resources.excludes += "DebugProbesKt.bin"
            }
        }

        create("dev") {
            initWith(getByName("release"))
            matchingFallbacks += listOf("release")
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            signingConfig = signingConfigs.getByName("dev")
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

fun versionBanner(): String {
    val os = org.apache.commons.io.output.ByteArrayOutputStream()
    project.exec {
        commandLine = "git describe --long".split(" ")
        standardOutput = os
    }
    return String(os.toByteArray()).trim()
}

java.toolchain.languageVersion = JavaLanguageVersion.of(21)

////////////////////////////////////////////////////////////////////////////////////////////////////
/////////////////////////////////////////////////////////////////////////////////////////////// cats
////////////////////////////////////////////////////////////////////////////////////////////////////

// This is hacky but it makes the AspectJ weaving only happen on debug builds.
// Note that `apply(plugin = ...)` prevents us from configuring a filter,
// which seems to require the unconditional syntax `plugin { id(...) }`.
// However, the filter somehow doesn't seem to work either way, even if we simplify it to this:
//
//     aopWeave {
//        filter = "ubergeek42"
//     }

val taskRequests = getGradle().startParameter.taskRequests.flatMap { it.args }

if (taskRequests.isNotEmpty() && taskRequests.all { "Debug" in it }) {
    apply(plugin = "com.ibotta.gradle.aop")
}
