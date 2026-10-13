# Code architecture

A map of the app's own code for new contributors: where things live and how they connect. It
complements [System Architecture](SYSTEM_ARCHITECTURE.md), which describes the servers the app
talks to.

In paths below, `APP/` is short for `onebusaway-android/src/main/java/org/onebusaway/android/`.

## The shape of it

OneBusAway is a **single-activity, all-Compose, all-Kotlin** app. It uses Hilt for dependency
injection, Retrofit with kotlinx.serialization for the network, Room and DataStore for storage, and
coroutines with `StateFlow` throughout. There is no Java, no Fragments, and no XML layouts left.

The layers, from the screen down:

```
 Compose screens (APP/ui/…)            ← observe StateFlow, send events
        │
 ViewModels (@HiltViewModel)           ← hold UI state, call repositories
        │
 Repositories (interface + Default*)   ← the app's data API; bound in RepositoryModule
        │
 ┌──────┴───────────────┬───────────────────────┐
 Data sources            Room AppDatabase        PreferencesRepository
 (APP/api/data)          (APP/database)          (DataStore)
        │
 Adapters: wire DTO → domain model (APP/api/adapters)
        │
 Retrofit / Apollo services (APP/api/contract, APP/api/net)
```

Two rules hold this together:

* **Domain models never see wire types.** Data sources in `api/data/` return types from `models/`
  (`ObaStop`, `ObaRoute`, `ArrivalData`, …). The serialization DTOs in `api/contract/` are converted
  in `api/adapters/` and go no further. This is also where units get normalized, such as seconds vs
  milliseconds. The `WireTimeEscape` lint check enforces the boundary for time fields.
* **Repositories are interfaces.** `app/di/RepositoryModule.kt` binds each one to its `Default*`
  implementation. Tests swap in hand-written fakes, and demo mode swaps in canned data.

## Modules and source sets

There are two Gradle modules. `:onebusaway-android` is the app, and `:lint-rules` holds the custom
lint checks ([README](../lint-rules/README.md)).

Inside the app module, `src/` is split by flavor (see [BUILD.md](BUILD.md#build-variants)):

| Source set | Contents |
|---|---|
| `main` | Nearly everything: all shared code and resources |
| `google` | Map rendering on the Google Maps SDK (`map/googlemapsv2/`) |
| `maplibre` | Map rendering on MapLibre (`map/maplibre/`), plus a manifest overlay that strips Google Maps metadata |
| `oba`, `agencyX`, `agencyY`, `kiedybus` | Brand resources only (strings, colors, icons, manifest). `oba` also holds the Play Store release notes |
| `test` | JVM unit tests |
| `testGoogle` | JVM tests for the Google map layers |
| `androidTest` | Instrumented tests |

Brand flavors are declared in `onebusaway-android/flavors/*.gradle`, one file per brand. See the
[flavors README](../onebusaway-android/flavors/README.md) and [Rebranding](REBRANDING.md).

### The map platform seam

All map logic is flavor-neutral and lives in `main`. Only drawing is platform-specific.

* `APP/map/compose/ObaComposeMap.kt` defines `ObaComposeMapAdapter` and the `ObaMap(...)`
  composable that screens use.
* Each platform flavor sets a `MAP_COMPOSE_ADAPTER_CLASS` buildConfigField. At runtime the adapter is
  instantiated reflectively from that class name: `GoogleComposeAdapter` or `MapLibreComposeAdapter`.
* Each adapter hosts a classic, imperative `MapView` inside `AndroidView`. It doesn't use
  android-maps-compose. Its renderer (`GoogleMapRenderer` / `MapLibreRenderer`) diffs a
  render-state snapshot onto native markers and polylines.

If you change map behavior, the change almost always belongs in `main`. Touch a flavor only for how
something is drawn.

## Package map

| Package | What's there |
|---|---|
| `app/` | `Application` (`@HiltAndroidApp`), `FeatureFlags`, and `di/` (Hilt modules and EntryPoints) |
| `ui/` | Every screen. `HomeActivity`; `home/` (the home map screen and its features); `nav/` (routes, deep links); `compose/` (theme and shared components); one package per feature: `arrivals`, `tripplan`, `tripresults`, `tripdetails`, `routeinfo`, `report`, `mylists`, `search`, `settings`, … |
| `map/` | Flavor-neutral map: `MapViewModel`, `MapHost`, the controllers, `render/` (render state), `compose/` (the platform seam), `rental/` |
| `api/` | Networking. `contract/` has the Retrofit interfaces and DTOs, `net/` the client setup and interceptors, `adapters/` converts DTOs to domain models, `data/` holds the data sources, and `bridge/` has `RegionsClient` and `ReminderClient` |
| `models/` | Domain types the rest of the app uses |
| `database/` | Room `AppDatabase`, `Migrations.kt`, DAOs and entities (`oba/`, `survey/`, `widealerts/`) |
| `preferences/` | `PreferencesRepository`, the settings store backed by DataStore |
| `region/` | Region selection, caching, custom regions, and Open311 |
| `directions/` | Trip-plan model, OTP request building, and the realtime itinerary monitor |
| `extrapolation/` | Predicts where a vehicle is between updates (see [Time](#time-and-real-time-data)) |
| `time/` | The typed clock domains: `ServerTime`, `WallTime`, `ElapsedTime`, `ScheduleTime` |
| `location/` | `LocationRepository`, the app's single live location feed |
| `tracking/` | Lock-screen live trip tracking (foreground service) |
| `nav/` | Destination reminders: `NavigationService` and a pure `ReminderEngine` |
| `push/`, `tripservice/` | FCM token handling, push registration, and the messaging service |
| `reminders/` | Server-side arrival reminders |
| `analytics/` | `ObaAnalytics`, which sends each event to Firebase, Plausible, and Umami |
| `demo/` | Demo mode: a fake OBA service with fixtures and a scripted clock |
| `backup/`, `donations/`, `notifications/`, `widealerts/`, `util/` | Smaller support pieces |

Tests mirror this layout under `src/test/` and `src/androidTest/`.

## Startup and navigation

**Entry point.** `APP/app/Application.kt` wires up app-wide concerns at launch: theme, the one-time
import of pre-Room data, region subsystems, notification channels, push registration, and trip
tracking. `APP/ui/HomeActivity.kt` is the only activity. The manifest's many `<activity-alias>`
entries exist only so old shortcuts and deep links keep working.

**Navigation has two layers. Knowing which one a screen uses saves time.**

1. **Navigation-Compose destinations.** `APP/ui/home/HomeNavHost.kt` holds the `NavHost`. Each
   feature contributes a `NavGraphBuilder` extension, such as `arrivalsGraph` in
   `ui/arrivals/ArrivalsDestinations.kt` or `settingsGraph` in `ui/settings/SettingsDestinations.kt`.
   Route strings are in `APP/ui/nav/NavRoutes.kt`. Full-screen pages are destinations: settings,
   my lists, trip details, the problem report, and so on.
2. **Focus on the home map.** Selecting a stop, a route, a bike station, or a set of directions does
   *not* navigate anywhere. It changes `HomeViewModel.currentFocus`, a `CurrentFocus` sealed type in
   `APP/ui/home/CurrentFocus.kt`, and the home screen redraws the map and its bottom sheet around it.
   Back handling for focus is in `HomeBackHandler`.

Incoming intents and deep links are mapped to routes or focus changes in `APP/ui/nav/`
(`IntentRouteMapper`, `ExternalDeepLinks`). See [Deep Linking](DEEP_LINKING.md).

## UI

Everything is Jetpack Compose with Material 3. The theme is `APP/ui/compose/theme/ObaTheme.kt`, and
shared components are in `APP/ui/compose/components/`. The only View interop is the map's `MapView`.

Most screens follow the same pattern: a composable observes a `@HiltViewModel`'s `StateFlow`s, and
the ViewModel calls repositories. HomeActivity holds the ViewModels the home screen shares, such as
`HomeViewModel` and `MapViewModel`. Other destinations get theirs with `hiltViewModel()`.

User-facing strings that mention the app name use a `%1$s` placeholder for white-label builds. See
the [Contributing Guide](../.github/CONTRIBUTING.md#project-rules-that-review-enforces).

## Dependency injection

Hilt modules are in `APP/app/di/`, all installed in `SingletonComponent`:

* `AppModule` provides the app coroutine scope, DataStore, and clocks.
* `NetworkModule` provides the OkHttp client, JSON configuration, and Retrofit services.
* `DatabaseModule` provides `AppDatabase` and the DAOs.
* `RepositoryModule` binds each repository interface to its implementation.

ViewModels receive dependencies through `@Inject` constructors. For classes Hilt can't construct,
such as a `BroadcastReceiver` or a static helper, use one of the `*EntryPoint` interfaces in the same
package. Don't reach for a global `Application` instance.

## Data

### The OBA API

`APP/api/net/ObaApiProvider.kt` is the single way to reach the OBA server. It resolves the base URL
for the current region or a custom server URL (`ObaEndpointResolver`), rebuilds the Retrofit service
when that URL changes, and routes to `DemoObaWebService` in demo mode. Its `call`/`callOrNull`
functions return `Result<T>`. The endpoints are declared in `APP/api/contract/ObaWebService.kt`.

### Storage

* **Room**: `APP/database/AppDatabase.kt`. Every schema change needs a migration in
  `Migrations.kt` and an exported schema under `onebusaway-android/schemas/`.
  `AppDatabaseMigrationTest` (instrumented) checks the migrations. Users' starred stops and routes
  live here, so a broken migration loses real data.
* **Settings**: `APP/preferences/PreferencesRepository.kt`, backed by Preferences DataStore.
  `util/PreferenceUtils` is a static facade over it for older call sites.

### Regions

Every OBA deployment is a "region". `APP/region/RegionRepository.kt` owns the current region and
exposes it as a `StateFlow`, along with a `RegionState` (`Resolving`, `Active`, `NeedsManualChoice`,
`Failed`). Region data comes from the live regions directory. The copy bundled at
`src/main/res/raw/regions_v3.json` is used on first launch and offline, so it has to stay in sync
with the directory. `CLAUDE.md` explains why.

### Trip planning

Trip planning goes to a per-region OpenTripPlanner server, and some regions don't have one.
`APP/directions/util/OtpTarget.kt` decides which server to use and whether one exists. Ask
`OtpTarget.isAvailable` and don't re-derive it. `ui/tripplan/TripPlanRepository.kt` calls either
OTP1 (REST, via Retrofit) or OTP2 (GraphQL, via Apollo; queries in `src/main/graphql/otp2/`).

## Time and real-time data

Arrival predictions come with the server's clock, which may disagree with the phone's. The typed
instants in `APP/time/TypedTime.kt` make mixing the two a compile error:

* `ServerTime` is the server's clock.
* `WallTime` is the phone's clock.
* `ElapsedTime` is the monotonic clock.

Several custom lint checks catch raw clock values that bypass the types. The full rules are in
`CLAUDE.md` under "Time domains".

`APP/extrapolation/` predicts where a vehicle is between updates, so it can be animated smoothly on
the map. `TripState` is an immutable snapshot of each trip's history. The extrapolators turn that
history into a predicted position, with an uncertainty band.

There is no shared polling framework. Each feature polls with its own coroutine loop, for example
`map/RouteVehiclePoller.kt` or the arrivals ViewModel.

## Background work

There are no WorkManager workers. Long-running work uses foreground services:

| Service | Purpose |
|---|---|
| `directions/realtime/TripPlanMonitorService` | Watches a saved itinerary for realtime changes, started by an exact alarm |
| `tracking/TripTrackingService` | Live countdowns on the lock screen |
| `nav/NavigationService` | Destination reminders ([doc](DESTINATION_REMINDERS.md)) |

Arrival reminders are scheduled on the server and delivered by Firebase Cloud Messaging to
`tripservice/MyFirebaseMessagingService`.

## Tests

* **JVM unit tests** (`src/test/`): JUnit 4 and `kotlinx-coroutines-test` with **hand-written
  fakes**. There is no Mockito, MockK, or Robolectric, and that's on purpose. Shared fakes and
  `MainDispatcherRule` are in the `testing/` package. Pull branchy logic into pure functions so it
  can be tested here; these tests run in seconds.
* **Instrumented tests** (`src/androidTest/`): Compose UI tests, Espresso, and Room migration tests.
  Recorded OBA responses live in `src/androidTest/res/raw/`. Tests annotated `@SmokeTest` also run
  nightly on an API 23 emulator, so only tag stable tests that guard core behavior.

## CI

| Workflow | When | What runs |
|---|---|---|
| `android.yml` | Every push and PR | `spotlessCheck`, then unit tests, `check`, and instrumented tests on an API 33 emulator, all with `-PwarningsAsErrors=true` |
| `android-nightly.yml` | Nightly | Android Lint; tests for all four brands; the API 23 smoke tests; regions-file drift check |
| `release.yml` | Manual | Signed release to Google Play ([RELEASING.md](RELEASING.md)) |

Lint is nightly only, so a lint finding shows up the day after a merge. See
[Before you open a pull request](../.github/CONTRIBUTING.md#before-you-open-a-pull-request).

## Where to look next

* `CLAUDE.md` in the repository root is the most detailed engineering reference: the time rules,
  coroutine cancellation, heuristics policy, regions, and trip-planner availability.
* `tools/` has scripts for checking regions drift (`check-regions-drift.py`), probing every region's
  trip planner (`check-region-routing.py`), and reporting translation status
  (`missing_localizations.sh`). `tools/gpx/` holds mock-location routes for testing on a device.
