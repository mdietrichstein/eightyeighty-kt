@file:OptIn(ExperimentalWasmDsl::class)

import com.vanniktech.maven.publish.SonatypeHost
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    kotlin("multiplatform") version "2.0.20"
    id("com.diffplug.spotless") version "7.0.2"
    id("com.vanniktech.maven.publish") version "0.30.0"
}

group = "com.sorrykaputt"
version = "1.0"

mavenPublishing {
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)

    signAllPublications()

    coordinates(group.toString(), "eightyeighty-kt", version.toString())

    pom {
        name = "Intel 8080 Emulator"
        description = "eightyeighty-kt is an Intel 8080 emulator, written in Kotlin."
        inceptionYear = "2024"
        url = "https://github.com/mdietrichstein/eightyeighty-kt/"
        licenses {
            license {
                name = "The Apache License, Version 2.0"
                url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
                distribution = "https://www.apache.org/licenses/LICENSE-2.0.txt"
            }
        }
        developers {
            developer {
                id = "mdietrichstein"
                name = "Marc Dietrichstein"
                url = "https://github.com/mdietrichstein/"
            }
        }
        scm {
            url = "https://github.com/mdietrichstein/eightyeighty-kt"
            connection = "scm:git:git://github.com/mdietrichstein/eightyeighty-kt.git"
            developerConnection = "scm:git:ssh://git@github.com/mdietrichstein/eightyeighty-kt.git"
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
