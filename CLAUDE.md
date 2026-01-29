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

CI runs on API level 33 emulator via GitHub Actions.

## Key Technical Details

- **Min SDK**: 21 (Android 5.0)
- **Target SDK**: 36 (Android 15)
- **Application ID**: `com.joulespersecond.seattlebusbot` (historical, must keep for Google Play)
- **Namespace**: `org.onebusaway.android`
- **Java compatibility**: 1.8
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

This allows white-label brands to only override `app_name` instead of duplicating entire string files.

## Contributing

- PRs should be single squashed commits
- ICLA signature required via CLA Assistant
- Run tests before submitting: `./gradlew connectedObaGoogleDebugAndroidTest`
