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
  lint *warnings* fail too (`warningsAsErrors`), so keep lint clean. Reproduce locally with
  `./gradlew :onebusaway-android:lintObaGoogleDebug -PwarningsAsErrors=true`.
  - Lint runs the **full catalog** (`checkAllWarnings true`), and the ~760 pre-existing findings are
    grandfathered in `onebusaway-android/lint-baseline.xml`. Only **new** issues (not in the baseline)
    are reported — so don't introduce new ones, and prefer fixing over baselining.
  - After an AGP/lint bump that adds or changes checks, **regenerate the baseline**: delete
    `lint-baseline.xml` and run `./gradlew :onebusaway-android:lintObaGoogleDebug` (it recreates the
    file and intentionally fails that one run; the next run passes). Then review the diff before
    committing so genuinely new issues aren't silently absorbed.

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

Configuration is in the `play {}` block of `onebusaway-android/build.gradle`. Default: App Bundles to the **beta** (open testing) track with auto-incrementing `versionCode`.

## Build Variants

The project uses two flavor dimensions:
- **Platform**: `google` (Google Play release)
- **Brand**: `oba` (original OneBusAway), `agencyX`, `agencyY` (sample rebrands)

Default variant: `obaGoogleDebug`

## Architecture

### Source Structure
Main module: `onebusaway-android/src/main/java/org/onebusaway/android/`

Key packages:
- `app/` - Application class and lifecycle management
- `ui/` - Activities and Fragments (HomeActivity is the main entry point)
- `io/` - REST API integration using Jackson for JSON binding
  - `elements/` - Response data models
  - `request/` - API request classes (e.g., ObaArrivalInfoRequest)
- `provider/` - Content provider (ObaProvider, ObaContract)
- `map/` - Google Maps integration (Google flavor only)
- `region/` - Multi-region support for different OBA server instances
- `directions/` - OpenTripPlanner integration for trip planning
- `tripservice/` - WorkManager-based arrival reminders
- `util/` - Utility classes (LocationUtils, PreferenceUtils, RegionUtils)

### API Layer Pattern
- Requests extend base classes and return typed responses
- Jackson handles JSON serialization/deserialization
- ObaApi provides static singleton access to API constants
- Multi-region architecture auto-discovers OBA servers based on device location

### Data Persistence
- Content Provider with ObaContract for data access
- Room database for structured storage (schemas in `schemas/` directory)
- SharedPreferences for user settings via PreferenceUtils

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

Use AOSP code style. Import `AndroidStyle.xml` (in repo root) into Android Studio:
1. Place in Android Studio `/codestyles` directory
2. Select "AndroidStyle" under File > Settings > Code Style

## Testing

Tests are in `onebusaway-android/src/androidTest/java/`. Key test classes:
- API request/response tests (ArrivalInfoRequestTest, StopRequestTest)
- Region functionality tests (RegionsTest)
- Utility tests (LocationUtilsTest, RegionUtilTest)

CI runs on API level 33 emulator via GitHub Actions, and is **strict** — Kotlin warnings and Android
Lint errors both fail the build (see "CI is strict" under Build Commands).

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
  domain mint) is not flagged — it never rests, so no domain is lost. Pre-existing sites are grandfathered
  in the lint baseline; a genuinely-sanctioned boundary mints into `WallTime`/`ElapsedTime` (domain made
  explicit) or suppresses the issue with a one-line rationale. See `lint-rules/README.md`.

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
- **Kotlin version**: 1.9.21

## Multi-Region Support

The app supports multiple OBA server deployments. Region configuration:
- `USE_FIXED_REGION` build config controls single vs. multi-region mode
- ObaRegionsTask handles async region discovery
- Regions API auto-selects server based on device location

## White-Label / Branding

The app supports white-labeling via Gradle product flavors. See `REBRANDING.md` for full documentation.

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

- PRs should be single squashed commits
- ICLA signature required via CLA Assistant
- Run tests before submitting: `./gradlew connectedObaGoogleDebugAndroidTest`
