plugins {
    `maven-publish`
    kotlin("jvm") version "2.3.0"
    alias(libs.plugins.shadow)
    alias(libs.plugins.spotless)
}

group = "dev.hygradle"
version = "0.0.1"

spotless {
    kotlin { ktfmt(libs.versions.ktfmt.get()).metaStyle() }
    kotlinGradle { ktfmt(libs.versions.ktfmt.get()).metaStyle() }
}

kotlin {
    jvmToolchain(25)
}

dependencies {
    compileOnly("com.hypixel.hytale:Server:2026.02.19-1a311a592")
    compileOnly("org.hotswapagent:hotswap-agent-core:2.0.3")
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