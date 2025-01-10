@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    id("maven-publish")
    kotlin("multiplatform") version "2.0.20"
    id("com.diffplug.spotless") version "7.0.0.BETA1"
}

group = "com.sorrykaputt"
version = "1.0"

repositories {
    mavenCentral()
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("Release") {
                groupId = project.group.toString()
                version = project.version.toString()
                artifactId = "eightyeighty"
            }
        }
    }
}

kotlin {
    jvm()
    js(IR) {
        browser()
    }
    wasmJs() {
        browser()
    }
    iosArm64()
    iosSimulatorArm64()
    jvmToolchain(19)

    sourceSets {
        jvmTest.dependencies {
            implementation(kotlin("test"))
        }
    }

}

spotless {
    kotlin {
        target("**/*.kt")
        ktfmt().kotlinlangStyle()
    }
}
