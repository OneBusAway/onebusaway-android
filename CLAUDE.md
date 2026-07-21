# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

OneBusAway for Android is a real-time transit information app providing bus arrival predictions, trip planning, and transit-related features. It's part of the non-profit Open Transit Software Foundation.

## Build Commands

```bash
# Build and install debug version (default OBA brand for Google Play)
./gradlew installObaGoogleDebug

# Run instrumented tests
./gradlew connectedObaGoogleDebugAndroidTest

# Build release APK (requires signing configuration)
./gradlew assembleObaGoogleRelease

# Full CI check (tests + lint)
./gradlew test check connectedObaGoogleDebugAndroidTest

# Start app manually after install
adb shell am start -n com.joulespersecond.seattlebusbot/org.onebusaway.android.ui.HomeActivity
```

### Dependency + plugin versions live in `gradle/libs.versions.toml` (#1819)

All dependency and plugin coordinates — plus the Android SDK levels (`compileSdk`/`minSdk`/`targetSdk`)
and the JDK/Kotlin target (`jdk`) — resolve from the Gradle **version catalog** at
`gradle/libs.versions.toml` (auto-imported; exposes `libs.*` accessors; the SDK/JDK entries are read as
`libs.versions.<name>.get()`). Bump a version there, in one place — not in a build script. Only
`versionCode`/`versionName` stay in `build.gradle.kts` (release-cadence app identity; gradle-play-publisher
auto-increments `versionCode`). Plugins are applied via the `plugins {}` DSL (`alias(libs.plugins.…)`),
with resolution repositories in `settings.gradle.kts`'s `pluginManagement`; the root `build.gradle.kts`
declares them `apply false`. All the build scripts are **Kotlin DSL** (`.kts`) — root, `settings`, the
app module (`onebusaway-android/build.gradle.kts`), and `:lint-rules`. The brand flavor files under
`onebusaway-android/flavors/` stay Groovy `.gradle` (they're the white-label extension mechanism, applied
via `apply(from = …)`), and `getPeliasKey` lives in `flavors/load-flavors.gradle` so those Groovy files
can call it. The migration kept every version bit-identical (no upgrades rode along), so don't treat a
bump as part of "the catalog work."

### Variant grid: `check`/`test` only exercise the `oba` brand by default

The app has two flavor dimensions — **brand** (`oba`, `agencyX`, `agencyY`, `kiedybus`) × **platform**
(`google`, `maplibre`) — so `test`/`check` would otherwise fan out `testXUnitTest` across all 8 debug
variants. Only `oba` ships; `agencyX`/`agencyY` are sample white-label rebrands and `kiedybus` is a
third-party brand, all sharing identical code (they differ only in resources/manifest/appId). So a
`beforeVariants` filter in `onebusaway-android/build.gradle.kts` **disables unit + Android tests for every
non-`oba` brand**, shrinking the routine grid to `obaGoogle` + `obaMaplibre`.

- The brand **main** variants stay enabled — `./gradlew assembleAgencyXGoogleDebug` (etc.) still builds
  on demand to verify rebranding compiles.
- To run the full 8-variant test grid (nightly / pre-release), pass **`-PbuildAllBrands=true`**.

### CI is strict — warnings fail the build

CI passes `-PwarningsAsErrors=true`, so **every Kotlin compiler warning (`w:`) is a hard error** in CI —
deprecations, redundant/dead code, unnecessary `!!`, etc. — even though a plain local `./gradlew` build
only prints them. The codebase is kept at **zero** compiler warnings (#1692); don't regress it.

- Before pushing, compile clean. To reproduce the CI gate locally:
  `./gradlew :onebusaway-android:compileObaGoogleDebugKotlin -PwarningsAsErrors=true`
- **Fix the warning at its source.** Only reach for `@Suppress` / `@SuppressWarnings` when the migration
  genuinely can't be done here (e.g. a replacement API that needs a higher `minSdk`, or a schema change),
  and then always add a one-line rationale **and a tracking-issue link** at the suppression site.
- **Android Lint is gated the same way.** Lint *errors* always fail the build (`abortOnError`) —
  notably `NewApi`/minSdk violations the API-33 tests can't catch. Under `-PwarningsAsErrors=true`,
  lint *warnings* fail too (`warningsAsErrors`), so keep lint clean.
  - **Don't run lint locally just to pre-empt a CI failure.** `lintObaGoogleDebug` takes ~5–15 minutes
    (worse when other builds are contending for the machine), which stalls the loop for little gain.
    Compile clean locally (the command above), then **let CI run lint** and react to its report.
    Reproduce lint locally *only* when you're specifically iterating on a lint finding CI already flagged:
    `./gradlew :onebusaway-android:lintObaGoogleDebug -PwarningsAsErrors=true`.
  - Lint runs the **full catalog** (`checkAllWarnings true`) with **no baseline** — the codebase is kept
    lint-clean, so *every* finding is reported and (under `-PwarningsAsErrors`) fails the build; nothing is
    grandfathered. The old whole-project `lint-baseline.xml` was retired once its findings were all fixed
    or their checks opted out (a lint-busting campaign); a handful of genuinely-unactionable checks are
    disabled in the `lint { disable += … }` block, each with a rationale.
  - So don't introduce new findings. If an AGP/lint bump adds a check with unavoidable pre-existing hits,
    **fix them, or opt that check out** in the `lint {}` block with a one-line rationale — don't
    reintroduce a whole-project baseline.

## Automated Publishing (gradle-play-publisher)

Uses [gradle-play-publisher](https://github.com/Triple-T/gradle-play-publisher) to auto-increment `versionCode`, build, and upload to Google Play.

### Setup
1. In [Google Cloud Console](https://console.cloud.google.com/), create a service account (IAM & Admin → Service Accounts) and download the JSON key
2. Enable the Google Play Android Developer API in Google Cloud Console
3. In [Google Play Console](https://play.google.com/console), invite the service account email under Users & permissions and grant "Release manager" permissions
4. Add to `gradle.properties`: `PLAY_STORE_JSON_KEY=/path/to/service-account-key.json`

### Commands
```bash
# Build AAB, auto-increment versionCode, upload to open testing (beta) track
./gradlew publishObaGoogleReleaseBundle

# Same as above + upload all Play Store metadata
./gradlew publishObaGoogleReleaseApps

# Promote beta release to production
./gradlew promoteObaGoogleReleaseArtifact

# Download existing Play Store listing metadata into repo
./gradlew bootstrapObaGoogleReleaseListing
```

Configuration is in the `play {}` block of `onebusaway-android/build.gradle.kts`. Default: App Bundles to the **beta** (open testing) track with auto-incrementing `versionCode`.

## Build Variants

The project uses two flavor dimensions:
- **Platform**: `google` (Google Play release)
- **Brand**: `oba` (original OneBusAway), `agencyX`, `agencyY` (sample rebrands)

Default variant: `obaGoogleDebug`

## Configuration

### Required for Trip Planning (Pelias Geocoding)
Add to `onebusaway-android/gradle.properties`:
```
Pelias_oba=YOUR_API_KEY
```

### Required for Push Notifications (OneSignal)
Add to `onebusaway-android/gradle.properties`:
```
ONESIGNAL_APP_ID=YOUR_APP_ID
```

### Release Builds
Create `secure.properties` with keystore info and reference it in `onebusaway-android/gradle.properties`:
```
secure.properties=/path/to/secure.properties
```

## Code Style

Formatting is automated by **Spotless** (root `build.gradle.kts`) — ktlint for Kotlin and
`*.gradle.kts` (`android_studio` style, pinned in `.editorconfig`), google-java-format for the few
Java files. Run `./gradlew spotlessApply` to format and `./gradlew spotlessCheck` to verify. Don't
hand-format; let Spotless own it. (The old `AndroidStyle.xml` Android Studio scheme was removed once
formatting moved into the build.)

**CI enforces it** — `spotlessCheck` is wired into `check` (which the CI job runs), so unformatted
code fails the build (same strictness tier as `warningsAsErrors`). ktlint runs the `android_studio`
code style's standard ruleset **as-is** — including its naming / file-structure checks
(`property-naming`, `backing-property-naming`, `filename`) — with only two deliberate adjustments:
`@Composable` functions are exempt from `function-naming` (PascalCase is correct there), and
`max-line-length` is off (Spotless can't auto-wrap). The one-time bulk reformat is in
`.git-blame-ignore-revs`.

## Testing

Tests are in `onebusaway-android/src/androidTest/java/`. Key test classes:
- API request/response tests (ArrivalInfoRequestTest, StopRequestTest)
- Region functionality tests (RegionsTest)
- Utility tests (LocationUtilsTest, RegionUtilTest)

The PR/push instrumented suite runs on an **API 33** emulator via GitHub Actions, and CI is **strict** —
Kotlin warnings and Android Lint errors both fail the build (see "CI is strict" under Build Commands).

Separately, a nightly **API 23 floor leg** (`smoke-api23` in `android-nightly.yml`, #1818) boots the
`minSdk` floor — which the API-33 path never exercises at runtime — and runs the `@SmokeTest`-annotated
subset (`org.onebusaway.android.SmokeTest`) to catch a crash-on-boot / desugaring / Compose-on-old-runtime
regression the static checks can't see. Keep that subset **small and stable**; tag a new test with
`@SmokeTest` only when it guards a core floor behaviour, and never tag a device-flaky one.

## Time domains: server clock vs device clock (#1612)

Any user-facing duration measured against a **server-provided timestamp** — ETAs ("N min"),
countdowns, "arriving now", vehicle age ("data updated N sec ago"), alert active-window checks — must
use the response's server clock as "now", **never** `System.currentTimeMillis()`. Mixing the two in a
single subtraction (server timestamp − device now) leaks device clock skew straight into the number.

- The server clock is on the response: `StopArrivals.currentTime`, `RouteTrips.currentTimeMs`,
  `TripDetailsRepository.lastLoadedTime()`.
- Extrapolation deliberately stays on the **device** clock (`TripState.anchorLocalTimeMs`) because it
  pairs each server time with the local receive time; cross back to the server clock with
  `TripState.toServerClock(...)` before plotting against server-clock data.
- Purely local timers stay on the device clock: cache TTLs / recent-window cutoffs (compared against
  locally-stamped `System.currentTimeMillis()` writes), poll scheduling (`SystemClock.elapsedRealtimeNanos`),
  WorkManager/alarm scheduling, "updated Ns ago" against a locally-stamped `lastResponseTimeMs`.

Keep ETA/active-window helpers pure — pass the "now" in as a parameter (see `SituationUtils`,
`ArrivalInfo`); don't call the clock inside a helper. This is verified by `SituationUtilsTest` and
`ServerNowMsTest`.

### Typed instants make the mix a compile error (#1620)

New time math should use the domain-tagged value classes in `org.onebusaway.android.time`
(`TypedTime.kt`) rather than raw `Long`s, so the rules above are enforced by the compiler, not just
review:

- `ServerTime` — the OBA server clock (`currentTime`, arrival predictions, `lastUpdateTime`).
- `WallTime` — the device wall clock (`System.currentTimeMillis()`), where extrapolation pairs each
  server time with its local receive time.
- `ElapsedTime` — the monotonic clock (`SystemClock.elapsedRealtime()`), for real elapsed intervals.

The **only** arithmetic defined is same-domain subtraction, which yields a `kotlin.time.Duration`;
there is no `ServerTime.minus(WallTime)`, so the #27-class bug (server timestamp − device now) fails
to compile. `TripState` and the arrivals ETA path (`ArrivalInfo`/`ArrivalData`) already use these —
follow that pattern when adding server-domain time math.

- **Mint at the boundary:** wrap a raw wire/Android `Long` into its domain right where it enters
  (`ServerTime(currentTime)`, `WallTime.now()`); unwrap `.epochMs` / `.ms` only when handing a value
  to a platform API (formatting, alarms, the renderer's animation clock). Keep the ceremony at the
  edges, not in the middle.
- **The typed classes carry no wire knowledge.** Any unit normalization (e.g. the service-alert
  active-window seconds↔millis rule) stays on the **API side**, at the wire→domain adapter — see
  `situationEpochToMillis` in `api/data/ServerClockNormalization.kt`, the single place that rule lives.
  The API layer normalizes, then hands `ServerTime` a value that is already epoch millis. Do not
  re-implement the seconds/millis guess anywhere else (see "No unsanctioned heuristics" below).
- The one deliberate server↔device crossing (measuring skew from a paired response) is done on raw
  `.epochMs` with an explicit comment — see `TripState.withStatus`/`toServerClock`. Verified by
  `TypedTimeTest`.
- **Lint-enforced.** Two custom checks in module `:lint-rules` give the app a phobia of bare time
  `Long`s: `RawClockArithmetic` fails the build when a raw time reading — `System.currentTimeMillis()`,
  `SystemClock.elapsedRealtime()`, `Location.getTime()`, `Instant.toEpochMilli()`, … — feeds arithmetic
  or a comparison; `UnwrappedClockValue` fails it when a reading comes to **rest in a bare `Long`/`Int`**
  (a local, field, return, or default parameter) instead of a domain type. The value classes make
  cross-domain math a *compile* error; these checks close the complementary gap where a producer reading
  never got minted. A reading passed straight through to a consuming API (DAO/prefs write, alarm, a
  domain mint) is not flagged — it never rests, so no domain is lost. A genuinely-sanctioned boundary
  mints into `WallTime`/`ElapsedTime` (domain made explicit) or carries an inline `@Suppress` with a
  one-line rationale (there is no lint baseline — these live at the site). See `lint-rules/README.md`.

## Coroutines: never swallow CancellationException (#1908 / #1921)

`kotlin.runCatching` catches **every** `Throwable`, and a broad `catch (Exception | Throwable |
RuntimeException | IllegalStateException)` catches `CancellationException` too — it's a plain
`java.util.concurrent.CancellationException`, i.e. an `IllegalStateException` subtype. So in coroutine code
either one silently converts a cancelled coroutine's `CancellationException` into a `Result.failure` /
caught value: the cancelled work keeps running and callers read the cancellation as an ordinary
error/empty state (structured concurrency breaks). It passes compilation and review and only misbehaves
under real cancellation.

- **Use `runCatchingCancellable`** (`org.onebusaway.android.util`) instead of bare `runCatching` in any
  `suspend`/coroutine code. It's behaviour-identical but rethrows `CancellationException`. The custom lint
  check `SwallowedCancellation` (module `:lint-rules`, no baseline) fails the build on a bare `runCatching`
  in a `suspend` function, so this is the enforced path.
- For a broad `try/catch` in coroutine code, put a leading `catch (e: CancellationException) { throw e }`
  before the broad catch (the lint check covers `runCatching` only). Narrow catches (`IOException`, …)
  don't catch cancellation and are fine.

## No unsanctioned heuristics

Do **not** introduce heuristics — magic thresholds, magnitude guesses, or "good enough" inference of
something the data should state explicitly. Examples of what counts: guessing whether a timestamp is
in seconds vs milliseconds from its size; inferring a unit, type, ID scheme, or intent from a value's
range; fuzzy string matching where an exact key exists; "if it looks like X, treat it as X." Heuristics
pass the happy path and misbehave silently at the edges, and they rot as the upstream data shifts.

Prefer resolving the fact at its source instead: normalize units/shape at the parse or wire boundary,
carry an explicit field, or fail loudly (throw / return an error) rather than guessing.

If a heuristic is genuinely unavoidable, it is a **human-sign-off gate**, not a judgment call an agent
makes alone:
1. Call it out explicitly in the PR description and get a human to approve it before merge.
2. Document it at the call site: the exact assumption, why no exact source exists, and its failure mode.
3. This applies equally to **pre-existing** heuristics you touch — reworking one re-opens the sign-off.

When you're tempted to guess a unit/type/intent, check the wire contract, the sample payloads, **and
the producer's source** first — the answer is usually knowable, and sometimes the honest answer is
"the field really is polymorphic," which is itself worth documenting. (Worked example: a legacy helper
guessed whether an alert active-window timestamp was seconds or millis by magnitude. Reading the OBA
**server** source settled it — GTFS-RT `active_period` is seconds per spec, but the server normalizes to
millis on ingestion via its own magnitude rule (`GtfsRealtimeAlertLibrary.toMillis`, threshold 1e12),
while older servers still emit seconds — so the field genuinely varies. The client normalization stays,
but it's now sanctioned by the server evidence and mirrors the server's exact threshold, rather than
being an invented guess — and it lives at the wire→domain adapter (`situationEpochToMillis`) so the
domain model is unambiguously millis. See `situationEpochToMillis` and `SituationWindow`.)

## Key Technical Details

- **Min SDK**: 23 (Android 6.0)
- **Target SDK**: 36 (Android 15)
- **Application ID**: `com.joulespersecond.seattlebusbot` (historical, must keep for Google Play)
- **Namespace**: `org.onebusaway.android`
- **Java compatibility**: 17
- **Kotlin version**: see `kotlin` in `gradle/libs.versions.toml` (2.4.10)

## Multi-Region Support

The app supports multiple OBA server deployments. Region configuration:
- `USE_FIXED_REGION` build config controls single vs. multi-region mode
- ObaRegionsTask handles async region discovery
- Regions API auto-selects server based on device location

## White-Label / Branding

The app supports white-labeling via Gradle product flavors. See `docs/REBRANDING.md` for full documentation.

### Branded String Pattern
Strings containing the app name use `%1$s` placeholders that are replaced at runtime:

```xml
<!-- In strings.xml -->
<string name="tutorial_welcome_title">Welcome to %1$s!</string>
```

```java
// In code - pass app_name as format argument
getString(R.string.tutorial_welcome_title, getString(R.string.app_name))
```

**When adding new user-facing strings that mention the app name:**
1. Use `%1$s` placeholder instead of hardcoding "OneBusAway"
2. Update code to pass `getString(R.string.app_name)` as the format argument
3. Update all translation files (`values-*/strings.xml`) with the same placeholder pattern

**Important:** Strings referenced directly in XML layouts (via `@string/...`) cannot use placeholders - the placeholder would display as literal `%1$s` text. For these strings, set the text programmatically in Java/Kotlin code after inflating the view.

This allows white-label brands to only override `app_name` instead of duplicating entire string files.

## Contributing

- A PR branch may be a single squashed commit or a string of commits — either is fine. On merge to
  `main` the PR is squashed, and the branch may be kept (archived) for an arbitrary period for future
  git archeology.
- ICLA signature required via CLA Assistant
- Run tests before submitting: `./gradlew connectedObaGoogleDebugAndroidTest`
