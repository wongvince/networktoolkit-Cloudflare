
plugins {
    kotlin("jvm") version "2.0.21"
    id("org.jetbrains.compose") version "1.7.3"
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
}

group = "com.networktoolkit"
version = "1.4.0"

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")
}

compose.desktop {
    application {
        mainClass = "com.networktoolkit.MainKt"

        // 关闭发布版本的 ProGuard，避免打包失败
        buildTypes.release.proguard {
            isEnabled.set(false)
            optimize.set(false)
            obfuscate.set(false)
        }

        nativeDistributions {
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Exe,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi
            )

            packageName = "NetworkToolkit"
            packageVersion = "1.4.0"
            description = "Windows Network Engineering Toolbox"
            vendor = "NetworkToolkit"
        }
    }
}
