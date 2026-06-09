import org.gradle.kotlin.dsl.implementation
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    kotlin("plugin.serialization") version "2.3.0"
    kotlin("native.cocoapods")
    id("com.codingfeline.buildkonfig") version "0.15.1"
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    // Only configure iOS if the host machine is a Mac

    iosX64()
    iosArm64()
    iosSimulatorArm64()

    cocoapods {
        summary = "Some description for the Shared Module"
        homepage = "Link to the Shared Module homepage"
        version = "1.0"
        ios.deploymentTarget = "15.0"

        // Make sure this points to your iOS app folder
        podfile = project.file("../iosApp/Podfile")

        framework {
            baseName = "shared"
            isStatic = true // Changing to static often fixes linker errors!
        }

        // Your Google Sign In dependencies
        pod("GoogleSignIn")
    }

    jvm()

    js {
        outputModuleName = "shared"
        browser()
        binaries.library()
        generateTypeScriptDefinitions()
        compilerOptions {
            target = "es2015"
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation("org.jetbrains.compose.runtime:runtime:1.10.0")
            implementation("org.jetbrains.compose.components:components-resources:1.10.0")
            // Your base Koin libraries
            implementation("io.insert-koin:koin-core:4.1.0") // Or your current version (e.g., 3.5.6)
            implementation("io.insert-koin:koin-compose:4.1.0")

            // 👇 ADD THESE TWO FOR KMP VIEWMODELS
            implementation("io.insert-koin:koin-core-viewmodel:4.1.0")    // Fixes AppModule.kt
            implementation("io.insert-koin:koin-compose-viewmodel:4.1.0") // Fixes LoginScreen.kt

            // Official KMP ViewModel & Coroutines
            implementation("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel:2.8.0")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")

            implementation("io.ktor:ktor-client-core:3.3.0")
            implementation("io.ktor:ktor-client-content-negotiation:3.3.0")
            implementation("io.ktor:ktor-serialization-kotlinx-json:3.3.0")
            implementation("io.ktor:ktor-client-auth:3.3.0")
            implementation("io.github.cdimascio:dotenv-kotlin:6.5.0")
            implementation("io.ktor:ktor-client-websockets:3.3.0")
            implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.0")
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        androidMain.dependencies {
            implementation("com.liftric:kvault:1.12.0")
            implementation("androidx.credentials:credentials:1.2.2")
            implementation("androidx.credentials:credentials-play-services-auth:1.2.2")
            implementation("com.google.android.libraries.identity.googleid:googleid:1.1.0")
            implementation("io.ktor:ktor-client-okhttp:3.3.0")
        }
        iosMain.dependencies {
            implementation("com.liftric:kvault:1.12.0")
            implementation("io.ktor:ktor-client-darwin:3.3.0")
        }
        jvmMain.dependencies {
            implementation("io.ktor:ktor-client-okhttp:3.3.0")
        }
        jsMain.dependencies {
            implementation("io.ktor:ktor-client-js:3.3.0")
        }
    }
}

android {
    namespace = "org.ttproject.shared"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "org.ttproject.shared.resources"
}


buildkonfig {
    packageName = "org.ttproject.config"

    defaultConfigs {
        val localProperties = Properties()
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            localProperties.load(FileInputStream(localPropertiesFile))
        }
        // Look in local.properties first (Local PC), then check System.getenv() (Codemagic)
        val webClientId = localProperties.getProperty("GOOGLE_WEB_CLIENT_ID")?.toString() ?: System.getenv("GOOGLE_WEB_CLIENT_ID") ?: ""
        val iosClientId = localProperties.getProperty("GOOGLE_IOS_CLIENT_ID")?.toString() ?: System.getenv("GOOGLE_IOS_CLIENT_ID") ?: ""

        buildConfigField(com.codingfeline.buildkonfig.compiler.FieldSpec.Type.STRING, "WEB_CLIENT_ID", webClientId)
        buildConfigField(com.codingfeline.buildkonfig.compiler.FieldSpec.Type.STRING, "IOS_CLIENT_ID", iosClientId)
    }
}