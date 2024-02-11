import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `java-library`
    kotlin("jvm")
    kotlin("plugin.serialization")
}

dependencies {
    implementation("org.slf4j:slf4j-api:2.0.9")

    // TODO Once the upstream is fixed, revert to pulling from Maven central.
    //   There are two problems with the current upstream version of sshlib, 2.2.23.
    //     * It uses Gradle Shadow, which seems to be merging all dependencies into a single entity.
    //       It is not an issue unless the same dependencies are pulled from elsewhere.
    //       In this situation you can have a class `foo.Bar` found both in its original
    //       dependency `foo`, and in the fat package `sshlib`, which leads to a conflict.
    //       See https://github.com/connectbot/sshlib/issues/301
    //     * Its Jitpack builds are failing.
    //       See https://github.com/connectbot/sshlib/issues/300
    //   To work around these issues, 97498cea, built on top of current upstream master,
    //   removes Gradle Shadow and bumps Jitpack Java version.
    //   See https://github.com/oakkitten/sshlib/commits/remove-gradle-shadow/
    //
    // TODO Change to `implementation`.
    //   This is using `api` because we are calling `SSHConnection.getKnownHosts` from the app
    //   and it returns something from inside sshlib.
    api("com.github.oakkitten:sshlib:97498cea3e183a7dd7efb11ec645caaff0740195")


    implementation("com.neovisionaries:nv-websocket-client:2.14")

    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.9.20")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.withType<KotlinCompile> {
    kotlinOptions { jvmTarget = "17" }
}
