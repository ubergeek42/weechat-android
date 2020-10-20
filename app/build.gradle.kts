import org.gradle.internal.os.OperatingSystem
import org.aspectj.bridge.IMessage
import org.aspectj.bridge.MessageHandler
import org.aspectj.tools.ajc.Main

plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("kapt") //version "1.4.10"
    id("kotlin-android-extensions")
}

dependencies {
    implementation(project(":cats"))
    implementation(project(":relay"))

    implementation("androidx.core:core-ktx:1.3.2")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.4.10")

    // these two are required for logging within the relay module. todo remove?
    implementation("org.slf4j:slf4j-api:1.7.30")
    implementation("com.noveogroup.android:android-logger:1.3.6")

    implementation("androidx.legacy:legacy-support-v4:1.0.0")
    implementation("androidx.annotation:annotation:1.1.0") // For @Nullable/@NonNull
    implementation("androidx.appcompat:appcompat:1.2.0")
    implementation("androidx.preference:preference-ktx:1.1.1")  // preference fragment & al
    implementation("androidx.legacy:legacy-preference-v14:1.0.0") // styling for the fragment

    implementation("com.github.bumptech.glide:glide:4.11.0")
    kapt("com.github.bumptech.glide:compiler:4.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.9.0")

    val roomVersion = "2.2.5"
    implementation("androidx.room:room-runtime:$roomVersion")
    annotationProcessor("androidx.room:room-compiler:$roomVersion")
    kapt("androidx.room:room-compiler:$roomVersion")

    implementation("org.yaml:snakeyaml:1.27:android")

    implementation("commons-codec:commons-codec:1.14")
    implementation("org.bouncycastle:bcpkix-jdk15on:1.64")

    // needed for thread-safe date formatting as SimpleDateFormat isn"t thread-safe
    // the alternatives, including apache commons and threetenabp, seem to be much slower
    // todo perhaps replace with core library desugaring, if it"s fast
    implementation("net.danlew:android.joda:2.10.6")

    implementation("org.greenrobot:eventbus:3.2.0")

    debugImplementation("org.aspectj:aspectjrt:1.9.6")
    debugImplementation("com.squareup.leakcanary:leakcanary-android:2.5")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

android {
    compileSdkVersion(29)
    buildToolsVersion("29.0.2")

    defaultConfig {
        versionCode = 1200
        versionName = "1.2"

        minSdkVersion(21)
        targetSdkVersion(29)
        buildConfigField("String", "VERSION_BANNER", "\"" + versionBanner() + "\"")

        vectorDrawables.useSupportLibrary = true

        javaCompileOptions {
            annotationProcessorOptions {
                arguments["room.schemaLocation"] = "$projectDir/schemas"
                arguments["room.incremental"] = "true"
            }
        }

        kotlinOptions {
            freeCompilerArgs += "-Xopt-in=kotlin.RequiresOptIn"
            jvmTarget = "1.8"
        }
    }

    signingConfigs {
        create("dev") {
            try {
                storeFile = file(project.properties["devStorefile"] as String)
                storePassword = project.properties["devStorePassword"] as String
                keyAlias = project.properties["devKeyAlias"] as String
                keyPassword = project.properties["devKeyPassword"] as String
            } catch (e: TypeCastException) {
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

        create("dev") {
            signingConfig = signingConfigs.getByName("dev")
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"),
                          "proguard-rules.pro",
                          "../cats/proguard-rules.pro")
        }

        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_1_8
            targetCompatibility = JavaVersion.VERSION_1_8
        }
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


// the problem with weaving Kotlin and Java is that:
//   * Java is compiled by task compileDebugJavaWithJavac
//   * gradle can run either one of these tasks, or both of them
//   * compileDebugJavaWithJavac depends on compileDebugKotlin
//   * weaving Kotlin requires Java classes
//
// this is an attempt to resolve this by postponing weaving until after task
// compileDebugJavaWithJavac has run. gradle.taskGraph.afterTask seems to be executed
// regardless of whether or not the task has been actually run

// the downside here is the the classes are modified in place, and so both compilation tasks
// have to be re-run on every assemble


// another problem is that ajc gets hold of some files such as R.jar, and so on Windows it leads to
// errors such as “The process cannot access the file because it is being used by another process.”
// to avoid these, weave in a process, which `javaexec` will helpfully launch for us.


fun weaveCats(classPath: String, aspectPath: String, inputOutput: String) {
    val arguments = listOf("-showWeaveInfo",
            "-1.8",
            "-inpath", inputOutput,
            "-aspectpath", aspectPath,
            "-d", inputOutput,
            "-classpath", classPath,
            "-bootclasspath", android.bootClasspath.joinToString(File.pathSeparator))

    if (OperatingSystem.current().isWindows) {
        javaexec {
            classpath = fileTree(weavingToolsClassPath)
            main = "org.aspectj.tools.ajc.Main"
            args = arguments
        }
    } else {
        val handler = MessageHandler(true)
        Main().run(arguments.toTypedArray(), handler)

        val log = project.logger
        for (message in handler.getMessages(null, true)) {
            when (message.kind) {
                IMessage.DEBUG -> log.debug("DEBUG " + message.message, message.thrown)
                IMessage.INFO -> log.info("INFO: " + message.message, message.thrown)
                IMessage.WARNING -> log.warn("WARN: " + message.message, message.thrown)
                IMessage.FAIL,
                IMessage.ERROR,
                IMessage.ABORT -> log.error("ERROR: " + message.message, message.thrown)
            }
        }
    }
}

var javaPath: String? = null
var kotlinPath: String? = null
var classPath: String? = null

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    if (name == "compileDebugKotlin") {
        doLast {
            println("scheduling kotlin weaving...")
            kotlinPath = destinationDir.toString()
            classPath = classpath.asPath
        }
    }
    dependsOn("fetchAspectjToolsBecauseIHaveNoIdeaHowToGetBuildScriptClassPath")
}

tasks.withType<JavaCompile> {
    if (name == "compileDebugJavaWithJavac") {
        doLast {
            println("scheduling java weaving...")
            javaPath = destinationDir.toString()
            classPath = classpath.asPath
        }
        dependsOn("fetchAspectjToolsBecauseIHaveNoIdeaHowToGetBuildScriptClassPath")
    }
}

gradle.taskGraph.afterTask {
    if (name == "compileDebugJavaWithJavac" && state.failure == null) {
        if (kotlinPath != null) {
            println("weaving cats into the kotlin part of the app...")
            if (javaPath == null) javaPath = project.buildDir.path + "/intermediates/javac/debug/classes"
            weaveCats(classPath + File.pathSeparator + javaPath, classPath!!, kotlinPath!!)
        }
        if (javaPath != null) {
            println("weaving cats into the java part of the app...")
            weaveCats(classPath!!, classPath!!, javaPath!!)
        }
    }
}


// javaexec needs to find aspectjtools on its classpath. this is a problem because
// i have no idea how to get the path of build script dependencies in gradle.
//
// one workaround is to also have aspectjtools as an *app* dependency.
// as we only need this in debug builds, this shouldn’t be an issue... right?
//
// a probably better one, implemented here, is using another configuration to find aspectjtools,
// and then have a task that copies the jars to a known location... hey, don't look at me like that!

val weavingToolsClassPath = "$buildDir/weaving"
val weaving: Configuration by configurations.creating

dependencies {
    weaving("org.aspectj:aspectjtools:1.9.6")
}

tasks.register<Copy>("fetchAspectjToolsBecauseIHaveNoIdeaHowToGetBuildScriptClassPath") {
    from(weaving)
    into(weavingToolsClassPath)
}
