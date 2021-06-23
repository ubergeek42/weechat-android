import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `java-library`
    kotlin("jvm")
    kotlin("plugin.serialization")
}

dependencies {
    implementation("org.slf4j:slf4j-api:1.7.31")
    implementation("commons-codec:commons-codec:1.15")

    // "api" because we are calling `SSHConnection.getKnownHosts` from the app
    // and it returns something from inside sshlib
    api("com.github.connectbot:sshlib:2.2.19")

    implementation("com.neovisionaries:nv-websocket-client:2.14")

    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.5.10")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.2.1")

    testImplementation("org.junit.jupiter:junit-jupiter:5.7.2")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.withType<KotlinCompile> {
    kotlinOptions { jvmTarget = "1.8" }
}
