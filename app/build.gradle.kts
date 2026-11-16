plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "io.github.mdmuntakim.peek"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.mdmuntakim.peek"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        resourceConfigurations += "en"
    }

    // Public release key for an open-source project; the password is documented
    // in the README so anyone can reproduce the signed build.
    signingConfigs {
        create("release") {
            storeFile = file("peek-release.keystore")
            storePassword = "peek-android"
            keyAlias = "peek"
            keyPassword = "peek-android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    // arm64-v8a only: this build targets 64-bit ARM devices.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a")
            isUniversalApk = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // AGP 9 built-in Kotlin: the JVM target follows the Java 17 compile options.
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core-splashscreen:1.2.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("io.coil-kt:coil-compose:2.7.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
