import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.4.21"
    kotlin("plugin.compose") version "2.4.21"
    id("org.jetbrains.compose") version "1.13.0-beta01"
}

group = "com.fwork"
version = "1.0.0"

repositories {
}

kotlin {
    jvmToolchain(25)
    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=androidx.compose.animation.ExperimentalAnimationApi",
        )
    }
}

compose.desktop {
    application {
        mainClass = "com.fwork.adbtools.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Msi)
            packageName = "WearADBToolBox"
            packageVersion = "1.0.0"
            vendor = "ReChoilles"
            copyright = "© 2026 ReChoilles. Kadb © Flyfish Xu (Apache-2.0)."
            description = "Windows 桌面版 ADB 工具箱（Kadb 驱动，无需 adb 二进制文件）"

            windows {
                menu = true
                shortcut = true
                dirChooser = true
                upgradeUuid = "e16c9d40-6f6f-4ee1-8f2c-8e08f7f0c1d2"
            }
        }
    }
}

dependencies {
    implementation("org.jetbrains.compose.desktop:desktop:1.13.0-beta01")
    implementation("org.jetbrains.compose.ui:ui:1.13.0-beta01")
    implementation("org.jetbrains.compose.foundation:foundation:1.13.0-beta01")
    implementation("org.jetbrains.compose.material3:material3:1.13.0-beta01")
    implementation("org.jetbrains.compose.material:material-icons-core:1.7.3")
    implementation("org.jetbrains.compose.material:material-icons-extended:1.7.3")

    // Kadb: Kotlin Multiplatform ADB client (no adb binary required)
    implementation("com.flyfishxu:kadb-jvm:2.1.5")
    // Optional mDNS discovery of adb wireless / TLS services (JmDNS backend)
    implementation("com.flyfishxu:kadb-mdns-jvm:2.1.4")
    // JVM wireless-pairing TLS requirement (see Kadb platform notes)
    implementation("org.conscrypt:conscrypt-openjdk-uber:2.7.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
}
