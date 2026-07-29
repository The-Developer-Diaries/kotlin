import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    kotlin("multiplatform")
}

@OptIn(ExperimentalKotlinGradlePluginApi::class)
kotlin {
    applyDefaultHierarchyTemplate {
        common {
            group("commonKotlin") {
                group("native") {
                    group("darwin") {
                        withLinuxX64()
                    }
                    group("windows") {
                        withMingw()
                    }
                }
            }
        }
    }

    mingwX64()
    linuxX64()

    compilerOptions {
        freeCompilerArgs.add("-Xrender-internal-diagnostic-names")
    }
}
