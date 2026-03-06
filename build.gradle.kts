plugins {
  `java-library`
  `maven-publish`
  alias(libs.plugins.shadow)
  alias(libs.plugins.spotless)
}

group = "dev.hygradle"

version = "0.0.1"

dependencies {
  compileOnly("com.hypixel.hytale:Server:2026.02.19-1a311a592")
  compileOnly("org.hotswapagent:hotswap-agent-core:2.0.3")
}

java.toolchain {
  languageVersion.set(JavaLanguageVersion.of(25))
  vendor.set(JvmVendorSpec.JETBRAINS)
}

spotless {
  java { googleJavaFormat() }
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
