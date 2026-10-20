// Assumed starting point: merge the dependencies, signing config and any
// existing settings from your current build.gradle.kts.
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.emberfall.game"
    compileSdk = 35
    ndkVersion = "27.0.12077973"

    defaultConfig {
        applicationId = "com.emberfall.game"
        minSdk = 33                       // Android 13: Vulkan 1.1 is guaranteed
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        ndk {
            // arm64 for devices; x86_64 so the emulator can run the build.
            abiFilters += listOf("arm64-v8a", "x86_64")
        }

        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++20"
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = false
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // Keep your existing dependencies here.
    testImplementation(kotlin("test"))
}