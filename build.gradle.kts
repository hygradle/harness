@file:Suppress("UnstableApiUsage")

import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
  kotlin("jvm") version "2.3.0"
  `maven-publish`
  alias(libs.plugins.shadow)
  alias(libs.plugins.spotless)
}

dependencies {
  compileOnly("com.hypixel.hytale:Server:2026.02.19-1a311a592")
  compileOnly("org.hotswapagent:hotswap-agent-core:2.0.3")
  implementation("org.javassist:javassist:3.30.2-GA")
}

java {
  toolchain {
    languageVersion = JavaLanguageVersion.of(21)
    vendor = JvmVendorSpec.JETBRAINS
  }

  sourceCompatibility = JavaVersion.VERSION_21
  targetCompatibility = JavaVersion.VERSION_21

  withSourcesJar()
}

kotlin {
  @OptIn(ExperimentalAbiValidation::class) abiValidation { enabled = true }
  jvmToolchain(21)
  compilerOptions {
    allWarningsAsErrors = true
    apiVersion = KotlinVersion.KOTLIN_2_3
    languageVersion = apiVersion
    jvmTarget = JvmTarget.JVM_21
  }
}

spotless {
  kotlin { ktfmt(libs.versions.ktfmt.get()) }
  kotlinGradle { ktfmt(libs.versions.ktfmt.get()) }
}

tasks.jar { enabled = false }

tasks.shadowJar {
  archiveBaseName = "hygradle-harness"
  archiveClassifier = null as String?
}

publishing {
  publications {
    create<MavenPublication>("shadow") {
      artifactId = "harness"

      from(components["shadow"])
      artifact(tasks.named("sourcesJar"))
    }
  }

  repositories {
    maven {
      name = "hygradle"
      url = uri("https://maven.hygradle.dev")
      credentials(HttpHeaderCredentials::class) {
        name = "Authorization"
        value = providers.gradleProperty("hygradlePublishToken").map { "Bearer $it" }.getOrElse("")
      }
      authentication { create<HttpHeaderAuthentication>("header") }
    }
  }
}
