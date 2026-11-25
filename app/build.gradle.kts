// SPDX-License-Identifier: AGPL-3.0-or-later

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// The release APK is always built unsigned (what F-Droid needs for its reproducibility
// check): the Release workflow signs it afterwards with scripts/sign-apk.sh, so the release
// key never reaches Gradle and its plugins.

// Debug signing: the fixed debug key only where its environment variables are set (the
// Build workflow on pushes, from the repository secrets), so test builds install over
// each other. Elsewhere (pull requests, forks, local builds) the SDK's own debug key of
// that machine is used.
val debugKeystoreFile: String? = System.getenv("FORMICA_DEBUG_KEYSTORE_FILE")
val debugKeystorePassword: String? = System.getenv("FORMICA_DEBUG_KEYSTORE_PASSWORD")
val hasDebugSigning = !debugKeystoreFile.isNullOrBlank() && !debugKeystorePassword.isNullOrBlank()

android {
    namespace = "io.github.nidamanx.formica"
    // Android 17 with its minor update 37.2: required by Compose 1.13 (BOM 2026.11.01)
    compileSdk {
        version = release(37) {
            minorApiLevel = 2
        }
    }

    defaultConfig {
        // Never change it: Android identifies the app (and its updates) by this id
        applicationId = "io.github.nidamanx.formica"
        minSdk = 26
        targetSdk = 37
        // versionCode must increase with every release (Android and F-Droid rely on it)
        versionCode = 1
        versionName = "0.1.0"
        manifestPlaceholders["appLabel"] = "Formica"
    }

    signingConfigs {
        if (hasDebugSigning) {
            getByName("debug") {
                storeFile = file(debugKeystoreFile!!)
                storeType = "pkcs12"
                storePassword = debugKeystorePassword
                keyAlias = "formica-debug"
                keyPassword = debugKeystorePassword
            }
        }
    }

    buildTypes {
        // Test builds are a separate app, "Formica-D", installable next to the released one
        debug {
            applicationIdSuffix = ".dev"
            manifestPlaceholders["appLabel"] = "Formica-D"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // No git metadata in the APK: identical output however the source was checked out
            vcsInfo.include = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    // Required by F-Droid: no Google-encrypted dependency metadata in the packages
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
        // Every UI text lives in strings.xml, translated into every supported language
        error += listOf("HardcodedText", "MissingTranslation")
    }
}

// Room writes the schema of every database version here; the files are versioned and
// describe what each migration must do
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
}
