/*
 * Custom Android Lint checks for onebusaway-android.
 *
 * A plain JVM (non-Android) module: lint checks are ordinary UAST detectors packaged into a jar and
 * consumed by the app module via `lintChecks project(':lint-rules')`. Nothing here runs on device.
 */
plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)
}

// The lint tooling version tracks AGP (lintVersion == agpVersion + 23.0.0); both live in
// gradle/libs.versions.toml.

java {
    sourceCompatibility = JavaVersion.toVersion(libs.versions.jdk.get())
    targetCompatibility = JavaVersion.toVersion(libs.versions.jdk.get())
}

kotlin {
    jvmToolchain(libs.versions.jdk.get().toInt())
}

dependencies {
    // lint-api/lint-checks are provided by the lint runtime that loads this jar, so compileOnly.
    compileOnly(libs.lint.api)
    compileOnly(libs.lint.checks)

    testImplementation(libs.lint.api)
    testImplementation(libs.lint.checks)
    testImplementation(libs.lint.tests)
    testImplementation(libs.junit)
}

// Lint discovers the registry through this manifest attribute when the jar is loaded via lintChecks.
tasks.jar {
    manifest {
        attributes(mapOf("Lint-Registry-v2" to "org.onebusaway.lint.OneBusAwayIssueRegistry"))
    }
}
