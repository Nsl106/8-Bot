plugins {
    kotlin("jvm") version "1.8.0"
    id("com.github.johnrengelman.shadow") version "7.1.1"

    application
}

group = "org.team8bot"

repositories {
    mavenCentral()
    maven("https://jitpack.io/")
    google()
}

dependencies {
    implementation("net.dv8tion:JDA:5.0.0-beta.9") {
        exclude(module = "opus-java")
    }
    implementation("ch.qos.logback:logback-classic:1.2.9")

    implementation("com.google.api-client:google-api-client:2.0.0")
    implementation("com.google.oauth-client:google-oauth-client-jetty:1.34.1")
    implementation("com.google.apis:google-api-services-calendar:v3-rev20220715-2.0.0")

    implementation("com.google.code.gson:gson:2.10.1")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.1")
    implementation("com.github.minndevelopment:jda-ktx:9370cb1")
}

kotlin {
    jvmToolchain(11)
}

task<Exec>("deploy") {
    dependsOn("shadowJar")
    commandLine("cmd", "/c", "Powershell  -File  deploy\\deploy.ps1")
}

application {
    mainClass.set("MainKt")
}