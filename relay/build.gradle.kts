plugins {
    `java-library`
    kotlin("jvm")
    kotlin("plugin.serialization")
}

dependencies {
    implementation(libs.slf4j.api)

    // "api" because we are calling `SSHConnection.getKnownHosts` from the app
    // and it returns something from inside sshlib
    api(libs.sshlib)

    implementation(libs.nvwebsocketclient)

    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher) // https://stackoverflow.com/questions/79546433
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

java.toolchain.languageVersion = JavaLanguageVersion.of(21)