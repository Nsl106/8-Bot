plugins {
    kotlin("jvm") version "1.8.0"
    id("com.github.johnrengelman.shadow") version "7.1.1"

    application
}

group = "org.team8bot"

repositories {
    mavenCentral()
}

dependencies {
    implementation("net.dv8tion:JDA:5.0.0-beta.9")
    implementation("ch.qos.logback:logback-classic:1.2.9")
}

kotlin {
    jvmToolchain(11)
}

application {
    mainClass.set("MainKt")
}