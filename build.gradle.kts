plugins {
  kotlin("jvm") version "2.3.0"
  `maven-publish`
  signing
  alias(libs.plugins.shadow)
  alias(libs.plugins.spotless)
}

group = "dev.hygradle"

version = "0.0.1"

dependencies {
  compileOnly("com.hypixel.hytale:Server:2026.02.19-1a311a592")
  compileOnly("org.hotswapagent:hotswap-agent-core:2.0.3")
  implementation("org.javassist:javassist:3.30.2-GA")
}

java {
  toolchain {
    languageVersion.set(JavaLanguageVersion.of(25))
    vendor.set(JvmVendorSpec.JETBRAINS)
  }

  withJavadocJar()
  withSourcesJar()
}

spotless {
  kotlin { ktfmt() }
  kotlinGradle { ktfmt() }
}

tasks.jar { enabled = false }

tasks.shadowJar {
  archiveBaseName = "hygradle-harness"
  archiveClassifier = null as String?
}

publishing {
  publications {
    create<MavenPublication>("shadow") {
      groupId = "dev.hygradle"
      artifactId = "harness"
      version = version

      from(components["shadow"])
      artifact(tasks.named("javadocJar"))
      artifact(tasks.named("sourcesJar"))


      pom {
        name = "Hygradle Harness"
        description = "Development harness for Hytale plugin development"
        url = "https://hygradle.dev"

        licenses {
          license {
            name = "MIT License"
            url = "https://opensource.org/licenses/MIT"
          }
        }

        developers {
          developer {
            id = "remi-gelinas"
            name = "Remi Gelinas"
          }
        }

        scm { url = "https://github.com/hygradle/harness" }
      }
    }
  }
}

signing {
  useInMemoryPgpKeys(
      providers.gradleProperty("signing.secretKey").get(),
      providers.gradleProperty("signing.keyPassword").get(),
  )
  sign(publishing.publications["shadow"])
}
