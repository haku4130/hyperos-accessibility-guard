plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Signing details arrive through the environment so that neither the keystore
// nor its passwords live in the repository. CI sets them from repository
// secrets; without them the release build is simply left unsigned, which keeps
// `assembleRelease` usable locally for checking that the build still works.
val keystorePath: String? = System.getenv("KEYSTORE_PATH")

android {
    namespace = "io.github.haku4130.noscrollguard"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.haku4130.noscrollguard"
        minSdk = 33
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    signingConfigs {
        if (keystorePath != null) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            // No shrinking: the accessibility and WorkManager entry points are
            // resolved by name, and there are no proguard rules to protect them.
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
        }
    }

    buildFeatures {
        viewBinding = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.lifecycle:lifecycle-service:2.8.7")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    testImplementation("junit:junit:4.13.2")
}
