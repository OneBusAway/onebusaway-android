/*
 * Copyright (C) 2013-2017 University of South Florida (sjbarbeau@gmail.com),
 * Microsoft Corporation.
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

import com.android.build.api.variant.BuildConfigField
import com.android.build.api.variant.HasAndroidTestBuilder
import com.android.build.api.variant.HasUnitTestBuilder
import java.io.File
import java.io.FileInputStream
import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.play.publisher)
    alias(libs.plugins.apollo)
    // Applied at the bottom of the old script; the plugins DSL requires it here. google-services
    // reads google-services.json and crashlytics wires the mapping upload — both order-independent.
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
}

repositories {
    maven {
        // OBA Releases - for comparator to sort alphanumeric routes
        url = uri("https://repo.camsys-apps.com/releases")
    }
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}

android {
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 153
        versionName = "26.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // This enables us to tell when we're running unit tests on CI (#1010 for Travis, #1072 for GitHub)
        buildConfigField("String", "CI", "\"" + System.getenv("CI") + "\"")
    }

    // Expose the exported Room schemas to instrumented tests so MigrationTestHelper can load them.
    sourceSets {
        getByName("androidTest") {
            assets.directories.add("$projectDir/schemas")
        }
    }

    /**
     * brand - how the name, look, and feel of each OneBusAway-based app is presented
     * platform - Google
     */
    flavorDimensions += listOf("brand", "platform")

    productFlavors {
        create("google") {
            // Normal Google Play release - see src/google
            dimension = "platform"
            isDefault = true
            buildConfigField("String", "MAP_COMPOSE_ADAPTER_CLASS", "\"org.onebusaway.android.map.googlemapsv2.compose.GoogleComposeAdapter\"")
        }

        create("maplibre") {
            // MapLibre-based build using OpenFreeMap tiles - see src/maplibre
            dimension = "platform"
            buildConfigField("String", "MAP_COMPOSE_ADAPTER_CLASS", "\"org.onebusaway.android.map.maplibre.compose.MapLibreComposeAdapter\"")
        }

        // Brand flavors are loaded from separate files in flavors/ directory.
        // See flavors/README.md for instructions on adding new white-label brands.
    }

    packaging {
        resources {
            excludes += setOf("META-INF/LICENSE", "META-INF/NOTICE")
        }
    }

    // http://stackoverflow.com/questions/20673625/gradle-0-7-0-duplicate-files-during-packaging-of-apk

    if (project.hasProperty("secure.properties") &&
        File(project.property("secure.properties") as String).exists()
    ) {

        val props = Properties()
        props.load(FileInputStream(file(project.property("secure.properties") as String)))

        signingConfigs {
            getByName("debug") {
                storeFile = file("seattlebusbot3.debug.keystore")
            }

            create("release") {
                storeFile = file(props["key.store"] as String)
                keyAlias = props["key.alias"] as String
                storePassword = props["key.storepassword"] as String
                keyPassword = props["key.keypassword"] as String
            }
        }
    } else {
        signingConfigs {
            getByName("debug") {
                storeFile = file("seattlebusbot3.debug.keystore")
            }

            create("release") {
                // Nothing here
            }
        }
    }

    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-project.txt")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(libs.versions.jdk.get())
        targetCompatibility = JavaVersion.toVersion(libs.versions.jdk.get())
        // Backport java.time.* (API 26) to minSdk 23. See issue #1693.
        isCoreLibraryDesugaringEnabled = true
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
    namespace = "org.onebusaway.android"
    lint {
        // LogNotTimber: this project intentionally uses android.util.Log, not Timber. The check is
        // a library-preference nudge, not a correctness signal, and its ~190 hits drown out the
        // actionable warnings, so we opt out of it rather than suppress each call site.
        //
        // The remaining disables are advisory GradleDetector checks that lint runs against the build
        // script itself. They did NOT fire on the old Groovy build files (none are in the baseline, and
        // CI was green), but lint scans the version catalog (gradle/libs.versions.toml) and the Kotlin
        // DSL build script (build.gradle.kts) more aggressively than the old Groovy `build.gradle`, so
        // the #1819 modernization newly surfaced them. Each would require a change #1819 explicitly
        // rules out, so disabling keeps the CI gate identical to the pre-modernization build rather than
        // baselining findings that would rot on the next bump:
        //  - GradleDependency / NewerVersionAvailable / AndroidGradlePluginVersion: "a newer version of
        //    X is available". This project pins versions deliberately (see the many "version pin"
        //    comments); currency is Dependabot/Renovate's job, not a build-failing check.
        //  - OldTargetApi: targetSdk (36) trails compileSdk (37). Bumping targetSdk is a deliberate,
        //    separately-tested change — an explicit #1819 non-goal (no version bumps ride along).
        // (ProguardAndroidTxtUsage was disabled here for #1819; the release build now uses
        // proguard-android-optimize.txt, so that check passes on its own and is no longer suppressed.)
        //
        // DuplicateStrings: flags distinct string resources that happen to share an identical value
        // (e.g. two context-menu labels both reading "Show arrival info"). These duplicates are
        // intentional — the keys are semantically separate and may diverge per-context or per-language —
        // so consolidating them is unwanted, and the ~140 hits are pure noise. Opt out rather than
        // baseline them.
        //
        // The next three are the tail of the lint-baseline cleanup (the goal being to delete the baseline
        // entirely): each is a single/handful of findings that is either not our code to fix or not worth
        // fixing, so we opt out of the check rather than carry a baseline entry for it:
        //  - PermissionNamingConvention: the app's `${applicationId}.permission.TRIP_SERVICE` custom
        //    permission predates the convention; renaming a shipped permission is a compatibility break,
        //    so the name stays.
        //  - MemberExtensionConflict: kotlinx.coroutines exposes both `Job.isActive` (member) and
        //    `CoroutineContext.isActive` (extension); on a `Job` receiver the member wins and is correct
        //    (RouteMapController's `vehicleJob?.isActive`). The one hit is benign and has no cleaner call.
        //  - ConvertToWebp: an advisory "this image could be smaller as WebP" nudge (wmata.jpg), not a
        //    correctness signal. Declined.
        //  - SyntheticAccessor: flags a `private` member accessed across the class/file boundary, whose
        //    only cost is a compiler-generated accessor method. Satisfying it forces widening real
        //    encapsulation — file-`private` Kotlin helpers → `internal` (app-global in this single-module
        //    app) and Java `private` → package-private — for a micro-optimization the toolchain already
        //    erases: release enables R8 with `-allowaccessmodification` (#1868), which inlines/strips the
        //    accessors, and in debug the cost is a handful of trivial methods. Leaving it enabled taxes
        //    every future file-private helper called from a sibling class. #1875 widened 34 declarations
        //    to clear it; #1912 restored their `private` and opts the check out here.
        disable += setOf(
            "MissingTranslation", "ExtraTranslation", "LogNotTimber",
            "GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion",
            "OldTargetApi", "DuplicateStrings",
            "PermissionNamingConvention", "MemberExtensionConflict", "ConvertToWebp",
            "SyntheticAccessor"
        )
        // Run the FULL lint catalog — including checks that are off by default and library-provided
        // ones (Compose, UseKtx, …) — so the checked set is comprehensive and current for the installed
        // lint, not just its (drifting) default-enabled subset. The codebase is kept lint-clean under
        // this full catalog, so there is NO baseline: every finding was either fixed in code or, where
        // fixing wasn't warranted, its check opted out above (the lint-busting campaign). Any NEW issue
        // is therefore reported and — under -PwarningsAsErrors — fails the build, with nothing
        // grandfathered. (If an AGP/lint bump adds checks with unavoidable pre-existing hits, prefer
        // fixing or a scoped opt-out over reintroducing a whole-project baseline.)
        // Fail the build (and CI, #1692) on any lint error — notably NewApi minSdk violations,
        // which compile + API-33 instrumented tests can't catch.
        abortOnError = true
        // Under -PwarningsAsErrors=true (CI passes it; see .github/workflows/android.yml), promote lint
        // warnings to errors too, matching the Kotlin-compiler gate below. Local builds default to off so
        // an AGP/lint bump that adds new warnings surfaces in CI rather than blocking day-to-day work.
        warningsAsErrors = project.hasProperty("warningsAsErrors") &&
            project.property("warningsAsErrors") == "true"
    }

    useLibrary("android.test.runner")
    useLibrary("android.test.base")
    useLibrary("android.test.mock")

    buildFeatures {
        buildConfig = true
        // Compose runtime is pinned to the 1.10.x line (BOM 2026.03.01); see the Compose dependency
        // block below before changing any Compose-related version.
        compose = true
    }
}

// The Kotlin Android plugin's extension is registered transitively (by the compose/serialization
// compiler plugins + AGP), not via an `org.jetbrains.kotlin.android` id in the plugins block — so
// there is no generated `kotlin { }` accessor to rely on. Configure the extension by type instead.
configure<KotlinAndroidProjectExtension> {
    compilerOptions {
        jvmTarget.set(JvmTarget.fromTarget(libs.versions.jdk.get()))
        // Promote Kotlin warnings to errors, but only when -PwarningsAsErrors=true is passed (CI
        // does; see .github/workflows/android.yml). Local builds default to off, so a Kotlin/AGP
        // bump that introduces new warnings can't block day-to-day work — it surfaces in CI
        // instead, where it's addressed. The codebase is at zero warnings as of #1692.
        allWarningsAsErrors.set(
            providers.gradleProperty("warningsAsErrors").map { it.toBoolean() }.orElse(false)
        )
    }
}

// Room schema location (KSP arg). Project-scoped — it's a global processor arg, not per-flavor.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

/**
 * Set brand BuildConfig.DATABASE_AUTHORITY, used in org.onebusaway.android.ui.nav.DeepLinkUris (the
 * content:// stop/route deep-link authority) — historically the ObaProvider ContentProvider authority.
 */
androidComponents {
    // Routine `check`/`test` builds only exercise the shipping `oba` brand's test suites. The sample
    // white-label brands (agencyX/agencyY) and the third-party `kiedybus` brand share identical code —
    // they differ only in resources/manifest/appId — so running their unit + Android tests on every
    // build multiplies the KSP + Hilt + compile + test grid ~4x for no added signal. Their MAIN
    // variants stay enabled, so `assembleAgencyXGoogleDebug` (etc.) still builds on demand to verify
    // rebranding compiles. Pass -PbuildAllBrands=true (nightly / pre-release) to restore the full grid.
    val buildAllBrands = providers.gradleProperty("buildAllBrands")
        .map { it.toBoolean() }.orElse(false).get()
    if (!buildAllBrands) {
        beforeVariants(selector().all()) { variantBuilder ->
            if (variantBuilder.productFlavors.none { it.second == "oba" }) {
                // The lambda param is typed as the base VariantBuilder, which doesn't expose these;
                // ApplicationVariantBuilder implements both Has*Builder interfaces at runtime.
                (variantBuilder as HasUnitTestBuilder).enableUnitTest = false
                (variantBuilder as HasAndroidTestBuilder).enableAndroidTest = false
            }
        }
    }

    onVariants(selector().all()) { variant ->
        // Check if the primary flavor is "oba"
        val authority = if (variant.productFlavors.any { it.second == "oba" }) {
            // Must keep the original OBA authority
            "\"" + "com.joulespersecond.oba" + "\""
        } else {
            // It's cleaner to just append ".provider" to the applicationId for brand flavors
            "\"" + variant.applicationId.get() + ".provider" + "\""
        }

        // buildConfigFields is nullable (null when the buildConfig feature is off); it's enabled here.
        variant.buildConfigFields?.put(
            "DATABASE_AUTHORITY",
            BuildConfigField("String", authority, null)
        )
    }

    onVariants(selector().withBuildType("release")) { variant ->
        // Append the version name to the end of aligned APKs
        variant.outputs.forEach { output ->
            // Access versionName from the output object
            output.outputFileName.set("${variant.name}-v${output.versionName.get()}.apk")
        }
    }
}

// Load brand flavor configurations from flavors/ directory
// See flavors/README.md for instructions on adding new white-label brands
apply(from = "flavors/load-flavors.gradle")

// Exclude all classes from dependencies that conflict with Android platform classes (#849)
configurations.all {
    exclude(group = "org.json", module = "json")
}

// OTP 2.x GraphQL trip planning (#1780). Codegen source: src/main/graphql/otp2/
// {schema.graphqls, Plan.graphql}. Schema is pinned to a specific OTP release tag (see the header
// comment in schema.graphqls) rather than live introspection, per the issue's instructions.
apollo {
    service("otp2") {
        packageName.set("org.onebusaway.android.api.graphql")
        srcDir("src/main/graphql/otp2")
        // These OTP2 custom scalars are all just strings on the wire (verified in schema.graphqls'
        // own @specifiedBy annotations: ISO-8601 duration / offset-datetime / encoded polyline).
        // Map them to String instead of Apollo's default `Any`, so parsing to java.time types stays
        // explicit in Otp2PlanAdapters.kt rather than happening implicitly in generated code.
        mapScalar("Duration", "kotlin.String")
        mapScalar("OffsetDateTime", "kotlin.String")
        mapScalar("Polyline", "kotlin.String")
        mapScalar("CoordinateValue", "kotlin.Double")
        // Long is a genuinely numeric custom scalar here (Itinerary.duration, in seconds) — not a
        // string like the others above, so map straight to kotlin.Long instead.
        mapScalar("Long", "kotlin.Long")
        // Cost (TransferPreferencesInput.cost) is seconds, per OTP's own
        // TransferPreferences.cost()/withCost(int) — genuinely numeric, not a string.
        mapScalar("Cost", "kotlin.Int")
    }
}

dependencies {
    // Custom lint checks (see :lint-rules) — RawClockArithmetic enforces the typed-time discipline.
    "lintChecks"(project(":lint-rules"))
    // Core library desugaring: backports java.time.* to minSdk 23 (issue #1693)
    "coreLibraryDesugaring"(libs.desugar.jdk.libs)
    // Firebase Analytics
    implementation(libs.firebase.analytics)
    // Plausible Analytics
    implementation(libs.plausible.android.sdk)
    implementation(libs.firebase.auth)
    implementation(libs.firebase.storage)
    // Firebase Crashlytics
    implementation(libs.firebase.crashlytics)
    // Google Play Services Location
    implementation(libs.play.services.location)
    // Support libraries
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.exifinterface)
    // Open311 client library
    implementation(libs.open311.client)
    // The OBA REST stack (api/): Retrofit + OkHttp + kotlinx.serialization. Every OBA "where"
    // endpoint runs on this; the hand-rolled io/ client it replaced has been removed. The whole app
    // now parses JSON through this stack — the last Jackson consumer (the OTP `/plan` response) moved
    // to kotlinx.serialization in api/contract/OtpPlanModels.kt, so the Jackson dependency is gone.
    implementation(libs.retrofit)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.retrofit.kotlinx.serialization.converter)
    implementation(libs.kotlinx.serialization.json)
    // OTP 2.x GraphQL trip planning (#1780), on top of the same OkHttp stack as the REST clients.
    implementation(libs.apollo.runtime)
    // For sorting alphanumeric route names
    implementation(libs.comparators)
    // Pelias for point-of-interest search and geocoding for trip planning origin and destination
    implementation(libs.pelias.client.library)
    implementation(libs.androidx.appcompat)
    implementation(libs.firebase.messaging)
    // Google Play Services Maps (only for Google flavor). The Google flavor renders the map
    // imperatively — a MapView in an AndroidView driven by GoogleComposeAdapter/GoogleMapRenderer —
    // so there is no android-maps-compose dependency.
    "googleImplementation"(libs.play.services.maps)
    // MapLibre GL Native (only for MapLibre flavor)
    "maplibreImplementation"(libs.maplibre.android.sdk)
    // Autocomplete text views with clear button for trip planning
    implementation(libs.material)
    // Unit tests - seems like this is still necessary w/ Android X even though useLibrary is declared earlier
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    // Pin past the transitively-resolved 3.5.0 (2022): its InputManagerEventInjectionStrategy reflects
    // for a hidden InputManager.getInstance() that Android 16 (API 36) removed, so Espresso.onIdle()
    // throws NoSuchMethodException on current devices before any test body runs.
    androidTestImplementation(libs.espresso.core)
    // WorkManager (Java only)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.core.ktx)
    // Preferences DataStore — the backing store behind PreferencesRepository.
    implementation(libs.androidx.datastore.preferences)
    // RoomDB
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.room.ktx)
    androidTestImplementation(libs.androidx.room.testing)
    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    // GTFS Realtime bindings for parsing GTFS-realtime data
    implementation(libs.gtfs.realtime.bindings)

    // Jetpack Compose on the 1.11.x line (BOM 2026.06.01 -> compose-ui/foundation 1.11.4, material3
    // 1.4.0). This required the coordinated toolchain bump to compileSdk 37 + AGP 9.2.0 + Gradle 9.4.1.
    // The BOM manages compose-ui + material3 (no explicit versions).
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation(libs.compose.ui) // 1.11.4 via BOM
    implementation(libs.compose.ui.tooling.preview) // @Preview support
    debugImplementation(libs.compose.ui.tooling) // preview renderer (debug only)
    // Compose UI instrumented testing (createComposeRule): semantic tree assertions on real devices,
    // per the "never tap raw screen coordinates" testing policy.
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
    implementation(libs.compose.material3) // 1.4.0 via BOM
    // The handful of Material Icons the app uses are vendored as plain Compose ImageVectors in
    // org.onebusaway.android.ui.icons.AppIcons, so we no longer depend on the deprecated (and frozen
    // at 1.7.8) androidx.compose.material:material-icons-core artifact.
    implementation(libs.androidx.activity.compose) // matches activity-ktx 1.13.0
    // ViewModel + StateFlow for Compose screens (lifecycle 2.10.0; minSdk 23)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    // ProcessLifecycleOwner: app-foreground (ON_START) trigger for push re-registration (#1957).
    implementation(libs.androidx.lifecycle.process)
    // Navigation-Compose backbone, on the 2.9.x line (minSdk 23).
    // hilt-navigation-compose bridges hiltViewModel() to @HiltViewModel + SavedStateHandle nav-args.
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    // Make the (previously transitive) coroutines dependency explicit; matches lifecycle 2.8.x
    implementation(libs.kotlinx.coroutines.android)
    // JVM unit tests (src/test)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

play {
    // Path to the Google Play service account JSON key file.
    // Set via gradle.properties: PLAY_STORE_JSON_KEY=/path/to/service-account-key.json
    if (project.hasProperty("PLAY_STORE_JSON_KEY")) {
        serviceAccountCredentials.set(file(project.property("PLAY_STORE_JSON_KEY") as String))
    }

    // Auto-increment versionCode based on the highest code currently on the Play Store
    resolutionStrategy.set(com.github.triplet.gradle.androidpublisher.ResolutionStrategy.AUTO)

    // Upload to the open testing (beta) track, matching the existing release workflow
    defaultToAppBundles.set(true)
    track.set("beta")
}

/**
 * Validates that strings with %1$s placeholders in the base English strings.xml
 * also have the placeholder in all translation files. This prevents runtime crashes
 * from mismatched format strings in white-label brands.
 *
 * Run with: ./gradlew validateStringPlaceholders
 */
tasks.register("validateStringPlaceholders") {
    group = "verification"
    description = "Validates placeholder consistency across all string translations"

    doLast {
        val baseStringsFile = file("src/main/res/values/strings.xml")
        if (!baseStringsFile.exists()) {
            throw GradleException("Base strings.xml not found at $baseStringsFile")
        }

        val documentBuilder = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder()

        // Read every <string name="…">…</string> in a resources file into name -> text.
        fun stringsIn(xml: File): Map<String, String> {
            val nodes = documentBuilder.parse(xml).getElementsByTagName("string")
            val result = LinkedHashMap<String, String>()
            for (i in 0 until nodes.length) {
                val element = nodes.item(i) as org.w3c.dom.Element
                result[element.getAttribute("name")] = element.textContent
            }
            return result
        }

        // Parse base strings and find those with the %1$s placeholder
        val stringsWithPlaceholder = stringsIn(baseStringsFile).filterValues { it.contains("%1\$s") }

        println("Found ${stringsWithPlaceholder.size} strings with %1\$s placeholder in base strings.xml")

        // Check each translation directory
        val errors = mutableListOf<String>()
        fileTree("src/main/res") { include("values-*/strings.xml") }.forEach { transFile ->
            val locale = transFile.parentFile.name.replace("values-", "")
            val transStrings = stringsIn(transFile)

            stringsWithPlaceholder.keys.forEach { name ->
                val transValue = transStrings[name]
                // Note: Missing translations are OK (they fall back to English)
                if (transValue != null && !transValue.contains("%1\$s")) {
                    errors.add("[$locale] String '$name' is missing %1\$s placeholder")
                }
            }
        }

        if (errors.isNotEmpty()) {
            println("\n❌ Found ${errors.size} placeholder consistency errors:\n")
            errors.forEach { println("  - $it") }
            println("\nThese strings have %1\$s in English but not in translations.")
            println("This can cause runtime crashes. Please add the placeholder to the translations.")
            throw GradleException("String placeholder validation failed")
        } else {
            println("✓ All translations have consistent placeholders")
        }
    }
}
