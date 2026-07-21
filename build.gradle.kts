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
    // Spotless is applied (not `apply false`) at the root so a single pair of tasks —
    // `spotlessApply` (fix) / `spotlessCheck` (verify) — formats the whole tree from one place,
    // rather than per-module. Replaces the old "import AndroidStyle.xml, reformat in the IDE" flow.
    alias(libs.plugins.spotless)
}

// Formatting is enforced by Spotless, driving ktlint for Kotlin and google-java-format for the few
// Java files. ktlint reads .editorconfig (ktlint_code_style = android_studio) for style. Generated
// sources under build/ are excluded. Run `./gradlew spotlessApply` before pushing.
spotless {
    // Applying Spotless auto-wires `spotlessCheck` into the `check` lifecycle task (which CI runs), so
    // formatting is enforced now that the whole tree is Spotless-clean. Run `./gradlew spotlessApply`
    // before pushing to satisfy it.
    val ktlintVersion = libs.versions.ktlint.get()
    // Authoritative ktlint config for the build/CI/agents. Applied via editorConfigOverride (rather
    // than relying on .editorconfig discovery, which Spotless doesn't apply to the kotlinGradle
    // target) so every entry point formats identically. .editorconfig mirrors these for IDEs and the
    // ktlint CLI — keep the two in sync.
    //
    // We take the `android_studio` code style's standard ruleset as-is — including its naming checks
    // (property-naming, backing-property-naming) — rather than carving out exceptions. Only two
    // deliberate adjustments below.
    val ktlintConfig = mapOf(
        "ktlint_code_style" to "android_studio",
        // function-naming stays on, but exempt @Composable functions from it since PascalCase is
        // correct for them (ktlint's own supported knob, not a workaround).
        "ktlint_function_naming_ignore_when_annotated_with" to "Composable",
        // max-line-length is the one standard rule Spotless cannot auto-fix (it can't decide how to
        // wrap a line), so leaving it on would turn every long line into a manual chore — the toil
        // this move away from hand-formatting removes. Disabled so formatting stays fully automatic.
        "ktlint_standard_max-line-length" to "disabled"
    )
    // `.claude/**` holds Claude Code's gitignored scratch — notably nested git worktrees whose (possibly
    // pre-reformat) sources would otherwise be globbed in and fail the format gate. It never ships and
    // doesn't exist on CI, so exclude it everywhere alongside generated `build/` output.
    kotlin {
        target("**/*.kt")
        targetExclude("**/build/**", "**/.claude/**")
        ktlint(ktlintVersion).editorConfigOverride(ktlintConfig)
    }
    kotlinGradle {
        // Kotlin DSL build scripts (root, settings, :onebusaway-android, :lint-rules). The Groovy
        // brand-flavor files under onebusaway-android/flavors/*.gradle are *.gradle, not *.gradle.kts,
        // so they're intentionally not matched here.
        target("**/*.gradle.kts")
        targetExclude("**/.claude/**")
        ktlint(ktlintVersion).editorConfigOverride(ktlintConfig)
    }
    java {
        target("**/*.java")
        targetExclude("**/build/**", "**/.claude/**")
        googleJavaFormat()
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
    }
}
