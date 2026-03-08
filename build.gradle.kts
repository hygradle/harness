plugins {
  kotlin("jvm") version "2.3.0"
  `maven-publish`
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

java.toolchain {
  languageVersion.set(JavaLanguageVersion.of(25))
  vendor.set(JvmVendorSpec.JETBRAINS)
}

spotless {
  kotlin { ktfmt() }
  kotlinGradle { ktfmt() }
}

publishing {
  publications {
    create<MavenPublication>("shadow") {
      groupId = group.toString()
      artifactId = rootProject.name
      version = version

      from(components["shadow"])
    }
  }
}
