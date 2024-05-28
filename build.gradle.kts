val ktor_version: String by project

plugins {
    kotlin("jvm") version "2.0.0-RC3"
    id("com.github.johnrengelman.shadow") version "7.1.1"

    kotlin("plugin.serialization") version "2.0.0-RC3"
    id("application")
}

group = "org.team8bot"

repositories {
    mavenCentral()
    maven("https://jitpack.io/")
    google()
}

dependencies {
    implementation("net.dv8tion:JDA:5.0.0-beta.24") {
        exclude(module = "opus-java")
    }
    implementation("ch.qos.logback:logback-classic:1.4.12")

    implementation("org.mongodb:mongodb-driver-kotlin-coroutine:5.1.0")
    implementation("org.mongodb:bson-kotlinx:5.1.0")
    implementation("com.google.code.gson:gson:2.10.1")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.1")
    implementation("com.github.minndevelopment:jda-ktx:78dbf82")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.0-RC")

    implementation("io.ktor:ktor-client-core:$ktor_version")
    implementation("io.ktor:ktor-client-cio:$ktor_version")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktor_version")
    implementation("io.ktor:ktor-client-content-negotiation:$ktor_version")
}

kotlin {
    jvmToolchain(11)
}

application {
    mainClass.set("org.team9432.discord.eightbot.MainKt")
}

tasks.withType<Jar> {
    manifest {
        attributes["Main-Class"] = "org.team9432.discord.eightbot.MainKt"
    }
}