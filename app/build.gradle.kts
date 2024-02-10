plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("kapt")
}

dependencies {
    implementation(project(":cats"))
    implementation(project(":relay"))

    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.9.20")

    // these two are required for logging within the relay module. todo remove?
    implementation("org.slf4j:slf4j-api:2.0.9")
    implementation("com.noveogroup.android:android-logger:1.3.6")

    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.legacy:legacy-support-v4:1.0.0")
    implementation("androidx.annotation:annotation:1.7.1") // For @Nullable/@NonNull
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.emoji2:emoji2:1.4.0")
    implementation("androidx.preference:preference-ktx:1.2.1")  // preference fragment & al
    implementation("androidx.legacy:legacy-preference-v14:1.0.0") // styling for the fragment
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.lifecycle:lifecycle-common-java8:2.6.2")
    implementation("androidx.sharetarget:sharetarget:1.2.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    implementation("com.github.bumptech.glide:glide:4.16.0")
    kapt("com.github.bumptech.glide:compiler:4.16.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    annotationProcessor("androidx.room:room-compiler:$roomVersion")
    kapt("androidx.room:room-compiler:$roomVersion")

    implementation("org.yaml:snakeyaml:2.2")

    implementation("org.bouncycastle:bcpkix-jdk15on:1.70")

    // needed for thread-safe date formatting as SimpleDateFormat isn"t thread-safe
    // the alternatives, including apache commons and threetenabp, seem to be much slower
    // todo perhaps replace with core library desugaring, if it"s fast
    implementation("net.danlew:android.joda:2.12.6")

    implementation("org.greenrobot:eventbus:3.3.1")

    debugImplementation("org.aspectj:aspectjrt:1.9.21")
    debugImplementation("com.squareup.leakcanary:leakcanary-android:2.12")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.10.1")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

android {
    namespace = "com.ubergeek42.WeechatAndroid"
    compileSdk = 34

    defaultConfig {
        versionCode = 1_09_00
        versionName = "1.9"

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

        kotlinOptions {
            jvmTarget = "17"
        }
    }

    signingConfigs {
        create("dev") {
            try {
                storeFile = file(project.properties["devStorefile"] as String)
                storePassword = project.properties["devStorePassword"] as String
                keyAlias = project.properties["devKeyAlias"] as String
                keyPassword = project.properties["devKeyPassword"] as String
            } catch (e: Exception) {
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
            packagingOptions {
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

        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }
    }

    buildFeatures {
        viewBinding = true
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
