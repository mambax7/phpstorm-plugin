import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType

fun prop(key: String): String =
    providers.gradleProperty(key).orNull
        ?: error("Missing gradle.properties key: $key")

plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.18.1"
}

group = prop("pluginGroup")
version = prop("pluginVersion")

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        phpstorm(prop("platformVersion"))
        bundledPlugin("com.jetbrains.php")
        pluginVerifier()
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

intellijPlatform {
    instrumentCode = false
    // Keep false for sandbox auto-reload and single-instance CI safety.
    buildSearchableOptions = false
    autoReload = true

    pluginConfiguration {
        // Keep version in sync with gradle.properties (description/change-notes stay in plugin.xml).
        version = prop("pluginVersion")
        ideaVersion {
            sinceBuild = prop("pluginSinceBuild")
            // Open-ended when pluginUntilBuild is blank — required for PhpStorm 2026.2+ installs.
            // https://plugins.jetbrains.com/docs/intellij/build-number-ranges.html
            val until = prop("pluginUntilBuild").trim()
            if (until.isEmpty()) {
                untilBuild = provider { null }
            } else {
                untilBuild = until
            }
        }
    }

    pluginVerification {
        ides {
            // Same PhpStorm we compile against — keeps CI time/bandwidth predictable.
            // Expand with recommended() / select { ... } when you want a wider matrix.
            create(IntelliJPlatformType.PhpStorm, prop("platformVersion"))
        }
    }
}

tasks {
    withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.release.set(21)
    }

    wrapper {
        gradleVersion = prop("gradleVersion")
    }

    runIde {
        autoReload = true
        jvmArgumentProviders += CommandLineArgumentProvider {
            listOf("-XX:+UnlockDiagnosticVMOptions")
        }
    }
}
