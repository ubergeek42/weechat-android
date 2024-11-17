import org.aspectj.bridge.IMessage
import org.aspectj.bridge.MessageHandler
import org.aspectj.tools.ajc.Main

plugins {
    id("com.android.library")
}

dependencies {
    implementation("org.aspectj:aspectjrt:1.9.21")
    implementation("androidx.annotation:annotation:1.7.1")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testImplementation("org.mockito:mockito-core:5.8.0")
}

android {
    namespace = "com.ubergeek42.cats"
    compileSdk = 34

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    defaultConfig {
        targetSdk = 34
        minSdk = 16
        consumerProguardFile("proguard-rules.pro")
    }

    buildFeatures {
        buildConfig = true
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
