// SPDX-License-Identifier: AGPL-3.0-or-later

buildscript {
    dependencies {
        // AGP's built-in Kotlin brings an older Kotlin Gradle plugin: this pins the
        // version of the version catalog, which the Compose compiler plugin must match
        classpath(libs.kotlin.gradle.plugin)
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
}
