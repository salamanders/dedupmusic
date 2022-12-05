import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.7.20"
    java
    application
}

group = "info.benjaminhill"
version = "1.0-SNAPSHOT"

repositories {
    mavenLocal()
    google()
    maven {
        url = uri("https://jitpack.io")
    }
    mavenCentral()
}
dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.8.0-Beta")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")
    implementation("com.mpatric:mp3agic:0.9.1")
    implementation("com.google.code.gson:gson:2.10")
    implementation("com.github.salamanders:utils:583a8dc26e")
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "11"
}

application {
    mainClass.set("MainKt")
}