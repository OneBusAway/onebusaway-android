/*
 * Copyright (C) 2013 University of South Florida (sjbarbeau@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// Top-level build file where you can add configuration options common to all sub-projects/modules.

// Plugins are declared here (apply false) so their versions — sourced from gradle/libs.versions.toml —
// are pinned build-wide and applied per-module. Replaces the old `buildscript { classpath ... }` block
// (#1819); plugin resolution repositories now live in settings.gradle.kts's pluginManagement.
plugins {
    alias(libs.plugins.android.application) apply false
    // kotlin-android is applied transitively (the compose/serialization compiler plugins pull in KGP),
    // not by id in the app module; declaring it here pins the Kotlin version on the classpath exactly
    // as the old `classpath "kotlin-gradle-plugin"` did. kotlin-jvm is applied by :lint-rules.
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    // Compose compiler plugin; version == Kotlin version. The Kotlin 2.x compiler is backward-compatible
    // with the Compose 1.11.x runtime used in the app module.
    alias(libs.plugins.kotlin.compose) apply false
    // kotlinx.serialization compiler plugin; version tracks the Kotlin version. Backs the OBA REST
    // stack in api/ (Retrofit + kotlinx.serialization).
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt.android) apply false
    // OTP 2.x GraphQL trip-planning client (#1780) — generates typed Kotlin from
    // onebusaway-android/src/main/graphql/otp2/{schema.graphqls,Plan.graphql}.
    alias(libs.plugins.apollo) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
    alias(libs.plugins.play.publisher) apply false
}

allprojects {
    repositories {
        google()
        mavenCentral()
    }
}
