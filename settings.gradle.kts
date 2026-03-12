plugins {
  id("org.gradle.toolchains.foojay-resolver-convention").version("1.0.0")
  id("com.gradleup.nmcp.settings").version("1.4.4")
}

dependencyResolutionManagement {
  repositories {
    mavenCentral()
    maven("https://maven.hytale.com/release")
  }
}

rootProject.name = "harness"

nmcpSettings {
  centralPortal {
    username = providers.gradleProperty("nmcpUsername").orNull
    password = providers.gradleProperty("nmcpPassword").orNull
    publishingType = "USER_MANAGED"
  }
}
