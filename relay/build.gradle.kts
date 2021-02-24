import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `java-library`
    kotlin("jvm")
    kotlin("plugin.serialization")
}

dependencies {
    implementation("org.slf4j:slf4j-api:1.7.30")
    implementation("commons-codec:commons-codec:1.14")

    // "api" because we are calling `SSHConnection.getKnownHosts` from the app
    // and it returns something from inside sshlib
    // todo update to >=2.2.15 once released and remove jitpack repo
    api("com.github.connectbot:sshlib:8ddc2cfa5c099d44b4982cf7d028b2833ba43c5f")

    implementation("com.neovisionaries:nv-websocket-client:2.10")

    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.4.10")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.0.0")

    testImplementation("org.junit.jupiter:junit-jupiter:5.7.1")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.withType<KotlinCompile> {
    kotlinOptions { jvmTarget = "1.8" }
}
