import org.aspectj.bridge.IMessage
import org.aspectj.bridge.MessageHandler
import org.aspectj.tools.ajc.Main

plugins {
    id("com.android.library")
}

dependencies {
    implementation("org.aspectj:aspectjrt:1.9.20.1")
    implementation("androidx.annotation:annotation:1.7.0")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
    testImplementation("org.mockito:mockito-core:5.5.0")
}

android {
    namespace = "com.ubergeek42.cats"
    compileSdk = 33

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildTypes {
        getByName("debug") {}

        getByName("release") {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro")
        }

        create("dev") { initWith(getByName("release")) }
    }

    defaultConfig {
        targetSdk = 33
        minSdk = 16
    }
}

tasks.withType<JavaCompile> {
    doLast {
        println("weaving cats...")

        val args = arrayOf("-showWeaveInfo",
                           "-1.5",
                           "-inpath", destinationDir.toString(),
                           "-aspectpath", classpath.asPath,
                           "-d", destinationDir.toString(),
                           "-classpath", classpath.asPath,
                           "-bootclasspath", android.bootClasspath.joinToString(File.pathSeparator))

        val handler = MessageHandler(true)
        Main().run(args, handler)

        val log = project.logger
        for (message in handler.getMessages(null, true)) {
            when (message.kind) {
                IMessage.DEBUG -> log.warn("DEBUG " + message.message, message.thrown)
                IMessage.INFO -> log.warn("INFO: " + message.message, message.thrown)
                IMessage.WARNING -> log.warn("WARN: " + message.message, message.thrown)
                IMessage.FAIL,
                IMessage.ERROR,
                IMessage.ABORT -> log.error("ERROR: " + message.message, message.thrown)
            }
        }
    }
}
