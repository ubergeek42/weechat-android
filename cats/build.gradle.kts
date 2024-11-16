import org.aspectj.bridge.IMessage
import org.aspectj.bridge.MessageHandler
import org.aspectj.tools.ajc.Main

plugins {
    id("com.android.library")
}

dependencies {
    implementation(libs.aspectj.rt)
    implementation(libs.androidx.annotation)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.core)
}

android {
    namespace = "com.ubergeek42.cats"
    compileSdk = 34

    defaultConfig {
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
                           "-inpath", destinationDirectory.asFile.get().toString(),
                           "-aspectpath", classpath.asPath,
                           "-d", destinationDirectory.asFile.get().toString(),
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

java.toolchain.languageVersion = JavaLanguageVersion.of(21)