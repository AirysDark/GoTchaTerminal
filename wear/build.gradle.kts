plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.airysdark.gotchaterminal.wear"
    compileSdk = 36
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "com.airysdark.gotchaterminal.wear"
        minSdk = 26 // Target Wear OS 3.0+ compatibility
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Local Project Modules
    implementation(project(":core"))
    implementation(project(":ble"))
    implementation(project(":firmware"))
    implementation(project(":protocol"))
    implementation(project(":models"))
    implementation(project(":storage"))

    // Core AndroidX & Services
    implementation(libs.androidx.core.ktx)
    implementation(libs.play.services.wearable)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.work.runtime.ktx)

    // Jetpack Compose BOM Integration
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.tooling.preview)

    // Wear OS UI Libraries
    implementation(libs.androidx.wear.compose.material)
    implementation(libs.androidx.wear.compose.material3)
    implementation(libs.androidx.wear.compose.foundation)
    implementation(libs.androidx.wear.compose.navigation)
    implementation(libs.horologist.layout)

    // Tiles & Complications
    implementation(libs.androidx.wear.tiles)
    implementation(libs.androidx.wear.tiles.material)
    implementation(libs.androidx.wear.protolayout)
    implementation(libs.androidx.wear.protolayout.expression)
    implementation(libs.androidx.wear.protolayout.material)
    implementation(libs.androidx.wear.watchface.complications.datasource)
    implementation(libs.androidx.wear.watchface.complications.datasource.ktx)
    debugImplementation(libs.androidx.wear.tiles.tooling)

    // Local Storage Data Architecture
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler) // Native annotation processor mapping for Kotlin 2.0+

    // Utilities
    implementation(libs.guava.android)
    implementation("androidx.compose.material:material-icons-extended")

    // Debugging Tooling
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
