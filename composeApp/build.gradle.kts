import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.secrets)
    id("com.google.gms.google-services")
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
            export(projects.shared)
        }
    }

    sourceSets {
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.core.splashscreen)
            implementation(libs.maps.compose)
            implementation(libs.play.services.maps)
            implementation("io.insert-koin:koin-android:3.5.3")
            implementation("io.ktor:ktor-client-okhttp:3.3.0")
            implementation("com.google.firebase:firebase-messaging:23.4.1")
            implementation("androidx.lifecycle:lifecycle-process:2.7.0")
            implementation("androidx.media3:media3-exoplayer:1.10.0")
            implementation("androidx.media3:media3-ui:1.10.0")
        }
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            api(projects.shared)
            implementation(compose.materialIconsExtended)
            implementation(libs.androidx.navigation.compose)
            implementation(libs.kotlinx.serialization.json)
            // Your base Koin libraries
            implementation("io.insert-koin:koin-core:4.1.0") // Or your current version (e.g., 3.5.6)
            implementation("io.insert-koin:koin-compose:4.1.0")

            // 👇 ADD THESE TWO FOR KMP VIEWMODELS
            implementation("io.insert-koin:koin-core-viewmodel:4.1.0")    // Fixes AppModule.kt
            implementation("io.insert-koin:koin-compose-viewmodel:4.1.0") // Fixes LoginScreen.kt
            // In your commonMain dependencies block:
            implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.5.0") // Or whatever the latest version is
            implementation("io.github.onseok:peekaboo-image-picker:0.5.2") // Check for the latest version
            implementation("io.github.vinceglb:filekit-compose:0.8.8")
            implementation("io.github.vinceglb:filekit-core:0.8.8")
            // 1. Core Compose library for AsyncImage
            implementation("io.coil-kt.coil3:coil-compose:3.0.4")

            // 2. The Network fetcher (use ktor2 if you are on Ktor 2.x, or ktor3 if on Ktor 3.x)
            implementation("io.coil-kt.coil3:coil-network-ktor3:3.0.4")

            // Optional but recommended: The core library to ensure version alignment
            implementation("io.coil-kt.coil3:coil:3.0.4")

            implementation("org.jetbrains.androidx.navigationevent:navigationevent-compose:1.0.0")
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        iosMain.dependencies {
            implementation("io.ktor:ktor-client-darwin:3.3.0")
        }
    }
}

android {
    namespace = "org.ttproject"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "org.ttproject"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("debug") {
            isMinifyEnabled = false
            isShrinkResources = false
        }
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    debugImplementation(libs.compose.uiTooling)
}
