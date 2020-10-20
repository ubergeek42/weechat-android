subprojects {
    repositories {
        mavenCentral()
        google()
        jcenter()
        maven { url = uri("https://jitpack.io") }
    }
}

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