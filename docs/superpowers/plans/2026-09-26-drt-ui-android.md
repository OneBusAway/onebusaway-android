# DRT UI (On-Demand Zones and Map Layers) Android Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give OneBusAway Android the demand-responsive-transit surfaces of the DRT UI design: a point probe with an availability model, region-level labelled zone pins, street-level stroke-only zones, the docked status bar and zone card, the overlap picker, the restructured zone detail page, the address line on the dropped pin, the trip-planner fallback, and the tile-grid map-layers sheet that replaces the rentals FAB menu.

**Architecture:** Pure logic first (`ondemand/` package: availability, match sort, colours, copy specs; `map/render/ZoneGeometry.kt`: label point, nearest edge, compass; `map/OnDemandZoomLevel.kt`), then one `OnDemandProbeController` owned by `MapViewModel` that publishes `OnDemandDockState` from cached point probes, then the two flavour renderers styled by `ZoneStyle`, then Compose surfaces (`ui/home/ondemand/`, `ui/ondemand/`, `ui/home/map/MapLayersSheet.kt`) that consume the pure functions and never re-derive them. Every surface stays gated on `OnDemandSupport`; nothing needs a server change.

**Tech Stack:** Kotlin 2.4 / Jetpack Compose (BOM 2026.09, Material 3, `HorizontalPager`, `Canvas`) / Hilt / kotlinx.serialization / java.time (desugared) / Google Maps SDK and MapLibre legacy annotations / JUnit4 + kotlinx-coroutines-test with hand-written fakes / Compose UI tests in `androidTest`.

**Spec:** `docs/superpowers/specs/2026-09-26-drt-ui-design.md` (binding; sections cited as §n). Companion material: `/private/tmp/claude-501/-Users-aaron-repos-onebusaway-maglev/215fa362-5529-43a3-9cb6-317b7d70341f/scratchpad/drt-design/{android-survey.md,drt-flow-screens.md,drt-design-notes.md,api-contract.md}`. The previous plan `docs/superpowers/plans/2026-09-24-gtfs-flex-android.md` built the models, data source, evaluator, zone layer and service page this plan extends.

Repo worktree: `/Users/aaron/repos/onebusaway/.worktrees/android-gtfs-flex` (branch `gtfs-flex`). All paths below are relative to that worktree. Prefixes: `M` = `onebusaway-android/src/main/java/org/onebusaway/android`, `T` = `onebusaway-android/src/test/java/org/onebusaway/android`, `AT` = `onebusaway-android/src/androidTest/java/org/onebusaway/android`, `G` = `onebusaway-android/src/google/java/org/onebusaway/android/map/googlemapsv2`, `L` = `onebusaway-android/src/maplibre/java/org/onebusaway/android/map/maplibre`, `R` = `onebusaway-android/src/main/res`.

## Global Constraints

Copied from the spec (§1, §2, §2.10, §3, §5) and the repo's build gates:

- Data comes only from the `/api/ondemand` contract in `api-contract.md`; nothing here requires a server change (§ preamble). The probe is `GET /api/ondemand/services-for-location.json?lat=&lon=&radius=5000&geometryDetail=none`; `list[].matchReason` and `references.serviceAreas[].distanceToArea` / `nearestPointOnBoundary` are the only source of "inside", "near" and "how far" (§2.1). Client polygons are never used to decide containment for anything a rider reads; client point-in-ring is allowed only for the label point (§2.3) and the existing tap hit-test.
- Probe triggers: map settle with ≥ 100 m movement, location authorization change, foreground, `nextChangeInstant` passing, a rider fix ≥ 100 m from the last probe point (or the first fix) with horizontal accuracy ≤ 100 m (§2.1). Rider/centre cache keyed `(deployment, point rounded to 3 decimals)` for 10 minutes; the address check and planner use a separate exact cache keyed `(deployment, point rounded to 5 decimals)`, 10 minutes, never the rider/centre cache (§2.1).
- A 404 on `services-for-location` marks the deployment unsupported through `OnDemandSupport` and hides every surface for the process lifetime; any other error keeps the last state only when the new probe point is within 100 m of the one that produced it, else the dock becomes hidden (§2.1, §2.10). No new feature flags; existing SharedPreferences keys keep their names and defaults (§2.10).
- Zoom levels from visible height in metres of latitude (`visibleHeightMeters(latSpan)`): Street ≤ 4,000 (`ONDEMAND_STREET_LEVEL_MAX_HEIGHT_METERS`), Region 4,000 < h ≤ 65,000 (`ONDEMAND_MAX_VISIBLE_HEIGHT_METERS`), Hidden > 65,000; published as `OnDemandZoomLevel` (§2.2).
- Region drawing: fill alpha 0.2, stroke 2 pt in the service colour, holes cut out, one pin per service at its label point; Street: fill alpha 0, stroke 4 pt, halo 10 pt at alpha 0.25 where the platform allows, pins hidden; highlight: stroke 4 pt full alpha, other street strokes at alpha 0.6 (§2.3). Colour collisions take the fixed fallback palette `#3b82f6, #d97706, #7c3aed, #db2777, #0891b2, #65a30d` by service id order (§2.3).
- The dock slot shows exactly one of zone card (region level, inside ≥ 1), docked bar (street level, inside or finite `distanceToArea` ≤ 5,000 m), nothing (hidden level; `CurrentFocus.Stop/.Route/.BikeStation/.Directions`; layer off; survey card shown; visible map area under 50 % because of the sheet); matches with a null distance never show; content cross-fades in 200 ms (§2.4). Landscape: bottom-start, 360 dp max width; the FAB stack is offset by the dock's measured height (§2.4).
- `OnDemandAvailability` is computed from `now` = device clock and the agency timezone from `references.agencies[].timezone`; `currentTime` is never used; missing timezone ⇒ every field null/false, status unknown, tier 5 (§2.5). Sort by tier, then distance (null last), then locale-aware natural name comparison; one function feeds picker, bar, card and planner (§2.6).
- Nearest-edge geometry uses `geometryDetail=full` per service, cached per `(deployment, serviceId)` for the process lifetime, local equirectangular projection with `R = 6,371,000 m`, point-to-segment distance over every ring of every area of the service; `edgeDirection` = bearing bucket probe → boundary, `riderDirection` = reversed; near edge = inside with client distance < 100 m (§2.7).
- All strings localized in `R/values/strings.xml` under the `ondemand_*` / `map_layers_*` keys of §2.8 (plurals as `<plurals>`); distances imperial: whole feet rounded to 10 below 0.1 mi, miles one decimal at or above; metric: metres rounded to 10 below 1,000 m, kilometres one decimal at or above; times in the short style in the agency timezone (§2.8).
- Eligibility decoded only when present; absent/`unknown`/`open` shows nothing; `certificationRequired` adds the tag, tier 4, planner exclusion; booking-rule messages shown verbatim, never pattern-matched (§2.9).
- Android google and maplibre both get everything; only google gets the basemap control (§2.10). Visual tokens per §5 (brand `brand_color`, accent `md_theme_primary`, outside gray `#636366`, open green `#248a3d`, rentals `layer_bikeshare_color`; radii card 22 / bar 20 / thumbnail 12 / lists 18 / tile 16 / tag 12 / badge 9; touch targets ≥ 48 dp; every icon-only button labelled with its action's copy).
- Build gates before every commit: `./gradlew :onebusaway-android:compileObaGoogleDebugKotlin :onebusaway-android:compileObaMaplibreDebugKotlin -PwarningsAsErrors=true` (both flavours, zero warnings), `./gradlew :onebusaway-android:testObaMaplibreDebugUnitTest --tests '<the touched test classes>'`, then `./gradlew spotlessApply` (ktlint `android_studio` style; never hand-format). Compose UI tests under `AT` are written for the on-device suite and compiled by `./gradlew :onebusaway-android:compileObaMaplibreDebugAndroidTestKotlin`; run them on the emulator in Task 20.
- `android.util.Log` is unmocked in JVM tests: no new code under JVM test may reach a `Log.*` call. `android.location.Location` is not constructible on the JVM: the probe controller consumes a flavour-neutral `RiderFix`, never `Location`.
- Use `runCatchingCancellable` (never bare `runCatching`) in suspend code; pass `TimeProvider` for "now"; no `@Suppress` without a rationale and issue link.
- Commit style: imperative, sentence case, ≤ 50-character subject, body wrapped at 72 explaining what and why; no `Co-Authored-By` lines.

## Review Focus

The five input classes the spec implies that no task's tests would otherwise exercise, most likely to bite first; each has its pinning test added to the owning task below.

1. **A service whose every calendar has already ended** (feed rolled over): availability must be `Closed`, tier 5, the bar must still show "Closed" and the day-walk must not run 400 days per probe — pinned in Task 2 (`every calendar ended is closed, tier 5, and walks no further`).
2. **A stop-group service (no areas) returned with `stopWithinRadius`**: null distance, so it must never reach the dock, the card, the bar stack or the address line, while still counting in the picker's full list — pinned in Task 6 (`a match with no area never enters the dock`) and Task 16 (`a stop-group match never names the address line`).
3. **A polygon vertex lying exactly on the bbox mid-latitude** (odd crossing count) or a ring with fewer than three points: the label point must fall back in the spec's order and never index past the crossings list — pinned in Task 4 (`a vertex on the mid-latitude and a degenerate ring fall back`).
4. **Location permission revoked mid-session** (the rider fix flow goes back to null): the probe source must flip to the map centre and re-probe there even though the centre has not moved 100 m, so the bar does not keep describing a position the app can no longer see — pinned in Task 6 (`losing the rider fix re-probes at the map centre`).
5. **A deployment switch while a full-geometry fetch is in flight**: the old deployment's areas must not populate `edges` for the new one — pinned in Task 6 (`a deployment change hides, re-probes the new server and discards a geometry answer for the old`).

---

## File Structure

| File | Responsibility | Task |
|---|---|---|
| `M/api/data/OnDemandDataSource.kt` | `servicesNear` point probe, `GEOMETRY_DETAIL_FULL` | 1 |
| `M/api/contract/OnDemandApiModels.kt`, `M/api/adapters/OnDemandAdapters.kt`, `M/models/OnDemandService.kt` | Optional `eligibility` | 1 |
| `M/ondemand/ServiceBookingLine.kt` | Booking-line logic moved out of the presentation (shared by page and availability) | 2 |
| `M/ondemand/OnDemandAvailability.kt` | §2.5 availability, status, tags, tier | 2 |
| `M/ondemand/ProbePoint.kt`, `M/ondemand/OnDemandMatch.kt`, `M/ondemand/OnDemandColors.kt` | Probe source types, match + §2.6 sort, colour resolution and bar text colour | 3 |
| `M/map/render/ZoneGeometry.kt`, `M/map/OnDemandZoomLevel.kt` | Label point, nearest edge, compass, zoom level, `ZoneStyle` tables | 4, 7 |
| `M/ondemand/OnDemandCopy.kt` | Resource-free `TextSpec` copy composition (§2.8) and distance formatting | 5 |
| `M/ondemand/OnDemandGeometryCache.kt`, `M/map/OnDemandProbeController.kt`, `M/map/OnDemandDockDecisions.kt` | Full-geometry cache; probe controller with caches, triggers, dock state | 6 |
| `M/map/render/MapRenderState.kt`, `M/map/OnDemandLayerController.kt` | `ZonePolygon.labelPoint/style`, zoom level and highlight on the layer | 7 |
| `G/GoogleMapRenderer.kt`, `G/compose/GoogleComposeAdapter.kt`, `L/MapLibreRenderer.kt`, `L/compose/MapLibreComposeAdapter.kt`, `M/map/render/ZonePinBitmaps.kt` | Styled polygons, halo, pins, region-only taps | 8 |
| `M/map/MapViewModel.kt`, `M/map/OnDemandFraming.kt` | Owns the probe controller; dock actions; zoom-out framing | 9 |
| `M/ui/home/ondemand/ZoneThumbnail.kt` | Canvas thumbnail shared by bar and detail | 10 |
| `M/ui/home/ondemand/OnDemandDockFeature.kt`, `OnDemandZoneCard.kt`, `OnDemandDockBar.kt`, `DockBarPages.kt` | Card, paged bar, dock switch | 11 |
| `M/ui/home/HomeScreen.kt`, `M/ui/home/ondemand/OnDemandDockHost.kt`, `M/ui/nav/NavRoutes.kt`, `M/ui/ondemand/OnDemandDestinations.kt`, `M/ui/home/HomeNavHost.kt` | Hosting the dock; suppression; navigation with a location check | 12 |
| `M/ondemand/LocalityResolver.kt`, `M/ui/home/ondemand/OnDemandSheetsViewModel.kt`, `M/ui/home/ondemand/OnDemandPickerSheet.kt` | Locality lookup, picker sheet | 13 |
| `M/ui/ondemand/OnDemandServicePresentation.kt` | Availability, tags, where rows, no-service days, location check | 14 |
| `M/ui/ondemand/OnDemandServiceScreen.kt`, `OnDemandServiceViewModel.kt` | Restructured page | 15 |
| `M/ui/home/directions/NavigateHereBubble.kt`, `M/map/MapViewModel.kt`, `M/ondemand/OnDemandCoverage.kt` | Address line | 16 |
| `M/ondemand/OnDemandPlannerQualifier.kt`, `M/ui/home/directions/OnDemandPlannerFallbackSheet.kt`, `DirectionsFeature.kt` | Planner fallback | 17 |
| `M/map/MapFlavourCapabilities.kt`, `M/map/Basemap.kt`, `M/ui/home/map/MapLayersViewModel.kt`, `M/map/MapHost.kt`, `G/compose/GoogleComposeAdapter.kt`, `onebusaway-android/build.gradle.kts`, `M/app/di/AppModule.kt` | Layers state, basemap preference | 18 |
| `M/ui/home/map/MapLayersSheet.kt`, `MapChrome.kt`, `MapChromeViewModel.kt`, `MapFeature.kt`, `M/ui/settings/SettingsScreen.kt` | Sheet UI, `LayersFab`, chrome rule | 19 |
| `R/values/strings.xml`, `R/values/colors.xml`, `R/values/donottranslate.xml`, `R/drawable/ic_*.xml` | Strings, colours, icons — added by the task that first uses them; audited in Task 20 | all |

---

### Task 1: Point-mode probe and the optional eligibility field

**Files:**
- Modify: `M/api/data/OnDemandDataSource.kt`
- Modify: `M/api/contract/OnDemandApiModels.kt` (after `OnDemandServiceDto`)
- Modify: `M/models/OnDemandService.kt` (before `OnDemandService`)
- Modify: `M/api/adapters/OnDemandAdapters.kt` (`toOnDemandService` private mapper)
- Modify: `T/map/OnDemandLayerControllerTest.kt:62-69`, `T/ui/ondemand/OnDemandServiceViewModelTest.kt:53-60`, `T/ui/arrivals/DefaultArrivalsRepositoryTest.kt:507-511` (the three fakes gain the new method)
- Test: `T/api/OnDemandEligibilityTest.kt`

**Interfaces:**
- Consumes: `ObaWebService.onDemandServicesForLocation(lat, lon, radius, latSpan, lonSpan, geometryDetail)` (radius wins, `M/api/contract/ObaWebService.kt:316`); `Result<T>.toOnDemandResult(probe)`; `References`.
- Produces: `const val GEOMETRY_DETAIL_FULL = "full"`; `suspend fun OnDemandDataSource.servicesNear(point: GeoPoint, radiusMeters: Int, geometryDetail: String = GEOMETRY_DETAIL_NONE): OnDemandResult<List<OnDemandService>>` (404 ⇒ `Unsupported`); `enum class EligibilityRequirement { OPEN, CERTIFICATION_REQUIRED, UNKNOWN }`; `data class OnDemandEligibility(val requirement: EligibilityRequirement, val infoUrl: String?)`; `OnDemandService.eligibility: OnDemandEligibility? = null`; `OnDemandEligibilityDto`.

The demo web service (`M/demo/DemoObaWebService.kt:118-125`) already answers `onDemandServicesForLocation` with an empty list for every argument shape, so the point probe needs no demo change; the demo transit system simply has no on-demand service.

- [ ] **Step 1: Write the failing test**

Create `T/api/OnDemandEligibilityTest.kt`:

```kotlin
package org.onebusaway.android.api

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.onebusaway.android.api.adapters.toOnDemandService
import org.onebusaway.android.api.contract.EntryWithReferences
import org.onebusaway.android.api.contract.OnDemandServiceDto
import org.onebusaway.android.models.EligibilityRequirement

class OnDemandEligibilityTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `an absent eligibility decodes to null`() {
        val dto = json.decodeFromString<OnDemandServiceDto>("""{"id":"a_1","agencyId":"a","name":"Ride","serviceKind":"zone"}""")
        assertNull(dto.eligibility)
        assertNull(EntryWithReferences(dto).toOnDemandService().eligibility)
    }

    @Test
    fun `certificationRequired adapts with its info url`() {
        val dto = json.decodeFromString<OnDemandServiceDto>(
            """{"id":"a_1","agencyId":"a","name":"Ride","serviceKind":"zone","eligibility":{"requirement":"certificationRequired","infoUrl":"https://example.org/apply"}}"""
        )
        val service = EntryWithReferences(dto).toOnDemandService()
        assertEquals(EligibilityRequirement.CERTIFICATION_REQUIRED, service.eligibility?.requirement)
        assertEquals("https://example.org/apply", service.eligibility?.infoUrl)
    }

    @Test
    fun `an unrecognised requirement reads as unknown`() {
        val dto = json.decodeFromString<OnDemandServiceDto>(
            """{"id":"a_1","agencyId":"a","name":"Ride","serviceKind":"zone","eligibility":{"requirement":"ageRestricted","infoUrl":null}}"""
        )
        assertEquals(EligibilityRequirement.UNKNOWN, EntryWithReferences(dto).toOnDemandService().eligibility?.requirement)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :onebusaway-android:testObaMaplibreDebugUnitTest --tests 'org.onebusaway.android.api.OnDemandEligibilityTest'`
Expected: compilation FAILS with `Unresolved reference: eligibility` / `EligibilityRequirement`.

- [ ] **Step 3: Add the wire, domain and adapter pieces**

In `M/api/contract/OnDemandApiModels.kt`, add the field to `OnDemandServiceDto` and the new DTO after it:

```kotlin
@Serializable
data class OnDemandServiceDto(
    val id: String = "",
    val agencyId: String = "",
    val routeId: String? = null,
    val name: String = "",
    val serviceKind: String = "unknown",
    val description: String? = null,
    val url: String? = null,
    val rules: List<AvailabilityRuleDto> = emptyList(),
    val matchReason: String? = null,
    /** Reserved in v1 (wiki §4); decoded only when a server sends it. Absent means "no information", never "open". */
    val eligibility: OnDemandEligibilityDto? = null
)

/** The planned `eligibility` extension: `open`, `certificationRequired` or `unknown`, plus an information URL. */
@Serializable
data class OnDemandEligibilityDto(
    val requirement: String = "unknown",
    val infoUrl: String? = null
)
```

In `M/models/OnDemandService.kt`, before `data class OnDemandService`:

```kotlin
/** Who may ride (spec §2.9). [UNKNOWN] is also what an unrecognised wire value reads as. */
enum class EligibilityRequirement(val wire: String) {
    OPEN("open"),
    CERTIFICATION_REQUIRED("certificationRequired"),
    UNKNOWN("unknown");

    companion object {
        fun fromWire(wire: String): EligibilityRequirement = entries.firstOrNull { it.wire == wire } ?: UNKNOWN
    }
}

/** A service's eligibility statement; null on the service when the feed publishes none. */
data class OnDemandEligibility(val requirement: EligibilityRequirement, val infoUrl: String?)
```

and add the last constructor parameter to `OnDemandService`:

```kotlin
    val routeColor: Int? = null,
    val eligibility: OnDemandEligibility? = null
```

In `M/api/adapters/OnDemandAdapters.kt`, in the private `OnDemandServiceDto.toOnDemandService(...)` mapper, add after `routeColor = ...`:

```kotlin
        routeColor = routeId?.let { references.route(it)?.colorArgb() },
        eligibility = eligibility?.let { OnDemandEligibility(EligibilityRequirement.fromWire(it.requirement), it.infoUrl) }
```

and import `org.onebusaway.android.models.EligibilityRequirement` and `org.onebusaway.android.models.OnDemandEligibility`.

- [ ] **Step 4: Add the point probe to the data source**

In `M/api/data/OnDemandDataSource.kt` add the constant after `GEOMETRY_DETAIL_NONE`, the interface method after `servicesForViewport`, and the implementation:

```kotlin
/** The `geometryDetail` for the nearest-edge geometry (spec §2.7): the verbatim feed rings, cached per service. */
const val GEOMETRY_DETAIL_FULL = "full"

/** The radius, in metres, every rider/centre and exact-point probe asks for (spec §2.1). */
const val ONDEMAND_PROBE_RADIUS_METERS = 5_000
```

```kotlin
    /**
     * Point mode (spec §2.1): every service whose area contains [point] or lies within [radiusMeters]
     * of it, each carrying `matchReason` and per-area `distanceToArea` / `nearestPointOnBoundary`.
     * A probe like [servicesForViewport]: a raw HTTP 404 yields [OnDemandResult.Unsupported].
     */
    suspend fun servicesNear(point: GeoPoint, radiusMeters: Int, geometryDetail: String = GEOMETRY_DETAIL_NONE): OnDemandResult<List<OnDemandService>>
```

```kotlin
    override suspend fun servicesNear(point: GeoPoint, radiusMeters: Int, geometryDetail: String): OnDemandResult<List<OnDemandService>> = api.call { service ->
        service.onDemandServicesForLocation(
            lat = point.latitude,
            lon = point.longitude,
            radius = radiusMeters,
            geometryDetail = geometryDetail
        ).requireData().toOnDemandServices()
    }.toOnDemandResult(probe = true).logged("services-near")
```

Import `org.onebusaway.android.util.GeoPoint`.

- [ ] **Step 5: Teach the three test fakes the new method**

In `T/map/OnDemandLayerControllerTest.kt` (class `FakeDataSource`), `T/ui/ondemand/OnDemandServiceViewModelTest.kt` (class `FakeDataSource`) and `T/ui/arrivals/DefaultArrivalsRepositoryTest.kt` (class `NoOnDemandDataSource`) add:

```kotlin
        override suspend fun servicesNear(point: GeoPoint, radiusMeters: Int, geometryDetail: String): OnDemandResult<List<OnDemandService>> = OnDemandResult.Loaded(emptyList())
```

with `import org.onebusaway.android.util.GeoPoint` where missing.

- [ ] **Step 6: Run the tests and the compile gates**

Run: `./gradlew :onebusaway-android:testObaMaplibreDebugUnitTest --tests 'org.onebusaway.android.api.OnDemandEligibilityTest' --tests 'org.onebusaway.android.map.OnDemandLayerControllerTest' --tests 'org.onebusaway.android.ui.ondemand.OnDemandServiceViewModelTest' --tests 'org.onebusaway.android.ui.arrivals.DefaultArrivalsRepositoryTest'`
Expected: PASS.
Run: `./gradlew :onebusaway-android:compileObaGoogleDebugKotlin :onebusaway-android:compileObaMaplibreDebugKotlin -PwarningsAsErrors=true`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
./gradlew spotlessApply
git add onebusaway-android/src/main/java/org/onebusaway/android/api onebusaway-android/src/main/java/org/onebusaway/android/models/OnDemandService.kt onebusaway-android/src/test
git commit -m "Add the point-mode on-demand probe and eligibility" -m "The dock, address line and planner fallback ask whether a point is
inside or near a zone. That answer is the server's point-mode
matchReason and per-area distances, which only a radius query returns.
Eligibility is the reserved extension in wiki §4; decoding it now means
a server that adds it needs no client redesign, and absence stays
\"no information\" rather than \"open\"."
```

---

### Task 2: `OnDemandAvailability` (with the booking-line logic moved to the `ondemand` package)

**Files:**
- Create: `M/ondemand/ServiceBookingLine.kt` (the six private helpers of `M/ui/ondemand/OnDemandServicePresentation.kt:107-176` moved verbatim, made `internal`)
- Modify: `M/ui/ondemand/OnDemandServicePresentation.kt` (delete the moved helpers; import them)
- Create: `M/ondemand/OnDemandAvailability.kt`
- Create: `T/ondemand/OnDemandFixtures.kt` (shared builders for every later `ondemand` test)
- Test: `T/ondemand/OnDemandAvailabilityTest.kt`; the existing `T/ui/ondemand/OnDemandServicePresentationTest.kt` must keep passing unchanged.

**Interfaces:**
- Consumes: `BookingDeadlineEvaluator.{instantOf, serviceDayAnchor, evaluate, nextBookableServiceDate, nextServiceDateInState}`; `FlexCalendar.isActiveOn`; `OnDemandService.pickupBookingRule`; `OnDemandEligibility` (Task 1).
- Produces (all in package `org.onebusaway.android.ondemand`): `internal data class DatedEvaluation(val travelDate: LocalDate, val evaluation: BookingEvaluation)`; `internal fun OnDemandService.bookingLine(now: Instant, zone: ZoneId): DatedEvaluation?`; `internal fun OnDemandService.serviceNextBookableDate(now, zone): LocalDate?`; `internal fun OnDemandService.evaluateBooking(rule, date, now, zone): BookingEvaluation`; `internal fun agencyZone(timezone: String?): ZoneId?`; `enum class BookingTier { REAL_TIME, SAME_DAY, ADVANCE }`; `enum class OnDemandTag { NO_NOTICE_NEEDED, SAME_DAY_BOOKING, ADVANCE_BOOKING, ELIGIBILITY_REQUIRED }`; `sealed interface OnDemandStatus { OpenNow(until: Instant?), OpensAt(at: Instant), BookingOpens(at: Instant, travelDate: LocalDate), BookBy(deadline: Instant, travelDate: LocalDate), Closed, Unknown }`; `data class OnDemandAvailability(runningNow, runningUntil: Instant?, nextRunStart: Instant?, bookingTier: BookingTier?, bookableNow, nextBookableServiceDate: LocalDate?, status: OnDemandStatus, tags: Set<OnDemandTag>, usabilityTier: Int, nextChangeInstant: Instant?, zone: ZoneId?)` with `OnDemandAvailability.UNKNOWN`; `const val TIER_OPEN_NOW = 1 … TIER_UNKNOWN_OR_CLOSED = 5`; `fun computeAvailability(service: OnDemandService, now: Instant): OnDemandAvailability`; test fixtures `service(...)`, `rule(...)`, `calendar(...)`, `bookingRule(...)`, `area(...)`, `SQUARE`, `instant("…")`, `MON_TO_SAT`, `ALL_WEEK`, `CHARLEVOIX_TZ`.

- [ ] **Step 1: Move the booking-line helpers (no behaviour change)**

Create `M/ondemand/ServiceBookingLine.kt` with the licence header used by every file in the module and:

```kotlin
package org.onebusaway.android.ondemand

import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.onebusaway.android.models.AvailabilityRule
import org.onebusaway.android.models.OnDemandService

/** The verdict the booking line states, and the ride date it is for. */
internal data class DatedEvaluation(val travelDate: LocalDate, val evaluation: BookingEvaluation)

internal val UNKNOWN_EVALUATION = BookingEvaluation(BookingState.UNKNOWN, cutoffInstant = null, openInstant = null)

/**
 * What the booking line states; see `BookingSummary` in the service presentation. Null when no date
 * can be promised. Shared by the service page and [computeAvailability] so the two never disagree.
 */
internal fun OnDemandService.bookingLine(now: Instant, zone: ZoneId): DatedEvaluation? {
    val bookableDate = serviceNextBookableDate(now, zone) ?: return earliestOpening(now, zone)
    return earliestOpenEvaluation(bookableDate, now, zone)?.let { DatedEvaluation(bookableDate, it) }
}

/**
 * For a service with nothing bookable on any date: each rule on the first of its service days whose
 * booking has not opened yet, and of those the one whose booking opens earliest. The walk has to go
 * past the rule's next service day: with a one-day notice window that day is already closed by the
 * evening before, while the day after it is still to open.
 */
internal fun OnDemandService.earliestOpening(now: Instant, zone: ZoneId): DatedEvaluation? {
    val today = now.atZone(zone).toLocalDate()
    return rules
        .filterNot(::hasUnresolvedPickupBookingRule)
        .mapNotNull { rule ->
            BookingDeadlineEvaluator.nextServiceDateInState(rule, pickupBookingRule(rule), BookingState.NOT_YET_OPEN, today, now, zone, calendars)
                ?.let { date -> DatedEvaluation(date, evaluateBooking(rule, date, now, zone)) }
        }
        .minByOrNull { it.evaluation.openInstant ?: Instant.MAX }
}

/** The earliest date any rule can be booked for right now, or null when none can (spec §2.5, service level). */
internal fun OnDemandService.serviceNextBookableDate(now: Instant, zone: ZoneId): LocalDate? {
    val today = now.atZone(zone).toLocalDate()
    return rules
        .filterNot(::hasUnresolvedPickupBookingRule)
        .mapNotNull { rule -> BookingDeadlineEvaluator.nextBookableServiceDate(rule, pickupBookingRule(rule), today, now, zone, calendars) }
        .minOrNull()
}

/** The verdict with the earliest cutoff among the rules that evaluate OPEN on [date]. */
internal fun OnDemandService.earliestOpenEvaluation(date: LocalDate, now: Instant, zone: ZoneId): BookingEvaluation? = rules
    .filter { rule -> rule.calendarIds.any { calendars[it]?.isActiveOn(date) == true } }
    .map { rule -> evaluateBooking(rule, date, now, zone) }
    .filter { it.state == BookingState.OPEN }
    .minByOrNull { it.cutoffInstant ?: Instant.MAX }

/**
 * The rule names a pickup booking rule the references don't resolve (absent, or dropped at the
 * adapter for a `booking_type` this build can't read). Notice *is* required, we just can't say how
 * much — so it is [BookingState.UNKNOWN], never the evaluator's null-rule "no notice, book any time".
 */
internal fun OnDemandService.hasUnresolvedPickupBookingRule(rule: AvailabilityRule): Boolean = rule.pickupBookingRuleId != null && pickupBookingRule(rule) == null

internal fun OnDemandService.evaluateBooking(rule: AvailabilityRule, date: LocalDate, now: Instant, zone: ZoneId): BookingEvaluation = if (hasUnresolvedPickupBookingRule(rule)) {
    UNKNOWN_EVALUATION
} else {
    BookingDeadlineEvaluator.evaluate(rule, pickupBookingRule(rule), date, now, zone, calendars)
}

/**
 * The agency timezone, required by GTFS and always in the references; null when it is missing or an
 * id `java.time` doesn't know. Never the device zone: a deadline computed in the rider's zone rather
 * than the agency's could be hours late, the one error a rider can't recover from.
 */
internal fun agencyZone(timezone: String?): ZoneId? = try {
    timezone?.let(ZoneId::of)
} catch (_: DateTimeException) {
    null
}
```

In `M/ui/ondemand/OnDemandServicePresentation.kt` delete everything from `/** The verdict the booking line states…` (line 107) to the end of `agencyZone` (line 176) and the `UNKNOWN_EVALUATION` val, delete the now-unused imports (`DateTimeException`, `AvailabilityRule`, `BookingDeadlineEvaluator`, `BookingEvaluation` stays if `BookingSummary` uses it — it does, keep it), and add:

```kotlin
import org.onebusaway.android.ondemand.agencyZone
import org.onebusaway.android.ondemand.bookingLine
```

- [ ] **Step 2: Run the presentation tests to prove the move is pure**

Run: `./gradlew :onebusaway-android:testObaMaplibreDebugUnitTest --tests 'org.onebusaway.android.ui.ondemand.OnDemandServicePresentationTest'`
Expected: PASS (same nine tests).

- [ ] **Step 3: Write the shared fixtures**

Create `T/ondemand/OnDemandFixtures.kt`:

```kotlin
package org.onebusaway.android.ondemand

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import org.onebusaway.android.models.AvailabilityRule
import org.onebusaway.android.models.BookingRule
import org.onebusaway.android.models.BookingType
import org.onebusaway.android.models.FlexCalendar
import org.onebusaway.android.models.OnDemandEligibility
import org.onebusaway.android.models.OnDemandMatchReason
import org.onebusaway.android.models.OnDemandService
import org.onebusaway.android.models.OnDemandServiceKind
import org.onebusaway.android.models.ServiceArea
import org.onebusaway.android.models.ServiceDayTime
import org.onebusaway.android.util.GeoPoint

/** Charlevoix County, the fixture feed every manual check uses; Eastern time, on DST from 2026-03-08. */
internal const val CHARLEVOIX_TZ = "America/Detroit"

internal val MON_TO_SAT: Set<DayOfWeek> = DayOfWeek.entries.filter { it != DayOfWeek.SUNDAY }.toSet()
internal val MON_TO_FRI: Set<DayOfWeek> = DayOfWeek.entries.filter { it.value <= DayOfWeek.FRIDAY.value }.toSet()
internal val ALL_WEEK: Set<DayOfWeek> = DayOfWeek.entries.toSet()

/** A 0.1° square (about 11 km tall) south of Charlevoix: lat 45.0–45.1, lon −85.2 to −85.0, closed ring. */
internal val SQUARE = listOf(GeoPoint(45.0, -85.2), GeoPoint(45.0, -85.0), GeoPoint(45.1, -85.0), GeoPoint(45.1, -85.2), GeoPoint(45.0, -85.2))

internal fun instant(iso: String): Instant = OffsetDateTime.parse(iso).toInstant()

internal fun calendar(
    id: String = "CC_cal",
    days: Set<DayOfWeek> = MON_TO_SAT,
    start: LocalDate = LocalDate.of(2026, 1, 1),
    end: LocalDate = LocalDate.of(2026, 12, 31),
    excepted: Set<LocalDate> = emptySet()
) = FlexCalendar(id, days, start, end, excepted)

internal fun bookingRule(
    id: String = "CC_b1",
    type: BookingType = BookingType.SAME_DAY,
    durationMin: Int? = 60,
    lastDay: Int? = null,
    lastTime: String? = null,
    phone: String? = "231-582-6900",
    bookingUrl: String? = null,
    infoUrl: String? = null,
    message: String? = null
) = BookingRule(
    id = id,
    bookingType = type,
    priorNoticeDurationMin = durationMin,
    priorNoticeDurationMax = null,
    priorNoticeLastDay = lastDay,
    priorNoticeLastTime = lastTime?.let(ServiceDayTime::parse),
    priorNoticeStartDay = null,
    priorNoticeStartTime = null,
    priorNoticeCalendarId = null,
    message = message,
    pickupMessage = null,
    dropOffMessage = null,
    phoneNumber = phone,
    infoUrl = infoUrl,
    bookingUrl = bookingUrl
)

internal fun rule(
    calendarIds: List<String> = listOf("CC_cal"),
    start: String? = "07:20:00",
    end: String? = "16:40:00",
    bookingRuleId: String? = "CC_b1",
    fromIds: List<String> = listOf("CC_area"),
    toIds: List<String> = fromIds
) = AvailabilityRule(
    fromIds = fromIds,
    toIds = toIds,
    startPickupTime = start?.let(ServiceDayTime::parse),
    endPickupTime = end?.let(ServiceDayTime::parse),
    endDropOffTime = null,
    calendarIds = calendarIds,
    pickupType = 2,
    dropOffType = 2,
    pickupBookingRuleId = bookingRuleId,
    dropOffBookingRuleId = bookingRuleId,
    safeDurationFactor = null,
    safeDurationOffset = null
)

internal fun area(
    id: String = "CC_area",
    name: String? = "Charlevoix County",
    rings: List<List<GeoPoint>> = listOf(SQUARE),
    distance: Double? = null,
    nearest: GeoPoint? = null
): ServiceArea {
    val points = rings.flatten()
    return ServiceArea(
        id = id,
        name = name,
        description = null,
        southWest = GeoPoint(points.minOf { it.latitude }, points.minOf { it.longitude }),
        northEast = GeoPoint(points.maxOf { it.latitude }, points.maxOf { it.longitude }),
        polygons = listOf(rings),
        distanceToAreaMeters = distance,
        nearestPointOnBoundary = nearest
    )
}

internal fun service(
    id: String = "CC_CC1",
    name: String = "Dial-a-Ride",
    rules: List<AvailabilityRule> = listOf(rule()),
    calendars: Map<String, FlexCalendar> = mapOf("CC_cal" to calendar()),
    bookingRules: Map<String, BookingRule> = mapOf("CC_b1" to bookingRule()),
    areas: List<ServiceArea> = listOf(area()),
    timezone: String? = CHARLEVOIX_TZ,
    routeColor: Int? = null,
    matchReason: OnDemandMatchReason? = null,
    eligibility: OnDemandEligibility? = null,
    url: String? = null,
    description: String? = null,
    kind: OnDemandServiceKind = OnDemandServiceKind.ZONE
) = OnDemandService(
    id = id,
    agencyId = "CC",
    routeId = id,
    name = name,
    kind = kind,
    description = description,
    url = url,
    rules = rules,
    matchReason = matchReason,
    areas = areas,
    bookingRules = bookingRules,
    calendars = calendars,
    agencyTimezone = timezone,
    routeColor = routeColor,
    eligibility = eligibility
)
```

- [ ] **Step 4: Write the failing availability tests**

Create `T/ondemand/OnDemandAvailabilityTest.kt`:

```kotlin
package org.onebusaway.android.ondemand

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.models.BookingType
import org.onebusaway.android.models.EligibilityRequirement
import org.onebusaway.android.models.OnDemandEligibility

class OnDemandAvailabilityTest {

    // Tuesday 2026-03-10, Eastern Daylight Time.
    private val tuesdayTwoPm = instant("2026-03-10T14:00:00-04:00")

    @Test
    fun `running inside today's window with same-day booking is open now until the window ends`() {
        val availability = computeAvailability(service(), tuesdayTwoPm)
        assertTrue(availability.runningNow)
        assertEquals(instant("2026-03-10T16:40:00-04:00"), availability.runningUntil)
        assertTrue(availability.bookableNow)
        assertEquals(BookingTier.SAME_DAY, availability.bookingTier)
        assertEquals(OnDemandStatus.OpenNow(instant("2026-03-10T16:40:00-04:00")), availability.status)
        assertEquals(setOf(OnDemandTag.SAME_DAY_BOOKING), availability.tags)
        assertEquals(TIER_OPEN_NOW, availability.usabilityTier)
        // The same-day cutoff (16:40 − 60 min) comes before the window end.
        assertEquals(instant("2026-03-10T15:40:00-04:00"), availability.nextChangeInstant)
    }

    @Test
    fun `a window past midnight is found from the previous service day`() {
        // Fifteen minutes' notice: at 00:30 the 01:00 window end is still bookable for yesterday's service date.
        val night = service(rules = listOf(rule(start = "20:00:00", end = "25:00:00")), bookingRules = mapOf("CC_b1" to bookingRule(durationMin = 15)))
        val availability = computeAvailability(night, instant("2026-03-11T00:30:00-04:00"))
        assertTrue(availability.runningNow)
        assertEquals(instant("2026-03-11T01:00:00-04:00"), availability.runningUntil)
        assertTrue("bookableNow evaluates for yesterday's service date", availability.bookableNow)
    }

    @Test
    fun `an all-hours service on consecutive days runs continuously`() {
        val allDay = service(rules = listOf(rule(start = null, end = null)), calendars = mapOf("CC_cal" to calendar(days = ALL_WEEK)))
        val availability = computeAvailability(allDay, instant("2026-03-10T03:00:00-04:00"))
        assertTrue(availability.runningNow)
        assertNull("abutting windows read as one continuous service", availability.runningUntil)
        assertEquals(OnDemandStatus.OpenNow(null), availability.status)
    }

    @Test
    fun `an all-hours service whose next day is inactive ends at midnight`() {
        val monTue = service(rules = listOf(rule(start = null, end = null)), calendars = mapOf("CC_cal" to calendar(days = setOf(java.time.DayOfWeek.MONDAY, java.time.DayOfWeek.TUESDAY))))
        val availability = computeAvailability(monTue, instant("2026-03-10T03:00:00-04:00"))
        assertEquals(instant("2026-03-11T00:00:00-04:00"), availability.runningUntil)
    }

    @Test
    fun `an excepted date removes today and the next run is tomorrow`() {
        val excepted = service(calendars = mapOf("CC_cal" to calendar(excepted = setOf(LocalDate.of(2026, 3, 10)))))
        val availability = computeAvailability(excepted, tuesdayTwoPm)
        assertFalse(availability.runningNow)
        assertEquals(instant("2026-03-11T07:20:00-04:00"), availability.nextRunStart)
        assertEquals(OnDemandStatus.OpensAt(instant("2026-03-11T07:20:00-04:00")), availability.status)
        assertEquals(instant("2026-03-11T07:20:00-04:00"), availability.nextChangeInstant)
    }

    @Test
    fun `an added calendar day counts like any other calendar`() {
        val sunday = LocalDate.of(2026, 3, 15)
        val added = service(
            rules = listOf(rule(calendarIds = listOf("CC_cal", "CC_cal_added_20260315"))),
            calendars = mapOf("CC_cal" to calendar(), "CC_cal_added_20260315" to calendar(id = "CC_cal_added_20260315", days = ALL_WEEK, start = sunday, end = sunday))
        )
        assertTrue(computeAvailability(added, instant("2026-03-15T10:00:00-04:00")).runningNow)
    }

    @Test
    fun `after the window ends the next run starts tomorrow and the tier is advance`() {
        val availability = computeAvailability(service(), instant("2026-03-10T17:00:00-04:00"))
        assertFalse(availability.runningNow)
        assertEquals(instant("2026-03-11T07:20:00-04:00"), availability.nextRunStart)
        // Spec §2.6: the next bookable date is tomorrow, not today, so an after-hours same-day service reads as tier 3.
        assertEquals(LocalDate.of(2026, 3, 11), availability.nextBookableServiceDate)
        assertEquals(TIER_ADVANCE, availability.usabilityTier)
    }

    @Test
    fun `a same-day service not yet running but bookable for today is tier 2`() {
        // Window 16:30–18:00 with an hour's notice, now 16:00: not running yet, but today's cutoff
        // (17:00) is still ahead — tier 2, and "Opens" at 16:30.
        val later = service(rules = listOf(rule(start = "16:30:00", end = "18:00:00")))
        val availability = computeAvailability(later, instant("2026-03-10T16:00:00-04:00"))
        assertFalse(availability.runningNow)
        assertEquals(LocalDate.of(2026, 3, 10), availability.nextBookableServiceDate)
        assertEquals(TIER_SAME_DAY, availability.usabilityTier)
        assertEquals(OnDemandStatus.OpensAt(instant("2026-03-10T16:30:00-04:00")), availability.status)
    }

    @Test
    fun `the booking tier is the least demanding rule active on the next active date`() {
        val mixed = service(
            rules = listOf(rule(bookingRuleId = "CC_prior"), rule(bookingRuleId = null)),
            bookingRules = mapOf("CC_prior" to bookingRule(id = "CC_prior", type = BookingType.PRIOR_DAYS, lastDay = 1, lastTime = "15:10:00"))
        )
        val availability = computeAvailability(mixed, tuesdayTwoPm)
        assertEquals(BookingTier.REAL_TIME, availability.bookingTier)
        assertEquals(setOf(OnDemandTag.NO_NOTICE_NEEDED), availability.tags)
    }

    @Test
    fun `a dangling booking rule id is skipped for the tier`() {
        val dangling = service(rules = listOf(rule(bookingRuleId = "missing"), rule(bookingRuleId = "CC_b1")))
        assertEquals(BookingTier.SAME_DAY, computeAvailability(dangling, tuesdayTwoPm).bookingTier)
    }

    @Test
    fun `an advance service promotes its deadline even while running`() {
        val advance = service(bookingRules = mapOf("CC_b1" to bookingRule(type = BookingType.PRIOR_DAYS, durationMin = null, lastDay = 1, lastTime = "15:10:00")))
        val availability = computeAvailability(advance, tuesdayTwoPm)
        assertTrue(availability.runningNow)
        assertEquals(OnDemandStatus.BookBy(instant("2026-03-10T15:10:00-04:00"), LocalDate.of(2026, 3, 11)), availability.status)
        assertEquals(LocalDate.of(2026, 3, 11), availability.nextBookableServiceDate)
        assertEquals(TIER_ADVANCE, availability.usabilityTier)
        assertEquals(setOf(OnDemandTag.ADVANCE_BOOKING), availability.tags)
    }

    @Test
    fun `an advance service whose booking has not opened reads booking opens`() {
        val opensLater = bookingRule(type = BookingType.PRIOR_DAYS, durationMin = null, lastDay = 1, lastTime = "15:10:00")
            .copy(priorNoticeStartDay = 1, priorNoticeStartTime = org.onebusaway.android.models.ServiceDayTime.parse("18:00:00"))
        val advance = service(bookingRules = mapOf("CC_b1" to opensLater))
        // Tuesday 16:00: Wednesday's deadline (Tue 15:10) has passed; Thursday's booking opens Wed 18:00.
        val availability = computeAvailability(advance, instant("2026-03-10T16:00:00-04:00"))
        assertEquals(OnDemandStatus.BookingOpens(instant("2026-03-11T18:00:00-04:00"), LocalDate.of(2026, 3, 12)), availability.status)
        assertEquals(TIER_UNKNOWN_OR_CLOSED, availability.usabilityTier)
    }

    @Test
    fun `eligibility required adds the tag and tier 4 over open now`() {
        val restricted = service(eligibility = OnDemandEligibility(EligibilityRequirement.CERTIFICATION_REQUIRED, null))
        val availability = computeAvailability(restricted, tuesdayTwoPm)
        assertEquals(OnDemandStatus.OpenNow(instant("2026-03-10T16:40:00-04:00")), availability.status)
        assertEquals(setOf(OnDemandTag.SAME_DAY_BOOKING, OnDemandTag.ELIGIBILITY_REQUIRED), availability.tags)
        assertEquals(TIER_ELIGIBILITY, availability.usabilityTier)
    }

    @Test
    fun `open eligibility shows nothing`() {
        val open = service(eligibility = OnDemandEligibility(EligibilityRequirement.OPEN, null))
        assertEquals(setOf(OnDemandTag.SAME_DAY_BOOKING), computeAvailability(open, tuesdayTwoPm).tags)
    }

    @Test
    fun `a missing timezone is unknown at tier 5`() {
        val availability = computeAvailability(service(timezone = null), tuesdayTwoPm)
        assertEquals(OnDemandAvailability.UNKNOWN, availability)
        assertEquals(TIER_UNKNOWN_OR_CLOSED, availability.usabilityTier)
        assertTrue(availability.tags.isEmpty())
    }

    @Test
    fun `no rules is unknown`() {
        assertEquals(OnDemandStatus.Unknown, computeAvailability(service(rules = emptyList()), tuesdayTwoPm).status)
    }

    @Test
    fun `every calendar ended is closed, tier 5, and walks no further`() {
        val ended = service(calendars = mapOf("CC_cal" to calendar(end = LocalDate.of(2026, 2, 28))))
        val started = System.nanoTime()
        val availability = computeAvailability(ended, tuesdayTwoPm)
        assertEquals(OnDemandStatus.Closed, availability.status)
        assertEquals(TIER_UNKNOWN_OR_CLOSED, availability.usabilityTier)
        assertNull(availability.nextRunStart)
        assertNull(availability.nextChangeInstant)
        assertTrue("the lookahead is bounded by the calendar end", System.nanoTime() - started < 200_000_000L)
    }
}
```

- [ ] **Step 5: Run the tests to verify they fail**

Run: `./gradlew :onebusaway-android:testObaMaplibreDebugUnitTest --tests 'org.onebusaway.android.ondemand.OnDemandAvailabilityTest'`
Expected: compilation FAILS with `Unresolved reference: computeAvailability`.

- [ ] **Step 6: Implement the availability model**

Create `M/ondemand/OnDemandAvailability.kt`:

```kotlin
package org.onebusaway.android.ondemand

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.onebusaway.android.models.AvailabilityRule
import org.onebusaway.android.models.BookingType
import org.onebusaway.android.models.EligibilityRequirement
import org.onebusaway.android.models.OnDemandEligibility
import org.onebusaway.android.models.OnDemandService
import org.onebusaway.android.models.ServiceDayTime

/** How much notice a service's pickup booking rules demand, least demanding first (spec §2.5). */
enum class BookingTier { REAL_TIME, SAME_DAY, ADVANCE }

/** The chips a surface shows for a service: exactly one booking tag, plus eligibility when required. */
enum class OnDemandTag { NO_NOTICE_NEEDED, SAME_DAY_BOOKING, ADVANCE_BOOKING, ELIGIBILITY_REQUIRED }

/** The one line every surface leads with (spec §2.5 `status`), chosen in the order listed there. */
sealed interface OnDemandStatus {
    /** Running and bookable; [until] is null for a continuous service ("Open", not "until 12:00 AM"). */
    data class OpenNow(val until: Instant?) : OnDemandStatus

    /** Not running; the service starts running at [at]. */
    data class OpensAt(val at: Instant) : OnDemandStatus

    /** Advance booking whose window has not opened yet — the *booking* opens at [at], for [travelDate]. */
    data class BookingOpens(val at: Instant, val travelDate: LocalDate) : OnDemandStatus

    /** Advance booking that is open now and closes at [deadline] for a ride on [travelDate]. */
    data class BookBy(val deadline: Instant, val travelDate: LocalDate) : OnDemandStatus

    data object Closed : OnDemandStatus
    data object Unknown : OnDemandStatus
}

const val TIER_OPEN_NOW = 1
const val TIER_SAME_DAY = 2
const val TIER_ADVANCE = 3
const val TIER_ELIGIBILITY = 4
const val TIER_UNKNOWN_OR_CLOSED = 5

/**
 * Everything a surface says about whether a service can be used now, computed once from the
 * service, its rules, calendars and booking rules in the agency timezone at [now] (the device
 * clock). Surfaces read this; they never re-derive it.
 */
data class OnDemandAvailability(
    val runningNow: Boolean,
    val runningUntil: Instant?,
    val nextRunStart: Instant?,
    val bookingTier: BookingTier?,
    val bookableNow: Boolean,
    val nextBookableServiceDate: LocalDate?,
    val status: OnDemandStatus,
    val tags: Set<OnDemandTag>,
    val usabilityTier: Int,
    /** When the dock must re-evaluate (plus one second); null when nothing is scheduled to change. */
    val nextChangeInstant: Instant?,
    /** The agency timezone every instant above is formatted in; null only for [UNKNOWN]. */
    val zone: ZoneId?
) {
    companion object {
        /** The answer when the agency timezone is missing: every field null or false, tier 5. */
        val UNKNOWN = OnDemandAvailability(
            runningNow = false,
            runningUntil = null,
            nextRunStart = null,
            bookingTier = null,
            bookableNow = false,
            nextBookableServiceDate = null,
            status = OnDemandStatus.Unknown,
            tags = emptySet(),
            usabilityTier = TIER_UNKNOWN_OR_CLOSED,
            nextChangeInstant = null,
            zone = null
        )
    }
}

/** How far ahead the run/tier walks look, matching the evaluator's lookahead. */
private const val LOOKAHEAD_DAYS = 400L
private val MIDNIGHT = ServiceDayTime(0)
private val END_OF_SERVICE_DAY = ServiceDayTime(24 * 3600)

/** One rule's pickup window on one service date, as instants. */
private class PickupWindow(val rule: AvailabilityRule, val date: LocalDate, val start: Instant, val end: Instant)

fun computeAvailability(service: OnDemandService, now: Instant): OnDemandAvailability {
    val zone = agencyZone(service.agencyTimezone) ?: return OnDemandAvailability.UNKNOWN
    val today = now.atZone(zone).toLocalDate()
    val windows = ServiceWindows(service, zone)
    // Yesterday's windows are checked too because a window may pass 24:00.
    val containing = (windows.on(today.minusDays(1)) + windows.on(today)).filter { !now.isBefore(it.start) && now.isBefore(it.end) }
    val runningNow = containing.isNotEmpty()
    val latest = containing.maxByOrNull { it.end }
    val runningUntil = latest?.end?.takeUnless { end -> windows.on(latest.date.plusDays(1)).any { it.start == end } }
    val nextRunStart = if (runningNow) null else windows.nextStartAfter(now, today)
    val firstActiveDate = windows.firstActiveDate(today)
    val bookingTier = bookingTier(service, firstActiveDate)
    val bookableNow = containing.any { service.evaluateBooking(it.rule, it.date, now, zone).state == BookingState.OPEN }
    val nextBookable = service.serviceNextBookableDate(now, zone)
    val line = service.bookingLine(now, zone)
    val status = status(bookingTier, line, anyActive = firstActiveDate != null, hasRules = service.rules.isNotEmpty(), runningNow, bookableNow, runningUntil, nextRunStart)
    val tags = tags(bookingTier, service.eligibility)
    val bookingChange = line?.evaluation?.let { evaluation ->
        listOfNotNull(evaluation.openInstant, evaluation.cutoffInstant).filter { it.isAfter(now) }.minOrNull()
    }
    return OnDemandAvailability(
        runningNow = runningNow,
        runningUntil = runningUntil,
        nextRunStart = nextRunStart,
        bookingTier = bookingTier,
        bookableNow = bookableNow,
        nextBookableServiceDate = nextBookable,
        status = status,
        tags = tags,
        usabilityTier = usabilityTier(tags, status, nextBookable, today),
        nextChangeInstant = listOfNotNull(runningUntil, nextRunStart, bookingChange).minOrNull(),
        zone = zone
    )
}

/** The ordered decision of spec §2.6; the first matching line wins. */
internal fun usabilityTier(tags: Set<OnDemandTag>, status: OnDemandStatus, nextBookableServiceDate: LocalDate?, today: LocalDate): Int = when {
    OnDemandTag.ELIGIBILITY_REQUIRED in tags -> TIER_ELIGIBILITY
    status is OnDemandStatus.OpenNow -> TIER_OPEN_NOW
    status is OnDemandStatus.Closed || status is OnDemandStatus.Unknown || nextBookableServiceDate == null -> TIER_UNKNOWN_OR_CLOSED
    nextBookableServiceDate == today -> TIER_SAME_DAY
    else -> TIER_ADVANCE
}

private class ServiceWindows(private val service: OnDemandService, private val zone: ZoneId) {

    fun on(date: LocalDate): List<PickupWindow> = service.rules
        .filter { rule -> rule.calendarIds.any { service.calendars[it]?.isActiveOn(date) == true } }
        .map { rule ->
            PickupWindow(
                rule = rule,
                date = date,
                start = BookingDeadlineEvaluator.instantOf(date, rule.startPickupTime ?: MIDNIGHT, zone),
                end = BookingDeadlineEvaluator.instantOf(date, rule.endPickupTime ?: END_OF_SERVICE_DAY, zone)
            )
        }

    /** The earliest window start after [now] from yesterday through [LOOKAHEAD_DAYS] ahead, or null. */
    fun nextStartAfter(now: Instant, today: LocalDate): Instant? {
        val lastCalendarDay = latestCalendarEnd() ?: return null
        var best: Instant? = null
        var date = today.minusDays(1)
        val last = minOf(today.plusDays(LOOKAHEAD_DAYS), lastCalendarDay)
        while (!date.isAfter(last)) {
            // Every window on a day starts at or after that day's anchor, so once the anchor passes the
            // best candidate no later day can beat it.
            val current = best
            if (current != null && !BookingDeadlineEvaluator.serviceDayAnchor(date, zone).isBefore(current)) break
            val candidate = on(date).map { it.start }.filter { it.isAfter(now) }.minOrNull()
            if (candidate != null && (current == null || candidate.isBefore(current))) best = candidate
            date = date.plusDays(1)
        }
        return best
    }

    /** The first date from [today] on which any rule is active, bounded by the calendars and the lookahead. */
    fun firstActiveDate(today: LocalDate): LocalDate? {
        val lastCalendarDay = latestCalendarEnd() ?: return null
        val last = minOf(today.plusDays(LOOKAHEAD_DAYS), lastCalendarDay)
        return generateSequence(today) { it.plusDays(1) }
            .takeWhile { !it.isAfter(last) }
            .firstOrNull { date -> on(date).isNotEmpty() }
    }

    private fun latestCalendarEnd(): LocalDate? = service.rules
        .flatMap { it.calendarIds }
        .mapNotNull { service.calendars[it]?.endDate }
        .maxOrNull()
}

/**
 * The least demanding pickup booking rule among the rules active on [date]: a null id is real-time,
 * a dangling id is skipped. Null when no date is active or nothing resolves.
 */
private fun bookingTier(service: OnDemandService, date: LocalDate?): BookingTier? {
    if (date == null) return null
    return service.rules
        .filter { rule -> rule.calendarIds.any { service.calendars[it]?.isActiveOn(date) == true } }
        .mapNotNull { rule ->
            val id = rule.pickupBookingRuleId ?: return@mapNotNull BookingTier.REAL_TIME
            when (service.bookingRules[id]?.bookingType) {
                BookingType.REAL_TIME -> BookingTier.REAL_TIME
                BookingType.SAME_DAY -> BookingTier.SAME_DAY
                BookingType.PRIOR_DAYS -> BookingTier.ADVANCE
                null -> null
            }
        }
        .minOrNull()
}

private fun status(
    tier: BookingTier?,
    line: DatedEvaluation?,
    anyActive: Boolean,
    hasRules: Boolean,
    runningNow: Boolean,
    bookableNow: Boolean,
    runningUntil: Instant?,
    nextRunStart: Instant?
): OnDemandStatus = when {
    tier == BookingTier.ADVANCE -> advanceStatus(line, anyActive)
    runningNow && bookableNow -> OnDemandStatus.OpenNow(runningUntil)
    nextRunStart != null -> OnDemandStatus.OpensAt(nextRunStart)
    // Rules that never activate again are closed; no rules at all is not knowable (spec §2.5 steps 4–5).
    !anyActive && hasRules -> OnDemandStatus.Closed
    else -> OnDemandStatus.Unknown
}

/** An advance service leads with its booking window, not with whether vehicles are running. */
private fun advanceStatus(line: DatedEvaluation?, anyActive: Boolean): OnDemandStatus {
    if (line == null) return if (anyActive) OnDemandStatus.Unknown else OnDemandStatus.Closed
    val evaluation = line.evaluation
    val opens = evaluation.openInstant
    val cutoff = evaluation.cutoffInstant
    return when {
        evaluation.state == BookingState.NOT_YET_OPEN && opens != null -> OnDemandStatus.BookingOpens(opens, line.travelDate)
        evaluation.state == BookingState.OPEN && cutoff != null -> OnDemandStatus.BookBy(cutoff, line.travelDate)
        else -> OnDemandStatus.Unknown
    }
}

private fun tags(tier: BookingTier?, eligibility: OnDemandEligibility?): Set<OnDemandTag> = buildSet {
    when (tier) {
        BookingTier.REAL_TIME -> add(OnDemandTag.NO_NOTICE_NEEDED)
        BookingTier.SAME_DAY -> add(OnDemandTag.SAME_DAY_BOOKING)
        BookingTier.ADVANCE -> add(OnDemandTag.ADVANCE_BOOKING)
        null -> Unit
    }
    if (eligibility?.requirement == EligibilityRequirement.CERTIFICATION_REQUIRED) add(OnDemandTag.ELIGIBILITY_REQUIRED)
}
```

- [ ] **Step 7: Run the tests**

Run: `./gradlew :onebusaway-android:testObaMaplibreDebugUnitTest --tests 'org.onebusaway.android.ondemand.OnDemandAvailabilityTest' --tests 'org.onebusaway.android.ui.ondemand.OnDemandServicePresentationTest'`
Expected: PASS. If `an advance service whose booking has not opened reads booking opens` fails on the travel date, print `line` from `bookingLine` for that fixture and correct the expected date in the test to the evaluator's answer (the evaluator, not this test, owns the count-back); the assertion on `BookingOpens` with `Wed 18:00` must hold.

- [ ] **Step 8: Compile both flavours and commit**

Run: `./gradlew :onebusaway-android:compileObaGoogleDebugKotlin :onebusaway-android:compileObaMaplibreDebugKotlin -PwarningsAsErrors=true`
Expected: BUILD SUCCESSFUL.

```bash
./gradlew spotlessApply
git add onebusaway-android/src/main/java/org/onebusaway/android/ondemand onebusaway-android/src/main/java/org/onebusaway/android/ui/ondemand/OnDemandServicePresentation.kt onebusaway-android/src/test/java/org/onebusaway/android/ondemand
git commit -m "Compute on-demand availability from rules and calendars" -m "The dock, picker, card and planner all need one answer to \"can I use
this now, and if not, when\". The booking-line helpers move out of the
page presentation into the ondemand package so the page and the new
availability model share them and cannot disagree."
```

---

### Task 3: Match, sort order, probe types and service colours

**Files:**
- Create: `M/ondemand/ProbePoint.kt`
- Create: `M/ondemand/OnDemandMatch.kt`
- Create: `M/ondemand/OnDemandColors.kt`
- Test: `T/ondemand/OnDemandSortTest.kt`, `T/ondemand/OnDemandColorsTest.kt`

**Interfaces:**
- Consumes: `OnDemandAvailability`, `computeAvailability` (Task 2); `ServiceArea.distanceToAreaMeters` / `nearestPointOnBoundary`; `OnDemandMatchReason`.
- Produces: `sealed interface ProbeSource { Rider; MapCenter; Point(label: String?) }`; `data class ProbePoint(point: GeoPoint, source: ProbeSource)`; `data class LocationCheck(source: ProbeSource, isInside: Boolean, locality: String?, point: GeoPoint? = null)`; `data class OnDemandMatch(service, matchReason: OnDemandMatchReason?, distanceToAreaMeters: Double?, nearestPointOnBoundary: GeoPoint?, availability) { val isInside; val isNearby }`; `fun matchFor(service, now): OnDemandMatch`; `fun List<OnDemandMatch>.sortedSoonestUsable(locale: Locale = Locale.getDefault()): List<OnDemandMatch>`; `const val ONDEMAND_NEARBY_MAX_METERS = 5_000.0`; `val ONDEMAND_FALLBACK_PALETTE: List<Int>`; `fun resolveServiceColors(services: List<OnDemandService>, brand: Int): Map<String, Int>`; `fun readableTextColor(background: Int, preferred: Int?): Int`; `const val ONDEMAND_OUTSIDE_GRAY = 0xFF636366.toInt()`.

- [ ] **Step 1: Write the failing tests**

Create `T/ondemand/OnDemandSortTest.kt`:

```kotlin
package org.onebusaway.android.ondemand

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.models.BookingType
import org.onebusaway.android.models.EligibilityRequirement
import org.onebusaway.android.models.OnDemandEligibility
import org.onebusaway.android.models.OnDemandMatchReason
import org.onebusaway.android.util.GeoPoint

class OnDemandSortTest {

    private val now = instant("2026-03-10T14:00:00-04:00")

    private fun match(id: String, name: String = id, distance: Double? = 0.0, tier: Int? = null, closed: Boolean = false, eligible: Boolean = false, advance: Boolean = false): OnDemandMatch {
        val base = service(
            id = id,
            name = name,
            areas = listOf(area(distance = distance, nearest = distance?.let { GeoPoint(45.0, -85.1) })),
            matchReason = if (distance == 0.0) OnDemandMatchReason.AREA_CONTAINS_POINT else OnDemandMatchReason.AREA_NEARBY,
            eligibility = if (eligible) OnDemandEligibility(EligibilityRequirement.CERTIFICATION_REQUIRED, null) else null,
            calendars = mapOf("CC_cal" to calendar(end = if (closed) java.time.LocalDate.of(2026, 2, 1) else java.time.LocalDate.of(2026, 12, 31))),
            bookingRules = mapOf("CC_b1" to if (advance) bookingRule(type = BookingType.PRIOR_DAYS, durationMin = null, lastDay = 1, lastTime = "15:10:00") else bookingRule())
        )
        val computed = matchFor(base, now)
        return if (tier == null) computed else computed.copy(availability = computed.availability.copy(usabilityTier = tier))
    }

    @Test
    fun `matchFor takes the nearest area's distance and boundary point`() {
        val far = area(id = "far", distance = 900.0, nearest = GeoPoint(45.2, -85.1))
        val near = area(id = "near", distance = 120.0, nearest = GeoPoint(45.05, -85.3))
        val match = matchFor(service(areas = listOf(far, near), matchReason = OnDemandMatchReason.AREA_NEARBY), now)
        assertEquals(120.0, match.distanceToAreaMeters)
        assertEquals(GeoPoint(45.05, -85.3), match.nearestPointOnBoundary)
        assertTrue(match.isNearby)
    }

    @Test
    fun `a stop group has no distance`() {
        val match = matchFor(service(areas = emptyList(), matchReason = OnDemandMatchReason.STOP_WITHIN_RADIUS), now)
        assertNull(match.distanceToAreaMeters)
        assertTrue(!match.isNearby)
    }

    @Test
    fun `tiers order open now, same-day, advance, eligibility, closed`() {
        val sorted = listOf(match("closed", closed = true), match("elig", eligible = true), match("advance", advance = true), match("open"), match("sameday", tier = TIER_SAME_DAY))
            .sortedSoonestUsable(Locale.US)
        assertEquals(listOf("open", "sameday", "advance", "elig", "closed"), sorted.map { it.service.id })
    }

    @Test
    fun `within a tier distance ascends with null last`() {
        val sorted = listOf(match("none", distance = null), match("far", distance = 800.0), match("inside", distance = 0.0), match("near", distance = 40.0))
            .map { it.copy(availability = it.availability.copy(usabilityTier = TIER_OPEN_NOW)) }
            .sortedSoonestUsable(Locale.US)
        assertEquals(listOf("inside", "near", "far", "none"), sorted.map { it.service.id })
    }

    @Test
    fun `names break ties naturally and case-insensitively`() {
        val sorted = listOf(match("b", name = "Route 10"), match("a", name = "route 9"), match("c", name = "Route 2"))
            .sortedSoonestUsable(Locale.US)
        assertEquals(listOf("Route 2", "route 9", "Route 10"), sorted.map { it.service.name })
    }
}
```

Create `T/ondemand/OnDemandColorsTest.kt`:

```kotlin
package org.onebusaway.android.ondemand

import org.junit.Assert.assertEquals
import org.junit.Test

class OnDemandColorsTest {

    private val brand = 0xFF78AA36.toInt()

    @Test
    fun `a route colour is used opaque and the brand fills in for none`() {
        val colours = resolveServiceColors(listOf(service(id = "a", routeColor = 0xFF112233.toInt()), service(id = "b")), brand)
        assertEquals(0xFF112233.toInt(), colours["a"])
        assertEquals(brand, colours["b"])
    }

    @Test
    fun `collisions take the palette in service id order regardless of list order`() {
        val first = resolveServiceColors(listOf(service(id = "b"), service(id = "a"), service(id = "c")), brand)
        val second = resolveServiceColors(listOf(service(id = "c"), service(id = "a"), service(id = "b")), brand)
        assertEquals(first, second)
        assertEquals(brand, first["a"])
        assertEquals(ONDEMAND_FALLBACK_PALETTE[0], first["b"])
        assertEquals(ONDEMAND_FALLBACK_PALETTE[1], first["c"])
    }

    @Test
    fun `a palette colour already taken by a route colour is skipped`() {
        val colours = resolveServiceColors(listOf(service(id = "a", routeColor = ONDEMAND_FALLBACK_PALETTE[0]), service(id = "b"), service(id = "c")), brand)
        assertEquals(brand, colours["b"])
        assertEquals(ONDEMAND_FALLBACK_PALETTE[1], colours["c"])
    }

    @Test
    fun `text colour prefers the route text colour then contrast`() {
        assertEquals(0xFFFFFF00.toInt(), readableTextColor(0xFF000080.toInt(), preferred = 0xFFFFFF00.toInt()))
        assertEquals(0xFFFFFFFF.toInt(), readableTextColor(0xFF1A3A8A.toInt(), preferred = null))
        assertEquals(0xFF000000.toInt(), readableTextColor(0xFFF5F5C0.toInt(), preferred = null))
        assertEquals(0xFFFFFFFF.toInt(), readableTextColor(ONDEMAND_OUTSIDE_GRAY, preferred = null))
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :onebusaway-android:testObaMaplibreDebugUnitTest --tests 'org.onebusaway.android.ondemand.OnDemandSortTest' --tests 'org.onebusaway.android.ondemand.OnDemandColorsTest'`
Expected: compilation FAILS (`Unresolved reference: matchFor`, `resolveServiceColors`).

- [ ] **Step 3: Write the types**

Create `M/ondemand/ProbePoint.kt`:

```kotlin
package org.onebusaway.android.ondemand

import org.onebusaway.android.util.GeoPoint

/** Where a probe's point came from (spec §2.1, §2.8): it decides the copy every surface reads. */
sealed interface ProbeSource {
    /** The rider's own location. */
    data object Rider : ProbeSource

    /** The map centre, used when there is no authorised fix. */
    data object MapCenter : ProbeSource

    /** A place the rider chose — a dropped pin or a planner endpoint; [label] names it when known. */
    data class Point(val label: String?) : ProbeSource
}

data class ProbePoint(val point: GeoPoint, val source: ProbeSource)

/**
 * What the zone detail page says about the probe that opened it (spec §3.6 item 3). [point] is the
 * probe coordinate, drawn on the page's thumbnail when it lies inside the zone's box; null from a
 * caller that has no coordinate to offer.
 */
data class LocationCheck(val source: ProbeSource, val isInside: Boolean, val locality: String?, val point: GeoPoint? = null)
```

Create `M/ondemand/OnDemandMatch.kt`:

```kotlin
package org.onebusaway.android.ondemand

import java.text.Collator
import java.time.Instant
import java.util.Locale
import org.onebusaway.android.models.OnDemandMatchReason
import org.onebusaway.android.models.OnDemandService
import org.onebusaway.android.util.GeoPoint

/** Beyond this the docked bar and address line say nothing rather than nag (spec §2.4, §3.7). */
const val ONDEMAND_NEARBY_MAX_METERS = 5_000.0

/**
 * One service in a probe result (spec §2.1): the server's reason, the minimum distance over the
 * service's areas with that area's boundary point, and the availability computed at probe time.
 */
data class OnDemandMatch(
    val service: OnDemandService,
    val matchReason: OnDemandMatchReason?,
    val distanceToAreaMeters: Double?,
    val nearestPointOnBoundary: GeoPoint?,
    val availability: OnDemandAvailability
) {
    val isInside: Boolean get() = matchReason == OnDemandMatchReason.AREA_CONTAINS_POINT

    /** Outside, with a finite distance the dock is allowed to show. A pure stop group (null) never is. */
    val isNearby: Boolean get() = !isInside && distanceToAreaMeters != null && distanceToAreaMeters <= ONDEMAND_NEARBY_MAX_METERS
}

fun matchFor(service: OnDemandService, now: Instant): OnDemandMatch {
    val nearest = service.areas
        .mapNotNull { area -> area.distanceToAreaMeters?.let { it to area } }
        .minByOrNull { it.first }
    return OnDemandMatch(
        service = service,
        matchReason = service.matchReason,
        distanceToAreaMeters = nearest?.first,
        nearestPointOnBoundary = nearest?.second?.nearestPointOnBoundary,
        availability = computeAvailability(service, now)
    )
}

/** Spec §2.6: tier, then distance (inside first, null last), then the name in natural order. */
fun List<OnDemandMatch>.sortedSoonestUsable(locale: Locale = Locale.getDefault()): List<OnDemandMatch> {
    val collator = Collator.getInstance(locale).apply { strength = Collator.SECONDARY }
    return sortedWith(
        compareBy<OnDemandMatch> { it.availability.usabilityTier }
            .thenBy(nullsLast()) { it.distanceToAreaMeters }
            .thenComparator { a, b -> naturalCompare(a.service.name, b.service.name, collator) }
    )
}

/**
 * Locale-aware comparison that orders digit runs by value ("Route 9" before "Route 10"), which a
 * plain [Collator] does not. Public because the picker and the layers sheet sort names too.
 */
fun naturalCompare(a: String, b: String, collator: Collator): Int {
    val left = chunk(a)
    val right = chunk(b)
    for (i in 0 until minOf(left.size, right.size)) {
        val x = left[i]
        val y = right[i]
        val cmp = if (x.first().isDigit() && y.first().isDigit()) {
            x.trimStart('0').length.compareTo(y.trimStart('0').length).takeIf { it != 0 } ?: x.trimStart('0').compareTo(y.trimStart('0'))
        } else {
            collator.compare(x, y)
        }
        if (cmp != 0) return cmp
    }
    return left.size.compareTo(right.size)
}

private fun chunk(text: String): List<String> {
    val chunks = mutableListOf<String>()
    var current = StringBuilder()
    for (ch in text) {
        if (current.isNotEmpty() && current.last().isDigit() != ch.isDigit()) {
            chunks += current.toString()
            current = StringBuilder()
        }
        current.append(ch)
    }
    if (current.isNotEmpty()) chunks += current.toString()
    return chunks
}
```

Create `M/ondemand/OnDemandColors.kt`:

```kotlin
package org.onebusaway.android.ondemand

import kotlin.math.pow
import org.onebusaway.android.models.OnDemandService

/** Spec §2.3: the fixed fallback palette for colour collisions, in order. */
val ONDEMAND_FALLBACK_PALETTE: List<Int> = listOf(0xFF3B82F6, 0xFFD97706, 0xFF7C3AED, 0xFFDB2777, 0xFF0891B2, 0xFF65A30D).map { it.toInt() }

/** The docked bar's colour when the probe point is outside every zone (spec §3.4). */
const val ONDEMAND_OUTSIDE_GRAY: Int = 0xFF636366.toInt()

private const val OPAQUE = 0xFF000000.toInt()
private const val MIN_CONTRAST = 4.5

/**
 * One opaque colour per service id: the route colour, else [brand]; when two services in the same
 * list resolve to the same colour, the second and later ones (by service id order, so the answer is
 * stable across refreshes) take the first palette colours no earlier service already has.
 */
fun resolveServiceColors(services: List<OnDemandService>, brand: Int): Map<String, Int> {
    val taken = mutableSetOf<Int>()
    val resolved = mutableMapOf<String, Int>()
    for (service in services.distinctBy { it.id }.sortedBy { it.id }) {
        val wanted = (service.routeColor ?: brand) or OPAQUE
        val colour = if (wanted in taken) ONDEMAND_FALLBACK_PALETTE.firstOrNull { it !in taken } ?: wanted else wanted
        taken += colour
        resolved[service.id] = colour
    }
    return resolved
}

/**
 * Spec §5: the route's own text colour when it has one; otherwise white or black, whichever reaches
 * a 4.5:1 contrast ratio against [background] (white when both do, so the outside gray reads white).
 */
fun readableTextColor(background: Int, preferred: Int?): Int {
    if (preferred != null) return preferred or OPAQUE
    val white = 0xFFFFFFFF.toInt()
    val black = 0xFF000000.toInt()
    return if (contrastRatio(white, background) >= MIN_CONTRAST) white else if (contrastRatio(black, background) >= MIN_CONTRAST) black else white
}

private fun contrastRatio(a: Int, b: Int): Double {
    val la = relativeLuminance(a) + 0.05
    val lb = relativeLuminance(b) + 0.05
    return maxOf(la, lb) / minOf(la, lb)
}

private fun relativeLuminance(argb: Int): Double {
    fun channel(shift: Int): Double {
        val c = ((argb shr shift) and 0xFF) / 255.0
        return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
}
```

- [ ] **Step 4: Run the tests**

Run: `./gradlew :onebusaway-android:testObaMaplibreDebugUnitTest --tests 'org.onebusaway.android.ondemand.OnDemandSortTest' --tests 'org.onebusaway.android.ondemand.OnDemandColorsTest'`
Expected: PASS. (`0xFF1A3A8A` dark blue: white contrast ≈ 9.6; `0xFFF5F5C0` pale yellow: black ≈ 18.)

- [ ] **Step 5: Compile both flavours and commit**

Run: `./gradlew :onebusaway-android:compileObaGoogleDebugKotlin :onebusaway-android:compileObaMaplibreDebugKotlin -PwarningsAsErrors=true`
Expected: BUILD SUCCESSFUL.

```bash
./gradlew spotlessApply
git add onebusaway-android/src/main/java/org/onebusaway/android/ondemand onebusaway-android/src/test/java/org/onebusaway/android/ondemand
git commit -m "Add on-demand matches, their sort order and colours" -m "A probe result is a list of matches sorted by how soon the rider can
use each service; the picker, bar, card and planner all read that one
order. Colours resolve once per list so the bar, pin and polygon of a
service always agree, with a fixed palette for collisions."
```

---

### Task 4: Label point, nearest edge, compass buckets and the zoom level

**Files:**
- Modify: `M/map/render/ZoneGeometry.kt` (append)
- Create: `M/map/OnDemandZoomLevel.kt`
- Test: `T/map/render/ZoneGeometryTest.kt` (append), `T/map/OnDemandZoomLevelTest.kt`

**Interfaces:**
- Consumes: `pointInRing` (`ZoneGeometry.kt:40`), `visibleHeightMeters` (`M/map/rental/RentalGuardrails.kt`), `ONDEMAND_MAX_VISIBLE_HEIGHT_METERS` (`M/map/OnDemandLayerController.kt:172`), `ServiceArea.polygons`, `OnDemandService.areas`.
- Produces (package `org.onebusaway.android.map.render`): `const val EARTH_RADIUS_METERS = 6_371_000.0`; `class LocalProjection(origin: GeoPoint) { fun x(p): Double; fun y(p): Double; fun toGeo(x, y): GeoPoint }`; `enum class CompassDirection { NORTH, NORTHEAST, EAST, SOUTHEAST, SOUTH, SOUTHWEST, WEST, NORTHWEST }`; `fun compassDirection(bearingDegrees: Double): CompassDirection`; `fun bearingDegrees(from: GeoPoint, to: GeoPoint): Double`; `data class ZoneEdge(distanceMeters: Double, point: GeoPoint, bearingDegrees: Double) { val edgeDirection; val riderDirection }`; `fun nearestBoundaryPoint(from: GeoPoint, areas: List<ServiceArea>): ZoneEdge?`; `fun labelPoint(rings: List<List<GeoPoint>>, bbox: Pair<GeoPoint, GeoPoint>): GeoPoint`; `fun ringBounds(ring): Pair<GeoPoint, GeoPoint>?`; `fun largestPolygon(areas: List<ServiceArea>): List<List<GeoPoint>>?`; `fun pinPointFor(areas: List<ServiceArea>): GeoPoint?`; `const val ONDEMAND_NEAR_EDGE_METERS = 100.0`. Package `org.onebusaway.android.map`: `enum class OnDemandZoomLevel { HIDDEN, REGION, STREET }`; `const val ONDEMAND_STREET_LEVEL_MAX_HEIGHT_METERS = 4_000.0`; `fun onDemandZoomLevel(latSpan: Double): OnDemandZoomLevel`.

- [ ] **Step 1: Write the failing tests**

Append to `T/map/render/ZoneGeometryTest.kt` (inside the class; add `import org.onebusaway.android.models.ServiceArea` and `import org.junit.Assert.assertNotEquals`):

```kotlin
    // A 0.1° square: lat 45.0–45.1, lon −85.2 to −85.0.
    private val square = listOf(GeoPoint(45.0, -85.2), GeoPoint(45.0, -85.0), GeoPoint(45.1, -85.0), GeoPoint(45.1, -85.2), GeoPoint(45.0, -85.2))
    private val squareBounds = GeoPoint(45.0, -85.2) to GeoPoint(45.1, -85.0)
    private val innerHole = listOf(GeoPoint(45.03, -85.13), GeoPoint(45.03, -85.07), GeoPoint(45.07, -85.07), GeoPoint(45.07, -85.13), GeoPoint(45.03, -85.13))
    private val uShape = listOf(
        GeoPoint(45.0, -85.2), GeoPoint(45.0, -85.0), GeoPoint(45.1, -85.0), GeoPoint(45.1, -85.05),
        GeoPoint(45.02, -85.05), GeoPoint(45.02, -85.15), GeoPoint(45.1, -85.15), GeoPoint(45.1, -85.2), GeoPoint(45.0, -85.2)
    )

    private fun areaOf(vararg polygons: List<List<GeoPoint>>): ServiceArea {
        val points = polygons.flatten().flatten()
        return ServiceArea("a", null, null, GeoPoint(points.minOf { it.latitude }, points.minOf { it.longitude }), GeoPoint(points.maxOf { it.latitude }, points.maxOf { it.longitude }), polygons.toList(), null, null)
    }

    @Test
    fun `the label point of a convex ring is inside it`() {
        val label = labelPoint(listOf(square), squareBounds)
        assertTrue(pointInRing(label, square))
        assertEquals(45.05, label.latitude, 1e-9)
        assertEquals(-85.1, label.longitude, 1e-9)
    }

    @Test
    fun `the label point of a U shape lands in an arm, not the notch`() {
        val label = labelPoint(listOf(uShape), squareBounds)
        assertTrue(pointInRing(label, uShape))
        assertNotEquals(-85.1, label.longitude, 1e-6)
    }

    @Test
    fun `the label point of a holed polygon avoids the hole`() {
        val label = labelPoint(listOf(square, innerHole), squareBounds)
        assertTrue(pointInRing(label, square))
        assertFalse(pointInRing(label, innerHole))
    }

    @Test
    fun `a vertex on the mid-latitude and a degenerate ring fall back`() {
        val diamond = listOf(GeoPoint(45.05, -85.2), GeoPoint(45.0, -85.1), GeoPoint(45.05, -85.0), GeoPoint(45.1, -85.1), GeoPoint(45.05, -85.2))
        val label = labelPoint(listOf(diamond), squareBounds)
        assertTrue(pointInRing(label, diamond))
        // Two points make no ring: the bbox centre is the only honest answer.
        val twoPoints = labelPoint(listOf(listOf(GeoPoint(45.0, -85.2), GeoPoint(45.1, -85.0))), squareBounds)
        assertEquals(45.05, twoPoints.latitude, 1e-9)
        assertEquals(-85.1, twoPoints.longitude, 1e-9)
        // No rings at all also uses the bbox centre.
        val none = labelPoint(emptyList(), squareBounds)
        assertEquals(45.05, none.latitude, 1e-9)
        assertEquals(-85.1, none.longitude, 1e-9)
    }

    @Test
    fun `the pin goes in the largest polygon of a two-polygon service`() {
        val small = listOf(GeoPoint(45.2, -85.2), GeoPoint(45.2, -85.19), GeoPoint(45.21, -85.19), GeoPoint(45.21, -85.2), GeoPoint(45.2, -85.2))
        val pin = requireNotNull(pinPointFor(listOf(areaOf(listOf(small), listOf(square)))))
        assertTrue(pointInRing(pin, square))
    }

    @Test
    fun `the nearest edge of a point just inside the north side is north and close`() {
        val edge = requireNotNull(nearestBoundaryPoint(GeoPoint(45.0999, -85.1), listOf(areaOf(listOf(square)))))
        assertEquals(11.1, edge.distanceMeters, 0.3)
        assertEquals(CompassDirection.NORTH, edge.edgeDirection)
        assertEquals(CompassDirection.SOUTH, edge.riderDirection)
        assertEquals(45.1, edge.point.latitude, 1e-6)
    }

    @Test
    fun `a point due south of the square is south of the zone`() {
        val edge = requireNotNull(nearestBoundaryPoint(GeoPoint(44.95, -85.1), listOf(areaOf(listOf(square)))))
        assertEquals(5560.0, edge.distanceMeters, 3.0)
        assertEquals(CompassDirection.NORTH, edge.edgeDirection)
        assertEquals(CompassDirection.SOUTH, edge.riderDirection)
    }

    @Test
    fun `a hole's edge counts as a boundary`() {
        val edge = requireNotNull(nearestBoundaryPoint(GeoPoint(45.05, -85.135), listOf(areaOf(listOf(square, innerHole)))))
        assertEquals(393.0, edge.distanceMeters, 3.0)
        assertEquals(CompassDirection.EAST, edge.edgeDirection)
    }

    @Test
    fun `no areas gives no edge`() {
        assertEquals(null, nearestBoundaryPoint(GeoPoint(45.05, -85.1), emptyList()))
    }

    @Test
    fun `compass buckets split at 22 point 5 degrees`() {
        assertEquals(CompassDirection.NORTH, compassDirection(0.0))
        assertEquals(CompassDirection.NORTH, compassDirection(22.49))
        assertEquals(CompassDirection.NORTHEAST, compassDirection(22.5))
        assertEquals(CompassDirection.EAST, compassDirection(67.5))
        assertEquals(CompassDirection.SOUTH, compassDirection(180.0))
        assertEquals(CompassDirection.SOUTHWEST, compassDirection(202.5))
        assertEquals(CompassDirection.NORTH, compassDirection(337.5))
        assertEquals(CompassDirection.NORTH, compassDirection(359.9))
        assertEquals(CompassDirection.WEST, compassDirection(-90.0))
    }
```

Create `T/map/OnDemandZoomLevelTest.kt`:

```kotlin
package org.onebusaway.android.map

import org.junit.Assert.assertEquals
import org.junit.Test

class OnDemandZoomLevelTest {

    private val metersPerDegree = 111_133.0

    @Test
    fun `street below 4 km, region to 65 km, hidden above`() {
        assertEquals(OnDemandZoomLevel.STREET, onDemandZoomLevel(0.0))
        assertEquals(OnDemandZoomLevel.STREET, onDemandZoomLevel(ONDEMAND_STREET_LEVEL_MAX_HEIGHT_METERS / metersPerDegree))
        assertEquals(OnDemandZoomLevel.REGION, onDemandZoomLevel(ONDEMAND_STREET_LEVEL_MAX_HEIGHT_METERS * 1.01 / metersPerDegree))
        assertEquals(OnDemandZoomLevel.REGION, onDemandZoomLevel(ONDEMAND_MAX_VISIBLE_HEIGHT_METERS / metersPerDegree))
        assertEquals(OnDemandZoomLevel.HIDDEN, onDemandZoomLevel(ONDEMAND_MAX_VISIBLE_HEIGHT_METERS * 1.01 / metersPerDegree))
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :onebusaway-android:testObaMaplibreDebugUnitTest --tests 'org.onebusaway.android.map.render.ZoneGeometryTest' --tests 'org.onebusaway.android.map.OnDemandZoomLevelTest'`
Expected: compilation FAILS (`Unresolved reference: labelPoint`, `OnDemandZoomLevel`).

- [ ] **Step 3: Implement the geometry**

Append to `M/map/render/ZoneGeometry.kt` (add imports `kotlin.math.abs`, `kotlin.math.atan2`, `kotlin.math.cos`, `kotlin.math.floor`, `kotlin.math.sqrt`, `org.onebusaway.android.models.ServiceArea`):

```kotlin
/** The sphere radius of the spec §2.7 local projection. */
const val EARTH_RADIUS_METERS = 6_371_000.0

/** Inside this far from an edge the bar says which way the edge is (spec §2.7 "near edge"). */
const val ONDEMAND_NEAR_EDGE_METERS = 100.0

/**
 * Spec §2.7's local equirectangular projection about [origin]: `x` east and `y` north in metres.
 * Good to a few metres over the tens of kilometres a zone spans, and invertible, which is all the
 * nearest-edge search and the thumbnail need.
 */
class LocalProjection(val origin: GeoPoint) {
    private val cosLat = cos(Math.toRadians(origin.latitude))

    fun x(point: GeoPoint): Double = Math.toRadians(point.longitude - origin.longitude) * cosLat * EARTH_RADIUS_METERS

    fun y(point: GeoPoint): Double = Math.toRadians(point.latitude - origin.latitude) * EARTH_RADIUS_METERS

    fun toGeo(x: Double, y: Double): GeoPoint = GeoPoint(
        latitude = origin.latitude + Math.toDegrees(y / EARTH_RADIUS_METERS),
        longitude = origin.longitude + Math.toDegrees(x / (EARTH_RADIUS_METERS * cosLat))
    )
}

/** The eight compass points, in bearing order from north. */
enum class CompassDirection { NORTH, NORTHEAST, EAST, SOUTHEAST, SOUTH, SOUTHWEST, WEST, NORTHWEST }

/** `floor((bearing + 22.5) / 45) mod 8` over a bearing normalised to `[0, 360)`. */
fun compassDirection(bearingDegrees: Double): CompassDirection {
    val normalised = ((bearingDegrees % 360.0) + 360.0) % 360.0
    val bucket = floor((normalised + 22.5) / 45.0).toInt() % CompassDirection.entries.size
    return CompassDirection.entries[bucket]
}

/** The bearing from [from] to [to] in degrees clockwise from north, `[0, 360)`, in the local projection. */
fun bearingDegrees(from: GeoPoint, to: GeoPoint): Double {
    val projection = LocalProjection(from)
    val degrees = Math.toDegrees(atan2(projection.x(to), projection.y(to)))
    return ((degrees % 360.0) + 360.0) % 360.0
}

/**
 * The nearest point of a service's boundary to the probe point. [edgeDirection] is the way to walk
 * to reach the edge (the inside near-edge title); [riderDirection] is where the rider stands relative
 * to the zone (the outside title). They are not interchangeable.
 */
data class ZoneEdge(val distanceMeters: Double, val point: GeoPoint, val bearingDegrees: Double) {
    val edgeDirection: CompassDirection get() = compassDirection(bearingDegrees)
    val riderDirection: CompassDirection get() = compassDirection(bearingDegrees + 180.0)
}

/**
 * The closest boundary point over every segment of every ring (exterior and holes) of every polygon of
 * [areas], by point-to-segment distance in the local projection about [from]. Null with no rings.
 */
fun nearestBoundaryPoint(from: GeoPoint, areas: List<ServiceArea>): ZoneEdge? {
    val projection = LocalProjection(from)
    var best: ZoneEdge? = null
    for (area in areas) for (polygon in area.polygons) for (ring in polygon) {
        if (ring.size < 2) continue
        for (i in ring.indices) {
            val a = ring[i]
            val b = ring[(i + 1) % ring.size]
            val ax = projection.x(a)
            val ay = projection.y(a)
            val bx = projection.x(b)
            val by = projection.y(b)
            val dx = bx - ax
            val dy = by - ay
            val lengthSquared = dx * dx + dy * dy
            // The projection parameter of the origin (0,0) onto the segment, clamped to the segment.
            val t = if (lengthSquared == 0.0) 0.0 else ((-ax) * dx + (-ay) * dy) / lengthSquared
            val clamped = t.coerceIn(0.0, 1.0)
            val px = ax + clamped * dx
            val py = ay + clamped * dy
            val distance = sqrt(px * px + py * py)
            if (best == null || distance < best.distanceMeters) {
                val bearing = ((Math.toDegrees(atan2(px, py)) % 360.0) + 360.0) % 360.0
                best = ZoneEdge(distance, projection.toGeo(px, py), bearing)
            }
        }
    }
    return best
}

/**
 * Spec §2.3's label point: the midpoint of the widest run inside the polygon along the bbox
 * mid-latitude; else the exterior centroid when it lies inside the ring; else the bbox centre. The
 * one place client point-in-ring is allowed for something a rider sees.
 */
fun labelPoint(rings: List<List<GeoPoint>>, bbox: Pair<GeoPoint, GeoPoint>): GeoPoint {
    val midLatitude = (bbox.first.latitude + bbox.second.latitude) / 2
    val bboxCentre = GeoPoint(midLatitude, (bbox.first.longitude + bbox.second.longitude) / 2)
    val crossings = rings.flatMap { crossingsAt(it, midLatitude) }.sorted()
    if (crossings.size >= 2) {
        var widest = -1.0
        var midpoint = bboxCentre.longitude
        var i = 0
        while (i + 1 < crossings.size) {
            val width = crossings[i + 1] - crossings[i]
            if (width > widest) {
                widest = width
                midpoint = (crossings[i] + crossings[i + 1]) / 2
            }
            i += 2
        }
        return GeoPoint(midLatitude, midpoint)
    }
    val exterior = rings.firstOrNull() ?: return bboxCentre
    val centroid = ringCentroid(exterior) ?: return bboxCentre
    return if (pointInRing(centroid, exterior)) centroid else bboxCentre
}

/** Longitudes where [ring]'s segments cross [latitude], with the same half-open rule as [pointInRing]. */
private fun crossingsAt(ring: List<GeoPoint>, latitude: Double): List<Double> {
    if (ring.size < 3) return emptyList()
    val crossings = mutableListOf<Double>()
    var j = ring.size - 1
    for (i in ring.indices) {
        val a = ring[i]
        val b = ring[j]
        if ((a.latitude > latitude) != (b.latitude > latitude)) {
            crossings += (b.longitude - a.longitude) * (latitude - a.latitude) / (b.latitude - a.latitude) + a.longitude
        }
        j = i
    }
    return crossings
}

/** The planar (lat/lon) shoelace centroid of [ring]; null for fewer than three points or zero area. */
private fun ringCentroid(ring: List<GeoPoint>): GeoPoint? {
    if (ring.size < 3) return null
    var twiceArea = 0.0
    var cx = 0.0
    var cy = 0.0
    for (i in ring.indices) {
        val a = ring[i]
        val b = ring[(i + 1) % ring.size]
        val cross = a.longitude * b.latitude - b.longitude * a.latitude
        twiceArea += cross
        cx += (a.longitude + b.longitude) * cross
        cy += (a.latitude + b.latitude) * cross
    }
    if (abs(twiceArea) < 1e-12) return null
    return GeoPoint(latitude = cy / (3 * twiceArea), longitude = cx / (3 * twiceArea))
}

/** The bounding box of [ring], or null when it is empty. */
fun ringBounds(ring: List<GeoPoint>): Pair<GeoPoint, GeoPoint>? {
    if (ring.isEmpty()) return null
    return GeoPoint(ring.minOf { it.latitude }, ring.minOf { it.longitude }) to GeoPoint(ring.maxOf { it.latitude }, ring.maxOf { it.longitude })
}

/** The polygon (its rings) with the largest exterior area in metres², across every area. */
fun largestPolygon(areas: List<ServiceArea>): List<List<GeoPoint>>? = areas
    .flatMap { it.polygons }
    .filter { it.firstOrNull()?.size ?: 0 >= 3 }
    .maxByOrNull { polygon -> projectedArea(polygon.first()) }

/** Where a service's one pin goes (spec §3.1): the label point of its largest polygon. */
fun pinPointFor(areas: List<ServiceArea>): GeoPoint? {
    val polygon = largestPolygon(areas) ?: return null
    val bounds = ringBounds(polygon.first()) ?: return null
    return labelPoint(polygon, bounds)
}

/** Shoelace area of [ring] in the local projection about its first vertex, metres². */
private fun projectedArea(ring: List<GeoPoint>): Double {
    val projection = LocalProjection(ring.first())
    var twiceArea = 0.0
    for (i in ring.indices) {
        val a = ring[i]
        val b = ring[(i + 1) % ring.size]
        twiceArea += projection.x(a) * projection.y(b) - projection.x(b) * projection.y(a)
    }
    return abs(twiceArea) / 2
}
```

Create `M/map/OnDemandZoomLevel.kt`:

```kotlin
package org.onebusaway.android.map

import org.onebusaway.android.map.rental.visibleHeightMeters

/** Spec §2.2: decided from the visible map height alone, so the layer, the dock and tests read one answer. */
enum class OnDemandZoomLevel { HIDDEN, REGION, STREET }

/** At or below this much visible latitude the map is at street level: strokes only, and the docked bar. */
const val ONDEMAND_STREET_LEVEL_MAX_HEIGHT_METERS = 4_000.0

fun onDemandZoomLevel(latSpan: Double): OnDemandZoomLevel {
    val height = visibleHeightMeters(latSpan)
    return when {
        height > ONDEMAND_MAX_VISIBLE_HEIGHT_METERS -> OnDemandZoomLevel.HIDDEN
        height > ONDEMAND_STREET_LEVEL_MAX_HEIGHT_METERS -> OnDemandZoomLevel.REGION
        else -> OnDemandZoomLevel.STREET
    }
}
```

- [ ] **Step 4: Run the tests**

Run: `./gradlew :onebusaway-android:testObaMaplibreDebugUnitTest --tests 'org.onebusaway.android.map.render.ZoneGeometryTest' --tests 'org.onebusaway.android.map.OnDemandZoomLevelTest'`
Expected: PASS.

- [ ] **Step 5: Compile both flavours and commit**

Run: `./gradlew :onebusaway-android:compileObaGoogleDebugKotlin :onebusaway-android:compileObaMaplibreDebugKotlin -PwarningsAsErrors=true`
Expected: BUILD SUCCESSFUL.

```bash
./gradlew spotlessApply
git add onebusaway-android/src/main/java/org/onebusaway/android/map onebusaway-android/src/test/java/org/onebusaway/android/map
git commit -m "Add zone label points, nearest edges and zoom levels" -m "A pin must sit inside its own polygon, a near-edge title must say which
way the edge is, and the layer and dock must agree on street versus
region. All three are pure geometry, so they live beside the existing
point-in-ring hit test and are tested without a map."
```

---

### Task 5: Copy composition and distance formatting (resource-free)

**Files:**
- Create: `M/ondemand/OnDemandCopy.kt`
- Modify: `R/values/strings.xml` (after the existing on-demand block, line ~1500)
- Test: `T/ondemand/OnDemandCopyTest.kt`

**Interfaces:**
- Consumes: `OnDemandStatus`, `OnDemandTag`, `OnDemandAvailability` (Task 2); `OnDemandMatch`, `ProbeSource` (Task 3); `ZoneEdge`, `CompassDirection`, `bearingDegrees`, `compassDirection`, `ONDEMAND_NEAR_EDGE_METERS` (Task 4); `R.string.*`.
- Produces: `data class TextSpec(@StringRes val res: Int, val args: List<Any> = emptyList())`; `data class PluralSpec(@PluralsRes val res: Int, val count: Int, val args: List<Any>)`; `enum class DistanceUnit(val abbreviationRes: Int) { FEET, MILES, METERS, KILOMETERS }`; `data class DistanceText(val value: String, val unit: DistanceUnit)`; `fun formatDistance(meters: Double, metric: Boolean, locale: Locale): DistanceText`; `fun formatClockTime(instant, zone, locale): String`; `fun relativeDayTime(instant, zone, now, locale): TextSpec`; `enum class OpenStyle { OPEN_UNTIL, OPEN_NOW_UNTIL }`; `fun statusText(status, zone: ZoneId?, now, locale, openStyle = OPEN_UNTIL): TextSpec?`; `fun tagText(tag): TextSpec`; `fun bookingTag(tags: Set<OnDemandTag>): OnDemandTag?`; `fun cardMeta(availability, now, locale): List<TextSpec>`; `fun barTitle(match, probe: GeoPoint, edge: ZoneEdge?, now, locale, metric): TextSpec?`; `fun badgeText(index: Int, count: Int): TextSpec`; `fun addressLine(serviceName: String, isInside: Boolean, othersInside: Int): Any`; `fun pickerSubtitle(source: ProbeSource, locality: String?): TextSpec`; `fun detailLocationText(source: ProbeSource, isInside: Boolean): TextSpec`; `fun zoneCount(count: Int): PluralSpec`. Args may nest `TextSpec`, `PluralSpec`, `DistanceText`, `String`, `Int`; Task 11 adds the Compose resolver.

- [ ] **Step 1: Add the strings this task references**

In `R/values/strings.xml`, directly after `<string name="ondemand_card_phone">…</string>`:

```xml
    <!-- On-demand: shared status, tag and location copy (DRT UI spec §2.8) -->
    <string name="ondemand_card_eyebrow">On-demand service here</string>
    <!-- %1$s is a clock time in the agency's timezone -->
    <string name="ondemand_status_open_until">Open · until %1$s</string>
    <string name="ondemand_status_open_now_until">Open now · until %1$s</string>
    <string name="ondemand_status_open">Open</string>
    <!-- %1$s is a relative day and time, e.g. "tomorrow at 7:20 AM" -->
    <string name="ondemand_status_opens">Opens %1$s</string>
    <!-- %1$s is a relative day and time, e.g. "tomorrow at 3:10 PM" -->
    <string name="ondemand_status_book_by">Book by %1$s</string>
    <string name="ondemand_status_closed">Closed</string>
    <!-- %1$s is a clock time -->
    <string name="ondemand_relative_today_at">today at %1$s</string>
    <string name="ondemand_relative_tomorrow_at">tomorrow at %1$s</string>
    <!-- %1$s is a weekday name or a date, %2$s a clock time -->
    <string name="ondemand_relative_day_at">%1$s at %2$s</string>
    <string name="ondemand_tag_same_day">Same-day booking</string>
    <string name="ondemand_tag_advance">Advance booking</string>
    <string name="ondemand_tag_no_notice">No notice needed</string>
    <string name="ondemand_tag_eligibility">Eligibility required</string>
    <string name="ondemand_bar_inside">Pickups available here</string>
    <!-- Docked bar titles when the rider is inside a zone near its edge; %1$s is a distance such as "300 ft" -->
    <string name="ondemand_bar_inside_near_edge_north">Inside · edge %1$s north</string>
    <string name="ondemand_bar_inside_near_edge_northeast">Inside · edge %1$s northeast</string>
    <string name="ondemand_bar_inside_near_edge_east">Inside · edge %1$s east</string>
    <string name="ondemand_bar_inside_near_edge_southeast">Inside · edge %1$s southeast</string>
    <string name="ondemand_bar_inside_near_edge_south">Inside · edge %1$s south</string>
    <string name="ondemand_bar_inside_near_edge_southwest">Inside · edge %1$s southwest</string>
    <string name="ondemand_bar_inside_near_edge_west">Inside · edge %1$s west</string>
    <string name="ondemand_bar_inside_near_edge_northwest">Inside · edge %1$s northwest</string>
    <!-- Docked bar titles when the rider is outside a zone; %1$s is a distance such as "0.3 mi" -->
    <string name="ondemand_bar_outside_north">%1$s north of the zone</string>
    <string name="ondemand_bar_outside_northeast">%1$s northeast of the zone</string>
    <string name="ondemand_bar_outside_east">%1$s east of the zone</string>
    <string name="ondemand_bar_outside_southeast">%1$s southeast of the zone</string>
    <string name="ondemand_bar_outside_south">%1$s south of the zone</string>
    <string name="ondemand_bar_outside_southwest">%1$s southwest of the zone</string>
    <string name="ondemand_bar_outside_west">%1$s west of the zone</string>
    <string name="ondemand_bar_outside_northwest">%1$s northwest of the zone</string>
    <!-- %1$d is the current page, %2$d the page count -->
    <string name="ondemand_bar_badge">%1$d of %2$d</string>
    <!-- Address check on a dropped pin; %1$s is a service name -->
    <string name="ondemand_address_inside">Inside %1$s</string>
    <string name="ondemand_address_outside">Outside %1$s</string>
    <!-- %1$s is a service name, %2$d how many more services also contain the point -->
    <plurals name="ondemand_address_inside_more">
        <item quantity="one">Inside %1$s and %2$d more</item>
        <item quantity="other">Inside %1$s and %2$d more</item>
    </plurals>
    <!-- Overlap picker subtitle; %1$s is a locality name -->
    <string name="ondemand_picker_subtitle_location">%1$s · Your location</string>
    <string name="ondemand_picker_subtitle_center">%1$s · Map center</string>
    <string name="ondemand_picker_subtitle_point">%1$s · Selected place</string>
    <string name="ondemand_picker_your_location">Your location</string>
    <string name="ondemand_picker_map_center">Map center</string>
    <string name="ondemand_picker_selected_place">Selected place</string>
    <!-- Zone detail location row -->
    <string name="ondemand_detail_location_inside">Your location is in this zone</string>
    <string name="ondemand_detail_location_outside">Your location is outside this zone</string>
    <string name="ondemand_detail_center_inside">Map center is in this zone</string>
    <string name="ondemand_detail_center_outside">Map center is outside this zone</string>
    <string name="ondemand_detail_point_inside">This place is in this zone</string>
    <string name="ondemand_detail_point_outside">This place is outside this zone</string>
    <!-- %d is a number of zones -->
    <plurals name="ondemand_detail_zone_count">
        <item quantity="one">%d zone</item>
        <item quantity="other">%d zones</item>
    </plurals>
```

- [ ] **Step 2: Write the failing tests**

Create `T/ondemand/OnDemandCopyTest.kt`:

```kotlin
package org.onebusaway.android.ondemand

import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.R
import org.onebusaway.android.map.render.ZoneEdge
import org.onebusaway.android.models.OnDemandMatchReason
import org.onebusaway.android.util.GeoPoint

class OnDemandCopyTest {

    private val now = instant("2026-03-10T14:00:00-04:00")
    private val probe = GeoPoint(45.05, -85.1)

    private fun inside(tier: Int = TIER_OPEN_NOW, status: OnDemandStatus? = null): OnDemandMatch {
        val match = matchFor(service(areas = listOf(area(distance = 0.0)), matchReason = OnDemandMatchReason.AREA_CONTAINS_POINT), now)
        return match.copy(availability = match.availability.copy(usabilityTier = tier, status = status ?: match.availability.status))
    }

    @Test
    fun `imperial distances round feet to ten below a tenth of a mile then miles to one decimal`() {
        assertEquals(DistanceText("980", DistanceUnit.FEET), formatDistance(300.0, metric = false, locale = Locale.US))
        assertEquals(DistanceText("30", DistanceUnit.FEET), formatDistance(10.0, metric = false, locale = Locale.US))
        assertEquals(DistanceText("0.1", DistanceUnit.MILES), formatDistance(161.0, metric = false, locale = Locale.US))
        assertEquals(DistanceText("3.1", DistanceUnit.MILES), formatDistance(5000.0, metric = false, locale = Locale.US))
    }

    @Test
    fun `metric distances round metres to ten below a kilometre then kilometres to one decimal`() {
        assertEquals(DistanceText("100", DistanceUnit.METERS), formatDistance(96.0, metric = true, locale = Locale.UK))
        assertEquals(DistanceText("300", DistanceUnit.METERS), formatDistance(300.0, metric = true, locale = Locale.UK))
        assertEquals(DistanceText("1.5", DistanceUnit.KILOMETERS), formatDistance(1500.0, metric = true, locale = Locale.UK))
        assertEquals(DistanceText("1,5", DistanceUnit.KILOMETERS), formatDistance(1500.0, metric = true, locale = Locale.GERMANY))
    }

    @Test
    fun `status copy picks the key per status and renders nothing for unknown`() {
        val zone = java.time.ZoneId.of(CHARLEVOIX_TZ)
        val until = statusText(OnDemandStatus.OpenNow(instant("2026-03-10T16:40:00-04:00")), zone, now, Locale.US)
        assertEquals(R.string.ondemand_status_open_until, until?.res)
        assertTrue((until?.args?.single() as String).contains("4:40"))
        assertEquals(R.string.ondemand_status_open_now_until, statusText(OnDemandStatus.OpenNow(now), zone, now, Locale.US, OpenStyle.OPEN_NOW_UNTIL)?.res)
        assertEquals(TextSpec(R.string.ondemand_status_open), statusText(OnDemandStatus.OpenNow(null), zone, now, Locale.US))
        val opens = statusText(OnDemandStatus.OpensAt(instant("2026-03-11T07:20:00-04:00")), zone, now, Locale.US)
        assertEquals(R.string.ondemand_status_opens, opens?.res)
        assertEquals(R.string.ondemand_relative_tomorrow_at, (opens?.args?.single() as TextSpec).res)
        val bookBy = statusText(OnDemandStatus.BookBy(instant("2026-03-10T15:10:00-04:00"), LocalDate.of(2026, 3, 11)), zone, now, Locale.US)
        assertEquals(R.string.ondemand_status_book_by, bookBy?.res)
        assertEquals(R.string.ondemand_relative_today_at, (bookBy?.args?.single() as TextSpec).res)
        assertEquals(R.string.ondemand_booking_opens, statusText(OnDemandStatus.BookingOpens(now, LocalDate.of(2026, 3, 12)), zone, now, Locale.US)?.res)
        assertEquals(TextSpec(R.string.ondemand_status_closed), statusText(OnDemandStatus.Closed, zone, now, Locale.US))
        assertNull(statusText(OnDemandStatus.Unknown, zone, now, Locale.US))
    }

    @Test
    fun `relative days name the weekday within a week and the date beyond`() {
        val zone = java.time.ZoneId.of(CHARLEVOIX_TZ)
        val saturday = relativeDayTime(instant("2026-03-14T09:00:00-04:00"), zone, now, Locale.US)
        assertEquals(R.string.ondemand_relative_day_at, saturday.res)
        assertEquals("Saturday", saturday.args[0])
        val nextMonth = relativeDayTime(instant("2026-04-14T09:00:00-04:00"), zone, now, Locale.US)
        assertTrue((nextMonth.args[0] as String).contains("Apr"))
    }

    @Test
    fun `card meta joins the status and the booking tag`() {
        val meta = cardMeta(inside().availability, now, Locale.US)
        assertEquals(listOf(R.string.ondemand_status_open_until, R.string.ondemand_tag_same_day), meta.map { it.res })
        assertEquals(listOf(R.string.ondemand_tag_same_day), cardMeta(inside(status = OnDemandStatus.Unknown).availability, now, Locale.US).map { it.res })
    }

    @Test
    fun `bar titles follow the precedence`() {
        assertEquals(TextSpec(R.string.ondemand_bar_inside), barTitle(inside(), probe, edge = null, now, Locale.US, metric = false))
        val nearNorth = ZoneEdge(30.0, GeoPoint(45.0503, -85.1), 0.0)
        val nearEdge = barTitle(inside(), probe, nearNorth, now, Locale.US, metric = false)
        assertEquals(R.string.ondemand_bar_inside_near_edge_north, nearEdge?.res)
        assertEquals(DistanceText("100", DistanceUnit.FEET), nearEdge?.args?.single())
        assertEquals(TextSpec(R.string.ondemand_bar_inside), barTitle(inside(), probe, ZoneEdge(150.0, probe, 0.0), now, Locale.US, metric = false))
        assertEquals(TextSpec(R.string.ondemand_tag_eligibility), barTitle(inside(tier = TIER_ELIGIBILITY), probe, null, now, Locale.US, metric = false))
        assertEquals(R.string.ondemand_status_opens, barTitle(inside(tier = TIER_SAME_DAY, status = OnDemandStatus.OpensAt(now)), probe, null, now, Locale.US, metric = false)?.res)
        assertEquals(R.string.ondemand_status_book_by, barTitle(inside(tier = TIER_ADVANCE, status = OnDemandStatus.BookBy(now, LocalDate.of(2026, 3, 11))), probe, null, now, Locale.US, metric = false)?.res)
        assertEquals(TextSpec(R.string.ondemand_status_closed), barTitle(inside(tier = TIER_UNKNOWN_OR_CLOSED, status = OnDemandStatus.Closed), probe, null, now, Locale.US, metric = false))
        assertNull(barTitle(inside(tier = TIER_UNKNOWN_OR_CLOSED, status = OnDemandStatus.Unknown), probe, null, now, Locale.US, metric = false))
    }

    @Test
    fun `a point due south of a zone reads south of the zone with the server distance`() {
        val outside = matchFor(
            service(areas = listOf(area(distance = 5000.0, nearest = GeoPoint(45.095, -85.1))), matchReason = OnDemandMatchReason.AREA_NEARBY),
            now
        )
        val title = barTitle(outside, probe, edge = null, now, Locale.US, metric = false)
        assertEquals(R.string.ondemand_bar_outside_south, title?.res)
        assertEquals(DistanceText("3.1", DistanceUnit.MILES), title?.args?.single())
        // Server point missing: the client edge stands in, and with neither there is no title.
        val noPoint = outside.copy(nearestPointOnBoundary = null)
        assertEquals(R.string.ondemand_bar_outside_south, barTitle(noPoint, probe, ZoneEdge(5000.0, GeoPoint(45.095, -85.1), 0.0), now, Locale.US, metric = false)?.res)
        assertNull(barTitle(noPoint, probe, null, now, Locale.US, metric = false))
    }

    @Test
    fun `badge, address line, picker subtitle and detail location copy`() {
        assertEquals(TextSpec(R.string.ondemand_bar_badge, listOf(1, 2)), badgeText(1, 2))
        assertEquals(TextSpec(R.string.ondemand_address_inside, listOf("Dial-a-Ride")), addressLine("Dial-a-Ride", isInside = true, othersInside = 0))
        assertEquals(PluralSpec(R.plurals.ondemand_address_inside_more, 2, listOf("Dial-a-Ride", 2)), addressLine("Dial-a-Ride", isInside = true, othersInside = 2))
        assertEquals(TextSpec(R.string.ondemand_address_outside, listOf("Dial-a-Ride")), addressLine("Dial-a-Ride", isInside = false, othersInside = 0))
        assertEquals(TextSpec(R.string.ondemand_picker_subtitle_location, listOf("Boyne City")), pickerSubtitle(ProbeSource.Rider, "Boyne City"))
        assertEquals(TextSpec(R.string.ondemand_picker_map_center), pickerSubtitle(ProbeSource.MapCenter, null))
        assertEquals(TextSpec(R.string.ondemand_picker_subtitle_point, listOf("Boyne City")), pickerSubtitle(ProbeSource.Point(null), "Boyne City"))
        assertEquals(TextSpec(R.string.ondemand_detail_location_inside), detailLocationText(ProbeSource.Rider, isInside = true))
        assertEquals(TextSpec(R.string.ondemand_detail_location_outside), detailLocationText(ProbeSource.Rider, isInside = false))
        assertEquals(TextSpec(R.string.ondemand_detail_center_inside), detailLocationText(ProbeSource.MapCenter, isInside = true))
        assertEquals(TextSpec(R.string.ondemand_detail_center_outside), detailLocationText(ProbeSource.MapCenter, isInside = false))
        assertEquals(TextSpec(R.string.ondemand_detail_point_inside), detailLocationText(ProbeSource.Point("x"), isInside = true))
        assertEquals(TextSpec(R.string.ondemand_detail_point_outside), detailLocationText(ProbeSource.Point("x"), isInside = false))
        assertEquals(PluralSpec(R.plurals.ondemand_detail_zone_count, 3, listOf(3)), zoneCount(3))
    }
}
```

- [ ] **Step 3: Run the tests to verify they fail**

Run: `./gradlew :onebusaway-android:testObaMaplibreDebugUnitTest --tests 'org.onebusaway.android.ondemand.OnDemandCopyTest'`
Expected: compilation FAILS (`Unresolved reference: formatDistance`).

- [ ] **Step 4: Implement the copy functions**

Create `M/ondemand/OnDemandCopy.kt`:

```kotlin
package org.onebusaway.android.ondemand

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToLong
import org.onebusaway.android.R
import org.onebusaway.android.map.render.CompassDirection
import org.onebusaway.android.map.render.ONDEMAND_NEAR_EDGE_METERS
import org.onebusaway.android.map.render.ZoneEdge
import org.onebusaway.android.map.render.bearingDegrees
import org.onebusaway.android.map.render.compassDirection
import org.onebusaway.android.util.GeoPoint

/**
 * A string resource with its arguments, composed without a `Resources` so the copy rules of spec
 * §2.8 are JVM-tested. Arguments may be a [TextSpec], a [PluralSpec], a [DistanceText], a `String`
 * or an `Int`; the Compose resolver (`resolve()` in `ui/home/ondemand/CopyResolvers.kt`) flattens them.
 */
data class TextSpec(@StringRes val res: Int, val args: List<Any> = emptyList())

data class PluralSpec(@PluralsRes val res: Int, val count: Int, val args: List<Any>)

enum class DistanceUnit(@StringRes val abbreviationRes: Int) {
    FEET(R.string.feet_abbreviation),
    MILES(R.string.miles_abbreviation),
    METERS(R.string.meters_abbreviation),
    KILOMETERS(R.string.kilometers_abbreviation)
}

/** A formatted distance: the number and the unit it is in; rendered as "value unit". */
data class DistanceText(val value: String, val unit: DistanceUnit)

private const val FEET_PER_METER = 3.28084
private const val FEET_PER_MILE = 5_280.0
private const val METERS_PER_KILOMETER = 1_000.0

/**
 * Spec §2.8 distances: imperial shows whole feet rounded to 10 below 0.1 mi and miles with one
 * decimal at or above; metric shows metres rounded to 10 below 1,000 m and kilometres with one decimal.
 */
fun formatDistance(meters: Double, metric: Boolean, locale: Locale): DistanceText {
    val whole = NumberFormat.getIntegerInstance(locale)
    val oneDecimal = NumberFormat.getInstance(locale).apply {
        minimumFractionDigits = 1
        maximumFractionDigits = 1
    }
    return if (metric) {
        if (meters < METERS_PER_KILOMETER) DistanceText(whole.format(roundToTen(meters)), DistanceUnit.METERS) else DistanceText(oneDecimal.format(meters / METERS_PER_KILOMETER), DistanceUnit.KILOMETERS)
    } else {
        val feet = meters * FEET_PER_METER
        if (feet < FEET_PER_MILE / 10) DistanceText(whole.format(roundToTen(feet)), DistanceUnit.FEET) else DistanceText(oneDecimal.format(feet / FEET_PER_MILE), DistanceUnit.MILES)
    }
}

private fun roundToTen(value: Double): Long = (value / 10.0).roundToLong() * 10

fun formatClockTime(instant: Instant, zone: ZoneId, locale: Locale): String = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).withZone(zone).format(instant)

private fun formatDateTime(instant: Instant, zone: ZoneId, locale: Locale): String = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(locale).withZone(zone).format(instant)

private fun formatDate(date: java.time.LocalDate, locale: Locale): String = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(date)

/** "today at 7:20 AM", "tomorrow at …", a weekday within the week, else a medium date. */
fun relativeDayTime(instant: Instant, zone: ZoneId, now: Instant, locale: Locale): TextSpec {
    val time = formatClockTime(instant, zone, locale)
    val today = now.atZone(zone).toLocalDate()
    val day = instant.atZone(zone).toLocalDate()
    return when {
        day == today -> TextSpec(R.string.ondemand_relative_today_at, listOf(time))
        day == today.plusDays(1) -> TextSpec(R.string.ondemand_relative_tomorrow_at, listOf(time))
        day.isBefore(today.plusDays(7)) -> TextSpec(R.string.ondemand_relative_day_at, listOf(day.dayOfWeek.getDisplayName(TextStyle.FULL, locale), time))
        else -> TextSpec(R.string.ondemand_relative_day_at, listOf(formatDate(day, locale), time))
    }
}

/** Which "open" key a surface uses: the card and picker say "Open · until", the detail page "Open now · until". */
enum class OpenStyle { OPEN_UNTIL, OPEN_NOW_UNTIL }

/** The status line per spec §2.8's composition rules; null for [OnDemandStatus.Unknown]. */
fun statusText(status: OnDemandStatus, zone: ZoneId?, now: Instant, locale: Locale, openStyle: OpenStyle = OpenStyle.OPEN_UNTIL): TextSpec? = when (status) {
    is OnDemandStatus.OpenNow -> {
        val until = status.until
        if (until == null || zone == null) {
            TextSpec(R.string.ondemand_status_open)
        } else {
            val res = if (openStyle == OpenStyle.OPEN_UNTIL) R.string.ondemand_status_open_until else R.string.ondemand_status_open_now_until
            TextSpec(res, listOf(formatClockTime(until, zone, locale)))
        }
    }
    is OnDemandStatus.OpensAt -> zone?.let { TextSpec(R.string.ondemand_status_opens, listOf(relativeDayTime(status.at, it, now, locale))) }
    is OnDemandStatus.BookingOpens -> zone?.let { TextSpec(R.string.ondemand_booking_opens, listOf(formatDateTime(status.at, it, locale), formatDate(status.travelDate, locale))) }
    is OnDemandStatus.BookBy -> zone?.let { TextSpec(R.string.ondemand_status_book_by, listOf(relativeDayTime(status.deadline, it, now, locale))) }
    OnDemandStatus.Closed -> TextSpec(R.string.ondemand_status_closed)
    OnDemandStatus.Unknown -> null
}

fun tagText(tag: OnDemandTag): TextSpec = TextSpec(
    when (tag) {
        OnDemandTag.NO_NOTICE_NEEDED -> R.string.ondemand_tag_no_notice
        OnDemandTag.SAME_DAY_BOOKING -> R.string.ondemand_tag_same_day
        OnDemandTag.ADVANCE_BOOKING -> R.string.ondemand_tag_advance
        OnDemandTag.ELIGIBILITY_REQUIRED -> R.string.ondemand_tag_eligibility
    }
)

/** The one booking tag (never the eligibility tag), or null for a service with no tier. */
fun bookingTag(tags: Set<OnDemandTag>): OnDemandTag? = tags.firstOrNull { it != OnDemandTag.ELIGIBILITY_REQUIRED }

/** The card meta line's segments — status then booking tag — with empty segments omitted; joined with " · ". */
fun cardMeta(availability: OnDemandAvailability, now: Instant, locale: Locale): List<TextSpec> = listOfNotNull(
    statusText(availability.status, availability.zone, now, locale),
    bookingTag(availability.tags)?.let(::tagText)
)

private val NEAR_EDGE_KEYS = mapOf(
    CompassDirection.NORTH to R.string.ondemand_bar_inside_near_edge_north,
    CompassDirection.NORTHEAST to R.string.ondemand_bar_inside_near_edge_northeast,
    CompassDirection.EAST to R.string.ondemand_bar_inside_near_edge_east,
    CompassDirection.SOUTHEAST to R.string.ondemand_bar_inside_near_edge_southeast,
    CompassDirection.SOUTH to R.string.ondemand_bar_inside_near_edge_south,
    CompassDirection.SOUTHWEST to R.string.ondemand_bar_inside_near_edge_southwest,
    CompassDirection.WEST to R.string.ondemand_bar_inside_near_edge_west,
    CompassDirection.NORTHWEST to R.string.ondemand_bar_inside_near_edge_northwest
)

private val OUTSIDE_KEYS = mapOf(
    CompassDirection.NORTH to R.string.ondemand_bar_outside_north,
    CompassDirection.NORTHEAST to R.string.ondemand_bar_outside_northeast,
    CompassDirection.EAST to R.string.ondemand_bar_outside_east,
    CompassDirection.SOUTHEAST to R.string.ondemand_bar_outside_southeast,
    CompassDirection.SOUTH to R.string.ondemand_bar_outside_south,
    CompassDirection.SOUTHWEST to R.string.ondemand_bar_outside_southwest,
    CompassDirection.WEST to R.string.ondemand_bar_outside_west,
    CompassDirection.NORTHWEST to R.string.ondemand_bar_outside_northwest
)

/**
 * The docked bar's title by spec §2.8 precedence: outside → "… of the zone" (server distance and
 * point, client [edge] as fallback); inside and near the edge → "Inside · edge …"; tier 4 → the
 * eligibility tag; tiers 3 and 2 → the status; tier 5 closed → "Closed"; tier 1 → "Pickups available
 * here". Null when there is nothing to say (an unknown status, or outside with no direction), and
 * the bar then shows the service name in the title position.
 */
fun barTitle(match: OnDemandMatch, probe: GeoPoint, edge: ZoneEdge?, now: Instant, locale: Locale, metric: Boolean): TextSpec? {
    val availability = match.availability
    if (!match.isInside) {
        val distance = match.distanceToAreaMeters ?: edge?.distanceMeters ?: return null
        val riderDirection = match.nearestPointOnBoundary?.let { compassDirection(bearingDegrees(probe, it) + 180.0) }
            ?: edge?.riderDirection
            ?: return null
        return TextSpec(OUTSIDE_KEYS.getValue(riderDirection), listOf(formatDistance(distance, metric, locale)))
    }
    if (edge != null && edge.distanceMeters < ONDEMAND_NEAR_EDGE_METERS) {
        return TextSpec(NEAR_EDGE_KEYS.getValue(edge.edgeDirection), listOf(formatDistance(edge.distanceMeters, metric, locale)))
    }
    return when (availability.usabilityTier) {
        TIER_ELIGIBILITY -> tagText(OnDemandTag.ELIGIBILITY_REQUIRED)
        TIER_ADVANCE, TIER_SAME_DAY -> statusText(availability.status, availability.zone, now, locale)
        TIER_OPEN_NOW -> TextSpec(R.string.ondemand_bar_inside)
        else -> if (availability.status == OnDemandStatus.Closed) TextSpec(R.string.ondemand_status_closed) else null
    }
}

/** "%1$d of %2$d": the thumbnail badge and the paged bar's accessibility value. */
fun badgeText(index: Int, count: Int): TextSpec = TextSpec(R.string.ondemand_bar_badge, listOf(index, count))

/** The dropped pin's line (spec §3.7): a [TextSpec], or a [PluralSpec] when [othersInside] > 0. */
fun addressLine(serviceName: String, isInside: Boolean, othersInside: Int): Any = when {
    !isInside -> TextSpec(R.string.ondemand_address_outside, listOf(serviceName))
    othersInside > 0 -> PluralSpec(R.plurals.ondemand_address_inside_more, othersInside, listOf(serviceName, othersInside))
    else -> TextSpec(R.string.ondemand_address_inside, listOf(serviceName))
}

/** The picker subtitle by probe source, with the locality when known (spec §3.5). */
fun pickerSubtitle(source: ProbeSource, locality: String?): TextSpec = when (source) {
    ProbeSource.Rider -> if (locality == null) TextSpec(R.string.ondemand_picker_your_location) else TextSpec(R.string.ondemand_picker_subtitle_location, listOf(locality))
    ProbeSource.MapCenter -> if (locality == null) TextSpec(R.string.ondemand_picker_map_center) else TextSpec(R.string.ondemand_picker_subtitle_center, listOf(locality))
    is ProbeSource.Point -> if (locality == null) TextSpec(R.string.ondemand_picker_selected_place) else TextSpec(R.string.ondemand_picker_subtitle_point, listOf(locality))
}

/** The detail page's location row title for the six source × inside cases (spec §3.6 item 3). */
fun detailLocationText(source: ProbeSource, isInside: Boolean): TextSpec = TextSpec(
    when (source) {
        ProbeSource.Rider -> if (isInside) R.string.ondemand_detail_location_inside else R.string.ondemand_detail_location_outside
        ProbeSource.MapCenter -> if (isInside) R.string.ondemand_detail_center_inside else R.string.ondemand_detail_center_outside
        is ProbeSource.Point -> if (isInside) R.string.ondemand_detail_point_inside else R.string.ondemand_detail_point_outside
    }
)

fun zoneCount(count: Int): PluralSpec = PluralSpec(R.plurals.ondemand_detail_zone_count, count, listOf(count))
```

- [ ] **Step 5: Run the tests**

Run: `./gradlew :onebusaway-android:testObaMaplibreDebugUnitTest --tests 'org.onebusaway.android.ondemand.OnDemandCopyTest'`
Expected: PASS. (`5000 m` outside at `(45.095, −85.1)` from probe `(45.05, −85.1)`: bearing 0° north, reversed 180° south.)

- [ ] **Step 6: Compile both flavours and commit**

Run: `./gradlew :onebusaway-android:compileObaGoogleDebugKotlin :onebusaway-android:compileObaMaplibreDebugKotlin -PwarningsAsErrors=true`
Expected: BUILD SUCCESSFUL.

```bash
./gradlew spotlessApply
git add onebusaway-android/src/main/java/org/onebusaway/android/ondemand/OnDemandCopy.kt onebusaway-android/src/main/res/values/strings.xml onebusaway-android/src/test/java/org/onebusaway/android/ondemand/OnDemandCopyTest.kt
git commit -m "Compose on-demand copy as resource-free text specs" -m "The bar title precedence, card meta, distances and relative days are
rules, not layout, so they are written once as (resource, arguments)
pairs and tested on the JVM; the composables only resolve them."
```

---

### Task 6: Geometry cache and the probe controller

**Files:**
- Create: `M/ondemand/OnDemandGeometryCache.kt`
- Create: `M/map/OnDemandDockDecisions.kt`
- Create: `M/map/OnDemandProbeController.kt`
- Test: `T/map/OnDemandDockDecisionsTest.kt`, `T/map/OnDemandProbeControllerTest.kt`

**Interfaces:**
- Consumes: `OnDemandDataSource.servicesNear/service`, `GEOMETRY_DETAIL_FULL`, `ONDEMAND_PROBE_RADIUS_METERS` (Task 1); `matchFor`, `sortedSoonestUsable`, `OnDemandMatch.isInside/isNearby`, `ProbePoint`, `ProbeSource` (Task 3); `nearestBoundaryPoint`, `ZoneEdge` (Task 4); `onDemandZoomLevel`, `OnDemandZoomLevel` (Task 4); `OnDemandSupport`; `haversineMeters` (`M/map/render/GeoMath.kt`); `TimeProvider`; `CameraSnapshot`.
- Produces: `@Singleton class OnDemandGeometryCache @Inject constructor(dataSource) { fun peek(deployment, serviceId): List<ServiceArea>?; suspend fun areas(deployment, serviceId): List<ServiceArea>?; fun clear() }`; `data class RiderFix(point: GeoPoint, accuracyMeters: Float?)`; `sealed interface OnDemandDockState { Hidden; Card(matches, probe); Bar(matches, probe) }`; `data class OnDemandProbeResult(deployment: String, probe: ProbePoint, matches: List<OnDemandMatch>)`; `internal fun dockStateFor(result, level, visible): OnDemandDockState`; `const val ONDEMAND_PROBE_MOVE_METERS = 100.0`; `class OnDemandProbeController(settledCamera: Flow<CameraSnapshot>, riderFixes: Flow<RiderFix?>, layerEnabled: Flow<Boolean>, deployment: Flow<String?>, dataSource, support, geometryCache, timeProvider, scope) { val result: StateFlow<OnDemandProbeResult?>; val dockState: StateFlow<OnDemandDockState>; val zoomLevel: StateFlow<OnDemandZoomLevel>; val edges: StateFlow<Map<String, ZoneEdge>>; val geometry: StateFlow<Map<String, List<ServiceArea>>>; fun start(); fun stop(); fun setSuppressed(Boolean); fun onForeground(); suspend fun probeExact(point): List<OnDemandMatch>? }`.

- [ ] **Step 1: Write the geometry cache**

Create `M/ondemand/OnDemandGeometryCache.kt`:

```kotlin
package org.onebusaway.android.ondemand

import javax.inject.Inject
import javax.inject.Singleton
import org.onebusaway.android.api.data.GEOMETRY_DETAIL_FULL
import org.onebusaway.android.api.data.OnDemandDataSource
import org.onebusaway.android.api.data.OnDemandResult
import org.onebusaway.android.models.ServiceArea

/**
 * Full-geometry rings per `(deployment, serviceId)` for the process lifetime (spec §2.7): fetched
 * lazily for matched services, never simplified, shared by the nearest-edge titles, the pan-to-edge
 * action and the dock thumbnail. A failed fetch is not cached, so the next need retries it.
 */
@Singleton
class OnDemandGeometryCache @Inject constructor(private val dataSource: OnDemandDataSource) {

    private val areas = HashMap<Pair<String, String>, List<ServiceArea>>()

    /** The cached rings, without fetching. */
    fun peek(deployment: String, serviceId: String): List<ServiceArea>? = synchronized(areas) { areas[deployment to serviceId] }

    /** The rings, fetching once; null when the fetch fails (or the deployment turns out unsupported). */
    suspend fun areas(deployment: String, serviceId: String): List<ServiceArea>? {
        peek(deployment, serviceId)?.let { return it }
        val fetched = when (val result = dataSource.service(serviceId, GEOMETRY_DETAIL_FULL)) {
            is OnDemandResult.Loaded -> result.value.areas
            is OnDemandResult.Failed, OnDemandResult.Unsupported -> return null
        }
        synchronized(areas) { areas[deployment to serviceId] = fetched }
        return fetched
    }

    fun clear() = synchronized(areas) { areas.clear() }
}
```

- [ ] **Step 2: Write the failing dock-decision tests**

Create `T/map/OnDemandDockDecisionsTest.kt`:

```kotlin
package org.onebusaway.android.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.models.OnDemandMatchReason
import org.onebusaway.android.ondemand.OnDemandMatch
import org.onebusaway.android.ondemand.ProbePoint
import org.onebusaway.android.ondemand.ProbeSource
import org.onebusaway.android.ondemand.TIER_ADVANCE
import org.onebusaway.android.ondemand.TIER_OPEN_NOW
import org.onebusaway.android.ondemand.area
import org.onebusaway.android.ondemand.instant
import org.onebusaway.android.ondemand.matchFor
import org.onebusaway.android.ondemand.service
import org.onebusaway.android.util.GeoPoint

class OnDemandDockDecisionsTest {

    private val now = instant("2026-03-10T14:00:00-04:00")
    private val probe = ProbePoint(GeoPoint(45.05, -85.1), ProbeSource.MapCenter)

    private fun match(id: String, reason: OnDemandMatchReason, distance: Double?, tier: Int = TIER_OPEN_NOW): OnDemandMatch {
        val areas = if (distance == null) emptyList() else listOf(area(distance = distance, nearest = GeoPoint(45.0, -85.1)))
        val match = matchFor(service(id = id, areas = areas, matchReason = reason), now)
        return match.copy(availability = match.availability.copy(usabilityTier = tier))
    }

    private val inside = match("in", OnDemandMatchReason.AREA_CONTAINS_POINT, 0.0, TIER_ADVANCE)
    private val insideOpen = match("in2", OnDemandMatchReason.AREA_CONTAINS_POINT, 0.0)
    private val near = match("near", OnDemandMatchReason.AREA_NEARBY, 800.0)
    private val far = match("far", OnDemandMatchReason.AREA_NEARBY, 6_000.0)
    private val stopGroup = match("stops", OnDemandMatchReason.STOP_WITHIN_RADIUS, null)

    private fun result(vararg matches: OnDemandMatch) = OnDemandProbeResult("https://maglev.example.org/", probe, matches.toList())

    @Test
    fun `region level shows the card only when something is inside, sorted`() {
        val state = dockStateFor(result(inside, insideOpen, near), OnDemandZoomLevel.REGION, visible = true)
        assertEquals(OnDemandDockState.Card(listOf(insideOpen, inside), probe), state)
        assertEquals(OnDemandDockState.Hidden, dockStateFor(result(near), OnDemandZoomLevel.REGION, visible = true))
    }

    @Test
    fun `street level stacks inside matches only when any is inside, else the nearby ones`() {
        assertEquals(OnDemandDockState.Bar(listOf(insideOpen, inside), probe), dockStateFor(result(near, inside, insideOpen), OnDemandZoomLevel.STREET, visible = true))
        assertEquals(OnDemandDockState.Bar(listOf(near), probe), dockStateFor(result(near, far), OnDemandZoomLevel.STREET, visible = true))
        assertEquals(OnDemandDockState.Hidden, dockStateFor(result(far), OnDemandZoomLevel.STREET, visible = true))
    }

    @Test
    fun `a match with no area never enters the dock`() {
        assertEquals(OnDemandDockState.Hidden, dockStateFor(result(stopGroup), OnDemandZoomLevel.STREET, visible = true))
        assertEquals(OnDemandDockState.Hidden, dockStateFor(result(stopGroup), OnDemandZoomLevel.REGION, visible = true))
        val withInside = dockStateFor(result(stopGroup, insideOpen), OnDemandZoomLevel.STREET, visible = true) as OnDemandDockState.Bar
        assertTrue(withInside.matches.none { it.service.id == "stops" })
    }

    @Test
    fun `hidden level, no result and suppression all hide`() {
        assertEquals(OnDemandDockState.Hidden, dockStateFor(result(insideOpen), OnDemandZoomLevel.HIDDEN, visible = true))
        assertEquals(OnDemandDockState.Hidden, dockStateFor(null, OnDemandZoomLevel.STREET, visible = true))
        assertEquals(OnDemandDockState.Hidden, dockStateFor(result(insideOpen), OnDemandZoomLevel.STREET, visible = false))
    }
}
```

- [ ] **Step 3: Write the failing controller tests**

Create `T/map/OnDemandProbeControllerTest.kt`:

```kotlin
package org.onebusaway.android.map

import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.onebusaway.android.api.data.OnDemandDataSource
import org.onebusaway.android.api.data.OnDemandResult
import org.onebusaway.android.api.data.OnDemandSupport
import org.onebusaway.android.map.render.CameraSnapshot
import org.onebusaway.android.models.OnDemandMatchReason
import org.onebusaway.android.models.OnDemandService
import org.onebusaway.android.ondemand.OnDemandGeometryCache
import org.onebusaway.android.ondemand.OnDemandStatus
import org.onebusaway.android.ondemand.ProbeSource
import org.onebusaway.android.ondemand.area
import org.onebusaway.android.ondemand.instant
import org.onebusaway.android.ondemand.service
import org.onebusaway.android.testing.MainDispatcherRule
import org.onebusaway.android.util.GeoPoint
import org.onebusaway.android.util.TimeProvider

@OptIn(ExperimentalCoroutinesApi::class)
class OnDemandProbeControllerTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(UnconfinedTestDispatcher())

    private data class NearRequest(val point: GeoPoint, val radius: Int, val detail: String)

    private class FakeDataSource(var near: OnDemandResult<List<OnDemandService>>) : OnDemandDataSource {
        val nearRequests = mutableListOf<NearRequest>()
        val geometryRequests = mutableListOf<String>()
        var geometry: OnDemandResult<OnDemandService> = OnDemandResult.Loaded(service(areas = listOf(area())))
        /** When set, the geometry fetch waits on it — a slow server. */
        var geometryGate: CompletableDeferred<Unit>? = null
        override suspend fun servicesForViewport(viewport: CameraSnapshot): OnDemandResult<List<OnDemandService>> = OnDemandResult.Loaded(emptyList())
        override suspend fun servicesNear(point: GeoPoint, radiusMeters: Int, geometryDetail: String): OnDemandResult<List<OnDemandService>> {
            nearRequests += NearRequest(point, radiusMeters, geometryDetail)
            return near
        }
        override suspend fun service(id: String, geometryDetail: String): OnDemandResult<OnDemandService> {
            geometryRequests += id
            geometryGate?.await()
            return geometry
        }
        override suspend fun servicesForAgency(agencyId: String): OnDemandResult<List<OnDemandService>> = OnDemandResult.Loaded(emptyList())
    }

    private val endpoint = "https://maglev.example.org/"
    private val camera = MutableSharedFlow<CameraSnapshot>(replay = 1)
    private val fixes = MutableStateFlow<RiderFix?>(null)
    private val enabled = MutableStateFlow(true)
    private val deployment = MutableStateFlow<String?>(endpoint)
    private val support = OnDemandSupport()
    private var nowMs = instant("2026-03-10T14:00:00-04:00").toEpochMilli()
    private val clock = TimeProvider { nowMs }

    private val centre = GeoPoint(45.05, -85.1)

    // 0.02° of latitude is ~2.2 km: street level. 0.1° is ~11 km: region level.
    private fun street(center: GeoPoint = centre) = CameraSnapshot(center, 15.0, 0.02, 0.03, GeoPoint(center.latitude - 0.01, center.longitude - 0.015), GeoPoint(center.latitude + 0.01, center.longitude + 0.015))
    private fun region(center: GeoPoint = centre) = street(center).copy(zoom = 11.0, latSpan = 0.1, lonSpan = 0.15)

    /** [centre] shifted [meters] north. */
    private fun north(meters: Double, from: GeoPoint = centre) = GeoPoint(from.latitude + meters / 111_194.9, from.longitude)

    private val insideService = service(areas = listOf(area(distance = 0.0)), matchReason = OnDemandMatchReason.AREA_CONTAINS_POINT)
    private val nearbyService = service(id = "CC_CC2", name = "Medical Trips", areas = listOf(area(distance = 900.0, nearest = north(900.0))), matchReason = OnDemandMatchReason.AREA_NEARBY)

    private fun controller(source: FakeDataSource, scope: kotlinx.coroutines.CoroutineScope, cache: OnDemandGeometryCache = OnDemandGeometryCache(source)) =
        OnDemandProbeController(camera, fixes, enabled, deployment, source, support, cache, clock, scope)

    @Test
    fun `a settled camera probes the centre with radius 5000 and no geometry`() = runTest {
        val source = FakeDataSource(OnDemandResult.Loaded(listOf(insideService)))
        val subject = controller(source, backgroundScope)
        subject.start()
        camera.emit(street())
        advanceTimeBy(1)

        assertEquals(listOf(NearRequest(centre, 5_000, "none")), source.nearRequests)
        val bar = subject.dockState.value as OnDemandDockState.Bar
        assertEquals(ProbeSource.MapCenter, bar.probe.source)
        assertEquals("CC_CC1", bar.matches.single().service.id)
        assertEquals(OnDemandZoomLevel.STREET, subject.zoomLevel.value)

        camera.emit(region())
        advanceTimeBy(1)
        assertTrue(subject.dockState.value is OnDemandDockState.Card)
        subject.stop()
    }

    @Test
    fun `moving under 100 m does not re-probe but 150 m does`() = runTest {
        val source = FakeDataSource(OnDemandResult.Loaded(listOf(insideService)))
        val subject = controller(source, backgroundScope)
        subject.start()
        camera.emit(street())
        advanceTimeBy(1)
        camera.emit(street(north(60.0)))
        advanceTimeBy(1)
        assertEquals(1, source.nearRequests.size)
        camera.emit(street(north(150.0)))
        advanceTimeBy(1)
        assertEquals(2, source.nearRequests.size)
        subject.stop()
    }

    @Test
    fun `the first rider fix takes over from the map centre and a later 150 m move re-probes`() = runTest {
        val source = FakeDataSource(OnDemandResult.Loaded(listOf(insideService)))
        val subject = controller(source, backgroundScope)
        subject.start()
        camera.emit(street())
        advanceTimeBy(1)
        fixes.value = RiderFix(north(30.0), accuracyMeters = 12f)
        advanceTimeBy(1)
        assertEquals(2, source.nearRequests.size)
        assertEquals(ProbeSource.Rider, (subject.dockState.value as OnDemandDockState.Bar).probe.source)

        fixes.value = RiderFix(north(30.0 + 200.0), accuracyMeters = 200f)
        advanceTimeBy(1)
        assertEquals("a 200 m accuracy fix is ignored", 2, source.nearRequests.size)
        fixes.value = RiderFix(north(30.0 + 150.0), accuracyMeters = 20f)
        advanceTimeBy(1)
        assertEquals(3, source.nearRequests.size)
        subject.stop()
    }

    @Test
    fun `losing the rider fix re-probes at the map centre`() = runTest {
        val source = FakeDataSource(OnDemandResult.Loaded(listOf(insideService)))
        val subject = controller(source, backgroundScope)
        subject.start()
        camera.emit(street())
        fixes.value = RiderFix(north(40.0), accuracyMeters = 10f)
        advanceTimeBy(1)
        assertEquals(ProbeSource.Rider, (subject.dockState.value as OnDemandDockState.Bar).probe.source)

        fixes.value = null
        advanceTimeBy(1)
        assertEquals(ProbeSource.MapCenter, (subject.dockState.value as OnDemandDockState.Bar).probe.source)
        assertEquals(centre, source.nearRequests.last().point)
        subject.stop()
    }

    @Test
    fun `a return to a probed point within ten minutes is answered from the cache`() = runTest {
        val source = FakeDataSource(OnDemandResult.Loaded(listOf(insideService)))
        val subject = controller(source, backgroundScope)
        subject.start()
        camera.emit(street())
        advanceTimeBy(1)
        camera.emit(street(north(150.0)))
        advanceTimeBy(1)
        camera.emit(street())
        advanceTimeBy(1)
        assertEquals(2, source.nearRequests.size)

        nowMs += 11 * 60_000
        camera.emit(street(north(150.0)))
        advanceTimeBy(1)
        assertEquals("expired after ten minutes", 3, source.nearRequests.size)
        subject.stop()
    }

    @Test
    fun `at nextChangeInstant the dock recomputes from the cached response without a request`() = runTest {
        nowMs = instant("2026-03-10T16:39:30-04:00").toEpochMilli()
        val source = FakeDataSource(OnDemandResult.Loaded(listOf(insideService)))
        val subject = controller(source, backgroundScope)
        subject.start()
        camera.emit(street())
        advanceTimeBy(1)
        assertTrue((subject.dockState.value as OnDemandDockState.Bar).matches.single().availability.status is OnDemandStatus.OpenNow)

        nowMs = instant("2026-03-10T16:40:01-04:00").toEpochMilli()
        advanceTimeBy(32_000)
        assertTrue((subject.dockState.value as OnDemandDockState.Bar).matches.single().availability.status is OnDemandStatus.OpensAt)
        assertEquals(1, source.nearRequests.size)
        subject.stop()
    }

    @Test
    fun `probeExact never reads the rider cache`() = runTest {
        val source = FakeDataSource(OnDemandResult.Loaded(listOf(insideService)))
        val subject = controller(source, backgroundScope)
        subject.start()
        camera.emit(street())
        advanceTimeBy(1)

        source.near = OnDemandResult.Loaded(listOf(nearbyService))
        val exact = requireNotNull(subject.probeExact(north(20.0)))
        assertEquals(2, source.nearRequests.size)
        assertEquals("CC_CC2", exact.single().service.id)
        // The exact cache is its own: the same exact point again costs nothing, the rider point still reads inside.
        subject.probeExact(north(20.0))
        assertEquals(2, source.nearRequests.size)
        assertEquals("CC_CC1", (subject.dockState.value as OnDemandDockState.Bar).matches.single().service.id)
        subject.stop()
    }

    @Test
    fun `a failed probe far from the last state hides the dock and a near one keeps it`() = runTest {
        val source = FakeDataSource(OnDemandResult.Loaded(listOf(insideService)))
        val subject = controller(source, backgroundScope)
        subject.start()
        camera.emit(street())
        advanceTimeBy(1)

        source.near = OnDemandResult.Failed(IOException("slow"))
        camera.emit(street(north(150.0)))
        advanceTimeBy(1)
        assertTrue("within 100 m of the state's probe point? no — 150 m", subject.dockState.value is OnDemandDockState.Hidden)

        source.near = OnDemandResult.Loaded(listOf(insideService))
        camera.emit(street(north(400.0)))
        advanceTimeBy(1)
        assertTrue(subject.dockState.value is OnDemandDockState.Bar)
        source.near = OnDemandResult.Failed(IOException("slow"))
        camera.emit(street(north(450.0)))
        advanceTimeBy(1)
        assertTrue("50 m from the state's probe point keeps it", subject.dockState.value is OnDemandDockState.Bar)
        subject.stop()
    }

    @Test
    fun `a 404 records absence and hides`() = runTest {
        val source = FakeDataSource(OnDemandResult.Unsupported)
        val subject = controller(source, backgroundScope)
        subject.start()
        camera.emit(street())
        advanceTimeBy(1)
        assertTrue(support.isKnownUnsupported(endpoint))
        assertEquals(OnDemandDockState.Hidden, subject.dockState.value)
        camera.emit(street(north(500.0)))
        advanceTimeBy(1)
        assertEquals(1, source.nearRequests.size)
        assertNull(subject.probeExact(centre))
        subject.stop()
    }

    @Test
    fun `a deployment change hides, re-probes the new server and discards a geometry answer for the old`() = runTest {
        val source = FakeDataSource(OnDemandResult.Loaded(listOf(insideService)))
        source.geometryGate = CompletableDeferred()
        val subject = controller(source, backgroundScope)
        subject.start()
        camera.emit(street())
        advanceTimeBy(1)
        assertEquals(listOf("CC_CC1"), source.geometryRequests)

        deployment.value = "https://other.example.org/"
        advanceTimeBy(1)
        assertEquals(2, source.nearRequests.size)
        // The new server is asked for its own geometry; the old fetch was cancelled with the old state.
        assertEquals(listOf("CC_CC1", "CC_CC1"), source.geometryRequests)
        assertTrue(subject.edges.value.isEmpty())
        source.geometryGate?.complete(Unit)
        advanceTimeBy(1)
        assertEquals("https://other.example.org/", requireNotNull(subject.result.value).deployment)
        assertNotNull(subject.edges.value["CC_CC1"])
        subject.stop()
    }

    @Test
    fun `near-edge geometry is fetched for matched services and edges follow the probe point`() = runTest {
        val source = FakeDataSource(OnDemandResult.Loaded(listOf(insideService)))
        val subject = controller(source, backgroundScope)
        subject.start()
        camera.emit(street(GeoPoint(45.0999, -85.1)))
        advanceTimeBy(1)
        val edge = requireNotNull(subject.edges.value["CC_CC1"])
        assertEquals(11.1, edge.distanceMeters, 0.3)
        assertNotNull(subject.geometry.value["CC_CC1"])

        camera.emit(street(GeoPoint(45.05, -85.1)))
        advanceTimeBy(1)
        assertEquals(1, source.geometryRequests.size)
        assertEquals(5560.0, requireNotNull(subject.edges.value["CC_CC1"]).distanceMeters, 3.0)
        subject.stop()
    }

    @Test
    fun `suppression hides the dock and lifting it restores the state`() = runTest {
        val source = FakeDataSource(OnDemandResult.Loaded(listOf(insideService)))
        val subject = controller(source, backgroundScope)
        subject.start()
        camera.emit(street())
        advanceTimeBy(1)
        subject.setSuppressed(true)
        advanceTimeBy(1)
        assertEquals(OnDemandDockState.Hidden, subject.dockState.value)
        subject.setSuppressed(false)
        advanceTimeBy(1)
        assertTrue(subject.dockState.value is OnDemandDockState.Bar)
        assertEquals(1, source.nearRequests.size)
        subject.stop()
    }

    @Test
    fun `the layer preference off hides the dock`() = runTest {
        val source = FakeDataSource(OnDemandResult.Loaded(listOf(insideService)))
        val subject = controller(source, backgroundScope)
        subject.start()
        camera.emit(street())
        advanceTimeBy(1)
        enabled.value = false
        advanceTimeBy(1)
        assertEquals(OnDemandDockState.Hidden, subject.dockState.value)
        subject.stop()
    }
}
```

- [ ] **Step 4: Run the tests to verify they fail**

Run: `./gradlew :onebusaway-android:testObaMaplibreDebugUnitTest --tests 'org.onebusaway.android.map.OnDemandDockDecisionsTest' --tests 'org.onebusaway.android.map.OnDemandProbeControllerTest'`
Expected: compilation FAILS (`Unresolved reference: OnDemandProbeController`).

- [ ] **Step 5: Write the dock decisions**

Create `M/map/OnDemandDockDecisions.kt`:

```kotlin
package org.onebusaway.android.map

import org.onebusaway.android.ondemand.OnDemandMatch
import org.onebusaway.android.ondemand.ProbePoint
import org.onebusaway.android.ondemand.sortedSoonestUsable

/** What the dock slot shows (spec §2.4); the planner fallback is a sheet on Android, so it has no case here. */
sealed interface OnDemandDockState {
    data object Hidden : OnDemandDockState

    /** Region level, probe point inside ≥ 1 service: [matches] are the inside matches in §2.6 order. */
    data class Card(val matches: List<OnDemandMatch>, val probe: ProbePoint) : OnDemandDockState

    /** Street level: the stack — inside matches when any, else the nearby ones — in §2.6 order. */
    data class Bar(val matches: List<OnDemandMatch>, val probe: ProbePoint) : OnDemandDockState
}

/** One probe's outcome, kept whole so the picker's "all services here" can read the full list. */
data class OnDemandProbeResult(val deployment: String, val probe: ProbePoint, val matches: List<OnDemandMatch>)

/**
 * The dock content for a probe result at a zoom level. [visible] folds in every host-side reason to
 * show nothing (a focused stop, the survey card, a sheet over half the map, the layer off).
 */
internal fun dockStateFor(result: OnDemandProbeResult?, level: OnDemandZoomLevel, visible: Boolean): OnDemandDockState {
    if (result == null || !visible) return OnDemandDockState.Hidden
    val inside = result.matches.filter { it.isInside }.sortedSoonestUsable()
    return when (level) {
        OnDemandZoomLevel.HIDDEN -> OnDemandDockState.Hidden
        OnDemandZoomLevel.REGION -> if (inside.isEmpty()) OnDemandDockState.Hidden else OnDemandDockState.Card(inside, result.probe)
        OnDemandZoomLevel.STREET -> {
            val stack = inside.ifEmpty { result.matches.filter { it.isNearby }.sortedSoonestUsable() }
            if (stack.isEmpty()) OnDemandDockState.Hidden else OnDemandDockState.Bar(stack, result.probe)
        }
    }
}
```

- [ ] **Step 6: Write the probe controller**

Create `M/map/OnDemandProbeController.kt`:

```kotlin
package org.onebusaway.android.map

import java.time.Instant
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.onebusaway.android.api.data.ONDEMAND_PROBE_RADIUS_METERS
import org.onebusaway.android.api.data.OnDemandDataSource
import org.onebusaway.android.api.data.OnDemandResult
import org.onebusaway.android.api.data.OnDemandSupport
import org.onebusaway.android.map.render.CameraSnapshot
import org.onebusaway.android.map.render.ZoneEdge
import org.onebusaway.android.map.render.haversineMeters
import org.onebusaway.android.map.render.nearestBoundaryPoint
import org.onebusaway.android.models.OnDemandService
import org.onebusaway.android.models.ServiceArea
import org.onebusaway.android.ondemand.OnDemandGeometryCache
import org.onebusaway.android.ondemand.OnDemandMatch
import org.onebusaway.android.ondemand.ProbePoint
import org.onebusaway.android.ondemand.ProbeSource
import org.onebusaway.android.ondemand.matchFor
import org.onebusaway.android.util.GeoPoint
import org.onebusaway.android.util.TimeProvider

/** A device fix the controller can reason about without `android.location.Location`. */
data class RiderFix(val point: GeoPoint, val accuracyMeters: Float?)

/** Spec §2.1: a probe point has to move this far before the map or the rider triggers a new probe. */
const val ONDEMAND_PROBE_MOVE_METERS = 100.0

/** A fix less accurate than this is ignored for the rider trigger, so GPS drift can't flip the copy. */
const val ONDEMAND_FIX_MAX_ACCURACY_METERS = 100f

private const val CACHE_LIFETIME_MS = 10 * 60_000L
private const val RIDER_CACHE_DECIMALS = 3
private const val EXACT_CACHE_DECIMALS = 5
private const val RECOMPUTE_GRACE_MS = 1_000L

/**
 * The one probe controller of spec §2.1: decides the probe point (the rider's fix when there is an
 * authorised one, else the map centre), runs a point probe when a trigger fires, caches responses
 * per rounded point for ten minutes, and publishes [dockState], [edges] and [geometry] for the dock.
 * [probeExact] serves the address check and the planner from a separate exact-coordinate cache.
 *
 * JVM-constructible: every input is a flow or an interface, and `now` comes from [timeProvider].
 */
class OnDemandProbeController(
    private val settledCamera: Flow<CameraSnapshot>,
    riderFixes: Flow<RiderFix?>,
    layerEnabled: Flow<Boolean>,
    private val deployment: Flow<String?>,
    private val dataSource: OnDemandDataSource,
    private val support: OnDemandSupport,
    private val geometryCache: OnDemandGeometryCache,
    private val timeProvider: TimeProvider,
    private val scope: CoroutineScope
) {
    private val _result = MutableStateFlow<OnDemandProbeResult?>(null)
    val result: StateFlow<OnDemandProbeResult?> = _result.asStateFlow()

    private val _zoomLevel = MutableStateFlow(OnDemandZoomLevel.HIDDEN)
    val zoomLevel: StateFlow<OnDemandZoomLevel> = _zoomLevel.asStateFlow()

    private val _edges = MutableStateFlow<Map<String, ZoneEdge>>(emptyMap())
    /** The nearest boundary per matched service id, from full geometry; absent until it has loaded. */
    val edges: StateFlow<Map<String, ZoneEdge>> = _edges.asStateFlow()

    private val _geometry = MutableStateFlow<Map<String, List<ServiceArea>>>(emptyMap())
    /** Full-geometry areas per matched service id (the thumbnail's rings); absent until loaded. */
    val geometry: StateFlow<Map<String, List<ServiceArea>>> = _geometry.asStateFlow()

    private val suppressed = MutableStateFlow(false)
    private val enabled = layerEnabled.stateIn(scope, SharingStarted.Eagerly, true)

    val dockState: StateFlow<OnDemandDockState> = combine(_result, _zoomLevel, suppressed, enabled) { result, level, hidden, on ->
        dockStateFor(result, level, visible = !hidden && on)
    }.stateIn(scope, SharingStarted.Eagerly, OnDemandDockState.Hidden)

    // The last usable rider point: an inaccurate fix keeps the previous one, a null fix (permission
    // gone) clears it so the probe source falls back to the map centre.
    private val riderPoint: Flow<GeoPoint?> = riderFixes
        .scan(null as GeoPoint?) { last, fix ->
            when {
                fix == null -> null
                fix.accuracyMeters != null && fix.accuracyMeters > ONDEMAND_FIX_MAX_ACCURACY_METERS -> last
                else -> fix.point
            }
        }
        .distinctUntilChanged()

    private val riderCache = ProbeCache(RIDER_CACHE_DECIMALS)
    private val exactCache = ProbeCache(EXACT_CACHE_DECIMALS)

    private var inputJob: Job? = null
    private var probeJob: Job? = null
    private var recomputeJob: Job? = null
    private var geometryJob: Job? = null

    // Confined to the input collector and the probe job, which never run concurrently for one point.
    private var currentDeployment: String? = null
    private var lastProbe: ProbePoint? = null

    private data class Inputs(val camera: CameraSnapshot, val rider: GeoPoint?, val deployment: String?, val enabled: Boolean)

    fun start() {
        inputJob?.cancel()
        inputJob = scope.launch {
            combine(settledCamera, riderPoint, deployment, enabled) { camera, rider, deployment, on -> Inputs(camera, rider, deployment, on) }
                .collect { onInputs(it) }
        }
    }

    /** Stop probing and forget the state; the next [start] probes afresh. */
    fun stop() {
        inputJob?.cancel()
        inputJob = null
        clearState()
        riderCache.clear()
        exactCache.clear()
        currentDeployment = null
    }

    /** Spec §2.4: the host hides the dock while a focus, the survey card or a tall sheet has the screen. */
    fun setSuppressed(value: Boolean) {
        suppressed.value = value
    }

    /** Spec §2.1 trigger 3: re-probe the last point on return to the foreground (through the cache). */
    fun onForeground() {
        val probe = lastProbe ?: return
        val deployment = currentDeployment ?: return
        launchProbe(probe, deployment)
    }

    /**
     * Spec §2.1's exact-point probe for the address check and the planner: its own cache, keyed on
     * five decimals, never the rider/centre one. Null when the deployment is unknown or unsupported,
     * or the probe fails.
     */
    suspend fun probeExact(point: GeoPoint): List<OnDemandMatch>? {
        val deployment = currentDeployment ?: return null
        if (support.isKnownUnsupported(deployment)) return null
        return when (val outcome = fetch(exactCache, deployment, point)) {
            is Outcome.Services -> outcome.services.map { matchFor(it, now()) }
            Outcome.Unsupported -> {
                support.recordAbsent(deployment)
                clearState()
                null
            }
            Outcome.Failed -> null
        }
    }

    private fun onInputs(inputs: Inputs) {
        _zoomLevel.value = onDemandZoomLevel(inputs.camera.latSpan)
        val deployment = inputs.deployment
        if (deployment == null || !inputs.enabled || support.isKnownUnsupported(deployment)) {
            clearState()
            return
        }
        if (deployment != currentDeployment) {
            // Cancel in-flight work and drop the old server's state before the new one is asked.
            currentDeployment = deployment
            clearState()
        }
        val probe = ProbePoint(inputs.rider ?: inputs.camera.center, if (inputs.rider != null) ProbeSource.Rider else ProbeSource.MapCenter)
        val last = lastProbe
        val moved = last == null || last.source != probe.source || haversineMeters(last.point, probe.point) >= ONDEMAND_PROBE_MOVE_METERS
        if (moved) launchProbe(probe, deployment)
    }

    private fun launchProbe(probe: ProbePoint, deployment: String) {
        probeJob?.cancel()
        lastProbe = probe
        probeJob = scope.launch {
            when (val outcome = fetch(riderCache, deployment, probe.point)) {
                is Outcome.Services -> publish(deployment, probe, outcome.services)
                Outcome.Unsupported -> {
                    support.recordAbsent(deployment)
                    clearState()
                }
                Outcome.Failed -> {
                    // Keep the last state only while it still describes roughly where the rider is.
                    val current = _result.value
                    val nearLastState = current != null && current.deployment == deployment &&
                        haversineMeters(current.probe.point, probe.point) < ONDEMAND_PROBE_MOVE_METERS
                    if (!nearLastState) clearState()
                    // Retry at the next trigger: forget this point so any settle asks again.
                    lastProbe = null
                }
            }
        }
    }

    private fun publish(deployment: String, probe: ProbePoint, services: List<OnDemandService>) {
        val now = now()
        val matches = services.map { matchFor(it, now) }
        _result.value = OnDemandProbeResult(deployment, probe, matches)
        refreshEdges(deployment, probe, matches)
        scheduleRecompute(deployment, probe, services, matches, now)
        loadGeometry(deployment, probe, matches)
    }

    /** Spec §2.1 trigger 4: re-evaluate availability at the earliest change, plus a second, without a request. */
    private fun scheduleRecompute(deployment: String, probe: ProbePoint, services: List<OnDemandService>, matches: List<OnDemandMatch>, now: Instant) {
        recomputeJob?.cancel()
        val next = matches.mapNotNull { it.availability.nextChangeInstant }.minOrNull() ?: return
        val delayMs = (next.toEpochMilli() + RECOMPUTE_GRACE_MS - now.toEpochMilli()).coerceAtLeast(0L)
        recomputeJob = scope.launch {
            delay(delayMs)
            if (currentDeployment == deployment && lastProbe == probe) publish(deployment, probe, services)
        }
    }

    /** Edges for the services whose full geometry is already cached; the rest arrive from [loadGeometry]. */
    private fun refreshEdges(deployment: String, probe: ProbePoint, matches: List<OnDemandMatch>) {
        val cached = matches.mapNotNull { match -> geometryCache.peek(deployment, match.service.id)?.let { match.service.id to it } }.toMap()
        _geometry.value = cached
        _edges.value = cached.mapNotNull { (id, areas) -> nearestBoundaryPoint(probe.point, areas)?.let { id to it } }.toMap()
    }

    private fun loadGeometry(deployment: String, probe: ProbePoint, matches: List<OnDemandMatch>) {
        geometryJob?.cancel()
        val wanted = matches.filter { (it.isInside || it.isNearby) && geometryCache.peek(deployment, it.service.id) == null }
        if (wanted.isEmpty()) return
        geometryJob = scope.launch {
            for (match in wanted) {
                val areas = geometryCache.areas(deployment, match.service.id) ?: continue
                // A fetch that outlived a deployment change answers for a server we no longer show.
                if (currentDeployment != deployment || lastProbe != probe) return@launch
                _geometry.update { it + (match.service.id to areas) }
                nearestBoundaryPoint(probe.point, areas)?.let { edge -> _edges.update { it + (match.service.id to edge) } }
            }
        }
    }

    private fun clearState() {
        probeJob?.cancel()
        recomputeJob?.cancel()
        geometryJob?.cancel()
        _result.value = null
        _edges.value = emptyMap()
        _geometry.value = emptyMap()
        lastProbe = null
    }

    private sealed interface Outcome {
        data class Services(val services: List<OnDemandService>) : Outcome
        data object Unsupported : Outcome
        data object Failed : Outcome
    }

    private suspend fun fetch(cache: ProbeCache, deployment: String, point: GeoPoint): Outcome {
        cache.get(deployment, point, timeProvider.now())?.let { return Outcome.Services(it) }
        return when (val result = dataSource.servicesNear(point, ONDEMAND_PROBE_RADIUS_METERS)) {
            is OnDemandResult.Loaded -> {
                cache.put(deployment, point, result.value, timeProvider.now())
                Outcome.Services(result.value)
            }
            OnDemandResult.Unsupported -> Outcome.Unsupported
            is OnDemandResult.Failed -> Outcome.Failed
        }
    }

    private fun now(): Instant = Instant.ofEpochMilli(timeProvider.now())

    /** Responses per `(deployment, point rounded to [decimals])`, alive for [CACHE_LIFETIME_MS]. */
    private class ProbeCache(private val decimals: Int) {
        private class Entry(val services: List<OnDemandService>, val atMs: Long)

        private val entries = HashMap<Triple<String, Long, Long>, Entry>()

        private fun key(deployment: String, point: GeoPoint): Triple<String, Long, Long> {
            val scale = 10.0.pow(decimals)
            return Triple(deployment, (point.latitude * scale).roundToLong(), (point.longitude * scale).roundToLong())
        }

        fun get(deployment: String, point: GeoPoint, nowMs: Long): List<OnDemandService>? = entries[key(deployment, point)]?.takeIf { nowMs - it.atMs < CACHE_LIFETIME_MS }?.services

        fun put(deployment: String, point: GeoPoint, services: List<OnDemandService>, nowMs: Long) {
            entries[key(deployment, point)] = Entry(services, nowMs)
        }

        fun clear() = entries.clear()
    }
}
```

- [ ] **Step 7: Run the tests**

Run: `./gradlew :onebusaway-android:testObaMaplibreDebugUnitTest --tests 'org.onebusaway.android.map.OnDemandDockDecisionsTest' --tests 'org.onebusaway.android.map.OnDemandProbeControllerTest'`
Expected: PASS. If `the first rider fix…` sees three requests instead of two after the 200 m-accuracy fix, the `scan` is emitting the previous point again — confirm `distinctUntilChanged()` follows it.

- [ ] **Step 8: Compile both flavours and commit**

Run: `./gradlew :onebusaway-android:compileObaGoogleDebugKotlin :onebusaway-android:compileObaMaplibreDebugKotlin -PwarningsAsErrors=true`
Expected: BUILD SUCCESSFUL.

```bash
./gradlew spotlessApply
git add onebusaway-android/src/main/java/org/onebusaway/android/ondemand/OnDemandGeometryCache.kt onebusaway-android/src/main/java/org/onebusaway/android/map/OnDemandDockDecisions.kt onebusaway-android/src/main/java/org/onebusaway/android/map/OnDemandProbeController.kt onebusaway-android/src/test/java/org/onebusaway/android/map
git commit -m "Probe the rider's point for on-demand zones" -m "One controller decides the probe point, runs the radius probe when the
map, the rider, the clock or the foreground says to, caches answers
per rounded point, and publishes what the dock shows. The address
check and planner get an exact-point probe with its own cache so a pin
dropped across an edge never inherits the rider's answer."
```

---

### Task 7: Zone styles, label points and the highlight on the layer

**Files:**
- Modify: `M/map/render/ZoneGeometry.kt` (append the style tables; keep the old `zoneFillColor(Int?)` / `zoneStrokeColor(Int?)` until Task 8 retires them)
- Modify: `M/map/render/MapRenderState.kt:487-494` (`ZonePolygon`)
- Modify: `M/map/OnDemandLayerController.kt`
- Modify: `M/map/MapViewModel.kt:226` (pass `brandColor`)
- Test: `T/map/render/ZoneGeometryTest.kt` (append), `T/map/OnDemandLayerControllerTest.kt` (update + append)

**Interfaces:**
- Consumes: `OnDemandZoomLevel`, `onDemandZoomLevel`, `pinPointFor`, `largestPolygon` (Task 4); `resolveServiceColors` (Task 3); `ZonePolygon.contains`.
- Produces: `enum class ZoneStyle { REGION, REGION_HIGHLIGHTED, STREET, STREET_DIMMED, STREET_HIGHLIGHTED }`; `val ZoneStyle.clickable: Boolean`; `val ZoneStyle.strokeWidthDp: Float`; `const val ZONE_HALO_WIDTH_DP = 10f`; `fun zoneFillColor(color: Int, style: ZoneStyle): Int`; `fun zoneStrokeColor(color: Int, style: ZoneStyle): Int`; `fun zoneHaloColor(color: Int, style: ZoneStyle): Int?`; `fun tappableZone(zones: List<ZonePolygon>, point: GeoPoint): ZonePolygon?`; `ZonePolygon(serviceId, serviceName, rings, color, labelPoint: GeoPoint? = null, style: ZoneStyle = ZoneStyle.REGION)`; `OnDemandLayerController(settledCamera, renderState, dataSource, support, prefsRepository, regionRepository, demoMode, brandColor: Int, scope) { val zoomLevel: StateFlow<OnDemandZoomLevel>; val highlightedServiceId: MutableStateFlow<String?> }`; `internal fun zonePolygons(services, level, highlightedServiceId: String?, brandColor: Int): List<ZonePolygon>`; `internal fun onDemandDeployment(regionRepository, prefsRepository, demoMode): Flow<String?>`.

- [ ] **Step 1: Write the failing tests**

Append to `T/map/render/ZoneGeometryTest.kt`:

```kotlin
    @Test
    fun `zone styles set fill, stroke, halo and clickability per level`() {
        val colour = 0xFF112233.toInt()
        assertEquals(0x33112233, zoneFillColor(colour, ZoneStyle.REGION))
        assertEquals(0x00112233, zoneFillColor(colour, ZoneStyle.STREET))
        assertEquals(0xCC112233.toInt(), zoneStrokeColor(colour, ZoneStyle.REGION))
        assertEquals(0xFF112233.toInt(), zoneStrokeColor(colour, ZoneStyle.STREET))
        assertEquals(0x99112233.toInt(), zoneStrokeColor(colour, ZoneStyle.STREET_DIMMED))
        assertEquals(0xFF112233.toInt(), zoneStrokeColor(colour, ZoneStyle.REGION_HIGHLIGHTED))
        assertEquals(2f, ZoneStyle.REGION.strokeWidthDp)
        assertEquals(4f, ZoneStyle.REGION_HIGHLIGHTED.strokeWidthDp)
        assertEquals(4f, ZoneStyle.STREET.strokeWidthDp)
        assertEquals(null, zoneHaloColor(colour, ZoneStyle.REGION))
        assertEquals(0x40112233, zoneHaloColor(colour, ZoneStyle.STREET))
        assertTrue(ZoneStyle.REGION.clickable)
        assertTrue(ZoneStyle.REGION_HIGHLIGHTED.clickable)
        assertFalse(ZoneStyle.STREET.clickable)
        assertFalse(ZoneStyle.STREET_HIGHLIGHTED.clickable)
    }

    @Test
    fun `a polygon tap at street level does not find a zone`() {
        val region = ZonePolygon("svc", "Zone", listOf(outer), null, style = ZoneStyle.REGION)
        val street = region.copy(style = ZoneStyle.STREET)
        assertEquals(region, tappableZone(listOf(region), GeoPoint(1.0, 1.0)))
        assertEquals(null, tappableZone(listOf(street), GeoPoint(1.0, 1.0)))
    }
```

In `T/map/OnDemandLayerControllerTest.kt` replace the `controller(...)` helper and the multipolygon test, and append three tests:

```kotlin
    private val brand = 0xFF78AA36.toInt()

    private fun controller(
        source: OnDemandDataSource,
        scope: kotlinx.coroutines.CoroutineScope,
        regionRepository: FakeRegionRepository = regions
    ) = OnDemandLayerController(camera, renderState, source, support, prefs, regionRepository, FakeDemoMode(), brand, scope)

    @Test
    fun `a multipolygon area becomes one zone per polygon with one pin`() {
        val zones = zonePolygons(listOf(service(polygons = 2)), OnDemandZoomLevel.REGION, null, brand)
        assertEquals(2, zones.size)
        assertEquals(1, zones.count { it.labelPoint != null })
        assertTrue(zones.all { it.style == ZoneStyle.REGION })
    }

    @Test
    fun `street level restyles the same zones without a pin or a request`() = runTest {
        val source = FakeDataSource(OnDemandResult.Loaded(listOf(service())))
        val subject = controller(source, backgroundScope)
        subject.start()
        camera.emit(viewport)
        advanceTimeBy(1)
        assertEquals(OnDemandZoomLevel.REGION, subject.zoomLevel.value)
        assertEquals(ZoneStyle.REGION, renderState.snapshot.value.onDemandZones.single().style)
        assertTrue(renderState.snapshot.value.onDemandZones.single().labelPoint != null)

        camera.emit(viewport.copy(latSpan = 0.02))
        advanceTimeBy(1)
        assertEquals(OnDemandZoomLevel.STREET, subject.zoomLevel.value)
        val street = renderState.snapshot.value.onDemandZones.single()
        assertEquals(ZoneStyle.STREET, street.style)
        assertEquals(null, street.labelPoint)
        subject.stop()
    }

    @Test
    fun `a highlight raises one service and dims the others at street level`() = runTest {
        val source = FakeDataSource(OnDemandResult.Loaded(listOf(service(id = "a"), service(id = "b"))))
        val subject = controller(source, backgroundScope)
        subject.start()
        camera.emit(viewport.copy(latSpan = 0.02))
        advanceTimeBy(1)
        subject.highlightedServiceId.value = "b"
        advanceTimeBy(1)
        val styles = renderState.snapshot.value.onDemandZones.associate { it.serviceId to it.style }
        assertEquals(ZoneStyle.STREET_HIGHLIGHTED, styles["b"])
        assertEquals(ZoneStyle.STREET_DIMMED, styles["a"])
        assertEquals(1, source.requests.size)

        subject.highlightedServiceId.value = null
        advanceTimeBy(1)
        assertTrue(renderState.snapshot.value.onDemandZones.all { it.style == ZoneStyle.STREET })
        subject.stop()
    }

    @Test
    fun `colliding colours resolve to the palette`() {
        val zones = zonePolygons(listOf(service(id = "a"), service(id = "b")), OnDemandZoomLevel.REGION, null, brand)
        assertEquals(brand, zones.first { it.serviceId == "a" }.color)
        assertEquals(ONDEMAND_FALLBACK_PALETTE[0], zones.first { it.serviceId == "b" }.color)
    }
```

with imports `org.onebusaway.android.map.render.ZoneStyle` and `org.onebusaway.android.ondemand.ONDEMAND_FALLBACK_PALETTE`. The existing `the zoom gate is 65 km of latitude` test stays as is.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :onebusaway-android:testObaMaplibreDebugUnitTest --tests 'org.onebusaway.android.map.render.ZoneGeometryTest' --tests 'org.onebusaway.android.map.OnDemandLayerControllerTest'`
Expected: compilation FAILS (`Unresolved reference: ZoneStyle`).

- [ ] **Step 3: Add the style tables and the tap filter**

Append to `M/map/render/ZoneGeometry.kt`:

```kotlin
/**
 * How a zone draws at the current level (spec §2.3): region fills, street strokes only; a highlighted
 * service draws at full alpha while the others at street level dim.
 */
enum class ZoneStyle { REGION, REGION_HIGHLIGHTED, STREET, STREET_DIMMED, STREET_HIGHLIGHTED }

/** Only region-level polygons take a tap (spec §3.1); at street level the polygon covers the screen. */
val ZoneStyle.clickable: Boolean get() = this == ZoneStyle.REGION || this == ZoneStyle.REGION_HIGHLIGHTED

/** Spec §2.3: 2 pt at region level, 4 pt at street level and for any highlight. */
val ZoneStyle.strokeWidthDp: Float get() = if (this == ZoneStyle.REGION) 2f else 4f

/** The soft halo drawn beneath a street-level stroke where the platform allows a second overlay. */
const val ZONE_HALO_WIDTH_DP = 10f

private const val REGION_STROKE_ALPHA = 0xCC
private const val FULL_ALPHA = 0xFF
private const val DIMMED_STROKE_ALPHA = 0x99
private const val HALO_ALPHA = 0x40
private const val DIMMED_HALO_ALPHA = 0x26

fun zoneFillColor(color: Int, style: ZoneStyle): Int = withAlpha(color, if (style == ZoneStyle.REGION || style == ZoneStyle.REGION_HIGHLIGHTED) FILL_ALPHA else 0)

fun zoneStrokeColor(color: Int, style: ZoneStyle): Int = withAlpha(
    color,
    when (style) {
        ZoneStyle.REGION -> REGION_STROKE_ALPHA
        ZoneStyle.STREET_DIMMED -> DIMMED_STROKE_ALPHA
        ZoneStyle.REGION_HIGHLIGHTED, ZoneStyle.STREET, ZoneStyle.STREET_HIGHLIGHTED -> FULL_ALPHA
    }
)

/** The halo colour at street level; null at region level, where no halo is drawn. */
fun zoneHaloColor(color: Int, style: ZoneStyle): Int? = when (style) {
    ZoneStyle.REGION, ZoneStyle.REGION_HIGHLIGHTED -> null
    ZoneStyle.STREET_DIMMED -> withAlpha(color, DIMMED_HALO_ALPHA)
    ZoneStyle.STREET, ZoneStyle.STREET_HIGHLIGHTED -> withAlpha(color, HALO_ALPHA)
}

/** The topmost *tappable* zone under [point]: region-level only, so street-level taps fall through. */
fun tappableZone(zones: List<ZonePolygon>, point: GeoPoint): ZonePolygon? = zones.lastOrNull { it.style.clickable && it.contains(point) }
```

In `M/map/render/MapRenderState.kt`, extend `ZonePolygon`:

```kotlin
data class ZonePolygon(
    val serviceId: String,
    val serviceName: String,
    val rings: List<List<GeoPoint>>,
    /** The service's resolved colour (ARGB, opaque), or null for the default line colour. */
    val color: Int?,
    /** Where this polygon's pin goes, set on exactly one polygon per service at region level; null draws no pin. */
    val labelPoint: GeoPoint? = null,
    val style: ZoneStyle = ZoneStyle.REGION
)
```

- [ ] **Step 4: Teach the layer controller levels, highlight and colours**

Rewrite `M/map/OnDemandLayerController.kt` (keep the file header and KDoc; the body becomes):

```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
class OnDemandLayerController(
    private val settledCamera: Flow<CameraSnapshot>,
    private val renderState: MapRenderState,
    private val dataSource: OnDemandDataSource,
    private val support: OnDemandSupport,
    private val prefsRepository: PreferencesRepository,
    private val regionRepository: RegionRepository,
    private val demoMode: DemoModeState,
    private val brandColor: Int,
    private val scope: CoroutineScope
) {

    private var loadJob: Job? = null

    private val _zoomLevel = MutableStateFlow(OnDemandZoomLevel.HIDDEN)

    /** Spec §2.2's level for the settled camera, published so the layer, the dock and tests read one answer. */
    val zoomLevel: StateFlow<OnDemandZoomLevel> = _zoomLevel.asStateFlow()

    /** The one service drawn highlighted (spec §2.3), set by the picker row and the bar page; null for none. */
    val highlightedServiceId = MutableStateFlow<String?>(null)

    // The last response and the viewport + deployment that produced it; confined to the loader
    // coroutine, so no synchronization. Services, not polygons: a level or highlight change restyles
    // without asking again.
    private var cachedViewport: CameraSnapshot? = null
    private var cachedDeployment: String? = null
    private var cachedServices: List<OnDemandService>? = null

    /** (Re)start the loader for the current view. */
    fun start() {
        loadJob?.cancel()
        loadJob = scope.launch {
            combine(
                settledCamera,
                prefsRepository.observeBoolean(R.string.preference_key_show_ondemand_zones, true),
                onDemandDeployment(regionRepository, prefsRepository, demoMode),
                highlightedServiceId
            ) { camera, enabled, deployment, highlighted -> Inputs(camera, enabled, deployment, highlighted) }
                // A newer viewport cancels an in-flight load.
                .collectLatest { (camera, enabled, deployment, highlighted) ->
                    val level = onDemandZoomLevel(camera.latSpan)
                    _zoomLevel.value = level
                    if (!enabled || deployment == null || support.isKnownUnsupported(deployment)) {
                        clearZones()
                        return@collectLatest
                    }
                    // The gate comes before the request, as the rental layer's does: a state-wide
                    // view costs no round trip and shows no zones, and the cache survives it so
                    // zooming back in to the same view redraws without asking again.
                    if (level == OnDemandZoomLevel.HIDDEN) {
                        clearZones()
                        return@collectLatest
                    }
                    servicesFor(camera, deployment)?.let { renderState.setOnDemandZones(zonePolygons(it, level, highlighted, brandColor)) }
                }
        }
    }

    private data class Inputs(val camera: CameraSnapshot, val enabled: Boolean, val deployment: String?, val highlighted: String?)

    /** Stop the loader, dropping the cache so the next [start] can't redraw another server's zones. */
    fun stop() {
        loadJob?.cancel()
        loadJob = null
        dropCache()
    }

    /** Leave the map with no zones on it and the loader off. */
    fun hide() {
        stop()
        clearZones()
    }

    private fun dropCache() {
        cachedViewport = null
        cachedDeployment = null
        cachedServices = null
    }

    /**
     * The services for [camera] on [deployment]: from cache when unchanged, else fetched. Null when
     * there is nothing new to draw — a transient failure keeps whatever is on screen (a pan must not
     * blank the layer), and an unsupported answer has already cleared it. The one exception is a
     * failure right after the deployment changed: what is on screen is then another server's zones,
     * so they come off before that server is asked rather than outliving a fetch of it that fails.
     */
    private suspend fun servicesFor(camera: CameraSnapshot, deployment: String): List<OnDemandService>? {
        cachedServices?.takeIf { cachedViewport == camera && cachedDeployment == deployment }?.let { return it }
        if (cachedDeployment != null && cachedDeployment != deployment) {
            dropCache()
            clearZones()
        }
        return when (val result = dataSource.servicesForViewport(camera)) {
            is OnDemandResult.Loaded -> result.value.also {
                cachedViewport = camera
                cachedDeployment = deployment
                cachedServices = it
            }
            OnDemandResult.Unsupported -> {
                support.recordAbsent(deployment)
                clearZones()
                null
            }
            is OnDemandResult.Failed -> null
        }
    }

    private fun clearZones() = renderState.clearOnDemandZones()
}

/**
 * Who answers the on-demand queries, as an input rather than a fact read once: a region switch or a
 * custom API URL changes it. Keyed exactly as the requests are routed ([obaEndpoint]), so a custom URL
 * with no region still loads zones and a 404 is recorded against the server that sent it. The demo
 * transit system has no flex data, so demo mode alone reads as "no deployment". Shared by the layer
 * and the probe controller so both key [OnDemandSupport] the same way.
 */
internal fun onDemandDeployment(regionRepository: RegionRepository, prefsRepository: PreferencesRepository, demoMode: DemoModeState): Flow<String?> = combine(
    regionRepository.region,
    prefsRepository.observeString(R.string.preference_key_oba_api_url, null),
    demoMode.active
) { region, customApiUrl, demo -> if (demo) null else obaEndpoint(customApiUrl, region) }
    .distinctUntilChanged()

/**
 * The tallest viewport the zone layer draws for, in metres of north-south extent.
 *
 * The sibling iOS app hides this layer above a visible-rect height of 600,000 Mercator map points,
 * which spans about 60 to 70 km of latitude across the mid-latitudes the app serves: a county-sized
 * zone stays drawn with the whole county on screen, and a state-wide view draws nothing.
 */
const val ONDEMAND_MAX_VISIBLE_HEIGHT_METERS = 65_000.0

/** Whether the viewport is tight enough to fetch and draw zones for. */
fun isWithinOnDemandZoomGate(latSpan: Double): Boolean = visibleHeightMeters(latSpan) <= ONDEMAND_MAX_VISIBLE_HEIGHT_METERS

/**
 * One [ZonePolygon] per polygon of every area of every service, styled for [level] and the
 * highlight, in the service's resolved colour; at region level the service's largest polygon carries
 * the pin's label point (spec §3.1).
 */
internal fun zonePolygons(services: List<OnDemandService>, level: OnDemandZoomLevel, highlightedServiceId: String?, brandColor: Int): List<ZonePolygon> {
    val colors = resolveServiceColors(services, brandColor)
    return services.flatMap { service ->
        val highlighted = service.id == highlightedServiceId
        val style = when (level) {
            OnDemandZoomLevel.HIDDEN, OnDemandZoomLevel.REGION -> if (highlighted) ZoneStyle.REGION_HIGHLIGHTED else ZoneStyle.REGION
            OnDemandZoomLevel.STREET -> when {
                highlighted -> ZoneStyle.STREET_HIGHLIGHTED
                highlightedServiceId != null -> ZoneStyle.STREET_DIMMED
                else -> ZoneStyle.STREET
            }
        }
        // The pin sits on the largest polygon only; identity is enough because `pinned` is one of these rings.
        val pinned = if (level == OnDemandZoomLevel.REGION) largestPolygon(service.areas) else null
        service.areas.flatMap { area ->
            area.polygons.map { rings ->
                val labelPoint = if (rings === pinned) ringBounds(rings.first())?.let { labelPoint(rings, it) } else null
                ZonePolygon(service.id, service.name, rings, colors[service.id], labelPoint, style)
            }
        }
    }
}
```

Add imports: `kotlinx.coroutines.flow.MutableStateFlow`, `kotlinx.coroutines.flow.StateFlow`, `kotlinx.coroutines.flow.asStateFlow`, `org.onebusaway.android.map.render.ZoneStyle`, `org.onebusaway.android.map.render.labelPoint`, `org.onebusaway.android.map.render.largestPolygon`, `org.onebusaway.android.map.render.ringBounds`, `org.onebusaway.android.ondemand.resolveServiceColors`.

In `M/map/MapViewModel.kt`, where `OnDemandLayerController(` is constructed (line 226), add `brandColor = ContextCompat.getColor(context, R.color.brand_color),` before `scope = viewModelScope` and import `androidx.core.content.ContextCompat`.

- [ ] **Step 5: Run the tests**

Run: `./gradlew :onebusaway-android:testObaMaplibreDebugUnitTest --tests 'org.onebusaway.android.map.render.ZoneGeometryTest' --tests 'org.onebusaway.android.map.OnDemandLayerControllerTest'`
Expected: PASS.

- [ ] **Step 6: Compile both flavours and commit**

Run: `./gradlew :onebusaway-android:compileObaGoogleDebugKotlin :onebusaway-android:compileObaMaplibreDebugKotlin -PwarningsAsErrors=true`
Expected: BUILD SUCCESSFUL (the renderers still call the `Int?` colour functions, which remain).

```bash
./gradlew spotlessApply
git add onebusaway-android/src/main/java/org/onebusaway/android/map onebusaway-android/src/test/java/org/onebusaway/android/map
git commit -m "Style zones by zoom level and highlight" -m "The layer now publishes the zoom level, resolves one colour per
service, places the pin's label point on the largest polygon at region
level, and carries a style per polygon so the renderers draw strokes
only at street level and raise one highlighted service."
```

---

### Task 8: Draw styled zones, halos and labelled pins on both flavours

**Files:**
- Create: `M/map/render/ZonePinBitmaps.kt`
- Create: `R/drawable/ic_directions_car.xml`
- Modify: `G/GoogleMapRenderer.kt:130-131, 338-356, 934-936`; `G/compose/GoogleComposeAdapter.kt:340-392` (`routeMarkerTap`)
- Modify: `L/MapLibreRenderer.kt:174-175, 343-363, 799-803`; `L/compose/MapLibreComposeAdapter.kt:333-345`
- Modify: `M/map/render/ZoneGeometry.kt` (delete the old `zoneFillColor(Int?)` / `zoneStrokeColor(Int?)`), `T/map/render/ZoneGeometryTest.kt` (delete `fill and stroke keep the route hue and set alpha`)

**Interfaces:**
- Consumes: `ZoneStyle`, `clickable`, `strokeWidthDp`, `ZONE_HALO_WIDTH_DP`, `zoneFillColor(Int, ZoneStyle)`, `zoneStrokeColor`, `zoneHaloColor`, `tappableZone`, `ZonePolygon.labelPoint` (Task 7); `ZonePolygonReconciler<NativePolygon>`; `DEFAULT_ROUTE_LINE_COLOR`.
- Produces: `object ZonePinBitmaps { data class ZonePin(val bitmap: Bitmap, val anchorY: Float); fun pin(context, name: String, color: Int): ZonePin }`; `GoogleMapRenderer.zoneForMarker(marker): ZonePolygon?`; `MapLibreRenderer.zoneForMarker(marker): ZonePolygon?`.

No JVM test exists for either renderer (both need a live map); the decisions they consume — `clickable`, the colour tables, `tappableZone` — were pinned in Task 7. The check here is that both flavours compile and Task 20's manual run shows pins at region level and strokes at street level.

- [ ] **Step 1: Add the car glyph**

Create `R/drawable/ic_directions_car.xml` (Material Icons `directions_car`, 24 dp):

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24"
    android:tint="?attr/colorControlNormal">
    <path
        android:fillColor="@android:color/white"
        android:pathData="M18.92,6.01C18.72,5.42 18.16,5 17.5,5h-11c-0.66,0 -1.21,0.42 -1.42,1.01L3,12v8c0,0.55 0.45,1 1,1h1c0.55,0 1,-0.45 1,-1v-1h12v1c0,0.55 0.45,1 1,1h1c0.55,0 1,-0.45 1,-1v-8l-2.08,-5.99zM6.5,16c-0.83,0 -1.5,-0.67 -1.5,-1.5S5.67,13 6.5,13s1.5,0.67 1.5,1.5S7.33,16 6.5,16zM17.5,16c-0.83,0 -1.5,-0.67 -1.5,-1.5s0.67,-1.5 1.5,-1.5 1.5,0.67 1.5,1.5 -0.67,1.5 -1.5,1.5zM5,11l1.5,-4.5h11L19,11H5z" />
</vector>
```

- [ ] **Step 2: Write the pin bitmap**

Create `M/map/render/ZonePinBitmaps.kt`:

```kotlin
package org.onebusaway.android.map.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import android.util.TypedValue
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.DrawableCompat
import kotlin.math.ceil
import kotlin.math.max
import org.onebusaway.android.R

/**
 * The region-level zone pin (spec §3.1): a 38 dp disc in the service colour with a 3 dp white border
 * and the car glyph, the service name below in 12 sp semibold with a white halo. Drawn with
 * `android.graphics` synchronously, so a viewport of pins is stamped in one pass; callers cache by
 * name and colour.
 */
object ZonePinBitmaps {

    /** The bitmap and the fraction of its height at which the disc's centre sits — the marker anchor. */
    data class ZonePin(val bitmap: Bitmap, val anchorY: Float)

    private const val DISC_DP = 38f
    private const val BORDER_DP = 3f
    private const val GLYPH_DP = 20f
    private const val TEXT_SP = 12f
    private const val HALO_DP = 3f
    private const val GAP_DP = 3f
    private const val MAX_LABEL_DP = 150f

    fun pin(context: Context, name: String, color: Int): ZonePin {
        val metrics = context.resources.displayMetrics
        val density = metrics.density
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, TEXT_SP, metrics)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            this.color = Color.BLACK
        }
        val haloPaint = TextPaint(textPaint).apply {
            style = Paint.Style.STROKE
            strokeWidth = HALO_DP * density
            strokeJoin = Paint.Join.ROUND
            this.color = Color.WHITE
        }
        val label = TextUtils.ellipsize(name, textPaint, MAX_LABEL_DP * density, TextUtils.TruncateAt.END).toString()
        val disc = DISC_DP * density
        val textHeight = textPaint.fontMetrics.let { it.descent - it.ascent }
        val width = ceil(max(disc, textPaint.measureText(label) + 2 * HALO_DP * density) + 2 * density).toInt()
        val height = ceil(disc + GAP_DP * density + textHeight + HALO_DP * density).toInt()
        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)
        val centreX = width / 2f
        val centreY = disc / 2f
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        fill.color = Color.WHITE
        canvas.drawCircle(centreX, centreY, disc / 2f, fill)
        fill.color = color
        canvas.drawCircle(centreX, centreY, disc / 2f - BORDER_DP * density, fill)
        ContextCompat.getDrawable(context, R.drawable.ic_directions_car)?.mutate()?.let { glyph ->
            DrawableCompat.setTint(glyph, Color.WHITE)
            val half = (GLYPH_DP * density / 2f).toInt()
            glyph.setBounds(centreX.toInt() - half, centreY.toInt() - half, centreX.toInt() + half, centreY.toInt() + half)
            glyph.draw(canvas)
        }
        val baseline = disc + GAP_DP * density - textPaint.fontMetrics.ascent
        canvas.drawText(label, centreX, baseline, haloPaint)
        canvas.drawText(label, centreX, baseline, textPaint)
        return ZonePin(bitmap, anchorY = centreY / height)
    }
}
```

- [ ] **Step 3: Google: styled polygon, halo, pin, region-only taps**

In `G/GoogleMapRenderer.kt` replace the reconciler field (line 130-131) and the zone functions (338-356) with:

```kotlin
    // On-demand zones have their own change boundary ([renderZones]), so a static redraw leaves them be.
    private val zoneReconciler = ZonePolygonReconciler(createPolygon = ::addZone, removePolygons = ::removeZones)
    private val zoneByPolygon = HashMap<Polygon, ZonePolygon>()
    private val zoneByPin = HashMap<Marker, ZonePolygon>()
    private val zonePinIcons = HashMap<String, PinIcon>()

    /** The native pieces one zone draws: its polygon, the street-level halo beneath it, the region-level pin. */
    private class ZoneNatives(val polygon: Polygon, val halo: Polygon?, val pin: Marker?)

    /** A pin's descriptor with the anchor its bitmap was drawn for (a `BitmapDescriptor` carries none). */
    private class PinIcon(val descriptor: BitmapDescriptor, val anchorY: Float)
```

```kotlin
    /** Reconcile the independently collected on-demand zones, retaining equal native polygons. */
    fun renderZones(zones: List<ZonePolygon>) {
        zoneReconciler.reconcile(zones)
    }

    // At [ZONE_Z_INDEX], so a zone sits beneath every line and marker whenever it is added; the halo one
    // step lower still. Only a region-level polygon takes taps (spec §3.1).
    private fun addZone(zone: ZonePolygon): ZoneNatives? {
        val exterior = zone.rings.firstOrNull() ?: return null
        val color = zone.color ?: DEFAULT_ROUTE_LINE_COLOR
        val outline = exterior.map { it.toLatLng() }
        val holes = zone.rings.drop(1).map { hole -> hole.map { it.toLatLng() } }
        val halo = zoneHaloColor(color, zone.style)?.let { haloColor ->
            val options = PolygonOptions()
                .addAll(outline)
                .fillColor(Color.TRANSPARENT)
                .strokeColor(haloColor)
                .strokeWidth(ZONE_HALO_WIDTH_DP * density)
                .clickable(false)
                .zIndex(ZONE_HALO_Z_INDEX)
            holes.forEach(options::addHole)
            map.addPolygon(options)
        }
        val options = PolygonOptions()
            .addAll(outline)
            .fillColor(zoneFillColor(color, zone.style))
            .strokeColor(zoneStrokeColor(color, zone.style))
            .strokeWidth(zone.style.strokeWidthDp * density)
            .clickable(zone.style.clickable)
            .zIndex(ZONE_Z_INDEX)
        holes.forEach(options::addHole)
        val polygon = map.addPolygon(options)
        zoneByPolygon[polygon] = zone
        val pin = zone.labelPoint?.let { point ->
            val icon = zonePinIcons.getOrPut("${zone.serviceName}:$color") {
                ZonePinBitmaps.pin(context, zone.serviceName, color).let { PinIcon(BitmapDescriptorFactory.fromBitmap(it.bitmap), it.anchorY) }
            }
            map.addMarkerOrFail(
                MarkerOptions()
                    .position(point.toLatLng())
                    .icon(icon.descriptor)
                    .anchor(0.5f, icon.anchorY)
                    .zIndex(ZONE_PIN_Z_INDEX)
            ).also { zoneByPin[it] = zone }
        }
        return ZoneNatives(polygon, halo, pin)
    }

    private fun removeZones(natives: List<ZoneNatives>) {
        for (zone in natives) {
            zoneByPolygon.remove(zone.polygon)
            zone.polygon.remove()
            zone.halo?.remove()
            zone.pin?.let { pin ->
                zoneByPin.remove(pin)
                pin.remove()
            }
        }
    }
```

Replace `zoneForPolygon` (line 935) with:

```kotlin
    /** The zone a native polygon draws, for the adapter's polygon-click dispatch. */
    fun zoneForPolygon(polygon: Polygon): ZonePolygon? = zoneByPolygon[polygon]

    /** The zone whose region-level pin [marker] is, for the adapter's marker-click dispatch. */
    fun zoneForMarker(marker: Marker): ZonePolygon? = zoneByPin[marker]
```

Add to the companion: `private const val ZONE_HALO_Z_INDEX = -2f` and `private const val ZONE_PIN_Z_INDEX = 0.5f`, and imports `org.onebusaway.android.map.render.DEFAULT_ROUTE_LINE_COLOR`, `ZONE_HALO_WIDTH_DP`, `ZonePinBitmaps`, `clickable`, `strokeWidthDp`, `zoneHaloColor` (drop `ZONE_STROKE_WIDTH_PX`). In `G/compose/GoogleComposeAdapter.kt` `routeMarkerTap`, before the final "titled markers" fallthrough:

```kotlin
    val zone = renderer.zoneForMarker(marker)
    if (zone != null) {
        infoWindows.clear()
        cb.onOnDemandZoneClick(zone)
        return true
    }
```

- [ ] **Step 4: MapLibre: fill plus stroked rings, pin, region-only taps**

In `L/MapLibreRenderer.kt` replace the reconciler field (174-175) and the zone functions (343-363) with:

```kotlin
    // On-demand zones have their own change boundary ([renderZones]), so a static redraw leaves them be.
    // A zone is several classic annotations — the fill, one stroke polyline per ring, the region pin.
    private val zoneReconciler = ZonePolygonReconciler<List<Annotation>>(createPolygon = ::addZone, removePolygons = ::removeZones)
    private val zoneByPin = HashMap<Marker, ZonePolygon>()
    private val zonePinIcons = HashMap<String, Icon>()
```

```kotlin
    /**
     * Reconcile the independently collected on-demand zones, retaining equal native annotations. Classic
     * shape annotations stack in add order with no z-index, so a zone added after the route lines would
     * cover them: when any zone is added, the route lines are re-added on top.
     */
    fun renderZones(zones: List<ZonePolygon>) {
        if (zoneReconciler.reconcile(zones)) routePolylineReconciler.redraw(map.cameraPosition.zoom.toFloat())
    }

    // The classic PolygonOptions has no stroke width, so the fill is drawn with a transparent outline and
    // each ring is stroked by its own polyline at the style's width (2 dp region, 4 dp street); there is
    // no halo on this flavour (spec §3.2). Markers always draw above shapes, so the pin and the stops sit on top.
    private fun addZone(zone: ZonePolygon): List<Annotation>? {
        val exterior = zone.rings.firstOrNull() ?: return null
        val color = zone.color ?: DEFAULT_ROUTE_LINE_COLOR
        val annotations = mutableListOf<Annotation>()
        val fill = PolygonOptions()
            .addAll(exterior.map { it.toLatLng() })
            .fillColor(zoneFillColor(color, zone.style))
            .strokeColor(Color.TRANSPARENT)
            .alpha(1f)
        for (hole in zone.rings.drop(1)) fill.addHole(hole.map { it.toLatLng() })
        annotations += map.addPolygon(fill)
        for (ring in zone.rings) {
            annotations += map.addPolyline(
                PolylineOptions()
                    .addAll(ring.map { it.toLatLng() })
                    .color(zoneStrokeColor(color, zone.style))
                    .width(zone.style.strokeWidthDp)
            )
        }
        zone.labelPoint?.let { point ->
            val icon = zonePinIcons.getOrPut("${zone.serviceName}:$color") { iconFactory.fromBitmap(ZonePinBitmaps.pin(context, zone.serviceName, color).bitmap) }
            val marker = map.addMarker(MarkerOptions().position(point.toLatLng()).icon(icon))
            zoneByPin[marker] = zone
            annotations += marker
        }
        return annotations
    }

    private fun removeZones(groups: List<List<Annotation>>) {
        val all = groups.flatten()
        all.filterIsInstance<Marker>().forEach(zoneByPin::remove)
        map.removeAnnotations(all)
    }
```

Replace `zoneAt` (799-803) with:

```kotlin
    /** The topmost region-level zone under [point], for the adapter's map-click dispatch (classic polygons have no click listener). */
    fun zoneAt(point: LatLng): ZonePolygon? = tappableZone(renderState.snapshot.value.onDemandZones, GeoPoint(point.latitude, point.longitude))

    /** The zone whose region-level pin [marker] is. */
    fun zoneForMarker(marker: Marker): ZonePolygon? = zoneByPin[marker]
```

Add imports `android.graphics.Color`, `org.onebusaway.android.map.render.DEFAULT_ROUTE_LINE_COLOR`, `ZonePinBitmaps`, `strokeWidthDp`, `tappableZone`; drop the `contains` import if nothing else uses it. In `L/compose/MapLibreComposeAdapter.kt`'s marker click listener, after the `stop` branch:

```kotlin
        val zone = renderer.zoneForMarker(marker)
        if (zone != null) {
            infoWindows.clear()
            callbacks.onOnDemandZoneClick(zone)
            return@setOnMarkerClickListener true
        }
```

- [ ] **Step 5: Retire the unstyled colour functions**

Delete `zoneFillColor(routeColor: Int?)`, `zoneStrokeColor(routeColor: Int?)` and `ZONE_STROKE_WIDTH_PX` from `M/map/render/ZoneGeometry.kt` (keep `FILL_ALPHA`, `RGB_MASK`, `withAlpha`), and delete the test `fill and stroke keep the route hue and set alpha` from `T/map/render/ZoneGeometryTest.kt` (its replacement is Task 7's `zone styles set fill, stroke, halo and clickability per level`).

- [ ] **Step 6: Compile both flavours, run the touched tests, commit**

Run: `./gradlew :onebusaway-android:compileObaGoogleDebugKotlin :onebusaway-android:compileObaMaplibreDebugKotlin -PwarningsAsErrors=true`
Expected: BUILD SUCCESSFUL.
Run: `./gradlew :onebusaway-android:testObaMaplibreDebugUnitTest --tests 'org.onebusaway.android.map.render.ZoneGeometryTest' --tests 'org.onebusaway.android.map.render.ZonePolygonReconcilerTest'`
Expected: PASS.

```bash
./gradlew spotlessApply
git add onebusaway-android/src/main onebusaway-android/src/google onebusaway-android/src/maplibre onebusaway-android/src/test
git commit -m "Draw zone strokes, halos and labelled pins per flavour" -m "Region level draws a filled polygon and one labelled pin per service;
street level draws a stroke with a halo on Google and per-ring
polylines on MapLibre, which has no stroke width. Taps reach a zone
only at region level so a street-level polygon covering the screen
keeps dismissing pins and focus."
```

---

### Task 9: `MapViewModel` owns the probe controller and the dock actions

**Files:**
- Create: `M/map/OnDemandFraming.kt`
- Modify: `M/map/MapViewModel.kt` (constructor, controllers, start/stop/hide sites at lines 359, 393, 434, 656, 746, `onResume` at 812)
- Test: `T/map/OnDemandFramingTest.kt`

**Interfaces:**
- Consumes: `OnDemandProbeController`, `RiderFix`, `OnDemandDockState`, `OnDemandProbeResult` (Task 6); `onDemandDeployment` (Task 7); `OnDemandGeometryCache`; `resolveServiceColors`; `MapHost.{settledCamera, myLocationEnabled, frame, centerOn}`; `Location.toGeoPoint()` (`M/util/Locations.kt`); `visibleHeightMeters`.
- Produces: `MapViewModel.onDemandDock: StateFlow<OnDemandDockState>`, `onDemandProbeResult: StateFlow<OnDemandProbeResult?>`, `onDemandEdges`, `onDemandGeometry`, `onDemandColors: StateFlow<Map<String, Int>>`, `fun setOnDemandDockSuppressed(Boolean)`, `fun highlightOnDemandService(id: String?)`, `fun zoomOutToOnDemandZones(matches: List<OnDemandMatch>, probe: GeoPoint)`, `fun panToOnDemandEdge(point: GeoPoint)`, `suspend fun probeOnDemandExact(point): List<OnDemandMatch>?`, `fun onDemandLocationCheckFor(serviceId: String): LocationCheck?`; `fun onDemandZoomOutCorners(bounds: List<Pair<GeoPoint, GeoPoint>>, probe: GeoPoint): Pair<GeoPoint, GeoPoint>?` with `ONDEMAND_ZOOM_OUT_PADDING_FRACTION = 0.2`, `ONDEMAND_ZOOM_OUT_MIN_HEIGHT_METERS = 8_000.0`, `ONDEMAND_ZOOM_OUT_MAX_HEIGHT_METERS = 58_500.0`.

- [ ] **Step 1: Write the failing framing test**

Create `T/map/OnDemandFramingTest.kt`:

```kotlin
package org.onebusaway.android.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.onebusaway.android.map.rental.visibleHeightMeters
import org.onebusaway.android.util.GeoPoint

class OnDemandFramingTest {

    private val probe = GeoPoint(45.05, -85.1)

    private fun box(heightDeg: Double, centre: GeoPoint = probe) = GeoPoint(centre.latitude - heightDeg / 2, centre.longitude - heightDeg / 2) to GeoPoint(centre.latitude + heightDeg / 2, centre.longitude + heightDeg / 2)

    @Test
    fun `a normal zone is padded by twenty percent about its own centre`() {
        val (sw, ne) = requireNotNull(onDemandZoomOutCorners(listOf(box(0.2, GeoPoint(45.2, -85.0))), probe))
        assertEquals(0.24, ne.latitude - sw.latitude, 1e-9)
        assertEquals(45.2, (sw.latitude + ne.latitude) / 2, 1e-9)
        assertEquals(-85.0, (sw.longitude + ne.longitude) / 2, 1e-9)
    }

    @Test
    fun `a tiny zone is framed at twice the street gate about the probe`() {
        val (sw, ne) = requireNotNull(onDemandZoomOutCorners(listOf(box(0.005)), probe))
        assertEquals(ONDEMAND_ZOOM_OUT_MIN_HEIGHT_METERS, visibleHeightMeters(ne.latitude - sw.latitude), 1.0)
        assertEquals(probe.latitude, (sw.latitude + ne.latitude) / 2, 1e-9)
    }

    @Test
    fun `a huge zone is clamped to nine tenths of the outer window about the probe`() {
        val (sw, ne) = requireNotNull(onDemandZoomOutCorners(listOf(box(2.0, GeoPoint(46.0, -85.0))), probe))
        assertEquals(ONDEMAND_ZOOM_OUT_MAX_HEIGHT_METERS, visibleHeightMeters(ne.latitude - sw.latitude), 1.0)
        assertEquals(probe.longitude, (sw.longitude + ne.longitude) / 2, 1e-9)
    }

    @Test
    fun `the union of several boxes is framed`() {
        val (sw, ne) = requireNotNull(onDemandZoomOutCorners(listOf(box(0.2, GeoPoint(45.0, -85.0)), box(0.2, GeoPoint(45.2, -85.2))), probe))
        assertEquals(45.0 - 0.1 - 0.04, sw.latitude, 1e-9)
        assertEquals(45.2 + 0.1 + 0.04, ne.latitude, 1e-9)
    }

    @Test
    fun `no boxes gives nothing to frame`() {
        assertNull(onDemandZoomOutCorners(emptyList(), probe))
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :onebusaway-android:testObaMaplibreDebugUnitTest --tests 'org.onebusaway.android.map.OnDemandFramingTest'`
Expected: compilation FAILS (`Unresolved reference: onDemandZoomOutCorners`).

- [ ] **Step 3: Write the framing math**

Create `M/map/OnDemandFraming.kt`:

```kotlin
package org.onebusaway.android.map

import org.onebusaway.android.map.rental.visibleHeightMeters
import org.onebusaway.android.util.GeoPoint

/** Spec §3.4: fit the stack's union bbox with 20 % padding. */
const val ONDEMAND_ZOOM_OUT_PADDING_FRACTION = 0.2

/** Never smaller than twice the street gate, so the thumbnail tap lands at region level. */
const val ONDEMAND_ZOOM_OUT_MIN_HEIGHT_METERS = 2 * ONDEMAND_STREET_LEVEL_MAX_HEIGHT_METERS

/** Never larger than 0.9 × the outer window, so the layer stays drawn. */
const val ONDEMAND_ZOOM_OUT_MAX_HEIGHT_METERS = 0.9 * ONDEMAND_MAX_VISIBLE_HEIGHT_METERS

private const val METERS_PER_DEGREE_LATITUDE = 111_133.0

/**
 * The corners the map fits when the bar's thumbnail is tapped: the union of [bounds] padded by
 * [ONDEMAND_ZOOM_OUT_PADDING_FRACTION]; when that is shorter than the minimum or taller than the
 * maximum, a box of that height centred on [probe], keeping the padded box's aspect ratio.
 */
fun onDemandZoomOutCorners(bounds: List<Pair<GeoPoint, GeoPoint>>, probe: GeoPoint): Pair<GeoPoint, GeoPoint>? {
    if (bounds.isEmpty()) return null
    val minLat = bounds.minOf { it.first.latitude }
    val maxLat = bounds.maxOf { it.second.latitude }
    val minLon = bounds.minOf { it.first.longitude }
    val maxLon = bounds.maxOf { it.second.longitude }
    val height = (maxLat - minLat) * (1 + ONDEMAND_ZOOM_OUT_PADDING_FRACTION)
    val width = (maxLon - minLon) * (1 + ONDEMAND_ZOOM_OUT_PADDING_FRACTION)
    val heightMeters = visibleHeightMeters(height)
    val clampedHeight = when {
        heightMeters < ONDEMAND_ZOOM_OUT_MIN_HEIGHT_METERS -> ONDEMAND_ZOOM_OUT_MIN_HEIGHT_METERS / METERS_PER_DEGREE_LATITUDE
        heightMeters > ONDEMAND_ZOOM_OUT_MAX_HEIGHT_METERS -> ONDEMAND_ZOOM_OUT_MAX_HEIGHT_METERS / METERS_PER_DEGREE_LATITUDE
        else -> null
    }
    if (clampedHeight == null) {
        val centreLat = (minLat + maxLat) / 2
        val centreLon = (minLon + maxLon) / 2
        return GeoPoint(centreLat - height / 2, centreLon - width / 2) to GeoPoint(centreLat + height / 2, centreLon + width / 2)
    }
    val clampedWidth = if (height == 0.0) clampedHeight else width * clampedHeight / height
    return GeoPoint(probe.latitude - clampedHeight / 2, probe.longitude - clampedWidth / 2) to GeoPoint(probe.latitude + clampedHeight / 2, probe.longitude + clampedWidth / 2)
}
```

- [ ] **Step 4: Wire the probe controller into `MapViewModel`**

In `M/map/MapViewModel.kt`:

1. Constructor: add `private val onDemandGeometryCache: OnDemandGeometryCache,` after `onDemandSupport` and `private val timeProvider: TimeProvider,` after `demoMode` (Hilt provides both; no test constructs `MapViewModel`).
2. Replace the `OnDemandLayerController(` construction so both controllers share the brand colour and the deployment flow, and add the probe controller and its surface directly after it:

```kotlin
    private val brandColor = ContextCompat.getColor(context, R.color.brand_color)

    // The on-demand zone overlay (GTFS-Flex service areas for the viewport); overlays every mode
    // exactly as the rental layer does.
    private val onDemandController = OnDemandLayerController(
        settledCamera = mapHost.settledCamera(),
        renderState = mapHost.renderState,
        dataSource = onDemandDataSource,
        support = onDemandSupport,
        prefsRepository = prefsRepository,
        regionRepository = regionRepo,
        demoMode = demoMode,
        brandColor = brandColor,
        scope = viewModelScope
    )

    // The point probe behind the dock (spec §2.1). The rider is the probe source only while the
    // blue dot is authorised and a fix exists; otherwise the settled map centre is.
    private val onDemandProbeController = OnDemandProbeController(
        settledCamera = mapHost.settledCamera(),
        riderFixes = combine(locationRepository.location, mapHost.myLocationEnabled) { location, authorised ->
            location?.takeIf { authorised }?.let { RiderFix(it.toGeoPoint(), if (it.hasAccuracy()) it.accuracy else null) }
        },
        layerEnabled = prefsRepository.observeBoolean(R.string.preference_key_show_ondemand_zones, true),
        deployment = onDemandDeployment(regionRepo, prefsRepository, demoMode),
        dataSource = onDemandDataSource,
        support = onDemandSupport,
        geometryCache = onDemandGeometryCache,
        timeProvider = timeProvider,
        scope = viewModelScope
    )

    /** What the dock slot shows (spec §2.4), already gated on zoom level, layer and host suppression. */
    val onDemandDock: StateFlow<OnDemandDockState> get() = onDemandProbeController.dockState

    /** The whole last probe, for the picker's "all services here" list. */
    val onDemandProbeResult: StateFlow<OnDemandProbeResult?> get() = onDemandProbeController.result

    val onDemandEdges: StateFlow<Map<String, ZoneEdge>> get() = onDemandProbeController.edges

    val onDemandGeometry: StateFlow<Map<String, List<ServiceArea>>> get() = onDemandProbeController.geometry

    /** One resolved colour per matched service id, so the bar, card and thumbnail agree with the map. */
    val onDemandColors: StateFlow<Map<String, Int>> = onDemandProbeController.result
        .map { result -> resolveServiceColors(result?.matches?.map { it.service } ?: emptyList(), brandColor) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    init {
        // Spec §2.3: the highlight clears whenever the dock leaves the bar state.
        viewModelScope.launch {
            onDemandProbeController.dockState.collect { if (it !is OnDemandDockState.Bar) onDemandController.highlightedServiceId.value = null }
        }
    }

    /** Spec §2.4: the host hides the dock while a focus, the survey card or a tall sheet has the screen. */
    fun setOnDemandDockSuppressed(suppressed: Boolean) = onDemandProbeController.setSuppressed(suppressed)

    /** Spec §2.3: draw [serviceId] highlighted (a picker row or a bar page), or nothing with null. */
    fun highlightOnDemandService(serviceId: String?) {
        onDemandController.highlightedServiceId.value = serviceId
    }

    /** The bar thumbnail's tap (spec §3.4): fit the stack's zones at region level, clamped about [probe]. */
    fun zoomOutToOnDemandZones(matches: List<OnDemandMatch>, probe: GeoPoint) {
        val bounds = matches.flatMap { match -> match.service.areas.map { it.southWest to it.northEast } }
        val corners = onDemandZoomOutCorners(bounds, probe) ?: return
        mapHost.frame(FramingIntent.Points(listOf(corners.first, corners.second), minSpanDeg = 0.0))
    }

    /** The outside chevron (spec §3.4): centre the nearest boundary point, keeping the zoom. */
    fun panToOnDemandEdge(point: GeoPoint) = mapHost.centerOn(point.latitude, point.longitude, animate = true)

    /** The exact-point probe for the dropped pin and the planner (spec §2.1); null when unanswerable. */
    suspend fun probeOnDemandExact(point: GeoPoint): List<OnDemandMatch>? = onDemandProbeController.probeExact(point)

    /**
     * The probe's location facts for [serviceId] when the last probe matched it (spec §3.6 item 3: a
     * region-level pin tap opens the page with them), else null and the page omits the row.
     */
    fun onDemandLocationCheckFor(serviceId: String): LocationCheck? {
        val result = onDemandProbeController.result.value ?: return null
        val match = result.matches.firstOrNull { it.service.id == serviceId } ?: return null
        return LocationCheck(result.probe.source, match.isInside, locality = null, point = result.probe.point)
    }
```

3. Beside every `onDemandController.start()` (in `showNearbyStops`, `enterRoute`, `clearAllFocus`) add `onDemandProbeController.start()`; beside `onDemandController.stop()` in `leaveCurrentView` add `onDemandProbeController.stop()`; beside `onDemandController.hide()` in `showItinerary` add `onDemandProbeController.stop()`.
4. In `onResume()` add `onDemandProbeController.onForeground()` after `rentalController.syncFromPreferences()`.
5. Imports: `androidx.core.content.ContextCompat`, `kotlinx.coroutines.flow.SharingStarted`, `kotlinx.coroutines.flow.combine`, `kotlinx.coroutines.flow.map`, `kotlinx.coroutines.flow.stateIn`, `org.onebusaway.android.map.render.FramingIntent`, `org.onebusaway.android.map.render.ZoneEdge`, `org.onebusaway.android.models.ServiceArea`, `org.onebusaway.android.ondemand.LocationCheck`, `org.onebusaway.android.ondemand.OnDemandGeometryCache`, `org.onebusaway.android.ondemand.OnDemandMatch`, `org.onebusaway.android.ondemand.resolveServiceColors`, `org.onebusaway.android.util.TimeProvider`, `org.onebusaway.android.util.toGeoPoint`.

- [ ] **Step 5: Run the test and the compile gates**

Run: `./gradlew :onebusaway-android:testObaMaplibreDebugUnitTest --tests 'org.onebusaway.android.map.OnDemandFramingTest'`
Expected: PASS.
Run: `./gradlew :onebusaway-android:compileObaGoogleDebugKotlin :onebusaway-android:compileObaMaplibreDebugKotlin -PwarningsAsErrors=true`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
./gradlew spotlessApply
git add onebusaway-android/src/main/java/org/onebusaway/android/map onebusaway-android/src/test/java/org/onebusaway/android/map
git commit -m "Run the on-demand probe from the home map" -m "The map view model owns the probe controller beside the zone layer,
starts and stops both together, feeds it the rider's fix while the
blue dot is authorised, and exposes the dock state and the actions the
dock needs: highlight, zoom out to the zone, pan to the edge, and the
exact-point probe."
```

---

### Task 10: `ZoneThumbnail` — the Canvas drawing shared by the bar and the detail page

**Files:**
- Create: `M/ui/home/ondemand/ThumbnailFraming.kt` (pure)
- Create: `M/ui/home/ondemand/ZoneThumbnail.kt`
- Create: `R/drawable/ic_location_on.xml`
- Test: `T/ui/home/ondemand/ThumbnailFramingTest.kt`

**Interfaces:**
- Consumes: `LocalProjection` (Task 4); `ServiceArea.polygons`.
- Produces (package `org.onebusaway.android.ui.home.ondemand`): `data class ThumbnailShape(val rings: List<List<GeoPoint>>, val color: Int)`; `fun List<ServiceArea>.toThumbnailShapes(color: Int): List<ThumbnailShape>`; `data class ThumbnailFrame(projection, pixelsPerMeter: Double, sizePx: Float) { fun toPixels(point): Pair<Float, Float> }`; `fun thumbnailFrame(vertices: List<GeoPoint>, centre: GeoPoint, sizePx: Float, insetPx: Float): ThumbnailFrame`; `fun shapesCentre(shapes: List<ThumbnailShape>): GeoPoint?`; `enum class ProbeDotStyle { RIDER, MAP_CENTER, POINT }`; `@Composable fun ZoneThumbnail(shapes: List<ThumbnailShape>, centre: GeoPoint?, probePoint: GeoPoint?, probeStyle: ProbeDotStyle, modifier: Modifier = Modifier, fitProbe: Boolean = true)`.

- [ ] **Step 1: Write the failing test**

Create `T/ui/home/ondemand/ThumbnailFramingTest.kt`:

```kotlin
package org.onebusaway.android.ui.home.ondemand

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.util.GeoPoint

class ThumbnailFramingTest {

    private val square = listOf(GeoPoint(45.0, -85.2), GeoPoint(45.0, -85.0), GeoPoint(45.1, -85.0), GeoPoint(45.1, -85.2), GeoPoint(45.0, -85.2))
    private val probeOutside = GeoPoint(44.9, -85.1)

    @Test
    fun `every vertex and the probe land inside the inset square, centred on the probe`() {
        val frame = thumbnailFrame(square + probeOutside, centre = probeOutside, sizePx = 56f, insetPx = 4f)
        for (point in square + probeOutside) {
            val (x, y) = frame.toPixels(point)
            assertTrue("$point at $x,$y", x in 4f..52f && y in 4f..52f)
        }
        assertEquals(28f to 28f, frame.toPixels(probeOutside))
    }

    @Test
    fun `north is up and east is right`() {
        val frame = thumbnailFrame(square, centre = GeoPoint(45.05, -85.1), sizePx = 100f, insetPx = 0f)
        val (_, northY) = frame.toPixels(GeoPoint(45.1, -85.1))
        val (eastX, _) = frame.toPixels(GeoPoint(45.05, -85.0))
        assertTrue(northY < 50f)
        assertTrue(eastX > 50f)
    }

    @Test
    fun `a single point does not divide by zero`() {
        val frame = thumbnailFrame(listOf(probeOutside), centre = probeOutside, sizePx = 56f, insetPx = 4f)
        assertEquals(28f to 28f, frame.toPixels(probeOutside))
    }

    @Test
    fun `the shapes centre is the bbox centre and null for none`() {
        val centre = requireNotNull(shapesCentre(listOf(ThumbnailShape(listOf(square), 0))))
        assertEquals(45.05, centre.latitude, 1e-9)
        assertEquals(-85.1, centre.longitude, 1e-9)
        assertNull(shapesCentre(emptyList()))
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :onebusaway-android:testObaMaplibreDebugUnitTest --tests 'org.onebusaway.android.ui.home.ondemand.ThumbnailFramingTest'`
Expected: compilation FAILS (`Unresolved reference: thumbnailFrame`).

- [ ] **Step 3: Write the framing and the drawable**

Create `M/ui/home/ondemand/ThumbnailFraming.kt`:

```kotlin
package org.onebusaway.android.ui.home.ondemand

import kotlin.math.abs
import org.onebusaway.android.map.render.LocalProjection
import org.onebusaway.android.models.ServiceArea
import org.onebusaway.android.util.GeoPoint

/** One polygon of a service in the thumbnail: exterior ring first, then holes, in the service colour. */
data class ThumbnailShape(val rings: List<List<GeoPoint>>, val color: Int)

fun List<ServiceArea>.toThumbnailShapes(color: Int): List<ThumbnailShape> = flatMap { area -> area.polygons.map { ThumbnailShape(it, color) } }

/**
 * Spec §3.4: the §2.7 projection about the frame's centre, scaled so every vertex handed to
 * [thumbnailFrame] fits inside the square with its inset. `y` grows south on screen, north on the ground.
 */
data class ThumbnailFrame(val projection: LocalProjection, val pixelsPerMeter: Double, val sizePx: Float) {
    fun toPixels(point: GeoPoint): Pair<Float, Float> = (sizePx / 2 + projection.x(point) * pixelsPerMeter).toFloat() to (sizePx / 2 - projection.y(point) * pixelsPerMeter).toFloat()
}

fun thumbnailFrame(vertices: List<GeoPoint>, centre: GeoPoint, sizePx: Float, insetPx: Float): ThumbnailFrame {
    val projection = LocalProjection(centre)
    val extent = vertices.maxOfOrNull { maxOf(abs(projection.x(it)), abs(projection.y(it))) } ?: 0.0
    val half = (sizePx / 2 - insetPx).coerceAtLeast(1f)
    val pixelsPerMeter = if (extent <= 0.0) 1.0 else half / extent
    return ThumbnailFrame(projection, pixelsPerMeter, sizePx)
}

/** The bbox centre of every vertex, for a thumbnail centred on the zone rather than the probe. */
fun shapesCentre(shapes: List<ThumbnailShape>): GeoPoint? {
    val points = shapes.flatMap { it.rings.flatten() }
    if (points.isEmpty()) return null
    return GeoPoint((points.minOf { it.latitude } + points.maxOf { it.latitude }) / 2, (points.minOf { it.longitude } + points.maxOf { it.longitude }) / 2)
}
```

Create `R/drawable/ic_location_on.xml` (Material Icons `location_on`):

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24"
    android:tint="?attr/colorControlNormal">
    <path
        android:fillColor="@android:color/white"
        android:pathData="M12,2C8.13,2 5,5.13 5,9c0,5.25 7,13 7,13s7,-7.75 7,-13c0,-3.87 -3.13,-7 -7,-7zM12,11.5c-1.38,0 -2.5,-1.12 -2.5,-2.5s1.12,-2.5 2.5,-2.5 2.5,1.12 2.5,2.5 -1.12,2.5 -2.5,2.5z" />
</vector>
```

- [ ] **Step 4: Write the composable**

Create `M/ui/home/ondemand/ZoneThumbnail.kt`:

```kotlin
package org.onebusaway.android.ui.home.ondemand

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import org.onebusaway.android.R
import org.onebusaway.android.util.GeoPoint

/** How the probe point is drawn (spec §3.6 item 4): the rider's blue dot, a gray dot for the map centre, a pin for a chosen place. */
enum class ProbeDotStyle { RIDER, MAP_CENTER, POINT }

private val THUMBNAIL_INSET = 4.dp
private val DOT_RADIUS = 3.dp
private val DOT_RING = 1.5.dp
private val PIN_SIZE = 18.dp
private const val SHAPE_FILL_ALPHA = 0.35f
private val RIDER_BLUE = Color(0xFF3B82F6)
private val CENTER_GRAY = Color(0xFF636366)
private val PIN_RED = Color(0xFFD32F2F)

/**
 * A Canvas drawing of zone rings (spec §3.4 thumbnail, R14): every shape filled at 0.35 and stroked
 * 1 dp in its colour, projected about [centre] and scaled so every vertex (and the probe, when
 * [fitProbe]) fits the square with a 4 dp inset. The probe is drawn only when it lands inside the
 * square, so a detail thumbnail centred on the zone omits a far-away rider.
 */
@Composable
fun ZoneThumbnail(
    shapes: List<ThumbnailShape>,
    centre: GeoPoint?,
    probePoint: GeoPoint?,
    probeStyle: ProbeDotStyle,
    modifier: Modifier = Modifier,
    fitProbe: Boolean = true
) {
    val pin = painterResource(R.drawable.ic_location_on)
    Canvas(modifier) {
        val frameCentre = centre ?: return@Canvas
        val vertices = shapes.flatMap { it.rings.flatten() } + listOfNotNull(probePoint.takeIf { fitProbe })
        val frame = thumbnailFrame(vertices, frameCentre, size.minDimension, THUMBNAIL_INSET.toPx())
        for (shape in shapes) {
            val path = Path().apply {
                fillType = PathFillType.EvenOdd
                for (ring in shape.rings) {
                    ring.forEachIndexed { index, point ->
                        val (x, y) = frame.toPixels(point)
                        if (index == 0) moveTo(x, y) else lineTo(x, y)
                    }
                    close()
                }
            }
            val colour = Color(shape.color)
            drawPath(path, colour.copy(alpha = SHAPE_FILL_ALPHA))
            drawPath(path, colour, style = Stroke(width = 1.dp.toPx()))
        }
        val point = probePoint ?: return@Canvas
        val (x, y) = frame.toPixels(point)
        if (x !in 0f..size.width || y !in 0f..size.height) return@Canvas
        when (probeStyle) {
            ProbeDotStyle.RIDER -> ringedDot(x, y, RIDER_BLUE)
            ProbeDotStyle.MAP_CENTER -> ringedDot(x, y, CENTER_GRAY)
            ProbeDotStyle.POINT -> {
                val side = PIN_SIZE.toPx()
                translate(left = x - side / 2, top = y - side) {
                    with(pin) { draw(Size(side, side), colorFilter = ColorFilter.tint(PIN_RED)) }
                }
            }
        }
    }
}

private fun DrawScope.ringedDot(x: Float, y: Float, colour: Color) {
    drawCircle(Color.White, radius = DOT_RADIUS.toPx() + DOT_RING.toPx(), center = Offset(x, y))
    drawCircle(colour, radius = DOT_RADIUS.toPx(), center = Offset(x, y))
}
```

- [ ] **Step 5: Run the test and the compile gates, commit**

Run: `./gradlew :onebusaway-android:testObaMaplibreDebugUnitTest --tests 'org.onebusaway.android.ui.home.ondemand.ThumbnailFramingTest'`
Expected: PASS.
Run: `./gradlew :onebusaway-android:compileObaGoogleDebugKotlin :onebusaway-android:compileObaMaplibreDebugKotlin -PwarningsAsErrors=true`
Expected: BUILD SUCCESSFUL.

```bash
./gradlew spotlessApply
git add onebusaway-android/src/main/java/org/onebusaway/android/ui/home/ondemand onebusaway-android/src/main/res/drawable/ic_location_on.xml onebusaway-android/src/test/java/org/onebusaway/android/ui/home/ondemand
git commit -m "Draw zone thumbnails on a Canvas" -m "The bar's thumbnail and the detail page's map stand-in are the same
drawing: the full-geometry rings scaled about a centre so every vertex
fits, with the probe point drawn in its source's style. Framing is
pure and tested; the Canvas only paints it."
```

---

### Task 11: Zone card and docked bar composables

**Files:**
- Create: `M/ui/home/ondemand/CopyResolvers.kt`, `M/ui/home/ondemand/DockBarPages.kt`, `M/ui/home/ondemand/OnDemandZoneCard.kt`, `M/ui/home/ondemand/OnDemandDockBar.kt`, `M/ui/home/ondemand/OnDemandDockFeature.kt`
- Modify: `M/models/OnDemandService.kt` (`routeTextColor`), `M/api/adapters/OnDemandAdapters.kt`
- Create: `R/drawable/ic_call.xml`; Modify: `R/values/strings.xml`, `R/values/colors.xml`
- Test: `T/ui/home/ondemand/DockBarPagesTest.kt`; `AT/ondemand/OnDemandAndroidFixtures.kt`, `AT/ui/home/ondemand/OnDemandZoneCardTest.kt`, `AT/ui/home/ondemand/OnDemandDockBarTest.kt`

**Interfaces:**
- Consumes: `TextSpec`, `PluralSpec`, `DistanceText`, `barTitle`, `cardMeta`, `badgeText`, `tagText`, `bookingTag` (Task 5); `OnDemandDockState.Bar/Card` (Task 6); `ZoneEdge`; `readableTextColor`, `ONDEMAND_OUTSIDE_GRAY` (Task 3); `ZoneThumbnail`, `toThumbnailShapes`, `ProbeDotStyle` (Task 10); `RouteReference.textColorArgb()` (`M/api/adapters/RouteAdapters.kt:46`).
- Produces: `@Composable fun TextSpec.resolve(): String`, `PluralSpec.resolve()`, `Any.resolveCopy()`; `enum class TrailingAction { PHONE, URL, CHEVRON_EDGE, CHEVRON_DETAIL, NONE }`; `data class ServiceContact(phone: String?, url: String?)`; `fun contactOf(service): ServiceContact`; `data class DockBarPage(match, color: Int, textColor: Int, title: TextSpec?, trailing: TrailingAction, contact: ServiceContact, edgePoint: GeoPoint?)`; `fun dockBarPages(state: OnDemandDockState.Bar, edges: Map<String, ZoneEdge>, colors: Map<String, Int>, now: Instant, locale: Locale, metric: Boolean): List<DockBarPage>`; `fun trailingAction(match, contact, edgePoint): TrailingAction`; `sealed interface CardPrimary { Call(phone); Online(url) }`; `fun cardPrimary(service): CardPrimary?`; `fun showsBadge(pageCount: Int): Boolean`; `data class OnDemandDockActions(openDetail: (OnDemandMatch, ProbePoint) -> Unit, openPicker: (matches: List<OnDemandMatch>, probe: ProbePoint, nearby: Boolean) -> Unit, call: (String) -> Unit, openUrl: (String) -> Unit, zoomOut: (List<OnDemandMatch>, GeoPoint) -> Unit, panTo: (GeoPoint) -> Unit, highlight: (String?) -> Unit)`; `@Composable fun OnDemandDockFeature(state: OnDemandDockState, allMatches: List<OnDemandMatch>, edges, geometry: Map<String, List<ServiceArea>>, colors, now: Instant, actions: OnDemandDockActions, modifier)`; `@Composable fun OnDemandZoneCard(...)`; `@Composable fun OnDemandDockBar(...)`; `OnDemandService.routeTextColor: Int?`; test tags `OnDemandDockTestTags.{CARD, BAR, THUMBNAIL, TRAILING, BADGE}`.

- [ ] **Step 1: Strings, colours, the phone glyph, the route text colour**

Append to `R/values/strings.xml` after the Task 5 block:

```xml
    <string name="ondemand_card_call_to_book">Call to Book</string>
    <string name="ondemand_card_details">Details</string>
    <!-- %d is how many other services also contain the probe point -->
    <plurals name="ondemand_card_more_services">
        <item quantity="one">%d more service here</item>
        <item quantity="other">%d more services here</item>
    </plurals>
    <string name="ondemand_bar_a11y_show_edge">Show nearest zone edge</string>
    <string name="ondemand_bar_a11y_zoom_out">Show whole zone</string>
    <!-- %1$s is a service name -->
    <string name="ondemand_bar_a11y_call">Call %1$s</string>
    <string name="ondemand_bar_a11y_more_services">All services here</string>
    <!-- %1$s is a number, %2$s a unit abbreviation, e.g. "300 ft" -->
    <string name="ondemand_distance">%1$s %2$s</string>
```

Append to `R/values/colors.xml` (before `</resources>`):

```xml
    <!-- DRT UI spec §5: the docked bar outside every zone, and the "Open" weight in status lines -->
    <color name="ondemand_outside">#636366</color>
    <color name="ondemand_open_green">#248a3d</color>
```

Create `R/drawable/ic_call.xml` (Material Icons `call`):

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24"
    android:tint="?attr/colorControlNormal">
    <path
        android:fillColor="@android:color/white"
        android:pathData="M6.62,10.79c1.44,2.83 3.76,5.14 6.59,6.59l2.2,-2.2c0.27,-0.27 0.67,-0.36 1.02,-0.24 1.12,0.37 2.33,0.57 3.57,0.57 0.55,0 1,0.45 1,1V20c0,0.55 -0.45,1 -1,1 -9.39,0 -17,-7.61 -17,-17 0,-0.55 0.45,-1 1,-1h3.5c0.55,0 1,0.45 1,1 0,1.25 0.2,2.45 0.57,3.57 0.11,0.35 0.03,0.74 -0.25,1.02l-2.2,2.2z" />
</vector>
```

In `M/models/OnDemandService.kt` add to `OnDemandService` after `routeColor`: `/** The route's GTFS text colour as ARGB, the bar text colour when present (spec §5). */ val routeTextColor: Int? = null,` and in `M/api/adapters/OnDemandAdapters.kt` after `routeColor = …`: `routeTextColor = routeId?.let { references.route(it)?.textColorArgb() },` (import `org.onebusaway.android.api.adapters.textColorArgb` is same-package; no import needed).

- [ ] **Step 2: Write the failing page tests**

Create `T/ui/home/ondemand/DockBarPagesTest.kt`:

```kotlin
package org.onebusaway.android.ui.home.ondemand

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.R
import org.onebusaway.android.map.OnDemandDockState
import org.onebusaway.android.map.render.ZoneEdge
import org.onebusaway.android.models.OnDemandMatchReason
import org.onebusaway.android.ondemand.ONDEMAND_OUTSIDE_GRAY
import org.onebusaway.android.ondemand.ProbePoint
import org.onebusaway.android.ondemand.ProbeSource
import org.onebusaway.android.ondemand.area
import org.onebusaway.android.ondemand.bookingRule
import org.onebusaway.android.ondemand.instant
import org.onebusaway.android.ondemand.matchFor
import org.onebusaway.android.ondemand.service
import org.onebusaway.android.util.GeoPoint

class DockBarPagesTest {

    private val now = instant("2026-03-10T14:00:00-04:00")
    private val probe = ProbePoint(GeoPoint(45.05, -85.1), ProbeSource.Rider)
    private val colors = mapOf("CC_CC1" to 0xFF78AA36.toInt(), "CC_CC2" to 0xFF3B82F6.toInt())

    private val inside = matchFor(service(areas = listOf(area(distance = 0.0)), matchReason = OnDemandMatchReason.AREA_CONTAINS_POINT), now)
    private val insideNoContact = matchFor(service(id = "CC_CC2", bookingRules = mapOf("CC_b1" to bookingRule(phone = null)), areas = listOf(area(distance = 0.0)), matchReason = OnDemandMatchReason.AREA_CONTAINS_POINT), now)
    private val outside = matchFor(service(id = "CC_CC2", areas = listOf(area(distance = 700.0, nearest = GeoPoint(45.056, -85.1))), matchReason = OnDemandMatchReason.AREA_NEARBY), now)

    @Test
    fun `an inside page takes the service colour, the inside title and the phone`() {
        val page = dockBarPages(OnDemandDockState.Bar(listOf(inside), probe), emptyMap(), colors, now, Locale.US, metric = false).single()
        assertEquals(0xFF78AA36.toInt(), page.color)
        assertEquals(0xFFFFFFFF.toInt(), page.textColor)
        assertEquals(R.string.ondemand_bar_inside, page.title?.res)
        assertEquals(TrailingAction.PHONE, page.trailing)
        assertEquals("231-582-6900", page.contact.phone)
    }

    @Test
    fun `an outside page is gray with the edge chevron, or nothing without a point`() {
        val page = dockBarPages(OnDemandDockState.Bar(listOf(outside), probe), emptyMap(), colors, now, Locale.US, metric = false).single()
        assertEquals(ONDEMAND_OUTSIDE_GRAY, page.color)
        assertEquals(R.string.ondemand_bar_outside_south, page.title?.res)
        assertEquals(TrailingAction.CHEVRON_EDGE, page.trailing)
        assertEquals(GeoPoint(45.056, -85.1), page.edgePoint)

        val noPoint = outside.copy(nearestPointOnBoundary = null)
        assertEquals(TrailingAction.NONE, trailingAction(noPoint, contactOf(noPoint.service), edgePoint = null))
        assertEquals(TrailingAction.CHEVRON_EDGE, trailingAction(noPoint, contactOf(noPoint.service), edgePoint = GeoPoint(45.0, -85.0)))
    }

    @Test
    fun `a near edge title arrives with the client geometry`() {
        val edges = mapOf("CC_CC1" to ZoneEdge(40.0, GeoPoint(45.0504, -85.1), 0.0))
        val page = dockBarPages(OnDemandDockState.Bar(listOf(inside), probe), edges, colors, now, Locale.US, metric = false).single()
        assertEquals(R.string.ondemand_bar_inside_near_edge_north, page.title?.res)
    }

    @Test
    fun `no contact means the detail chevron and no card primary`() {
        assertEquals(TrailingAction.CHEVRON_DETAIL, trailingAction(insideNoContact, contactOf(insideNoContact.service), edgePoint = null))
        assertNull(cardPrimary(insideNoContact.service))
        assertEquals(CardPrimary.Call("231-582-6900"), cardPrimary(inside.service))
        val onlineOnly = service(bookingRules = mapOf("CC_b1" to bookingRule(phone = null, bookingUrl = "https://book.example.org")))
        assertEquals(CardPrimary.Online("https://book.example.org"), cardPrimary(onlineOnly))
    }

    @Test
    fun `pages keep the stack order and the badge shows only for more than one`() {
        val pages = dockBarPages(OnDemandDockState.Bar(listOf(insideNoContact, inside), probe), emptyMap(), colors, now, Locale.US, metric = false)
        assertEquals(listOf("CC_CC2", "CC_CC1"), pages.map { it.match.service.id })
        assertTrue(showsBadge(2))
        assertFalse(showsBadge(1))
    }
}
```

- [ ] **Step 3: Run the tests to verify they fail**

Run: `./gradlew :onebusaway-android:testObaMaplibreDebugUnitTest --tests 'org.onebusaway.android.ui.home.ondemand.DockBarPagesTest'`
Expected: compilation FAILS (`Unresolved reference: dockBarPages`).

- [ ] **Step 4: Write the pure page model**

Create `M/ui/home/ondemand/DockBarPages.kt`:

```kotlin
package org.onebusaway.android.ui.home.ondemand

import java.time.Instant
import java.util.Locale
import org.onebusaway.android.map.OnDemandDockState
import org.onebusaway.android.map.render.ZoneEdge
import org.onebusaway.android.models.OnDemandService
import org.onebusaway.android.ondemand.ONDEMAND_OUTSIDE_GRAY
import org.onebusaway.android.ondemand.OnDemandMatch
import org.onebusaway.android.ondemand.TextSpec
import org.onebusaway.android.ondemand.barTitle
import org.onebusaway.android.ondemand.readableTextColor
import org.onebusaway.android.util.GeoPoint

/** What the bar's trailing button does on a page (spec §3.4 states). */
enum class TrailingAction { PHONE, URL, CHEVRON_EDGE, CHEVRON_DETAIL, NONE }

/** The first rule's pickup booking rule's contact, which the card and bar act on (spec §3.3). */
data class ServiceContact(val phone: String?, val url: String?)

fun contactOf(service: OnDemandService): ServiceContact {
    val rule = service.rules.firstOrNull()?.let(service::pickupBookingRule)
    return ServiceContact(rule?.phoneNumber, rule?.bookingUrl)
}

/** One page of the docked bar, fully decided so the composable only lays it out. */
data class DockBarPage(
    val match: OnDemandMatch,
    val color: Int,
    val textColor: Int,
    /** Null when there is nothing to say: the service name then takes the title position. */
    val title: TextSpec?,
    val trailing: TrailingAction,
    val contact: ServiceContact,
    /** Where the outside chevron pans: the server's boundary point, else the client edge. */
    val edgePoint: GeoPoint?
)

fun dockBarPages(state: OnDemandDockState.Bar, edges: Map<String, ZoneEdge>, colors: Map<String, Int>, now: Instant, locale: Locale, metric: Boolean): List<DockBarPage> = state.matches.map { match ->
    val edge = edges[match.service.id]
    val contact = contactOf(match.service)
    val edgePoint = match.nearestPointOnBoundary ?: edge?.point
    val color = if (match.isInside) colors[match.service.id] ?: ONDEMAND_OUTSIDE_GRAY else ONDEMAND_OUTSIDE_GRAY
    DockBarPage(
        match = match,
        color = color,
        // The outside gray always reads white; a route's own text colour applies only over its own colour.
        textColor = readableTextColor(color, preferred = if (match.isInside) match.service.routeTextColor else null),
        title = barTitle(match, state.probe.point, edge, now, locale, metric),
        trailing = trailingAction(match, contact, edgePoint),
        contact = contact,
        edgePoint = edgePoint
    )
}

fun trailingAction(match: OnDemandMatch, contact: ServiceContact, edgePoint: GeoPoint?): TrailingAction = when {
    !match.isInside -> if (edgePoint != null) TrailingAction.CHEVRON_EDGE else TrailingAction.NONE
    contact.phone != null -> TrailingAction.PHONE
    contact.url != null -> TrailingAction.URL
    else -> TrailingAction.CHEVRON_DETAIL
}

/** The card's primary pill: "Call to Book" with a phone, else "Book online" with a URL, else none. */
sealed interface CardPrimary {
    data class Call(val phone: String) : CardPrimary
    data class Online(val url: String) : CardPrimary
}

fun cardPrimary(service: OnDemandService): CardPrimary? {
    val contact = contactOf(service)
    return contact.phone?.let { CardPrimary.Call(it) } ?: contact.url?.let { CardPrimary.Online(it) }
}

fun showsBadge(pageCount: Int): Boolean = pageCount > 1
```

- [ ] **Step 5: Write the resolver and the composables**

Create `M/ui/home/ondemand/CopyResolvers.kt`:

```kotlin
package org.onebusaway.android.ui.home.ondemand

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import org.onebusaway.android.R
import org.onebusaway.android.ondemand.DistanceText
import org.onebusaway.android.ondemand.PluralSpec
import org.onebusaway.android.ondemand.TextSpec

/** Resolves a [TextSpec] against resources, flattening nested specs and distances. */
@Composable
fun TextSpec.resolve(): String = stringResource(res, *args.resolved())

@Composable
fun PluralSpec.resolve(): String = pluralStringResource(res, count, *args.resolved())

/** Resolves whatever a copy function returned (a [TextSpec] or a [PluralSpec]). */
@Composable
fun Any.resolveCopy(): String = when (this) {
    is TextSpec -> resolve()
    is PluralSpec -> resolve()
    is DistanceText -> stringResource(R.string.ondemand_distance, value, stringResource(unit.abbreviationRes))
    else -> toString()
}

// Nested specs and distances become strings; a number stays a number so a `%d` placeholder still formats.
@Composable
private fun Any.resolveArgument(): Any = when (this) {
    is TextSpec, is PluralSpec, is DistanceText -> resolveCopy()
    else -> this
}

@Composable
private fun List<Any>.resolved(): Array<Any> = map { it.resolveArgument() }.toTypedArray()
```

Create `M/ui/home/ondemand/OnDemandZoneCard.kt`:

```kotlin
package org.onebusaway.android.ui.home.ondemand

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import java.time.Instant
import org.onebusaway.android.R
import org.onebusaway.android.ondemand.OnDemandAvailability
import org.onebusaway.android.ondemand.OnDemandMatch
import org.onebusaway.android.ondemand.OnDemandStatus
import org.onebusaway.android.ondemand.cardMeta

/** Stable handles for the on-device tests. */
object OnDemandDockTestTags {
    const val CARD = "onDemandZoneCard"
    const val BAR = "onDemandDockBar"
    const val THUMBNAIL = "onDemandDockThumbnail"
    const val TRAILING = "onDemandDockTrailing"
    const val BADGE = "onDemandDockBadge"
}

private val CARD_RADIUS = 22.dp
private val ICON_DISC = 40.dp
private val PILL_HEIGHT = 40.dp

/**
 * The zone card (spec §3.3, screen 1): the first inside match at region level, its meta line, a
 * primary contact pill, "Details", and the "more services" footer when others also contain the point.
 */
@Composable
fun OnDemandZoneCard(
    match: OnDemandMatch,
    moreCount: Int,
    color: Int,
    now: Instant,
    onOpenDetail: () -> Unit,
    onOpenPicker: () -> Unit,
    onCall: (String) -> Unit,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val primary = cardPrimary(match.service)
    Surface(
        modifier = modifier.testTag(OnDemandDockTestTags.CARD),
        shape = RoundedCornerShape(CARD_RADIUS),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 6.dp
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenDetail),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ServiceDisc(color)
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.ondemand_card_eyebrow),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = match.service.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = metaLine(match.availability, now),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(painterResource(R.drawable.ic_navigation_chevron_right), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (primary) {
                    is CardPrimary.Call -> Button(onClick = { onCall(primary.phone) }, modifier = Modifier.weight(1.5f).height(PILL_HEIGHT)) {
                        Icon(painterResource(R.drawable.ic_call), contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.ondemand_card_call_to_book), modifier = Modifier.padding(start = 8.dp))
                    }
                    is CardPrimary.Online -> Button(onClick = { onOpenUrl(primary.url) }, modifier = Modifier.weight(1.5f).height(PILL_HEIGHT)) {
                        Icon(painterResource(R.drawable.ic_open_in_new), contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.ondemand_book_online), modifier = Modifier.padding(start = 8.dp))
                    }
                    null -> Unit
                }
                FilledTonalButton(onClick = onOpenDetail, modifier = Modifier.weight(1f).height(PILL_HEIGHT)) {
                    Text(stringResource(R.string.ondemand_card_details))
                }
            }
            if (moreCount > 0) {
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenPicker).padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = pluralStringResource(R.plurals.ondemand_card_more_services, moreCount, moreCount),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(painterResource(R.drawable.ic_navigation_chevron_right), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

/** A 40 dp disc in the service colour with the white car glyph. */
@Composable
internal fun ServiceDisc(color: Int, modifier: Modifier = Modifier) {
    Box(modifier.size(ICON_DISC).background(Color(color), CircleShape), contentAlignment = Alignment.Center) {
        Icon(painterResource(R.drawable.ic_directions_car), contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
    }
}

/** Status · booking tag, with "Open…" in the open-green weight when the service is open now (spec §2.8). */
@Composable
internal fun metaLine(availability: OnDemandAvailability, now: Instant): AnnotatedString {
    val locale = LocalLocale.current.platformLocale
    val segments = cardMeta(availability, now, locale).map { it.resolve() }
    val open = availability.status is OnDemandStatus.OpenNow
    val green = colorResource(R.color.ondemand_open_green)
    return buildAnnotatedString {
        segments.forEachIndexed { index, segment ->
            if (index > 0) append(" · ")
            if (index == 0 && open) withStyle(SpanStyle(color = green, fontWeight = FontWeight.SemiBold)) { append(segment) } else append(segment)
        }
    }
}
```

Create `M/ui/home/ondemand/OnDemandDockBar.kt`:

```kotlin
package org.onebusaway.android.ui.home.ondemand

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import org.onebusaway.android.R
import org.onebusaway.android.models.ServiceArea
import org.onebusaway.android.ondemand.ProbePoint
import org.onebusaway.android.ondemand.ProbeSource
import org.onebusaway.android.ondemand.badgeText
import org.onebusaway.android.util.GeoPoint

private val BAR_RADIUS = 20.dp
private val BAR_MIN_HEIGHT = 72.dp
private val THUMBNAIL_SIZE = 56.dp
private val THUMBNAIL_RADIUS = 12.dp
private val TRAILING_SIZE = 48.dp
private val DOT_SIZE = 7.dp
private const val ACCESSIBILITY_FONT_SCALE = 1.3f

/**
 * The docked bar (spec §3.4, A–E): one page per stack entry, paged horizontally with dots and a
 * "%1$d of %2$d" badge on the thumbnail when there is more than one. The whole bar is one adjustable
 * element for TalkBack (its value is the badge; increment/decrement move pages) with a custom
 * "All services here" action, so the long-press picker has an accessible equivalent.
 */
@Composable
fun OnDemandDockBar(
    pages: List<DockBarPage>,
    probe: ProbePoint,
    geometry: Map<String, List<ServiceArea>>,
    onPageShown: (DockBarPage) -> Unit,
    onOpenDetail: (DockBarPage) -> Unit,
    onOpenPicker: () -> Unit,
    onCall: (String) -> Unit,
    onOpenUrl: (String) -> Unit,
    onZoomOut: () -> Unit,
    onPanTo: (GeoPoint) -> Unit,
    modifier: Modifier = Modifier
) {
    if (pages.isEmpty()) return
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    LaunchedEffect(pagerState, pages) {
        snapshotFlow { pagerState.currentPage }.collect { index -> pages.getOrNull(index)?.let(onPageShown) }
    }
    val badge = badgeText(pagerState.currentPage + 1, pages.size).resolve()
    val moreServicesLabel = stringResource(R.string.ondemand_bar_a11y_more_services)
    val shapes = if (pages.all { geometry.containsKey(it.match.service.id) }) {
        pages.flatMap { page -> geometry.getValue(page.match.service.id).toThumbnailShapes(page.color) }
    } else {
        null
    }
    Column(modifier.testTag(OnDemandDockTestTags.BAR), horizontalAlignment = Alignment.CenterHorizontally) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    stateDescription = badge
                    customActions = listOf(CustomAccessibilityAction(moreServicesLabel) { onOpenPicker(); true })
                    if (pages.size > 1) {
                        setProgress { target ->
                            scope.launch { pagerState.animateScrollToPage(target.roundToInt().coerceIn(0, pages.lastIndex)) }
                            true
                        }
                    }
                }
                .then(if (pages.size > 1) Modifier.progressSemantics(pagerState.currentPage.toFloat(), 0f..pages.lastIndex.toFloat(), pages.size - 2) else Modifier)
        ) { index ->
            val page = pages[index]
            DockBarPageContent(
                page = page,
                probe = probe,
                shapes = shapes,
                badge = badge.takeIf { showsBadge(pages.size) },
                onOpenDetail = { onOpenDetail(page) },
                onOpenPicker = onOpenPicker,
                onCall = onCall,
                onOpenUrl = onOpenUrl,
                onZoomOut = onZoomOut,
                onPanTo = onPanTo
            )
        }
        if (pages.size > 1) {
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(pages.size) { index ->
                    val on = index == pagerState.currentPage
                    Box(
                        Modifier
                            .size(DOT_SIZE)
                            .background(if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant, CircleShape)
                    )
                }
            }
        }
    }
}

@Composable
private fun DockBarPageContent(
    page: DockBarPage,
    probe: ProbePoint,
    shapes: List<ThumbnailShape>?,
    badge: String?,
    onOpenDetail: () -> Unit,
    onOpenPicker: () -> Unit,
    onCall: (String) -> Unit,
    onOpenUrl: (String) -> Unit,
    onZoomOut: () -> Unit,
    onPanTo: (GeoPoint) -> Unit
) {
    val textColor = Color(page.textColor)
    val fontScale = LocalContext.current.resources.configuration.fontScale
    val zoomOutLabel = stringResource(R.string.ondemand_bar_a11y_zoom_out)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = BAR_MIN_HEIGHT)
            .combinedClickable(onClick = onOpenDetail, onLongClick = onOpenPicker),
        shape = RoundedCornerShape(BAR_RADIUS),
        color = Color(page.color),
        contentColor = textColor,
        shadowElevation = 8.dp
    ) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier
                    .size(THUMBNAIL_SIZE)
                    .clip(RoundedCornerShape(THUMBNAIL_RADIUS))
                    .border(BorderStroke(2.dp, Color.White.copy(alpha = 0.85f)), RoundedCornerShape(THUMBNAIL_RADIUS))
                    .clickable(onClick = onZoomOut)
                    .semantics { contentDescription = zoomOutLabel; role = Role.Button }
                    .testTag(OnDemandDockTestTags.THUMBNAIL)
            ) {
                if (shapes == null) {
                    Box(Modifier.fillMaxSize().background(Color(page.color)), contentAlignment = Alignment.Center) {
                        Icon(painterResource(R.drawable.ic_directions_car), contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                    }
                } else {
                    ZoneThumbnail(
                        shapes = shapes,
                        centre = probe.point,
                        probePoint = probe.point,
                        probeStyle = if (probe.source == ProbeSource.Rider) ProbeDotStyle.RIDER else ProbeDotStyle.MAP_CENTER,
                        modifier = Modifier.fillMaxSize().background(Color.White)
                    )
                }
                badge?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(3.dp)
                            .background(Color(0xE63A3A3C), RoundedCornerShape(9.dp))
                            .border(BorderStroke(1.dp, Color.White), RoundedCornerShape(9.dp))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                            .testTag(OnDemandDockTestTags.BADGE)
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                val title = page.title?.resolve()
                if (title != null) {
                    Text(page.match.service.name, style = MaterialTheme.typography.labelMedium, color = textColor.copy(alpha = 0.85f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(title, style = MaterialTheme.typography.titleMedium, maxLines = if (fontScale >= ACCESSIBILITY_FONT_SCALE) 2 else 1, overflow = TextOverflow.Ellipsis)
                } else {
                    Text(page.match.service.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            TrailingButton(page, textColor, onOpenDetail, onCall, onOpenUrl, onPanTo)
        }
    }
}

@Composable
private fun TrailingButton(page: DockBarPage, tint: Color, onOpenDetail: () -> Unit, onCall: (String) -> Unit, onOpenUrl: (String) -> Unit, onPanTo: (GeoPoint) -> Unit) {
    if (page.trailing == TrailingAction.NONE) return
    val (iconRes, label, action) = when (page.trailing) {
        TrailingAction.PHONE -> Triple(R.drawable.ic_call, stringResource(R.string.ondemand_bar_a11y_call, page.match.service.name)) { page.contact.phone?.let(onCall) }
        TrailingAction.URL -> Triple(R.drawable.ic_open_in_new, stringResource(R.string.ondemand_book_online)) { page.contact.url?.let(onOpenUrl) }
        TrailingAction.CHEVRON_EDGE -> Triple(R.drawable.ic_navigation_chevron_right, stringResource(R.string.ondemand_bar_a11y_show_edge)) { page.edgePoint?.let(onPanTo) }
        TrailingAction.CHEVRON_DETAIL -> Triple(R.drawable.ic_navigation_chevron_right, stringResource(R.string.ondemand_card_details), onOpenDetail)
        TrailingAction.NONE -> return
    }
    IconButton(
        onClick = { action() },
        modifier = Modifier
            .size(TRAILING_SIZE)
            .background(Color.White.copy(alpha = 0.2f), CircleShape)
            .testTag(OnDemandDockTestTags.TRAILING)
    ) {
        Icon(painterResource(iconRes), contentDescription = label, tint = tint)
    }
}
```

Create `M/ui/home/ondemand/OnDemandDockFeature.kt`:

```kotlin
package org.onebusaway.android.ui.home.ondemand

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import java.time.Instant
import org.onebusaway.android.map.OnDemandDockState
import org.onebusaway.android.map.render.ZoneEdge
import org.onebusaway.android.models.ServiceArea
import org.onebusaway.android.ondemand.ONDEMAND_OUTSIDE_GRAY
import org.onebusaway.android.ondemand.OnDemandMatch
import org.onebusaway.android.ondemand.ProbePoint
import org.onebusaway.android.ui.compose.unitsAreMetric
import org.onebusaway.android.util.GeoPoint

/** What the dock's surfaces can do; the host binds these to the map view model, the dialer and navigation. */
data class OnDemandDockActions(
    val openDetail: (OnDemandMatch, ProbePoint) -> Unit,
    /** [nearby] is true for the bar's long-press list (every match), false for the card's inside-only list. */
    val openPicker: (matches: List<OnDemandMatch>, probe: ProbePoint, nearby: Boolean) -> Unit,
    val call: (String) -> Unit,
    val openUrl: (String) -> Unit,
    val zoomOut: (List<OnDemandMatch>, GeoPoint) -> Unit,
    val panTo: (GeoPoint) -> Unit,
    val highlight: (String?) -> Unit
)

private const val CROSSFADE_MS = 200

/**
 * The dock slot (spec §2.4): exactly one of the zone card, the docked bar or nothing, cross-fading in
 * 200 ms. [allMatches] is the whole last probe, for the bar's long-press picker.
 */
@Composable
fun OnDemandDockFeature(
    state: OnDemandDockState,
    allMatches: List<OnDemandMatch>,
    edges: Map<String, ZoneEdge>,
    geometry: Map<String, List<ServiceArea>>,
    colors: Map<String, Int>,
    now: Instant,
    actions: OnDemandDockActions,
    modifier: Modifier = Modifier
) {
    val locale = LocalLocale.current.platformLocale
    val metric = unitsAreMetric()
    Crossfade(targetState = state, animationSpec = tween(CROSSFADE_MS), label = "onDemandDock", modifier = modifier) { shown ->
        when (shown) {
            OnDemandDockState.Hidden -> Unit
            is OnDemandDockState.Card -> {
                val first = shown.matches.first()
                OnDemandZoneCard(
                    match = first,
                    moreCount = shown.matches.size - 1,
                    color = colors[first.service.id] ?: ONDEMAND_OUTSIDE_GRAY,
                    now = now,
                    onOpenDetail = { actions.openDetail(first, shown.probe) },
                    onOpenPicker = { actions.openPicker(shown.matches, shown.probe, false) },
                    onCall = actions.call,
                    onOpenUrl = actions.openUrl
                )
            }
            is OnDemandDockState.Bar -> OnDemandDockBar(
                pages = dockBarPages(shown, edges, colors, now, locale, metric),
                probe = shown.probe,
                geometry = geometry,
                onPageShown = { actions.highlight(it.match.service.id) },
                onOpenDetail = { actions.openDetail(it.match, shown.probe) },
                onOpenPicker = { actions.openPicker(allMatches, shown.probe, true) },
                onCall = actions.call,
                onOpenUrl = actions.openUrl,
                onZoomOut = { actions.zoomOut(shown.matches, shown.probe.point) },
                onPanTo = actions.panTo
            )
        }
    }
}
```

- [ ] **Step 6: Write the on-device tests**

Create `AT/ondemand/OnDemandAndroidFixtures.kt`:

```kotlin
package org.onebusaway.android.ondemand

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import org.onebusaway.android.models.AvailabilityRule
import org.onebusaway.android.models.BookingRule
import org.onebusaway.android.models.BookingType
import org.onebusaway.android.models.FlexCalendar
import org.onebusaway.android.models.OnDemandMatchReason
import org.onebusaway.android.models.OnDemandService
import org.onebusaway.android.models.OnDemandServiceKind
import org.onebusaway.android.models.ServiceArea
import org.onebusaway.android.models.ServiceDayTime
import org.onebusaway.android.util.GeoPoint

/** The JVM fixtures in `src/test` are not visible here; this is the subset the Compose tests need. */
internal val TUESDAY_TWO_PM: Instant = OffsetDateTime.parse("2026-03-10T14:00:00-04:00").toInstant()

internal val SQUARE = listOf(GeoPoint(45.0, -85.2), GeoPoint(45.0, -85.0), GeoPoint(45.1, -85.0), GeoPoint(45.1, -85.2), GeoPoint(45.0, -85.2))

internal fun sampleService(id: String = "CC_CC1", name: String = "Dial-a-Ride", phone: String? = "231-582-6900", url: String? = null, inside: Boolean = true): OnDemandService {
    val calendar = FlexCalendar("CC_cal", DayOfWeek.entries.filter { it != DayOfWeek.SUNDAY }.toSet(), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), emptySet())
    val booking = BookingRule("CC_b1", BookingType.SAME_DAY, 60, null, null, null, null, null, null, null, null, null, phone, null, url)
    val rule = AvailabilityRule(listOf("CC_area"), listOf("CC_area"), ServiceDayTime.parse("07:20:00"), ServiceDayTime.parse("16:40:00"), null, listOf("CC_cal"), 2, 2, "CC_b1", "CC_b1", null, null)
    val area = ServiceArea("CC_area", "Charlevoix County", null, GeoPoint(45.0, -85.2), GeoPoint(45.1, -85.0), listOf(listOf(SQUARE)), if (inside) 0.0 else 700.0, if (inside) null else GeoPoint(45.056, -85.1))
    return OnDemandService(
        id = id, agencyId = "CC", routeId = id, name = name, kind = OnDemandServiceKind.ZONE, rules = listOf(rule),
        matchReason = if (inside) OnDemandMatchReason.AREA_CONTAINS_POINT else OnDemandMatchReason.AREA_NEARBY,
        areas = listOf(area), bookingRules = mapOf("CC_b1" to booking), calendars = mapOf("CC_cal" to calendar), agencyTimezone = "America/Detroit"
    )
}

internal fun sampleMatch(service: OnDemandService = sampleService()): OnDemandMatch = matchFor(service, TUESDAY_TWO_PM)
```

Create `AT/ui/home/ondemand/OnDemandZoneCardTest.kt`:

```kotlin
package org.onebusaway.android.ui.home.ondemand

import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.onebusaway.android.R
import org.onebusaway.android.ondemand.TUESDAY_TWO_PM
import org.onebusaway.android.ondemand.sampleMatch
import org.onebusaway.android.ondemand.sampleService
import org.onebusaway.android.ui.compose.createUnconfinedComposeRule
import org.onebusaway.android.ui.compose.theme.ObaTheme

class OnDemandZoneCardTest {

    @get:Rule
    val composeRule = createUnconfinedComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun render(phone: String? = "231-582-6900", url: String? = null, moreCount: Int = 0, onCall: (String) -> Unit = {}, onPicker: () -> Unit = {}) {
        composeRule.setContent {
            ObaTheme {
                OnDemandZoneCard(
                    match = sampleMatch(sampleService(phone = phone, url = url)),
                    moreCount = moreCount,
                    color = 0xFF78AA36.toInt(),
                    now = TUESDAY_TWO_PM,
                    onOpenDetail = {},
                    onOpenPicker = onPicker,
                    onCall = onCall,
                    onOpenUrl = {}
                )
            }
        }
    }

    @Test
    fun aPhoneMakesTheCallToBookPrimary() {
        var called: String? = null
        render(onCall = { called = it })
        composeRule.onNodeWithText(context.getString(R.string.ondemand_card_call_to_book)).performClick()
        assertEquals("231-582-6900", called)
        composeRule.onNodeWithText(context.getString(R.string.ondemand_card_eyebrow)).assertIsDisplayed()
    }

    @Test
    fun aUrlAloneMakesBookOnlinePrimaryAndNoContactLeavesOnlyDetails() {
        render(phone = null, url = "https://book.example.org")
        composeRule.onNodeWithText(context.getString(R.string.ondemand_book_online)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.ondemand_card_call_to_book)).assertDoesNotExist()
    }

    @Test
    fun theFooterCountsTheOtherServicesAndOpensThePicker() {
        var opened = 0
        render(moreCount = 2, onPicker = { opened++ })
        val footer = context.resources.getQuantityString(R.plurals.ondemand_card_more_services, 2, 2)
        composeRule.onNodeWithText(footer).performClick()
        assertEquals(1, opened)
    }
}
```

Create `AT/ui/home/ondemand/OnDemandDockBarTest.kt`:

```kotlin
package org.onebusaway.android.ui.home.ondemand

import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.onebusaway.android.R
import org.onebusaway.android.map.OnDemandDockState
import org.onebusaway.android.ondemand.OnDemandMatch
import org.onebusaway.android.ondemand.ProbePoint
import org.onebusaway.android.ondemand.ProbeSource
import org.onebusaway.android.ondemand.TUESDAY_TWO_PM
import org.onebusaway.android.ondemand.sampleMatch
import org.onebusaway.android.ondemand.sampleService
import org.onebusaway.android.ui.compose.createUnconfinedComposeRule
import org.onebusaway.android.ui.compose.theme.ObaTheme
import org.onebusaway.android.util.GeoPoint

class OnDemandDockBarTest {

    @get:Rule
    val composeRule = createUnconfinedComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val probe = ProbePoint(GeoPoint(45.05, -85.1), ProbeSource.Rider)

    private fun render(vararg matches: OnDemandMatch, onCall: (String) -> Unit = {}, onZoomOut: () -> Unit = {}) {
        val state = OnDemandDockState.Bar(matches.toList(), probe)
        val colors = matches.associate { it.service.id to 0xFF78AA36.toInt() }
        composeRule.setContent {
            ObaTheme {
                OnDemandDockBar(
                    pages = dockBarPages(state, emptyMap(), colors, TUESDAY_TWO_PM, Locale.US, metric = false),
                    probe = probe,
                    geometry = emptyMap(),
                    onPageShown = {},
                    onOpenDetail = {},
                    onOpenPicker = {},
                    onCall = onCall,
                    onOpenUrl = {},
                    onZoomOut = onZoomOut,
                    onPanTo = {}
                )
            }
        }
    }

    @Test
    fun anInsidePageShowsTheInsideTitleAndCallsTheService() {
        var called: String? = null
        render(sampleMatch(), onCall = { called = it })
        composeRule.onNodeWithText(context.getString(R.string.ondemand_bar_inside)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(context.getString(R.string.ondemand_bar_a11y_call, "Dial-a-Ride")).performClick()
        assertEquals("231-582-6900", called)
        composeRule.onNodeWithTag(OnDemandDockTestTags.BADGE).assertDoesNotExist()
    }

    @Test
    fun theThumbnailIsLabelledAndZoomsOut() {
        var zoomed = 0
        render(sampleMatch(), onZoomOut = { zoomed++ })
        composeRule.onNodeWithContentDescription(context.getString(R.string.ondemand_bar_a11y_zoom_out)).performClick()
        assertEquals(1, zoomed)
    }

    @Test
    fun twoPagesShowTheBadgeAndAnOutsidePageShowsTheEdgeChevron() {
        render(sampleMatch(), sampleMatch(sampleService(id = "CC_CC2", name = "Medical Trips", inside = false)))
        composeRule.onNodeWithTag(OnDemandDockTestTags.BADGE).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.ondemand_bar_badge, 1, 2)).assertIsDisplayed()
    }
}
```

- [ ] **Step 7: Run the JVM test, compile every source set, commit**

Run: `./gradlew :onebusaway-android:testObaMaplibreDebugUnitTest --tests 'org.onebusaway.android.ui.home.ondemand.DockBarPagesTest' --tests 'org.onebusaway.android.api.adapters.OnDemandAdaptersTest'`
Expected: PASS.
Run: `./gradlew :onebusaway-android:compileObaGoogleDebugKotlin :onebusaway-android:compileObaMaplibreDebugKotlin :onebusaway-android:compileObaMaplibreDebugAndroidTestKotlin -PwarningsAsErrors=true`
Expected: BUILD SUCCESSFUL.

```bash
./gradlew spotlessApply
git add onebusaway-android/src/main onebusaway-android/src/test onebusaway-android/src/androidTest
git commit -m "Add the on-demand zone card and docked bar" -m "The card shows the top inside service at region level with its contact
pill and a doorway to the picker; the bar is a paged stack at street
level with the thumbnail, a per-state title and trailing action, page
dots and a badge, and one adjustable TalkBack element with an
\"All services here\" action standing in for the long press."
```

---

### Task 12: Overlap picker sheet, locality lookup and the sheets view model

**Files:**
- Create: `M/ondemand/LocalityResolver.kt`
- Modify: `M/app/di/RepositoryModule.kt` (one `@Binds`)
- Create: `M/ui/home/ondemand/OnDemandSheetsViewModel.kt`, `M/ui/home/ondemand/PickerCopy.kt`, `M/ui/home/ondemand/OnDemandPickerSheet.kt`
- Modify: `R/values/strings.xml`
- Test: `T/ui/home/ondemand/OnDemandSheetsViewModelTest.kt`, `T/ui/home/ondemand/PickerCopyTest.kt`

**Interfaces:**
- Consumes: `sortedSoonestUsable`, `OnDemandMatch`, `ProbePoint` (Task 3); `pickerSubtitle`, `statusText`, `tagText`, `bookingTag`, `zoneCount`, `TextSpec`, `PluralSpec` (Task 5); `resolve()`, `resolveCopy()`, `ServiceDisc`, `OnDemandDockTestTags` (Task 11); `SheetDragHandle`; `runCatchingCancellable`; `TimeProvider`.
- Produces: `interface LocalityResolver { suspend fun locality(point: GeoPoint): String? }`; `class DefaultLocalityResolver`; `const val LOCALITY_TIMEOUT_MS = 1_000L`; `data class PickerRequest(matches: List<OnDemandMatch>, probe: ProbePoint, nearby: Boolean, locality: String?)`; `@HiltViewModel class OnDemandSheetsViewModel(localityResolver, timeProvider) { val picker: StateFlow<PickerRequest?>; fun openPicker(matches, probe, nearby); fun closePicker(); fun now(): Instant }`; `fun pickerAreasText(service: OnDemandService): Any`; `@Composable fun OnDemandPickerSheet(request: PickerRequest, colors: Map<String, Int>, now: Instant, onSelect: (OnDemandMatch) -> Unit, onDismiss: () -> Unit)`.

- [ ] **Step 1: Strings**

Append to `R/values/strings.xml` after the Task 11 block:

```xml
    <!-- Overlap picker (DRT UI spec §3.5); %d is a count of services -->
    <plurals name="ondemand_picker_title">
        <item quantity="one">%d service here</item>
        <item quantity="other">%d services here</item>
    </plurals>
    <plurals name="ondemand_picker_title_nearby">
        <item quantity="one">%d service nearby</item>
        <item quantity="other">%d services nearby</item>
    </plurals>
    <string name="ondemand_picker_footer">Sorted by soonest available.</string>
```

- [ ] **Step 2: Write the failing tests**

Create `T/ui/home/ondemand/PickerCopyTest.kt`:

```kotlin
package org.onebusaway.android.ui.home.ondemand

import org.junit.Assert.assertEquals
import org.junit.Test
import org.onebusaway.android.R
import org.onebusaway.android.ondemand.PluralSpec
import org.onebusaway.android.ondemand.area
import org.onebusaway.android.ondemand.service

class PickerCopyTest {

    @Test
    fun `named areas are joined and unnamed ones fall back to a zone count`() {
        val named = service(areas = listOf(area(id = "a", name = "Boyne City"), area(id = "b", name = null), area(id = "c", name = "Petoskey")))
        assertEquals("Boyne City, Petoskey", pickerAreasText(named))
        val unnamed = service(areas = listOf(area(id = "a", name = null), area(id = "b", name = null)))
        assertEquals(PluralSpec(R.plurals.ondemand_detail_zone_count, 2, listOf(2)), pickerAreasText(unnamed))
    }
}
```

Create `T/ui/home/ondemand/OnDemandSheetsViewModelTest.kt`:

```kotlin
package org.onebusaway.android.ui.home.ondemand

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.onebusaway.android.models.OnDemandMatchReason
import org.onebusaway.android.ondemand.LocalityResolver
import org.onebusaway.android.ondemand.ProbePoint
import org.onebusaway.android.ondemand.ProbeSource
import org.onebusaway.android.ondemand.TIER_ADVANCE
import org.onebusaway.android.ondemand.area
import org.onebusaway.android.ondemand.instant
import org.onebusaway.android.ondemand.matchFor
import org.onebusaway.android.ondemand.service
import org.onebusaway.android.testing.MainDispatcherRule
import org.onebusaway.android.util.GeoPoint
import org.onebusaway.android.util.TimeProvider

@OptIn(ExperimentalCoroutinesApi::class)
class OnDemandSheetsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(UnconfinedTestDispatcher())

    private class GatedResolver : LocalityResolver {
        val answer = CompletableDeferred<String?>()
        override suspend fun locality(point: GeoPoint): String? = answer.await()
    }

    private val now = instant("2026-03-10T14:00:00-04:00")
    private val probe = ProbePoint(GeoPoint(45.05, -85.1), ProbeSource.Rider)
    private val open = matchFor(service(id = "open", areas = listOf(area(distance = 0.0)), matchReason = OnDemandMatchReason.AREA_CONTAINS_POINT), now)
    private val advance = open.copy(service = open.service.copy(id = "advance"), availability = open.availability.copy(usabilityTier = TIER_ADVANCE))

    @Test
    fun `the picker opens sorted at once and gains its locality when the geocoder answers`() = runTest {
        val resolver = GatedResolver()
        val vm = OnDemandSheetsViewModel(resolver, TimeProvider { now.toEpochMilli() })
        vm.openPicker(listOf(advance, open), probe, nearby = true)
        val request = requireNotNull(vm.picker.value)
        assertEquals(listOf("open", "advance"), request.matches.map { it.service.id })
        assertNull(request.locality)
        assertEquals(true, request.nearby)

        resolver.answer.complete("Boyne City")
        assertEquals("Boyne City", vm.picker.value?.locality)
        assertEquals(now, vm.now())
    }

    @Test
    fun `a late locality for a closed picker is dropped`() = runTest {
        val resolver = GatedResolver()
        val vm = OnDemandSheetsViewModel(resolver, TimeProvider { now.toEpochMilli() })
        vm.openPicker(listOf(open), probe, nearby = false)
        vm.closePicker()
        resolver.answer.complete("Boyne City")
        assertNull(vm.picker.value)
    }
}
```

- [ ] **Step 3: Run the tests to verify they fail**

Run: `./gradlew :onebusaway-android:testObaMaplibreDebugUnitTest --tests 'org.onebusaway.android.ui.home.ondemand.PickerCopyTest' --tests 'org.onebusaway.android.ui.home.ondemand.OnDemandSheetsViewModelTest'`
Expected: compilation FAILS (`Unresolved reference: OnDemandSheetsViewModel`).

- [ ] **Step 4: Locality resolver and its binding**

Create `M/ondemand/LocalityResolver.kt`:

```kotlin
package org.onebusaway.android.ondemand

import android.content.Context
import android.location.Geocoder
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.onebusaway.android.util.GeoPoint
import org.onebusaway.android.util.runCatchingCancellable

/** Spec §2.8: the locality is used only when it arrives within a second; otherwise the copy omits it. */
const val LOCALITY_TIMEOUT_MS = 1_000L

/** The reverse-geocoded locality ("Boyne City") of a point, or null when unknown or too slow. */
interface LocalityResolver {
    suspend fun locality(point: GeoPoint): String?
}

/** The on-device [Geocoder], on the IO thread, capped at [LOCALITY_TIMEOUT_MS]. Never throws. */
class DefaultLocalityResolver @Inject constructor(
    @param:ApplicationContext private val context: Context
) : LocalityResolver {

    override suspend fun locality(point: GeoPoint): String? = withTimeoutOrNull(LOCALITY_TIMEOUT_MS) {
        withContext(Dispatchers.IO) {
            runCatchingCancellable {
                if (!Geocoder.isPresent()) return@runCatchingCancellable null
                // Sync getFromLocation is deprecated in API 33; its replacement needs API 33 while minSdk
                // is 23 — the same degraded path DefaultGeocodeRepository.platformReverse takes.
                @Suppress("DEPRECATION")
                val address = Geocoder(context).getFromLocation(point.latitude, point.longitude, 1)?.firstOrNull()
                address?.locality ?: address?.subAdminArea
            }.getOrNull()?.takeIf { it.isNotBlank() }
        }
    }
}
```

In `M/app/di/RepositoryModule.kt` add beside `bindGeocodeRepository`:

```kotlin
    @Binds
    abstract fun bindLocalityResolver(impl: DefaultLocalityResolver): LocalityResolver
```

with imports `org.onebusaway.android.ondemand.DefaultLocalityResolver` and `org.onebusaway.android.ondemand.LocalityResolver`.

- [ ] **Step 5: The view model and the picker copy**

Create `M/ui/home/ondemand/OnDemandSheetsViewModel.kt`:

```kotlin
package org.onebusaway.android.ui.home.ondemand

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.onebusaway.android.ondemand.LocalityResolver
import org.onebusaway.android.ondemand.OnDemandMatch
import org.onebusaway.android.ondemand.ProbePoint
import org.onebusaway.android.ondemand.sortedSoonestUsable
import org.onebusaway.android.util.TimeProvider

/** What the overlap picker shows (spec §3.5): the list, where it was probed, and the locality once known. */
data class PickerRequest(
    val matches: List<OnDemandMatch>,
    val probe: ProbePoint,
    /** True for the bar's long-press list (every match); false for the inside-only list. */
    val nearby: Boolean,
    val locality: String?
)

/**
 * The home screen's on-demand sheets: the overlap picker (and, from the planner, the fallback sheet).
 * Scoped to the HOME nav entry through `hiltViewModel()`, like `MapChromeViewModel`.
 */
@HiltViewModel
class OnDemandSheetsViewModel @Inject constructor(
    private val localityResolver: LocalityResolver,
    private val timeProvider: TimeProvider
) : ViewModel() {

    private val _picker = MutableStateFlow<PickerRequest?>(null)
    val picker: StateFlow<PickerRequest?> = _picker.asStateFlow()

    /** Open the picker sorted by §2.6 at once; the locality follows when the geocoder answers in time. */
    fun openPicker(matches: List<OnDemandMatch>, probe: ProbePoint, nearby: Boolean) {
        val request = PickerRequest(matches.sortedSoonestUsable(), probe, nearby, locality = null)
        _picker.value = request
        viewModelScope.launch {
            val locality = localityResolver.locality(probe.point) ?: return@launch
            // Only the request still open for this probe takes the answer; a closed or replaced one ignores it.
            _picker.update { current -> if (current != null && current.probe == probe && current.locality == null) current.copy(locality = locality) else current }
        }
    }

    fun closePicker() {
        _picker.value = null
    }

    /** The device clock, minted here so the sheet's copy is evaluated at open time. */
    fun now(): Instant = Instant.ofEpochMilli(timeProvider.now())
}
```

Create `M/ui/home/ondemand/PickerCopy.kt`:

```kotlin
package org.onebusaway.android.ui.home.ondemand

import org.onebusaway.android.models.OnDemandService
import org.onebusaway.android.ondemand.zoneCount

/** The picker row's area text (spec §2.8): named areas joined with ", ", else the zone count plural. */
fun pickerAreasText(service: OnDemandService): Any {
    val names = service.areas.mapNotNull { it.name?.takeIf { name -> name.isNotBlank() } }
    return if (names.isEmpty()) zoneCount(service.areas.size) else names.joinToString(", ")
}
```

- [ ] **Step 6: The sheet composable**

Create `M/ui/home/ondemand/OnDemandPickerSheet.kt`:

```kotlin
package org.onebusaway.android.ui.home.ondemand

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import java.time.Instant
import org.onebusaway.android.R
import org.onebusaway.android.ondemand.ONDEMAND_OUTSIDE_GRAY
import org.onebusaway.android.ondemand.OnDemandMatch
import org.onebusaway.android.ondemand.OnDemandStatus
import org.onebusaway.android.ondemand.bookingTag
import org.onebusaway.android.ondemand.pickerSubtitle
import org.onebusaway.android.ondemand.statusText
import org.onebusaway.android.ondemand.tagText
import org.onebusaway.android.ui.compose.components.SheetDragHandle
import org.onebusaway.android.ui.icons.AppIcons

private val LIST_RADIUS = 18.dp

/**
 * The overlap picker (spec §3.5, screen 2): every service at the probe point in §2.6 order, one row
 * each with its status line and its areas · booking tag. Tapping a row reports the match; the host
 * highlights it and pushes the detail page.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnDemandPickerSheet(
    request: PickerRequest,
    colors: Map<String, Int>,
    now: Instant,
    onSelect: (OnDemandMatch) -> Unit,
    onDismiss: () -> Unit
) {
    val count = request.matches.size
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        dragHandle = { SheetDragHandle() },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = if (request.nearby) pluralStringResource(R.plurals.ondemand_picker_title_nearby, count, count) else pluralStringResource(R.plurals.ondemand_picker_title, count, count),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = pickerSubtitle(request.probe.source, request.locality).resolve(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(AppIcons.Close, contentDescription = stringResource(R.string.dismiss))
                }
            }
            Surface(shape = RoundedCornerShape(LIST_RADIUS), color = MaterialTheme.colorScheme.surface) {
                Column {
                    request.matches.forEachIndexed { index, match ->
                        if (index > 0) HorizontalDivider(Modifier.padding(start = 68.dp))
                        PickerRow(match, colors[match.service.id] ?: ONDEMAND_OUTSIDE_GRAY, now) { onSelect(match) }
                    }
                }
            }
            Text(
                text = stringResource(R.string.ondemand_picker_footer),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PickerRow(match: OnDemandMatch, color: Int, now: Instant, onClick: () -> Unit) {
    val locale = LocalLocale.current.platformLocale
    val availability = match.availability
    val status = statusText(availability.status, availability.zone, now, locale)?.resolve()
    val green = colorResource(R.color.ondemand_open_green)
    val areas = pickerAreasText(match.service).resolveCopy()
    val tag = bookingTag(availability.tags)?.let { tagText(it).resolve() }
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ServiceDisc(color)
        Column(Modifier.weight(1f)) {
            Text(match.service.name, style = MaterialTheme.typography.titleMedium)
            if (status != null) {
                Text(
                    text = buildAnnotatedString {
                        if (availability.status is OnDemandStatus.OpenNow) withStyle(SpanStyle(color = green, fontWeight = FontWeight.SemiBold)) { append(status) } else append(status)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = listOfNotNull(areas, tag).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(painterResource(R.drawable.ic_navigation_chevron_right), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
```

- [ ] **Step 7: Run the tests and the gates, commit**

Run: `./gradlew :onebusaway-android:testObaMaplibreDebugUnitTest --tests 'org.onebusaway.android.ui.home.ondemand.PickerCopyTest' --tests 'org.onebusaway.android.ui.home.ondemand.OnDemandSheetsViewModelTest'`
Expected: PASS.
Run: `./gradlew :onebusaway-android:compileObaGoogleDebugKotlin :onebusaway-android:compileObaMaplibreDebugKotlin -PwarningsAsErrors=true`
Expected: BUILD SUCCESSFUL.

```bash
./gradlew spotlessApply
git add onebusaway-android/src/main onebusaway-android/src/test
git commit -m "Add the on-demand overlap picker sheet" -m "The picker lists every service at the probe point in the one sort
order, each row leading with the fact that decides whether the rider
can use it. The locality in its subtitle comes from the on-device
geocoder only when it answers within a second."
```

---

### Task 13: Host the dock and the picker on the home screen

**Files:**
- Create: `M/ondemand/LocationCheckArgs.kt`, `M/ui/home/ondemand/OnDemandDockHost.kt`
- Modify: `M/ui/nav/NavRoutes.kt:190-195`, `M/ui/ondemand/OnDemandDestinations.kt:34-37`, `M/ui/home/HomeNavHost.kt:261`, `M/ui/home/HomeScreen.kt:190, ~604, ~876-896, ~1155`, `M/ui/home/HomeSheetLogic.kt` (append)
- Test: `T/ondemand/LocationCheckArgsTest.kt`, `T/ui/home/HomeSheetLogicTest.kt` (append)

**Interfaces:**
- Consumes: `MapViewModel.{onDemandDock, onDemandProbeResult, onDemandEdges, onDemandGeometry, onDemandColors, setOnDemandDockSuppressed, highlightOnDemandService, zoomOutToOnDemandZones, panToOnDemandEdge}` (Task 9); `OnDemandDockFeature`, `OnDemandDockActions` (Task 11); `OnDemandSheetsViewModel`, `PickerRequest`, `OnDemandPickerSheet` (Task 12); `LocationCheck`, `ProbeSource` (Task 3); `SurveyUiState.heroQuestion/sheet`; `ExternalIntents.goToPhoneDialer/goToUrl`.
- Produces: `fun ProbeSource.toRouteValue(): String`; `fun probeSourceFromRoute(value: String?): ProbeSource?`; `fun locationCheckFromArgs(inside: String?, source: String?, locality: String?, lat: String?, lon: String?): LocationCheck?`; `NavRoutes.ARG_ONDEMAND_INSIDE/SOURCE/LOCALITY/LAT/LON`; `NavRoutes.onDemandService(serviceId: String, check: LocationCheck? = null)`; `HomeCallbacks.onOpenOnDemandServiceAt: (serviceId: String, check: LocationCheck) -> Unit`; `internal fun dockCoveredBySheet(sheetShown: Boolean, expanded: Boolean, peekPx: Int, windowHeightPx: Int): Boolean`; `@Composable fun BoxScope.OnDemandDockOverlay(mapViewModel, sheetsViewModel, bottomInset: Dp, onOpenDetail: (String, LocationCheck) -> Unit, onHeight: (Int) -> Unit)`.

- [ ] **Step 1: Write the failing tests**

Create `T/ondemand/LocationCheckArgsTest.kt`:

```kotlin
package org.onebusaway.android.ondemand

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.onebusaway.android.util.GeoPoint

class LocationCheckArgsTest {

    @Test
    fun `sources round-trip through their route values`() {
        for (source in listOf(ProbeSource.Rider, ProbeSource.MapCenter, ProbeSource.Point(null))) {
            assertEquals(source, probeSourceFromRoute(source.toRouteValue()))
        }
        assertNull(probeSourceFromRoute("elsewhere"))
        assertNull(probeSourceFromRoute(null))
    }

    @Test
    fun `a check is rebuilt only when both inside and source are present`() {
        assertEquals(
            LocationCheck(ProbeSource.Rider, true, "Boyne City", GeoPoint(45.05, -85.1)),
            locationCheckFromArgs("true", "rider", "Boyne City", "45.05", "-85.1")
        )
        assertEquals(LocationCheck(ProbeSource.MapCenter, false, null, null), locationCheckFromArgs("false", "center", null, null, null))
        assertEquals(null, locationCheckFromArgs("true", "point", null, "45.05", null)?.point)
        assertNull(locationCheckFromArgs(null, "rider", null, null, null))
        assertNull(locationCheckFromArgs("true", null, null, null, null))
    }
}
```

Append to `T/ui/home/HomeSheetLogicTest.kt` (inside the class):

```kotlin
    // --- dockCoveredBySheet ---

    @Test
    fun `the dock is covered by an expanded sheet or a peek over half the window`() {
        assertFalse(dockCoveredBySheet(sheetShown = false, expanded = true, peekPx = 900, windowHeightPx = 1000))
        assertTrue(dockCoveredBySheet(sheetShown = true, expanded = true, peekPx = 100, windowHeightPx = 1000))
        assertTrue(dockCoveredBySheet(sheetShown = true, expanded = false, peekPx = 501, windowHeightPx = 1000))
        assertFalse(dockCoveredBySheet(sheetShown = true, expanded = false, peekPx = 500, windowHeightPx = 1000))
    }
```

(add `import org.junit.Assert.assertFalse` / `assertTrue` if the file lacks them).

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :onebusaway-android:testObaMaplibreDebugUnitTest --tests 'org.onebusaway.android.ondemand.LocationCheckArgsTest' --tests 'org.onebusaway.android.ui.home.HomeSheetLogicTest'`
Expected: compilation FAILS (`Unresolved reference: probeSourceFromRoute`, `dockCoveredBySheet`).

- [ ] **Step 3: Route arguments and the gate**

Create `M/ondemand/LocationCheckArgs.kt`:

```kotlin
package org.onebusaway.android.ondemand

import org.onebusaway.android.util.geoPointOrNull

private const val ROUTE_RIDER = "rider"
private const val ROUTE_CENTER = "center"
private const val ROUTE_POINT = "point"

/** The probe source as a navigation argument; the point's label is not carried (the page never shows it). */
fun ProbeSource.toRouteValue(): String = when (this) {
    ProbeSource.Rider -> ROUTE_RIDER
    ProbeSource.MapCenter -> ROUTE_CENTER
    is ProbeSource.Point -> ROUTE_POINT
}

fun probeSourceFromRoute(value: String?): ProbeSource? = when (value) {
    ROUTE_RIDER -> ProbeSource.Rider
    ROUTE_CENTER -> ProbeSource.MapCenter
    ROUTE_POINT -> ProbeSource.Point(null)
    else -> null
}

/** The detail page's location check from its optional route arguments; null unless both facts arrived. */
fun locationCheckFromArgs(inside: String?, source: String?, locality: String?, lat: String?, lon: String?): LocationCheck? {
    val isInside = inside?.toBooleanStrictOrNull() ?: return null
    val probeSource = probeSourceFromRoute(source) ?: return null
    val point = geoPointOrNull(lat?.toDoubleOrNull(), lon?.toDoubleOrNull())
    return LocationCheck(probeSource, isInside, locality?.takeIf { it.isNotBlank() }, point)
}
```

In `M/ui/nav/NavRoutes.kt` replace the on-demand block with:

```kotlin
    // --- On-demand (GTFS-Flex) service page ---
    const val ARG_ONDEMAND_SERVICE_ID = "onDemandServiceId"
    const val ARG_ONDEMAND_INSIDE = "inside"
    const val ARG_ONDEMAND_SOURCE = "source"
    const val ARG_ONDEMAND_LOCALITY = "locality"
    const val ARG_ONDEMAND_LAT = "lat"
    const val ARG_ONDEMAND_LON = "lon"
    const val ONDEMAND_SERVICE = "onDemandService/{$ARG_ONDEMAND_SERVICE_ID}?$ARG_ONDEMAND_INSIDE={$ARG_ONDEMAND_INSIDE}&$ARG_ONDEMAND_SOURCE={$ARG_ONDEMAND_SOURCE}&$ARG_ONDEMAND_LOCALITY={$ARG_ONDEMAND_LOCALITY}&$ARG_ONDEMAND_LAT={$ARG_ONDEMAND_LAT}&$ARG_ONDEMAND_LON={$ARG_ONDEMAND_LON}"

    /**
     * Builds a navigable [ONDEMAND_SERVICE] route; service ids equal route ids and may contain `/`.
     * [check] adds the probe's location facts (spec §3.6 item 3); without it the page omits the row.
     */
    fun onDemandService(serviceId: String, check: LocationCheck? = null): String {
        val base = "onDemandService/${Uri.encode(serviceId)}"
        if (check == null) return base
        val query = listOfNotNull(
            "$ARG_ONDEMAND_INSIDE=${check.isInside}",
            "$ARG_ONDEMAND_SOURCE=${check.source.toRouteValue()}",
            check.locality?.let { "$ARG_ONDEMAND_LOCALITY=${Uri.encode(it)}" },
            check.point?.let { "$ARG_ONDEMAND_LAT=${it.latitude}" },
            check.point?.let { "$ARG_ONDEMAND_LON=${it.longitude}" }
        ).joinToString("&")
        return "$base?$query"
    }
```

with imports `org.onebusaway.android.ondemand.LocationCheck` and `org.onebusaway.android.ondemand.toRouteValue`. In `M/ui/ondemand/OnDemandDestinations.kt` replace the `arguments = listOf(...)` with:

```kotlin
        arguments = listOf(
            navArgument(NavRoutes.ARG_ONDEMAND_SERVICE_ID) { type = NavType.StringType },
            navArgument(NavRoutes.ARG_ONDEMAND_INSIDE) { type = NavType.StringType; nullable = true; defaultValue = null },
            navArgument(NavRoutes.ARG_ONDEMAND_SOURCE) { type = NavType.StringType; nullable = true; defaultValue = null },
            navArgument(NavRoutes.ARG_ONDEMAND_LOCALITY) { type = NavType.StringType; nullable = true; defaultValue = null },
            navArgument(NavRoutes.ARG_ONDEMAND_LAT) { type = NavType.StringType; nullable = true; defaultValue = null },
            navArgument(NavRoutes.ARG_ONDEMAND_LON) { type = NavType.StringType; nullable = true; defaultValue = null }
        )
```

Append to `M/ui/home/HomeSheetLogic.kt`:

```kotlin
/**
 * Spec §2.4: the on-demand dock shows nothing when the visible map area is under half the screen
 * because of the sheet — an expanded sheet, or a collapsed peek taller than half the window.
 */
internal fun dockCoveredBySheet(sheetShown: Boolean, expanded: Boolean, peekPx: Int, windowHeightPx: Int): Boolean = sheetShown && (expanded || peekPx * 2 > windowHeightPx)
```

- [ ] **Step 4: The overlay host**

Create `M/ui/home/ondemand/OnDemandDockHost.kt`:

```kotlin
package org.onebusaway.android.ui.home.ondemand

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.onebusaway.android.map.MapViewModel
import org.onebusaway.android.map.OnDemandDockState
import org.onebusaway.android.ondemand.LocationCheck
import org.onebusaway.android.util.ExternalIntents

/** Spec §2.4: 16 dp side gutters, 12 dp above the sheet edge, 360 dp maximum in landscape. */
private val DOCK_GUTTER = 16.dp
private val DOCK_SHEET_GAP = 12.dp
private val DOCK_LANDSCAPE_MAX_WIDTH = 360.dp

/**
 * The dock slot on the home map: the zone card or the docked bar from [MapViewModel.onDemandDock],
 * bottom-centre above the sheet edge (bottom-start, 360 dp wide, in landscape), reporting its
 * measured height through [onHeight] so the FAB stack lifts clear of it. Also hosts the overlap
 * picker the card footer and the bar long-press open.
 */
@Composable
fun BoxScope.OnDemandDockOverlay(
    mapViewModel: MapViewModel,
    sheetsViewModel: OnDemandSheetsViewModel,
    bottomInset: Dp,
    onOpenDetail: (serviceId: String, check: LocationCheck) -> Unit,
    onHeight: (Int) -> Unit
) {
    val context = LocalContext.current
    val state by mapViewModel.onDemandDock.collectAsStateWithLifecycle()
    val probeResult by mapViewModel.onDemandProbeResult.collectAsStateWithLifecycle()
    val edges by mapViewModel.onDemandEdges.collectAsStateWithLifecycle()
    val geometry by mapViewModel.onDemandGeometry.collectAsStateWithLifecycle()
    val colors by mapViewModel.onDemandColors.collectAsStateWithLifecycle()
    val picker by sheetsViewModel.picker.collectAsStateWithLifecycle()
    val windowSize = LocalWindowInfo.current.containerSize
    val landscape = windowSize.width > windowSize.height
    // Spec §2.3: the highlight a picker row or a bar page set clears when the detail page closes — on
    // Android that is this screen resuming; the next swipe re-highlights its page.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { mapViewModel.highlightOnDemandService(null) }
    val actions = remember(mapViewModel, sheetsViewModel, context) {
        OnDemandDockActions(
            openDetail = { match, probe -> onOpenDetail(match.service.id, LocationCheck(probe.source, match.isInside, locality = null, point = probe.point)) },
            openPicker = sheetsViewModel::openPicker,
            // ACTION_DIAL never places the call itself; the rider confirms in the dialer.
            call = { phone -> ExternalIntents.goToPhoneDialer(context, phone) },
            openUrl = { url -> ExternalIntents.goToUrl(context, url) },
            zoomOut = mapViewModel::zoomOutToOnDemandZones,
            panTo = mapViewModel::panToOnDemandEdge,
            highlight = mapViewModel::highlightOnDemandService
        )
    }
    // Measured through a wrapper that is always here: a hidden dock composes nothing, and a modifier
    // on nothing never reports the shrink.
    OnDemandDockFeature(
        state = state,
        allMatches = probeResult?.matches ?: emptyList(),
        edges = edges,
        geometry = geometry,
        colors = colors,
        now = sheetsViewModel.now(),
        actions = actions,
        modifier = Modifier
            .align(if (landscape) Alignment.BottomStart else Alignment.BottomCenter)
            .then(if (landscape) Modifier.widthIn(max = DOCK_LANDSCAPE_MAX_WIDTH) else Modifier.fillMaxWidth())
            .padding(start = DOCK_GUTTER, end = DOCK_GUTTER, bottom = bottomInset + DOCK_SHEET_GAP)
            .onSizeChanged { onHeight(if (state == OnDemandDockState.Hidden) 0 else it.height) }
    )
    picker?.let { request ->
        OnDemandPickerSheet(
            request = request,
            colors = colors,
            now = sheetsViewModel.now(),
            onSelect = { match ->
                mapViewModel.highlightOnDemandService(match.service.id)
                sheetsViewModel.closePicker()
                onOpenDetail(match.service.id, LocationCheck(request.probe.source, match.isInside, request.locality, request.probe.point))
            },
            onDismiss = {
                mapViewModel.highlightOnDemandService(null)
                sheetsViewModel.closePicker()
            }
        )
    }
}
```

- [ ] **Step 5: Wire the home screen**

In `M/ui/home/HomeScreen.kt`:

1. `HomeCallbacks` (line 190): after `onOpenOnDemandService` add
   ```kotlin
       // Opening a zone from the dock, picker, pin or planner carries the probe's location facts.
       val onOpenOnDemandServiceAt: (serviceId: String, check: LocationCheck) -> Unit = { id, _ -> onOpenOnDemandService(id) },
   ```
2. After `val collapsedPeekPx = with(density) { collapsedPeekDp.roundToPx() }` add:
   ```kotlin
                   // Spec §2.4: the on-demand dock yields to a focus, the survey card and a sheet over half the map.
                   val surveyState by surveyViewModel.state.collectAsStateWithLifecycle()
                   val surveyCardShown = surveyState.heroQuestion != null && surveyState.sheet == null
                   val dockCovered = dockCoveredBySheet(
                       sheetShown = sheetShown,
                       expanded = sheetState.currentValue == SheetValue.Expanded,
                       peekPx = collapsedPeekPx,
                       windowHeightPx = LocalWindowInfo.current.containerSize.height
                   )
                   LaunchedEffect(currentFocus, dockCovered, surveyCardShown) {
                       mapViewModel.setOnDemandDockSuppressed(currentFocus !is CurrentFocus.None || dockCovered || surveyCardShown)
                   }
                   val onDemandSheetsViewModel = hiltViewModel<OnDemandSheetsViewModel>()
                   var onDemandDockHeightPx by remember { mutableIntStateOf(0) }
   ```
3. Where `MapFeature(` is called, pass `fabBottomInset = fabInsetTarget + with(density) { onDemandDockHeightPx.toDp() },` and replace `onOpenOnDemandService = onOpenOnDemandService,` with

   ```kotlin
                                    // A region-level pin tap carries the probe's facts when the last probe matched the service (spec §3.6 item 3).
                                    onOpenOnDemandService = { id ->
                                        val check = mapViewModel.onDemandLocationCheckFor(id)
                                        if (check == null) onOpenOnDemandService(id) else onOpenOnDemandServiceAt(id, check)
                                    },
   ```
4. After the `NavigateHereOverlay(mapViewModel) { … }` call add:
   ```kotlin
                                   // The on-demand dock (spec §2.4) at the top edge of the sheet, and its picker.
                                   OnDemandDockOverlay(
                                       mapViewModel = mapViewModel,
                                       sheetsViewModel = onDemandSheetsViewModel,
                                       bottomInset = fabInsetTarget,
                                       onOpenDetail = onOpenOnDemandServiceAt,
                                       onHeight = { onDemandDockHeightPx = it }
                                   )
   ```
5. Imports: `androidx.compose.runtime.mutableIntStateOf`, `org.onebusaway.android.ondemand.LocationCheck`, `org.onebusaway.android.ui.home.ondemand.OnDemandDockOverlay`, `org.onebusaway.android.ui.home.ondemand.OnDemandSheetsViewModel`.

In `M/ui/home/HomeNavHost.kt` after the `onOpenOnDemandService = …` line (261) add:

```kotlin
                    onOpenOnDemandServiceAt = { id, check -> navController.navigateFromHome(NavRoutes.onDemandService(id, check)) },
```

- [ ] **Step 6: Run the tests and the gates, commit**

Run: `./gradlew :onebusaway-android:testObaMaplibreDebugUnitTest --tests 'org.onebusaway.android.ondemand.LocationCheckArgsTest' --tests 'org.onebusaway.android.ui.home.HomeSheetLogicTest'`
Expected: PASS.
Run: `./gradlew :onebusaway-android:compileObaGoogleDebugKotlin :onebusaway-android:compileObaMaplibreDebugKotlin -PwarningsAsErrors=true`
Expected: BUILD SUCCESSFUL.

```bash
./gradlew spotlessApply
git add onebusaway-android/src/main onebusaway-android/src/test
git commit -m "Dock the on-demand card and bar on the home map" -m "The dock sits at the top edge of the arrivals sheet, lifts the FAB
stack by its measured height, hides behind a focus, the survey card
or a tall sheet, and opens the picker and the detail page with the
probe's location facts as route arguments."
```

---

### Task 14: Zone detail presentation — availability, tags, Where, No service, location check

**Files:**
- Modify: `M/ui/ondemand/OnDemandServicePresentation.kt`
- Test: `T/ui/ondemand/OnDemandServicePresentationTest.kt` (one test changes, several added)

**Interfaces:**
- Consumes: `computeAvailability`, `OnDemandAvailability`, `OnDemandStatus`, `TIER_*` (Task 2); `LocationCheck` (Task 3); `bookingLine`, `agencyZone` (Task 2); `OnDemandService.eligibility` (Task 1).
- Produces: `data class WhereRows(val serviceAreaNames: List<String>, val dropOffNames: List<String>?)`; `enum class DetailPromotion { STATUS_LINE, DEADLINE_ROW, NONE }`; `OnDemandServiceUiState.Content(service, whenRows, booking, availability: OnDemandAvailability, whereRows: WhereRows?, noServiceDays: Set<DayOfWeek>, locationCheck: LocationCheck?, presentedAt: Instant)`; `internal fun presentService(service, now, locationCheck: LocationCheck? = null): Content`; `internal fun detailPromotion(availability): DetailPromotion`; `internal fun noServiceDays(service, today: LocalDate?): Set<DayOfWeek>`; `internal fun whereRows(service): WhereRows?`; `internal fun formatDayRanges(days: Set<DayOfWeek>, locale: Locale): String`. `BookingSummary.infoUrl` is now null when it would equal `service.url`.

- [ ] **Step 1: Change and add the tests**

In `T/ui/ondemand/OnDemandServicePresentationTest.kt` replace `the service url is the info link when no booking rule has one` with:

```kotlin
    @Test
    fun `the service url is never repeated as the info link`() {
        val service = alexandria.copy(bookingRules = mapOf("5088_booking" to booking.copy(infoUrl = null)), url = "https://example.com/dot-paratransit")
        assertNull(presentService(service, tuesdayAfternoon).booking?.infoUrl)
        val sameAsUrl = alexandria.copy(url = booking.infoUrl)
        assertNull(presentService(sameAsUrl, tuesdayAfternoon).booking?.infoUrl)
    }

    @Test
    fun `an eligibility info url stands in when the booking rule has none`() {
        val service = alexandria.copy(
            bookingRules = mapOf("5088_booking" to booking.copy(infoUrl = null)),
            eligibility = OnDemandEligibility(EligibilityRequirement.CERTIFICATION_REQUIRED, "https://example.com/apply")
        )
        assertEquals("https://example.com/apply", presentService(service, tuesdayAfternoon).booking?.infoUrl)
    }
```

and append (inside the class):

```kotlin
    @Test
    fun `the promoted fact follows the tier`() {
        val content = presentService(alexandria, tuesdayAfternoon)
        assertEquals(TIER_ADVANCE, content.availability.usabilityTier)
        assertEquals(DetailPromotion.DEADLINE_ROW, detailPromotion(content.availability))
        assertEquals(DetailPromotion.STATUS_LINE, detailPromotion(content.availability.copy(usabilityTier = TIER_OPEN_NOW)))
        assertEquals(DetailPromotion.STATUS_LINE, detailPromotion(content.availability.copy(usabilityTier = TIER_SAME_DAY)))
        assertEquals(DetailPromotion.STATUS_LINE, detailPromotion(content.availability.copy(usabilityTier = TIER_UNKNOWN_OR_CLOSED, status = OnDemandStatus.Closed)))
        assertEquals(DetailPromotion.NONE, detailPromotion(content.availability.copy(usabilityTier = TIER_UNKNOWN_OR_CLOSED, status = OnDemandStatus.Unknown)))
        assertEquals(DetailPromotion.NONE, detailPromotion(content.availability.copy(usabilityTier = TIER_ELIGIBILITY)))
    }

    @Test
    fun `no service names the weekdays every live calendar skips`() {
        val today = LocalDate.of(2026, 3, 10)
        val monToSat = alexandria.copy(rules = listOf(rule("5088_c_63", "05:00:00")))
        assertEquals(setOf(DayOfWeek.SUNDAY), noServiceDays(monToSat, today))
        val monToFri = FlexCalendar("wk", weekdays.days - DayOfWeek.SATURDAY, weekdays.startDate, weekdays.endDate, emptySet())
        val weekdaysOnly = alexandria.copy(rules = listOf(rule("wk", "05:00:00")), calendars = mapOf("wk" to monToFri))
        assertEquals(setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY), noServiceDays(weekdaysOnly, today))
        // A calendar that has already ended no longer serves its days.
        val ended = alexandria.copy(calendars = mapOf("5088_c_63" to weekdays, "5088_c_64" to sunday.copy(endDate = LocalDate.of(2026, 2, 1))))
        assertEquals(setOf(DayOfWeek.SUNDAY), noServiceDays(ended, today))
        assertTrue(noServiceDays(alexandria, today).isEmpty())
        assertTrue(noServiceDays(alexandria, null).isEmpty())
    }

    @Test
    fun `rules with equal hours merge their weekdays into one row`() {
        val saturday = FlexCalendar("sat", setOf(DayOfWeek.SATURDAY), weekdays.startDate, weekdays.endDate, emptySet())
        val monToFri = FlexCalendar("wk", weekdays.days - DayOfWeek.SATURDAY, weekdays.startDate, weekdays.endDate, emptySet())
        val service = alexandria.copy(rules = listOf(rule("wk", "05:00:00"), rule("sat", "05:00:00")), calendars = mapOf("wk" to monToFri, "sat" to saturday))
        val rows = presentService(service, tuesdayAfternoon).whenRows
        assertEquals(1, rows.size)
        assertEquals(weekdays.days, rows.single().days)
        assertEquals("Mon–Sat", formatDayRanges(rows.single().days, java.util.Locale.US))
        assertEquals("Sat–Sun", formatDayRanges(setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY), java.util.Locale.US))
        assertEquals("Sun", formatDayRanges(setOf(DayOfWeek.SUNDAY), java.util.Locale.US))
        assertEquals("Mon, Wed–Fri", formatDayRanges(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY), java.util.Locale.US))
    }

    @Test
    fun `where rows appear only for zone-to-zone or multi-area services`() {
        assertNull(whereRows(alexandria))
        val zoneToZone = alexandria.copy(
            rules = listOf(rule("5088_c_63", "05:00:00").copy(fromIds = listOf("a"), toIds = listOf("b"))),
            areas = listOf(
                alexandriaArea("a", "Boyne City"),
                alexandriaArea("b", "Petoskey")
            )
        )
        val rows = requireNotNull(whereRows(zoneToZone))
        assertEquals(listOf("Boyne City"), rows.serviceAreaNames)
        assertEquals(listOf("Petoskey"), rows.dropOffNames)
        val twoAreasSameEnds = zoneToZone.copy(rules = listOf(rule("5088_c_63", "05:00:00").copy(fromIds = listOf("a", "b"), toIds = listOf("a", "b"))))
        assertNull(requireNotNull(whereRows(twoAreasSameEnds)).dropOffNames)
    }

    @Test
    fun `the location check and the presentation instant ride along`() {
        val check = LocationCheck(ProbeSource.Rider, isInside = true, locality = "Boyne City")
        val content = presentService(alexandria, tuesdayAfternoon, check)
        assertEquals(check, content.locationCheck)
        assertEquals(tuesdayAfternoon, content.presentedAt)
        assertNull(presentService(alexandria, tuesdayAfternoon).locationCheck)
    }

    private fun alexandriaArea(id: String, name: String) = org.onebusaway.android.models.ServiceArea(id, name, null, GeoPoint(45.0, -85.2), GeoPoint(45.1, -85.0), emptyList(), null, null)
```

with imports `org.onebusaway.android.models.EligibilityRequirement`, `org.onebusaway.android.models.OnDemandEligibility`, `org.onebusaway.android.ondemand.LocationCheck`, `org.onebusaway.android.ondemand.OnDemandStatus`, `org.onebusaway.android.ondemand.ProbeSource`, `org.onebusaway.android.ondemand.TIER_ADVANCE`, `TIER_ELIGIBILITY`, `TIER_OPEN_NOW`, `TIER_SAME_DAY`, `TIER_UNKNOWN_OR_CLOSED`, `org.onebusaway.android.util.GeoPoint`.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :onebusaway-android:testObaMaplibreDebugUnitTest --tests 'org.onebusaway.android.ui.ondemand.OnDemandServicePresentationTest'`
Expected: compilation FAILS (`Unresolved reference: detailPromotion`).

- [ ] **Step 3: Extend the presentation**

In `M/ui/ondemand/OnDemandServicePresentation.kt` replace `OnDemandServiceUiState` and `presentService` with:

```kotlin
/** The "Where" section (spec §3.6 item 5): the pickup areas, and the drop-off places when they differ. */
data class WhereRows(val serviceAreaNames: List<String>, val dropOffNames: List<String>?)

/** Which fact the page promotes (spec §3.6 items 1–2): the status line, the deadline row, or neither. */
enum class DetailPromotion { STATUS_LINE, DEADLINE_ROW, NONE }

sealed interface OnDemandServiceUiState {
    data object Loading : OnDemandServiceUiState
    data object NotFound : OnDemandServiceUiState
    data object Error : OnDemandServiceUiState
    data class Content(
        val service: OnDemandService,
        val whenRows: List<WhenRow>,
        val booking: BookingSummary?,
        val availability: OnDemandAvailability,
        val whereRows: WhereRows?,
        /** Weekdays no live calendar serves, shown as "No service" so the absence is visible. */
        val noServiceDays: Set<DayOfWeek>,
        val locationCheck: LocationCheck?,
        /** The instant the page was presented at; relative copy ("tomorrow") is measured from it. */
        val presentedAt: Instant
    ) : OnDemandServiceUiState
}

/**
 * Projects a service for the page at [now] (the device wall clock, minted by the caller), with the
 * probe's [locationCheck] when the page was opened from one. Pure, so it is JVM-tested with a fixed instant.
 */
internal fun presentService(service: OnDemandService, now: Instant, locationCheck: LocationCheck? = null): OnDemandServiceUiState.Content {
    val availability = computeAvailability(service, now)
    val bookingRules = service.rules.mapNotNull(service::pickupBookingRule).distinctBy { it.id }
    // Spec §3.6 item 7: the booking rule's info URL, else the eligibility's, and never the agency
    // website again — the two rows must not open the same page.
    val infoUrl = (bookingRules.firstNotNullOfOrNull { it.infoUrl } ?: service.eligibility?.infoUrl)?.takeIf { it != service.url }
    val booking = if (service.rules.isEmpty()) {
        infoUrl?.let {
            BookingSummary(travelDate = null, evaluation = null, zone = null, phoneNumber = null, bookingUrl = null, infoUrl = it, messages = emptyList())
        }
    } else {
        val zone = agencyZone(service.agencyTimezone)
        val line = zone?.let { service.bookingLine(now, it) }
        BookingSummary(
            travelDate = line?.travelDate,
            evaluation = line?.evaluation,
            zone = zone,
            phoneNumber = bookingRules.firstNotNullOfOrNull { it.phoneNumber },
            bookingUrl = bookingRules.firstNotNullOfOrNull { it.bookingUrl },
            infoUrl = infoUrl,
            messages = bookingRules.flatMap { listOfNotNull(it.message, it.pickupMessage, it.dropOffMessage) }.distinct()
        )
    }
    val today = availability.zone?.let { now.atZone(it).toLocalDate() }
    return OnDemandServiceUiState.Content(
        service = service,
        whenRows = mergedWhenRows(service),
        booking = booking,
        availability = availability,
        whereRows = whereRows(service),
        noServiceDays = noServiceDays(service, today),
        locationCheck = locationCheck,
        presentedAt = now
    )
}

/** One row per distinct pickup window, its weekdays the union of every calendar with those hours (spec §3.6 item 6). */
private fun mergedWhenRows(service: OnDemandService): List<WhenRow> {
    val rows = service.rules.flatMap { rule ->
        rule.calendarIds.mapNotNull { id -> service.calendars[id]?.let { WhenRow(id, it.days, rule.startPickupTime, rule.endPickupTime) } }
    }
    return rows.groupBy { it.start to it.end }.values.map { group -> group.first().copy(days = group.flatMap { it.days }.toSet()) }
}

/** Weekdays absent from every rule calendar whose end date is [today] or later. Empty without a date. */
internal fun noServiceDays(service: OnDemandService, today: LocalDate?): Set<DayOfWeek> {
    if (today == null) return emptySet()
    val served = service.rules
        .flatMap { it.calendarIds }
        .mapNotNull { service.calendars[it] }
        .filter { !it.endDate.isBefore(today) }
        .flatMap { it.days }
        .toSet()
    return DayOfWeek.entries.toSet() - served
}

/** Present when the service has more than one area or any rule's drop-off places differ from its pickups. */
internal fun whereRows(service: OnDemandService): WhereRows? {
    val differs = service.rules.any { it.toIds.toSet() != it.fromIds.toSet() }
    if (service.areas.size <= 1 && !differs) return null
    val fromIds = service.rules.flatMapTo(mutableSetOf()) { it.fromIds }
    val toIds = service.rules.flatMapTo(mutableSetOf()) { it.toIds }
    return WhereRows(serviceAreaNames = service.placeNames(fromIds), dropOffNames = if (differs) service.placeNames(toIds) else null)
}

// Areas first, then location groups; member stops are not carried on the service, so a bare stop id names nothing.
private fun OnDemandService.placeNames(ids: Set<String>): List<String> = ids
    .mapNotNull { id -> areas.firstOrNull { it.id == id }?.name ?: locationGroups.firstOrNull { it.id == id }?.name }
    .filter { it.isNotBlank() }
    .distinct()

internal fun detailPromotion(availability: OnDemandAvailability): DetailPromotion = when {
    availability.usabilityTier == TIER_ADVANCE -> DetailPromotion.DEADLINE_ROW
    availability.usabilityTier == TIER_OPEN_NOW || availability.usabilityTier == TIER_SAME_DAY -> DetailPromotion.STATUS_LINE
    availability.usabilityTier == TIER_UNKNOWN_OR_CLOSED && availability.status == OnDemandStatus.Closed -> DetailPromotion.STATUS_LINE
    else -> DetailPromotion.NONE
}

/** "Mon–Sat", "Sat–Sun", "Sun", "Mon, Wed–Fri": consecutive runs joined with an en dash. */
internal fun formatDayRanges(days: Set<DayOfWeek>, locale: Locale): String {
    val ordered = DayOfWeek.entries.filter { it in days }
    val runs = mutableListOf<List<DayOfWeek>>()
    for (day in ordered) {
        val last = runs.lastOrNull()
        if (last != null && last.last().value + 1 == day.value) runs[runs.lastIndex] = last + day else runs += listOf(day)
    }
    return runs.joinToString(", ") { run ->
        val first = run.first().getDisplayName(TextStyle.SHORT, locale)
        if (run.size == 1) first else "$first–${run.last().getDisplayName(TextStyle.SHORT, locale)}"
    }
}
```

Add imports: `java.time.format.TextStyle`, `java.util.Locale`, `org.onebusaway.android.ondemand.LocationCheck`, `OnDemandAvailability`, `OnDemandStatus`, `TIER_ADVANCE`, `TIER_OPEN_NOW`, `TIER_SAME_DAY`, `TIER_UNKNOWN_OR_CLOSED`, `computeAvailability`.

- [ ] **Step 4: Run the tests**

Run: `./gradlew :onebusaway-android:testObaMaplibreDebugUnitTest --tests 'org.onebusaway.android.ui.ondemand.OnDemandServicePresentationTest' --tests 'org.onebusaway.android.ui.ondemand.OnDemandServiceViewModelTest'`
Expected: PASS (the screen still compiles: it reads only `service`, `whenRows`, `booking`).

- [ ] **Step 5: Compile both flavours and commit**

Run: `./gradlew :onebusaway-android:compileObaGoogleDebugKotlin :onebusaway-android:compileObaMaplibreDebugKotlin -PwarningsAsErrors=true`
Expected: BUILD SUCCESSFUL.

```bash
./gradlew spotlessApply
git add onebusaway-android/src/main/java/org/onebusaway/android/ui/ondemand/OnDemandServicePresentation.kt onebusaway-android/src/test/java/org/onebusaway/android/ui/ondemand/OnDemandServicePresentationTest.kt
git commit -m "Present availability, Where and No service on the page" -m "The service page now carries the same availability the dock reads,
merges equal hours into weekday ranges, names the weekdays no live
calendar serves, adds pickup and drop-off places for zone-to-zone
services, and never offers the agency website twice."
```

---

### Task 15: Restructured zone detail screen and its view model

**Files:**
- Modify: `M/ui/ondemand/OnDemandServiceScreen.kt` (rewrite), `M/ui/ondemand/OnDemandServiceViewModel.kt`
- Create: `R/drawable/ic_check_circle.xml`, `R/drawable/ic_cancel.xml`, `R/drawable/ic_schedule.xml`, `R/drawable/ic_info.xml`
- Modify: `R/values/strings.xml` (add seven, remove `ondemand_details_title`)
- Test: `T/ui/ondemand/OnDemandServiceViewModelTest.kt` (append two tests)

**Interfaces:**
- Consumes: `Content.{availability, whereRows, noServiceDays, locationCheck, presentedAt}`, `detailPromotion`, `formatDayRanges` (Task 14); `statusText`, `OpenStyle.OPEN_NOW_UNTIL`, `tagText`, `detailLocationText`, `zoneCount` (Task 5); `resolve()`, `resolveCopy()` (Task 11); `ZoneThumbnail`, `toThumbnailShapes`, `shapesCentre`, `ProbeDotStyle` (Task 10); `locationCheckFromArgs`, `NavRoutes.ARG_ONDEMAND_*` (Task 13).
- Produces: `OnDemandServiceViewModel` reads the location check from its `SavedStateHandle` and re-presents itself one second after `availability.nextChangeInstant`; `OnDemandServiceScreen(state, onBack, onRetry, onCall, onOpenUrl)` unchanged in signature.

- [ ] **Step 1: Strings and glyphs**

Append to `R/values/strings.xml` after the Task 12 block, and delete `<string name="ondemand_details_title">Details</string>` (the footnote has no heading now):

```xml
    <!-- Zone detail page (DRT UI spec §3.6) -->
    <string name="ondemand_where_title">Where</string>
    <string name="ondemand_detail_service_area">Service area</string>
    <string name="ondemand_detail_drop_off">Drop-off</string>
    <string name="ondemand_detail_no_service">No service</string>
    <string name="ondemand_open_agency_website">Open Agency Website</string>
    <!-- %1$s is a locality name -->
    <string name="ondemand_detail_location_sub">Pickups available in %1$s</string>
    <!-- %1$s is a list of area names -->
    <string name="ondemand_detail_includes_location">%1$s — includes your location</string>
```

Create the four drawables (Material Icons, 24 dp, each with the same `<vector …>` wrapper as `ic_directions_car.xml`):

- `ic_check_circle.xml` path: `M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM10,17l-5,-5 1.41,-1.41L10,14.17l7.59,-7.59L19,8l-9,9z`
- `ic_cancel.xml` path: `M12,2C6.47,2 2,6.47 2,12s4.47,10 10,10 10,-4.47 10,-10S17.53,2 12,2zM17,15.59L15.59,17 12,13.41 8.41,17 7,15.59 10.59,12 7,8.41 8.41,7 12,10.59 15.59,7 17,8.41 13.41,12 17,15.59z`
- `ic_schedule.xml` path: `M11.99,2C6.47,2 2,6.48 2,12s4.47,10 9.99,10C17.52,22 22,17.52 22,12S17.52,2 11.99,2zM12,20c-4.42,0 -8,-3.58 -8,-8s3.58,-8 8,-8 8,3.58 8,8 -3.58,8 -8,8zM12.5,7H11v6l5.25,3.15 0.75,-1.23 -4.5,-2.67z`
- `ic_info.xml` path: `M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM13,17h-2v-6h2v6zM13,9h-2V7h2v2z`

- [ ] **Step 2: Write the failing view-model tests**

Append to `T/ui/ondemand/OnDemandServiceViewModelTest.kt` (inside the class):

```kotlin
    private fun viewModelWithArgs(source: OnDemandDataSource, args: Map<String, Any?>) = OnDemandServiceViewModel(
        SavedStateHandle(mapOf(NavRoutes.ARG_ONDEMAND_SERVICE_ID to "5088_77652") + args),
        source,
        TimeProvider { nowMs },
        UnconfinedTestDispatcher()
    )

    @Test
    fun `the route arguments become the location check`() = runTest {
        val vm = viewModelWithArgs(
            FakeDataSource(OnDemandResult.Loaded(service)),
            mapOf(NavRoutes.ARG_ONDEMAND_INSIDE to "true", NavRoutes.ARG_ONDEMAND_SOURCE to "rider", NavRoutes.ARG_ONDEMAND_LOCALITY to "Boyne City", NavRoutes.ARG_ONDEMAND_LAT to "45.05", NavRoutes.ARG_ONDEMAND_LON to "-85.1")
        )
        val content = vm.state.value as OnDemandServiceUiState.Content
        assertEquals(LocationCheck(ProbeSource.Rider, true, "Boyne City", GeoPoint(45.05, -85.1)), content.locationCheck)
        assertEquals(null, (viewModel(FakeDataSource(OnDemandResult.Loaded(service))).state.value as OnDemandServiceUiState.Content).locationCheck)
    }

    @Test
    fun `the page re-presents itself a second after the next change`() = runTest {
        val calendar = FlexCalendar("c", setOf(DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), emptySet())
        val running = service.copy(
            rules = listOf(AvailabilityRule(listOf("a"), listOf("a"), ServiceDayTime.parse("07:20:00"), ServiceDayTime.parse("16:40:00"), null, listOf("c"), 2, 2, null, null, null, null)),
            calendars = mapOf("c" to calendar)
        )
        nowMs = OffsetDateTime.parse("2026-03-10T16:39:30-07:00").toInstant().toEpochMilli()
        val vm = viewModel(FakeDataSource(OnDemandResult.Loaded(running)))
        assertTrue((vm.state.value as OnDemandServiceUiState.Content).availability.status is OnDemandStatus.OpenNow)

        nowMs = OffsetDateTime.parse("2026-03-10T16:40:01-07:00").toInstant().toEpochMilli()
        advanceTimeBy(32_000)
        assertTrue((vm.state.value as OnDemandServiceUiState.Content).availability.status is OnDemandStatus.OpensAt)
    }
```

with imports `kotlinx.coroutines.test.advanceTimeBy`, `org.onebusaway.android.ondemand.LocationCheck`, `org.onebusaway.android.ondemand.OnDemandStatus`, `org.onebusaway.android.ondemand.ProbeSource`, `org.onebusaway.android.util.GeoPoint`.

- [ ] **Step 3: Run the tests to verify they fail**

Run: `./gradlew :onebusaway-android:testObaMaplibreDebugUnitTest --tests 'org.onebusaway.android.ui.ondemand.OnDemandServiceViewModelTest'`
Expected: FAIL (`locationCheck` is null; the status stays `OpenNow`).

- [ ] **Step 4: The view model**

In `M/ui/ondemand/OnDemandServiceViewModel.kt`:

```kotlin
    private val serviceId: String = requireNotNull(savedState[NavRoutes.ARG_ONDEMAND_SERVICE_ID]) { "on-demand service page requires a service id" }

    // The probe's location facts when the page was opened from the dock, picker, pin or planner.
    private val locationCheck: LocationCheck? = locationCheckFromArgs(
        inside = savedState[NavRoutes.ARG_ONDEMAND_INSIDE],
        source = savedState[NavRoutes.ARG_ONDEMAND_SOURCE],
        locality = savedState[NavRoutes.ARG_ONDEMAND_LOCALITY],
        lat = savedState[NavRoutes.ARG_ONDEMAND_LAT],
        lon = savedState[NavRoutes.ARG_ONDEMAND_LON]
    )

    // The re-presentation scheduled for the next availability change (spec §3.6), separate from [job]
    // so a fetch or a manual re-present neither waits for nor is cancelled by it.
    private var refreshJob: Job? = null
```

and replace `present`:

```kotlin
    // The deadline is computed against the device wall clock (spec §6.3), never the envelope's
    // currentTime, which sits on the long-cache tier.
    private suspend fun present(service: OnDemandService): OnDemandServiceUiState.Content {
        val now = Instant.ofEpochMilli(timeProvider.now())
        val content = withContext(presentDispatcher) { presentService(service, now, locationCheck) }
        scheduleRefresh(content.availability.nextChangeInstant, now)
        return content
    }

    /** Re-present one second after the availability changes, so "Open now" becomes "Opens …" on its own. */
    private fun scheduleRefresh(at: Instant?, now: Instant) {
        refreshJob?.cancel()
        if (at == null) return
        refreshJob = viewModelScope.launch {
            delay((at.toEpochMilli() + REFRESH_GRACE_MS - now.toEpochMilli()).coerceAtLeast(0L))
            representNow()
        }
    }

    private companion object {
        const val REFRESH_GRACE_MS = 1_000L
    }
```

Imports: `kotlinx.coroutines.delay`, `org.onebusaway.android.ondemand.LocationCheck`, `org.onebusaway.android.ondemand.locationCheckFromArgs`.

- [ ] **Step 5: The screen**

Replace the body of `M/ui/ondemand/OnDemandServiceScreen.kt` (keep the licence header) with:

```kotlin
package org.onebusaway.android.ui.ondemand

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import org.onebusaway.android.R
import org.onebusaway.android.models.ServiceDayTime
import org.onebusaway.android.ondemand.BookingState
import org.onebusaway.android.ondemand.LocationCheck
import org.onebusaway.android.ondemand.OnDemandStatus
import org.onebusaway.android.ondemand.OnDemandTag
import org.onebusaway.android.ondemand.OpenStyle
import org.onebusaway.android.ondemand.ProbeSource
import org.onebusaway.android.ondemand.detailLocationText
import org.onebusaway.android.ondemand.statusText
import org.onebusaway.android.ondemand.tagText
import org.onebusaway.android.ondemand.zoneCount
import org.onebusaway.android.ui.compose.components.ErrorContent
import org.onebusaway.android.ui.compose.components.LoadingContent
import org.onebusaway.android.ui.compose.components.ObaTopAppBar
import org.onebusaway.android.ui.home.ondemand.ProbeDotStyle
import org.onebusaway.android.ui.home.ondemand.ZoneThumbnail
import org.onebusaway.android.ui.home.ondemand.resolve
import org.onebusaway.android.ui.home.ondemand.resolveCopy
import org.onebusaway.android.ui.home.ondemand.shapesCentre
import org.onebusaway.android.ui.home.ondemand.toThumbnailShapes

private val CARD_RADIUS = 18.dp
private val THUMBNAIL_HEIGHT = 210.dp
private val PRIMARY_HEIGHT = 46.dp
private val TAG_FILL = Color(0xFFE9F1DD)
private val WARN_FILL = Color(0xFFFBEFD5)
private val WARN_TEXT = Color(0xFF8A5A00)

/** The zone detail page (spec §3.6, screens 3–4): header card, promoted fact, location, thumbnail, Where, When, How to book, footnote. */
@Composable
fun OnDemandServiceScreen(
    state: OnDemandServiceUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onCall: (String) -> Unit,
    onOpenUrl: (String) -> Unit
) {
    val title = (state as? OnDemandServiceUiState.Content)?.service?.name ?: stringResource(R.string.ondemand_service_title)
    Scaffold(topBar = { ObaTopAppBar(title = title, onBack = onBack) }, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (state) {
                OnDemandServiceUiState.Loading -> LoadingContent(Modifier.align(Alignment.Center))
                OnDemandServiceUiState.Error -> ErrorContent(onRetry = onRetry, modifier = Modifier.align(Alignment.Center))
                OnDemandServiceUiState.NotFound -> Text(
                    text = stringResource(R.string.ondemand_not_found),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.align(Alignment.Center).padding(32.dp)
                )
                is OnDemandServiceUiState.Content -> ServiceContent(state, onCall, onOpenUrl)
            }
        }
    }
}

@Composable
private fun ServiceContent(content: OnDemandServiceUiState.Content, onCall: (String) -> Unit, onOpenUrl: (String) -> Unit) {
    // LocalLocale observes the app's locale so this recomposes on a locale change,
    // unlike Locale.getDefault() which reads it once and never updates.
    val locale = LocalLocale.current.platformLocale
    val color = content.service.routeColor ?: colorResource(R.color.brand_color).toArgb()
    val promotion = detailPromotion(content.availability)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        HeaderCard(content, promotion, locale, onCall, onOpenUrl)
        if (promotion == DetailPromotion.DEADLINE_ROW) content.booking?.let { DetailCard { IconRow(R.drawable.ic_schedule, deadlineLine(it, locale)) } }
        content.locationCheck?.let { LocationRow(it) }
        Thumbnail(content, color)
        content.whereRows?.let { WhereSection(it, content) }
        WhenSection(content, locale)
        HowToBookSection(content, locale, onOpenUrl)
        content.booking?.messages?.forEach { message ->
            Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun HeaderCard(content: OnDemandServiceUiState.Content, promotion: DetailPromotion, locale: Locale, onCall: (String) -> Unit, onOpenUrl: (String) -> Unit) {
    val availability = content.availability
    DetailCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(content.service.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            if (availability.tags.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    availability.tags.sortedBy { it.ordinal }.forEach { tag -> TagChip(tag) }
                }
            }
            content.service.description?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (promotion == DetailPromotion.STATUS_LINE) {
                statusText(availability.status, availability.zone, content.presentedAt, locale, OpenStyle.OPEN_NOW_UNTIL)?.resolve()?.let { status ->
                    val green = colorResource(R.color.ondemand_open_green)
                    Text(
                        text = buildAnnotatedString {
                            if (availability.status is OnDemandStatus.OpenNow) withStyle(SpanStyle(color = green, fontWeight = FontWeight.SemiBold)) { append(status) } else append(status)
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            val phone = content.booking?.phoneNumber
            val url = content.booking?.bookingUrl
            when {
                phone != null -> Button(onClick = { onCall(phone) }, modifier = Modifier.fillMaxWidth().height(PRIMARY_HEIGHT)) {
                    Icon(painterResource(R.drawable.ic_call), contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.ondemand_call, phone), modifier = Modifier.padding(start = 8.dp))
                }
                url != null -> Button(onClick = { onOpenUrl(url) }, modifier = Modifier.fillMaxWidth().height(PRIMARY_HEIGHT)) {
                    Icon(painterResource(R.drawable.ic_open_in_new), contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.ondemand_book_online), modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun TagChip(tag: OnDemandTag) {
    val warn = tag == OnDemandTag.ELIGIBILITY_REQUIRED
    Text(
        text = tagText(tag).resolve(),
        style = MaterialTheme.typography.labelMedium,
        color = if (warn) WARN_TEXT else MaterialTheme.colorScheme.primary,
        modifier = Modifier.background(if (warn) WARN_FILL else TAG_FILL, RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

@Composable
private fun LocationRow(check: LocationCheck) {
    val sub = check.locality?.takeIf { check.isInside }?.let { stringResource(R.string.ondemand_detail_location_sub, it) }
    DetailCard {
        IconRow(
            iconRes = if (check.isInside) R.drawable.ic_check_circle else R.drawable.ic_cancel,
            title = detailLocationText(check.source, check.isInside).resolve(),
            subtitle = sub,
            tint = if (check.isInside) colorResource(R.color.ondemand_open_green) else colorResource(R.color.ondemand_outside)
        )
    }
}

@Composable
private fun Thumbnail(content: OnDemandServiceUiState.Content, color: Int) {
    val shapes = content.service.areas.toThumbnailShapes(color)
    val centre = shapesCentre(shapes) ?: return
    val check = content.locationCheck
    ZoneThumbnail(
        shapes = shapes,
        centre = centre,
        probePoint = check?.point,
        probeStyle = when (check?.source) {
            ProbeSource.Rider -> ProbeDotStyle.RIDER
            ProbeSource.MapCenter -> ProbeDotStyle.MAP_CENTER
            is ProbeSource.Point, null -> ProbeDotStyle.POINT
        },
        fitProbe = false,
        modifier = Modifier.fillMaxWidth().height(THUMBNAIL_HEIGHT).clip(RoundedCornerShape(CARD_RADIUS)).background(MaterialTheme.colorScheme.surface)
    )
}

@Composable
private fun WhereSection(rows: WhereRows, content: OnDemandServiceUiState.Content) {
    val check = content.locationCheck
    val areaNames = rows.serviceAreaNames.joinToString(", ").ifEmpty { zoneCount(content.service.areas.size).resolveCopy() }
    val serviceAreaSub = if (check?.source == ProbeSource.Rider && check.isInside) stringResource(R.string.ondemand_detail_includes_location, areaNames) else areaNames
    val inside = check?.isInside == true
    SectionHeader(stringResource(R.string.ondemand_where_title))
    DetailCard {
        Column {
            IconRow(
                iconRes = if (inside) R.drawable.ic_check_circle else R.drawable.ic_location_on,
                title = stringResource(R.string.ondemand_detail_service_area),
                subtitle = serviceAreaSub,
                tint = if (inside) colorResource(R.color.ondemand_open_green) else MaterialTheme.colorScheme.onSurfaceVariant
            )
            rows.dropOffNames?.let { names ->
                HorizontalDivider(Modifier.padding(start = 56.dp))
                IconRow(R.drawable.ic_location_on, stringResource(R.string.ondemand_detail_drop_off), names.joinToString(", ").ifEmpty { null })
            }
        }
    }
}

@Composable
private fun WhenSection(content: OnDemandServiceUiState.Content, locale: Locale) {
    SectionHeader(stringResource(R.string.ondemand_when_title))
    DetailCard {
        Column {
            if (content.whenRows.isEmpty()) {
                Text(stringResource(R.string.ondemand_no_rules), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
            }
            content.whenRows.forEachIndexed { index, row ->
                if (index > 0) HorizontalDivider(Modifier.padding(start = 16.dp))
                ScheduleRow(formatDayRanges(row.days, locale), formatWindow(row.start, row.end, locale))
            }
            if (content.noServiceDays.isNotEmpty()) {
                if (content.whenRows.isNotEmpty()) HorizontalDivider(Modifier.padding(start = 16.dp))
                ScheduleRow(formatDayRanges(content.noServiceDays, locale), stringResource(R.string.ondemand_detail_no_service), muted = true)
            }
        }
    }
}

@Composable
private fun HowToBookSection(content: OnDemandServiceUiState.Content, locale: Locale, onOpenUrl: (String) -> Unit) {
    val booking = content.booking
    val url = content.service.url
    val infoUrl = booking?.infoUrl
    val hasDeadline = booking != null && content.service.rules.isNotEmpty()
    if (!hasDeadline && url == null && infoUrl == null) return
    SectionHeader(stringResource(R.string.ondemand_how_to_book_title))
    DetailCard {
        Column {
            if (hasDeadline && booking != null) IconRow(R.drawable.ic_schedule, deadlineLine(booking, locale))
            url?.let {
                if (hasDeadline) HorizontalDivider(Modifier.padding(start = 56.dp))
                IconRow(R.drawable.ic_open_in_new, stringResource(R.string.ondemand_open_agency_website), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable { onOpenUrl(it) })
            }
            infoUrl?.let {
                if (hasDeadline || url != null) HorizontalDivider(Modifier.padding(start = 56.dp))
                IconRow(R.drawable.ic_info, stringResource(R.string.ondemand_more_info), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable { onOpenUrl(it) })
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun DetailCard(content: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(CARD_RADIUS), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) { content() }
}

@Composable
private fun IconRow(iconRes: Int, title: String, subtitle: String? = null, tint: Color = MaterialTheme.colorScheme.onSurfaceVariant, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Icon(painterResource(iconRes), contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = if (tint == MaterialTheme.colorScheme.primary) tint else MaterialTheme.colorScheme.onSurface)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun ScheduleRow(days: String, hours: String, muted: Boolean = false) {
    val colour = if (muted) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(days, style = MaterialTheme.typography.bodyMedium, color = colour)
        Text(hours, style = MaterialTheme.typography.bodyMedium, color = if (muted) colour else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun deadlineLine(booking: BookingSummary, locale: Locale): String {
    val evaluation = booking.evaluation
    val travelDate = booking.travelDate
    val zone = booking.zone
    if (evaluation == null || travelDate == null || zone == null) return stringResource(R.string.ondemand_no_deadline_published)
    val dateTime = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(locale).withZone(zone)
    val date = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
    return when (evaluation.state) {
        BookingState.NOT_YET_OPEN -> stringResource(R.string.ondemand_booking_opens, dateTime.format(evaluation.openInstant), date.format(travelDate))
        BookingState.OPEN, BookingState.CLOSED_FOR_DATE -> {
            val cutoff = evaluation.cutoffInstant
            if (cutoff == null) stringResource(R.string.ondemand_book_at_ride_time) else stringResource(R.string.ondemand_book_by, dateTime.format(cutoff), date.format(travelDate))
        }
        BookingState.UNKNOWN -> stringResource(R.string.ondemand_no_deadline_published)
    }
}

@Composable
private fun formatWindow(start: ServiceDayTime?, end: ServiceDayTime?, locale: Locale): String {
    if (start == null && end == null) return stringResource(R.string.ondemand_all_hours)
    return stringResource(R.string.ondemand_time_window, formatTime(start ?: ServiceDayTime(0), locale), formatTime(end ?: ServiceDayTime(24 * 3600), locale))
}

@Composable
private fun formatTime(time: ServiceDayTime, locale: Locale): String {
    val clock = LocalTime.of(time.hours % 24, time.minutesOfHour).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale))
    return if (time.hours >= 24) stringResource(R.string.ondemand_time_next_day, clock) else clock
}
```

- [ ] **Step 6: Run the tests and the gates, commit**

Run: `./gradlew :onebusaway-android:testObaMaplibreDebugUnitTest --tests 'org.onebusaway.android.ui.ondemand.OnDemandServiceViewModelTest' --tests 'org.onebusaway.android.ui.ondemand.OnDemandServicePresentationTest'`
Expected: PASS.
Run: `./gradlew :onebusaway-android:compileObaGoogleDebugKotlin :onebusaway-android:compileObaMaplibreDebugKotlin -PwarningsAsErrors=true`
Expected: BUILD SUCCESSFUL.

```bash
./gradlew spotlessApply
git add onebusaway-android/src/main onebusaway-android/src/test
git commit -m "Restructure the on-demand service page" -m "The page now leads with the fact that decides whether the rider can
use the service — the status line, or the deadline row for advance
booking — then says whether the probe point is in the zone, draws the
zone, and lists Where, When with \"No service\" days, How to book and
the feed's own notes. It re-presents itself when the availability
changes."
```

---

### Task 16: The address line on the dropped pin

**Files:**
- Create: `M/ondemand/OnDemandCoverage.kt`
- Modify: `M/map/MapViewModel.kt` (`setNavigateHerePin`, lines ~723-737), `M/ui/home/directions/NavigateHereBubble.kt`, `M/ui/home/HomeScreen.kt` (`NavigateHereOverlay`, ~1155 and ~1245)
- Test: `T/ondemand/OnDemandCoverageTest.kt`; `AT/ui/home/directions/NavigateHereCoverageRenderTest.kt`

**Interfaces:**
- Consumes: `MapViewModel.probeOnDemandExact` (Task 9); `OnDemandMatch.isInside/isNearby`, `sortedSoonestUsable` (Task 3); `addressLine` (Task 5); `resolveCopy()` (Task 11); `LocationCheck`, `ProbeSource.Point`.
- Produces: `data class OnDemandCoverageLine(serviceName: String, isInside: Boolean, match: OnDemandMatch, othersInside: Int)`; `fun coverageLineFor(matches: List<OnDemandMatch>): OnDemandCoverageLine?`; `MapViewModel.navigateHereCoverage: StateFlow<OnDemandCoverageLine?>`; `NavigateHereBubble(point, projector, onNavigate, onDismiss, modifier, coverage: OnDemandCoverageLine? = null, onOpenCoverage: () -> Unit = {})`; `NavigateHereOffer(anchor, onNavigate, onDismiss, modifier, coverage, onOpenCoverage)`; `NavigateHereBubbleTestTags.COVERAGE`.

- [ ] **Step 1: Write the failing tests**

Create `T/ondemand/OnDemandCoverageTest.kt`:

```kotlin
package org.onebusaway.android.ondemand

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.models.OnDemandMatchReason
import org.onebusaway.android.util.GeoPoint

class OnDemandCoverageTest {

    private val now = instant("2026-03-10T14:00:00-04:00")

    private fun match(id: String, reason: OnDemandMatchReason, distance: Double?, tier: Int = TIER_OPEN_NOW): OnDemandMatch {
        val areas = if (distance == null) emptyList() else listOf(area(distance = distance, nearest = GeoPoint(45.0, -85.1)))
        val match = matchFor(service(id = id, name = "Service $id", areas = areas, matchReason = reason), now)
        return match.copy(availability = match.availability.copy(usabilityTier = tier))
    }

    @Test
    fun `inside names the soonest usable service and counts the others`() {
        val line = requireNotNull(coverageLineFor(listOf(match("advance", OnDemandMatchReason.AREA_CONTAINS_POINT, 0.0, TIER_ADVANCE), match("open", OnDemandMatchReason.AREA_CONTAINS_POINT, 0.0))))
        assertTrue(line.isInside)
        assertEquals("Service open", line.serviceName)
        assertEquals(1, line.othersInside)
    }

    @Test
    fun `outside names the nearest service within five kilometres`() {
        val line = requireNotNull(coverageLineFor(listOf(match("far", OnDemandMatchReason.AREA_NEARBY, 4_000.0), match("near", OnDemandMatchReason.AREA_NEARBY, 300.0))))
        assertFalse(line.isInside)
        assertEquals("Service near", line.serviceName)
        assertEquals(0, line.othersInside)
        assertNull(coverageLineFor(listOf(match("toofar", OnDemandMatchReason.AREA_NEARBY, 6_000.0))))
    }

    @Test
    fun `a stop-group match never names the address line`() {
        assertNull(coverageLineFor(listOf(match("stops", OnDemandMatchReason.STOP_WITHIN_RADIUS, null))))
        assertNull(coverageLineFor(emptyList()))
    }
}
```

Create `AT/ui/home/directions/NavigateHereCoverageRenderTest.kt`:

```kotlin
package org.onebusaway.android.ui.home.directions

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.onebusaway.android.R
import org.onebusaway.android.map.render.ScreenOffset
import org.onebusaway.android.ondemand.OnDemandCoverageLine
import org.onebusaway.android.ondemand.sampleMatch
import org.onebusaway.android.ui.compose.createUnconfinedComposeRule
import org.onebusaway.android.ui.compose.theme.ObaTheme

/** The dropped pin's third line (DRT UI spec §3.7): "Inside …" / "Outside …", opening the zone. */
class NavigateHereCoverageRenderTest {

    @get:Rule
    val composeRule = createUnconfinedComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun theCoverageLineNamesTheZoneAndOpensIt() {
        var opened = 0
        composeRule.setContent {
            ObaTheme {
                NavigateHereOffer(
                    anchor = { ScreenOffset(200f, 500f) },
                    onNavigate = {},
                    onDismiss = {},
                    coverage = OnDemandCoverageLine("Dial-a-Ride", isInside = true, match = sampleMatch(), othersInside = 0),
                    onOpenCoverage = { opened++ }
                )
            }
        }
        composeRule.onNodeWithText(context.getString(R.string.ondemand_address_inside, "Dial-a-Ride")).assertIsDisplayed().performClick()
        assertEquals(1, opened)
        composeRule.onNodeWithText(context.getString(R.string.map_navigate_here)).assertIsDisplayed()
    }
}
```

- [ ] **Step 2: Run the JVM test to verify it fails**

Run: `./gradlew :onebusaway-android:testObaMaplibreDebugUnitTest --tests 'org.onebusaway.android.ondemand.OnDemandCoverageTest'`
Expected: compilation FAILS (`Unresolved reference: coverageLineFor`).

- [ ] **Step 3: The coverage decision and the view model**

Create `M/ondemand/OnDemandCoverage.kt`:

```kotlin
package org.onebusaway.android.ondemand

/** What the dropped pin's extra line says (spec §3.7): the named service, inside or outside, and how many more contain the pin. */
data class OnDemandCoverageLine(val serviceName: String, val isInside: Boolean, val match: OnDemandMatch, val othersInside: Int)

/**
 * Inside: the first inside match by §2.6, with the count of the others. Outside: the nearest match
 * with a finite distance ≤ 5,000 m. Null when nothing is within reach — a pure stop group never counts.
 */
fun coverageLineFor(matches: List<OnDemandMatch>): OnDemandCoverageLine? {
    val inside = matches.filter { it.isInside }.sortedSoonestUsable()
    inside.firstOrNull()?.let { return OnDemandCoverageLine(it.service.name, isInside = true, match = it, othersInside = inside.size - 1) }
    val nearest = matches.filter { it.isNearby }.minByOrNull { it.distanceToAreaMeters ?: Double.MAX_VALUE } ?: return null
    return OnDemandCoverageLine(nearest.service.name, isInside = false, match = nearest, othersInside = 0)
}
```

In `M/map/MapViewModel.kt` replace `setNavigateHerePin` and add the flow beside `navigateHerePin`:

```kotlin
    private val _navigateHereCoverage = MutableStateFlow<OnDemandCoverageLine?>(null)

    /** The pin's on-demand line (spec §3.7), or null while none stands, the probe is in flight, or it failed. */
    val navigateHereCoverage: StateFlow<OnDemandCoverageLine?> = _navigateHereCoverage.asStateFlow()

    private var coverageJob: Job? = null

    /**
     * Drop the "navigate here" pin at [point] — moving it if one is already down — or clear the offer
     * with null. The pin itself is the directions layer's (it is the destination's own red pin, drawn
     * and reconciled where the trip's endpoints are); what this adds is publishing *where* it stands, so
     * the home screen can hang the offer's bubble off it — and asking, at that exact point, whether an
     * on-demand zone covers it.
     */
    fun setNavigateHerePin(point: GeoPoint?) {
        directionsController.setNavigateHerePin(point)
        _navigateHerePin.value = point
        coverageJob?.cancel()
        _navigateHereCoverage.value = null
        if (point == null) return
        coverageJob = viewModelScope.launch {
            val matches = onDemandProbeController.probeExact(point) ?: return@launch
            if (_navigateHerePin.value == point) _navigateHereCoverage.value = coverageLineFor(matches)
        }
    }
```

with imports `kotlinx.coroutines.Job`, `org.onebusaway.android.ondemand.OnDemandCoverageLine`, `org.onebusaway.android.ondemand.coverageLineFor`.

- [ ] **Step 4: The bubble's third line**

In `M/ui/home/directions/NavigateHereBubble.kt`:

1. `NavigateHereBubbleTestTags` gains `const val COVERAGE = "navigateHereCoverage"`.
2. `NavigateHereBubble(...)` gains `coverage: OnDemandCoverageLine? = null, onOpenCoverage: () -> Unit = {}` after `modifier` and passes them: `NavigateHereOffer({ anchor.value }, onNavigate, onDismiss, modifier, coverage, onOpenCoverage)`.
3. `NavigateHereOffer(...)` gains the same two parameters after `modifier` and passes them to `NavigateHerePill(onNavigate, coverage, onOpenCoverage)`.
4. Replace `NavigateHerePill`:

```kotlin
/** The offer itself: the destination dot the trip form will show, what pressing it does, and the zone line when one applies. */
@Composable
private fun NavigateHerePill(onNavigate: () -> Unit, coverage: OnDemandCoverageLine?, onOpenCoverage: () -> Unit) {
    Surface(
        onClick = onNavigate,
        modifier = Modifier.testTag(NavigateHereBubbleTestTags.BUBBLE),
        shape = MaterialTheme.shapes.large,
        // Flat-toned on purpose: a tonal elevation would tint the pill away from the plain container
        // colour, and the tail — which is a bare shape, not a Surface — would no longer match it.
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 6.dp
    ) {
        Column {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // The mark the pressed point will carry once the trip is planned — the trip-plan rail's own
                // destination dot, kept from the menu this replaces so the offer and the filled form agree.
                TripEndpointDotIcon(
                    TripEndpointSlot.TO,
                    Modifier.testTag(NavigateHereBubbleTestTags.DOT)
                )
                Text(
                    text = stringResource(R.string.map_navigate_here),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            // Spec §3.7: whether an on-demand zone covers the pressed point, opening that zone's page.
            if (coverage != null) {
                HorizontalDivider()
                Row(
                    modifier = Modifier
                        .clickable(onClick = onOpenCoverage)
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                        .testTag(NavigateHereBubbleTestTags.COVERAGE),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        painter = painterResource(if (coverage.isInside) R.drawable.ic_check_circle else R.drawable.ic_cancel),
                        contentDescription = null,
                        tint = colorResource(if (coverage.isInside) R.color.ondemand_open_green else R.color.md_theme_severityError),
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = addressLine(coverage.serviceName, coverage.isInside, coverage.othersInside).resolveCopy(),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}
```

Imports to add: `androidx.compose.foundation.clickable`, `androidx.compose.foundation.layout.Column`, `androidx.compose.material3.HorizontalDivider`, `androidx.compose.material3.Icon`, `androidx.compose.ui.res.colorResource`, `androidx.compose.ui.res.painterResource`, `org.onebusaway.android.ondemand.OnDemandCoverageLine`, `org.onebusaway.android.ondemand.addressLine`, `org.onebusaway.android.ui.home.ondemand.resolveCopy`.

In `M/ui/home/HomeScreen.kt` change `NavigateHereOverlay` to:

```kotlin
@Composable
private fun NavigateHereOverlay(
    mapViewModel: MapViewModel,
    onNavigate: (GeoPoint) -> Unit,
    onOpenCoverage: (OnDemandCoverageLine, GeoPoint) -> Unit
) {
    val pin by mapViewModel.navigateHerePin.collectAsStateWithLifecycle()
    val projector by mapViewModel.renderState.projector.collectAsStateWithLifecycle()
    val coverage by mapViewModel.navigateHereCoverage.collectAsStateWithLifecycle()
    pin?.let { point ->
        NavigateHereBubble(
            point = point,
            projector = projector,
            onNavigate = {
                mapViewModel.setNavigateHerePin(null)
                onNavigate(point)
            },
            onDismiss = { mapViewModel.setNavigateHerePin(null) },
            coverage = coverage,
            onOpenCoverage = { coverage?.let { line -> onOpenCoverage(line, point) } }
        )
    }
}
```

and its call site to:

```kotlin
                                NavigateHereOverlay(
                                    mapViewModel = mapViewModel,
                                    onNavigate = { point ->
                                        navigateTo(
                                            TripEndpoint.MapPoint(point.latitude, point.longitude),
                                            homeViewModel,
                                            mapViewModel,
                                            tripPlanViewModel
                                        )
                                    },
                                    onOpenCoverage = { line, point ->
                                        mapViewModel.setNavigateHerePin(null)
                                        onOpenOnDemandServiceAt(line.match.service.id, LocationCheck(ProbeSource.Point(null), line.isInside, locality = null, point = point))
                                    }
                                )
```

Imports: `org.onebusaway.android.ondemand.OnDemandCoverageLine`, `org.onebusaway.android.ondemand.ProbeSource`.

- [ ] **Step 5: Run the test and the gates, commit**

Run: `./gradlew :onebusaway-android:testObaMaplibreDebugUnitTest --tests 'org.onebusaway.android.ondemand.OnDemandCoverageTest' --tests 'org.onebusaway.android.ui.home.directions.NavigateHereBubbleTest'`
Expected: PASS.
Run: `./gradlew :onebusaway-android:compileObaGoogleDebugKotlin :onebusaway-android:compileObaMaplibreDebugKotlin :onebusaway-android:compileObaMaplibreDebugAndroidTestKotlin -PwarningsAsErrors=true`
Expected: BUILD SUCCESSFUL.

```bash
./gradlew spotlessApply
git add onebusaway-android/src/main onebusaway-android/src/test onebusaway-android/src/androidTest
git commit -m "Say whether a dropped pin is inside an on-demand zone" -m "A long-pressed point asks the exact-point probe, never the rider's
cached answer, and the bubble gains an \"Inside …\" or \"Outside …\" line
that opens the zone's page with the point's own location facts."
```

---

### Task 17: Trip planner fallback

**Files:**
- Create: `M/ondemand/OnDemandPlannerQualifier.kt`, `M/ui/home/directions/PlannerFallbackGate.kt`, `M/ui/home/directions/OnDemandPlannerFallbackSheet.kt`
- Modify: `M/ui/home/directions/DirectionsFeature.kt:676-716` (`DirectionsErrorSnackbar`), `M/ui/home/ondemand/OnDemandSheetsViewModel.kt`, `M/ui/home/ondemand/OnDemandDockHost.kt`, `M/map/MapViewModel.kt` (`onDemandSupported`), `M/ui/home/HomeScreen.kt:1123-1127`, `R/values/strings.xml`
- Test: `T/ondemand/OnDemandPlannerQualifierTest.kt`, `T/ui/home/directions/PlannerFallbackGateTest.kt`, `T/ui/home/ondemand/OnDemandSheetsViewModelTest.kt` (append)

**Interfaces:**
- Consumes: `OnDemandMatch`, `sortedSoonestUsable`, `TIER_ELIGIBILITY` (Tasks 2–3); `MapViewModel.probeOnDemandExact` (Task 9); `onDemandDeployment` (Task 7); `OnDemandSheetsViewModel` (Task 12); `TripPlanError.Category`, `TripEndpoint.hasCoordinates`; `ServiceDisc`, `metaLine`, `cardPrimary`, `CardPrimary` (Task 11); `LoadingContent`.
- Produces: `data class PlannerQualification(qualifying: List<OnDemandMatch>, hiddenCount: Int)`; `fun qualifyingServices(origin: List<OnDemandMatch>, destination: List<OnDemandMatch>): PlannerQualification`; `internal fun showsOnDemandOptions(category, fromHasCoordinates: Boolean, toHasCoordinates: Boolean, onDemandSupported: Boolean): Boolean`; `sealed interface PlannerFallbackState { Loading(origin: ProbePoint); Ready(origin: ProbePoint, originMatches: List<OnDemandMatch>, qualification: PlannerQualification) }`; `OnDemandSheetsViewModel.{planner: StateFlow<PlannerFallbackState?>, openPlanner(origin: GeoPoint, destination: GeoPoint, probeExact: suspend (GeoPoint) -> List<OnDemandMatch>?), closePlanner()}`; `MapViewModel.onDemandSupported: StateFlow<Boolean>`; `DirectionsErrorSnackbar(error, onDismiss, modifier, onOnDemandOptions: (() -> Unit)? = null)`; `@Composable fun OnDemandPlannerFallbackSheet(state, colors, now, onOpenDetail: (OnDemandMatch, ProbePoint) -> Unit, onCall, onOpenUrl, onShowAll: (List<OnDemandMatch>, ProbePoint) -> Unit, onDismiss)`.

- [ ] **Step 1: Strings**

Append to `R/values/strings.xml` after the Task 15 block:

```xml
    <!-- Trip planner fallback (DRT UI spec §3.8) -->
    <string name="ondemand_planner_section">On-demand options</string>
    <string name="ondemand_planner_serves_both">Serves both locations</string>
    <string name="ondemand_planner_caption">Services that need eligibility or cover only one end are hidden.</string>
    <string name="ondemand_planner_show_all">Show all in the zone picker</string>
    <string name="ondemand_planner_empty">No on-demand service covers both locations.</string>
    <string name="ondemand_planner_status_now">Status shown for now.</string>
```

- [ ] **Step 2: Write the failing tests**

Create `T/ondemand/OnDemandPlannerQualifierTest.kt`:

```kotlin
package org.onebusaway.android.ondemand

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.models.EligibilityRequirement
import org.onebusaway.android.models.OnDemandEligibility
import org.onebusaway.android.models.OnDemandMatchReason
import org.onebusaway.android.models.OnDemandService
import org.onebusaway.android.util.GeoPoint

class OnDemandPlannerQualifierTest {

    private val now = instant("2026-03-10T14:00:00-04:00")

    /** [insideArea] has distance 0 (contains the probe), every other area is 3 km away. */
    private fun at(service: OnDemandService, insideArea: String?): OnDemandMatch {
        val areas = service.areas.map { it.copy(distanceToAreaMeters = if (it.id == insideArea) 0.0 else 3_000.0, nearestPointOnBoundary = GeoPoint(45.0, -85.0)) }
        val reason = if (insideArea == null) OnDemandMatchReason.AREA_NEARBY else OnDemandMatchReason.AREA_CONTAINS_POINT
        return matchFor(service.copy(areas = areas, matchReason = reason), now)
    }

    private val singleZone = service(id = "zone", rules = listOf(rule(fromIds = listOf("CC_area"), toIds = listOf("CC_area"))))
    private val aToB = service(id = "ab", areas = listOf(area(id = "A", name = "A"), area(id = "B", name = "B")), rules = listOf(rule(fromIds = listOf("A"), toIds = listOf("B"))))
    private val bToA = aToB.copy(id = "ba", rules = listOf(rule(fromIds = listOf("B"), toIds = listOf("A"))))

    @Test
    fun `a single zone containing both ends qualifies`() {
        val result = qualifyingServices(listOf(at(singleZone, "CC_area")), listOf(at(singleZone, "CC_area")))
        assertEquals(listOf("zone"), result.qualifying.map { it.service.id })
        assertEquals(0, result.hiddenCount)
    }

    @Test
    fun `zone to zone qualifies only in the rule's direction`() {
        assertEquals(listOf("ab"), qualifyingServices(listOf(at(aToB, "A")), listOf(at(aToB, "B"))).qualifying.map { it.service.id })
        val wrongWay = qualifyingServices(listOf(at(bToA, "A")), listOf(at(bToA, "B")))
        assertTrue(wrongWay.qualifying.isEmpty())
        assertEquals(1, wrongWay.hiddenCount)
    }

    @Test
    fun `eligibility required and one end outside are hidden`() {
        val restricted = singleZone.copy(eligibility = OnDemandEligibility(EligibilityRequirement.CERTIFICATION_REQUIRED, null))
        val hidden = qualifyingServices(listOf(at(restricted, "CC_area")), listOf(at(restricted, "CC_area")))
        assertTrue(hidden.qualifying.isEmpty())
        assertEquals(1, hidden.hiddenCount)
        val oneEnd = qualifyingServices(listOf(at(singleZone, "CC_area")), listOf(at(singleZone, null)))
        assertTrue(oneEnd.qualifying.isEmpty())
        assertEquals(1, oneEnd.hiddenCount)
    }
}
```

Create `T/ui/home/directions/PlannerFallbackGateTest.kt`:

```kotlin
package org.onebusaway.android.ui.home.directions

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.ui.tripplan.TripPlanError

class PlannerFallbackGateTest {

    @Test
    fun `the action shows for no-route and schedule failures with two coordinates on a supported server`() {
        assertTrue(showsOnDemandOptions(TripPlanError.Category.NO_ROUTE, fromHasCoordinates = true, toHasCoordinates = true, onDemandSupported = true))
        assertTrue(showsOnDemandOptions(TripPlanError.Category.SCHEDULE, fromHasCoordinates = true, toHasCoordinates = true, onDemandSupported = true))
        assertFalse(showsOnDemandOptions(TripPlanError.Category.CONNECTIVITY, fromHasCoordinates = true, toHasCoordinates = true, onDemandSupported = true))
        assertFalse(showsOnDemandOptions(TripPlanError.Category.NO_ROUTE, fromHasCoordinates = false, toHasCoordinates = true, onDemandSupported = true))
        assertFalse(showsOnDemandOptions(TripPlanError.Category.NO_ROUTE, fromHasCoordinates = true, toHasCoordinates = true, onDemandSupported = false))
    }
}
```

Append to `T/ui/home/ondemand/OnDemandSheetsViewModelTest.kt` (inside the class):

```kotlin
    @Test
    fun `the planner probes both ends and qualifies the services that cover both`() = runTest {
        val vm = OnDemandSheetsViewModel(GatedResolver(), TimeProvider { now.toEpochMilli() })
        val origin = GeoPoint(45.05, -85.1)
        val destination = GeoPoint(45.06, -85.1)
        vm.openPlanner(origin, destination) { point -> if (point == origin || point == destination) listOf(open) else emptyList() }
        val ready = vm.planner.value as PlannerFallbackState.Ready
        assertEquals(listOf("open"), ready.qualification.qualifying.map { it.service.id })
        assertEquals(ProbeSource.Point(null), ready.origin.source)
        vm.closePlanner()
        assertNull(vm.planner.value)
    }

    @Test
    fun `an unanswerable probe leaves the planner empty rather than failing`() = runTest {
        val vm = OnDemandSheetsViewModel(GatedResolver(), TimeProvider { now.toEpochMilli() })
        vm.openPlanner(GeoPoint(45.05, -85.1), GeoPoint(45.06, -85.1)) { null }
        val ready = vm.planner.value as PlannerFallbackState.Ready
        assertEquals(0, ready.qualification.qualifying.size)
        assertEquals(0, ready.qualification.hiddenCount)
    }
```

- [ ] **Step 3: Run the tests to verify they fail**

Run: `./gradlew :onebusaway-android:testObaMaplibreDebugUnitTest --tests 'org.onebusaway.android.ondemand.OnDemandPlannerQualifierTest' --tests 'org.onebusaway.android.ui.home.directions.PlannerFallbackGateTest' --tests 'org.onebusaway.android.ui.home.ondemand.OnDemandSheetsViewModelTest'`
Expected: compilation FAILS (`Unresolved reference: qualifyingServices`).

- [ ] **Step 4: Qualifier, gate, view-model state**

Create `M/ondemand/OnDemandPlannerQualifier.kt`:

```kotlin
package org.onebusaway.android.ondemand

/** The planner fallback's answer (spec §3.8): the services that serve both ends, and how many were hidden. */
data class PlannerQualification(val qualifying: List<OnDemandMatch>, val hiddenCount: Int)

/**
 * A service qualifies when it contains both ends, is not tier 4, and some rule goes from an area
 * containing the origin (`distanceToArea == 0` there) to an area containing the destination. Areas
 * are read off the server's per-area distances; no client containment is used.
 */
fun qualifyingServices(origin: List<OnDemandMatch>, destination: List<OnDemandMatch>): PlannerQualification {
    val originInside = origin.filter { it.isInside }.associateBy { it.service.id }
    val destinationInside = destination.filter { it.isInside }.associateBy { it.service.id }
    val qualifying = originInside.values.filter { atOrigin ->
        val atDestination = destinationInside[atOrigin.service.id] ?: return@filter false
        if (atOrigin.availability.usabilityTier == TIER_ELIGIBILITY) return@filter false
        val originAreas = atOrigin.service.areas.filter { it.distanceToAreaMeters == 0.0 }.map { it.id }.toSet()
        val destinationAreas = atDestination.service.areas.filter { it.distanceToAreaMeters == 0.0 }.map { it.id }.toSet()
        atOrigin.service.rules.any { rule -> rule.fromIds.any { it in originAreas } && rule.toIds.any { it in destinationAreas } }
    }.sortedSoonestUsable()
    val considered = (originInside.keys + destinationInside.keys).size
    return PlannerQualification(qualifying, hiddenCount = considered - qualifying.size)
}
```

Create `M/ui/home/directions/PlannerFallbackGate.kt`:

```kotlin
package org.onebusaway.android.ui.home.directions

import org.onebusaway.android.ui.tripplan.TripPlanError

/** Spec §3.8: the "On-demand options" action appears for a no-route or schedule failure with two coordinates on a server not known unsupported. */
internal fun showsOnDemandOptions(category: TripPlanError.Category, fromHasCoordinates: Boolean, toHasCoordinates: Boolean, onDemandSupported: Boolean): Boolean =
    onDemandSupported &&
        (category == TripPlanError.Category.NO_ROUTE || category == TripPlanError.Category.SCHEDULE) &&
        fromHasCoordinates &&
        toHasCoordinates
```

In `M/ui/home/ondemand/OnDemandSheetsViewModel.kt` add:

```kotlin
/** The planner fallback sheet's content (spec §3.8). */
sealed interface PlannerFallbackState {
    data class Loading(val origin: ProbePoint) : PlannerFallbackState
    data class Ready(val origin: ProbePoint, val originMatches: List<OnDemandMatch>, val qualification: PlannerQualification) : PlannerFallbackState
}
```

and inside the view model:

```kotlin
    private val _planner = MutableStateFlow<PlannerFallbackState?>(null)
    val planner: StateFlow<PlannerFallbackState?> = _planner.asStateFlow()
    private var plannerJob: Job? = null

    /**
     * Probe both ends through [probeExact] (the exact-point cache) and qualify. An unanswerable end
     * reads as no matches, so the sheet says nothing covers both rather than failing.
     */
    fun openPlanner(origin: GeoPoint, destination: GeoPoint, probeExact: suspend (GeoPoint) -> List<OnDemandMatch>?) {
        val probe = ProbePoint(origin, ProbeSource.Point(null))
        plannerJob?.cancel()
        _planner.value = PlannerFallbackState.Loading(probe)
        plannerJob = viewModelScope.launch {
            val atOrigin = probeExact(origin) ?: emptyList()
            val atDestination = probeExact(destination) ?: emptyList()
            _planner.value = PlannerFallbackState.Ready(probe, atOrigin, qualifyingServices(atOrigin, atDestination))
        }
    }

    fun closePlanner() {
        plannerJob?.cancel()
        _planner.value = null
    }
```

Imports: `kotlinx.coroutines.Job`, `org.onebusaway.android.ondemand.PlannerQualification`, `org.onebusaway.android.ondemand.ProbeSource`, `org.onebusaway.android.ondemand.qualifyingServices`, `org.onebusaway.android.util.GeoPoint`.

In `M/map/MapViewModel.kt` add beside `onDemandColors`:

```kotlin
    /** Whether the current deployment may serve `/api/ondemand`: unknown counts as supported until a 404 says otherwise. */
    val onDemandSupported: StateFlow<Boolean> = onDemandDeployment(regionRepo, prefsRepository, demoMode)
        .map { deployment -> deployment != null && !onDemandSupport.isKnownUnsupported(deployment) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
```

- [ ] **Step 5: The snackbar action and the sheet**

In `M/ui/home/directions/DirectionsFeature.kt` change `DirectionsErrorSnackbar` to:

```kotlin
@Composable
fun DirectionsErrorSnackbar(
    error: TripPlanError,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    // Spec §3.8: opens the on-demand fallback when the failure is one an on-demand ride could answer.
    onOnDemandOptions: (() -> Unit)? = null
) {
    Surface(
        // A polite live region so a screen reader announces a newly surfaced planning failure.
        modifier = modifier.fillMaxWidth().navigationBarsPadding().padding(8.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        shadowElevation = 6.dp
    ) {
        Column {
            Row(
                modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(error.category.headerRes),
                        style = MaterialTheme.typography.titleSmall,
                        color = colorResource(error.category.severity.colorRes)
                    )
                    Text(
                        text = stringResource(error.detailRes),
                        modifier = Modifier.padding(top = 2.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.9f)
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = AppIcons.Close,
                        contentDescription = stringResource(R.string.dismiss),
                        tint = MaterialTheme.colorScheme.inverseOnSurface
                    )
                }
            }
            if (onOnDemandOptions != null) {
                TextButton(onClick = onOnDemandOptions, modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)) {
                    Text(stringResource(R.string.ondemand_planner_section), color = MaterialTheme.colorScheme.inversePrimary)
                }
            }
        }
    }
}
```

(`Column`, `TextButton` are already imported in that file.) Create `M/ui/home/directions/OnDemandPlannerFallbackSheet.kt`:

```kotlin
package org.onebusaway.android.ui.home.directions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.Instant
import org.onebusaway.android.R
import org.onebusaway.android.ondemand.ONDEMAND_OUTSIDE_GRAY
import org.onebusaway.android.ondemand.OnDemandMatch
import org.onebusaway.android.ondemand.ProbePoint
import org.onebusaway.android.ui.compose.components.LoadingContent
import org.onebusaway.android.ui.compose.components.SheetDragHandle
import org.onebusaway.android.ui.home.ondemand.CardPrimary
import org.onebusaway.android.ui.home.ondemand.PlannerFallbackState
import org.onebusaway.android.ui.home.ondemand.ServiceDisc
import org.onebusaway.android.ui.home.ondemand.cardPrimary
import org.onebusaway.android.ui.home.ondemand.metaLine

private val CARD_RADIUS = 22.dp
private val PILL_HEIGHT = 40.dp

/** Spec §3.8 (screen 5): the services that serve both planned ends, with the caption and the picker link. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnDemandPlannerFallbackSheet(
    state: PlannerFallbackState,
    colors: Map<String, Int>,
    now: Instant,
    onOpenDetail: (OnDemandMatch, ProbePoint) -> Unit,
    onCall: (String) -> Unit,
    onOpenUrl: (String) -> Unit,
    onShowAll: (List<OnDemandMatch>, ProbePoint) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        dragHandle = { SheetDragHandle() },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.ondemand_planner_section), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            when (state) {
                is PlannerFallbackState.Loading -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { LoadingContent() }
                is PlannerFallbackState.Ready -> ReadyContent(state, colors, now, onOpenDetail, onCall, onOpenUrl, onShowAll)
            }
        }
    }
}

@Composable
private fun ReadyContent(
    state: PlannerFallbackState.Ready,
    colors: Map<String, Int>,
    now: Instant,
    onOpenDetail: (OnDemandMatch, ProbePoint) -> Unit,
    onCall: (String) -> Unit,
    onOpenUrl: (String) -> Unit,
    onShowAll: (List<OnDemandMatch>, ProbePoint) -> Unit
) {
    val qualifying = state.qualification.qualifying
    val hidden = state.qualification.hiddenCount
    if (qualifying.isEmpty() && hidden == 0) {
        Text(stringResource(R.string.ondemand_planner_empty), style = MaterialTheme.typography.bodyMedium)
        return
    }
    qualifying.forEach { match ->
        PlannerServiceCard(match, colors[match.service.id] ?: ONDEMAND_OUTSIDE_GRAY, now, { onOpenDetail(match, state.origin) }, onCall, onOpenUrl)
    }
    Text(stringResource(R.string.ondemand_planner_status_now), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(stringResource(R.string.ondemand_planner_caption), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(
        text = stringResource(R.string.ondemand_planner_show_all),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.clickable { onShowAll(state.originMatches.filter { it.isInside }, state.origin) }.padding(vertical = 4.dp)
    )
}

/** The screen-1 card without its footer: name, "Serves both locations", the meta line and one primary pill. */
@Composable
private fun PlannerServiceCard(match: OnDemandMatch, color: Int, now: Instant, onOpenDetail: () -> Unit, onCall: (String) -> Unit, onOpenUrl: (String) -> Unit) {
    val primary = cardPrimary(match.service)
    Surface(shape = RoundedCornerShape(CARD_RADIUS), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth().clickable(onClick = onOpenDetail), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ServiceDisc(color)
                Column(Modifier.weight(1f)) {
                    Text(match.service.name, style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.ondemand_planner_serves_both), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(metaLine(match.availability, now), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(painterResource(R.drawable.ic_navigation_chevron_right), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            when (primary) {
                is CardPrimary.Call -> Button(onClick = { onCall(primary.phone) }, modifier = Modifier.fillMaxWidth().height(PILL_HEIGHT)) {
                    Icon(painterResource(R.drawable.ic_call), contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.ondemand_card_call_to_book), modifier = Modifier.padding(start = 8.dp))
                }
                is CardPrimary.Online -> Button(onClick = { onOpenUrl(primary.url) }, modifier = Modifier.fillMaxWidth().height(PILL_HEIGHT)) {
                    Icon(painterResource(R.drawable.ic_open_in_new), contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.ondemand_book_online), modifier = Modifier.padding(start = 8.dp))
                }
                null -> Unit
            }
        }
    }
}
```

- [ ] **Step 6: Host it**

In `M/ui/home/HomeScreen.kt` replace the `DirectionsErrorSnackbar(` call with:

```kotlin
                                        directionsError != null -> {
                                            val onDemandSupported by mapViewModel.onDemandSupported.collectAsStateWithLifecycle()
                                            val from = tripPlanFormState.from
                                            val to = tripPlanFormState.to
                                            val offersOnDemand = showsOnDemandOptions(directionsError.category, from.hasCoordinates, to.hasCoordinates, onDemandSupported)
                                            DirectionsErrorSnackbar(
                                                error = directionsError,
                                                onDismiss = tripPlanViewModel::clearPlanResult,
                                                modifier = Modifier.align(Alignment.BottomCenter),
                                                onOnDemandOptions = if (offersOnDemand) {
                                                    {
                                                        val origin = GeoPoint(requireNotNull(from.lat), requireNotNull(from.lon))
                                                        val destination = GeoPoint(requireNotNull(to.lat), requireNotNull(to.lon))
                                                        onDemandSheetsViewModel.openPlanner(origin, destination, mapViewModel::probeOnDemandExact)
                                                    }
                                                } else {
                                                    null
                                                }
                                            )
                                        }
```

(`hasCoordinates` guarantees the `requireNotNull`s hold.) Import `org.onebusaway.android.ui.home.directions.showsOnDemandOptions`. In `M/ui/home/ondemand/OnDemandDockHost.kt` add after the picker block, with `val planner by sheetsViewModel.planner.collectAsStateWithLifecycle()` beside the other collections:

```kotlin
    planner?.let { state ->
        val brand = colorResource(R.color.brand_color).toArgb()
        val services = (state as? PlannerFallbackState.Ready)?.qualification?.qualifying?.map { it.service } ?: emptyList()
        OnDemandPlannerFallbackSheet(
            state = state,
            colors = remember(services, brand) { resolveServiceColors(services, brand) },
            now = sheetsViewModel.now(),
            onOpenDetail = { match, probe ->
                sheetsViewModel.closePlanner()
                onOpenDetail(match.service.id, LocationCheck(probe.source, match.isInside, locality = null, point = probe.point))
            },
            onCall = actions.call,
            onOpenUrl = actions.openUrl,
            onShowAll = { matches, probe ->
                sheetsViewModel.closePlanner()
                sheetsViewModel.openPicker(matches, probe, nearby = false)
            },
            onDismiss = sheetsViewModel::closePlanner
        )
    }
```

Imports: `androidx.compose.ui.graphics.toArgb`, `androidx.compose.ui.res.colorResource`, `org.onebusaway.android.R`, `org.onebusaway.android.ondemand.resolveServiceColors`, `org.onebusaway.android.ui.home.directions.OnDemandPlannerFallbackSheet`.

- [ ] **Step 7: Run the tests and the gates, commit**

Run: `./gradlew :onebusaway-android:testObaMaplibreDebugUnitTest --tests 'org.onebusaway.android.ondemand.OnDemandPlannerQualifierTest' --tests 'org.onebusaway.android.ui.home.directions.PlannerFallbackGateTest' --tests 'org.onebusaway.android.ui.home.ondemand.OnDemandSheetsViewModelTest'`
Expected: PASS.
Run: `./gradlew :onebusaway-android:compileObaGoogleDebugKotlin :onebusaway-android:compileObaMaplibreDebugKotlin -PwarningsAsErrors=true`
Expected: BUILD SUCCESSFUL.

```bash
./gradlew spotlessApply
git add onebusaway-android/src/main onebusaway-android/src/test
git commit -m "Offer on-demand services when no trip is found" -m "A no-route or schedule failure with two coordinates gains an
\"On-demand options\" action that probes both ends exactly and lists
the services whose rules go from an area containing the origin to one
containing the destination, hiding the rest behind the picker link."
```

---

### Task 18: Map layers state — view model, flavour capabilities, basemap preference

**Files:**
- Create: `M/map/Basemap.kt`, `M/map/MapFlavourCapabilities.kt`, `M/ui/home/map/MapLayersState.kt`, `M/ui/home/map/MapLayersViewModel.kt`
- Modify: `onebusaway-android/build.gradle.kts:96-108` (flavour `buildConfigField`s), `M/app/di/AppModule.kt` (one `@Provides`), `R/values/donottranslate.xml`, `R/values/strings.xml`, `M/map/MapHost.kt` (`basemap` flow), `G/compose/GoogleComposeAdapter.kt` (apply the map type), `M/map/MapViewModel.kt` (`syncRentalLayersFromPreferences`)
- Test: `T/ui/home/map/MapLayersStateTest.kt`, `T/ui/home/map/MapLayersViewModelTest.kt`

**Interfaces:**
- Consumes: `onDemandDeployment` (Task 7); `OnDemandSupport`; `RentalLayer.defaultVisible`, `RENTALS_VISIBLE_BY_DEFAULT` (`M/map/rental/RentalLayerPreferences.kt`); `BikeshareAvailability.isStationLayerEnabled(region, otpUrl)`; `RentalLayerController.syncFromPreferences()`.
- Produces: `enum class Basemap(prefValue: String, labelRes: Int) { STANDARD, SATELLITE, HYBRID }`; `fun basemapFromPref(value: String?): Basemap`; `data class MapFlavourCapabilities(val hasBasemapChoice: Boolean)`; `BuildConfig.MAP_HAS_BASEMAP_CHOICE`; `enum class LayerTileId { ON_DEMAND_ZONES, BIKES, SCOOTERS }`; `enum class LayerGroup { TRANSIT, RENTALS }`; `data class LayerTile(id, titleRes: Int, iconRes: Int, enabled: Boolean, group: LayerGroup)`; `data class MapLayersUiState(basemap: Basemap?, transit: List<LayerTile>, rentals: List<LayerTile>, differsFromDefaults: Boolean, enabledCount: Int) { val isEmpty }` with `MapLayersUiState.EMPTY`; `data class LayerPrefs(zones, rentalsMaster, bikes, scooters, basemap) { DEFAULTS }`; `fun layersUiState(prefs: LayerPrefs, onDemandTileVisible: Boolean, rentalsEnabled: Boolean, hasBasemapChoice: Boolean): MapLayersUiState`; `fun LayerPrefs.toggled(id: LayerTileId): LayerPrefs`; `@HiltViewModel class MapLayersViewModel(prefs, regionRepo, onDemandSupport, demoMode, capabilities) { val state; fun refresh(); fun toggle(id); fun setBasemap(basemap); fun reset() }`; `MapHost.basemap: StateFlow<Basemap>`; `MapViewModel.syncRentalLayersFromPreferences()`.

- [ ] **Step 1: Preference key, strings, flavour flag, capabilities**

In `R/values/donottranslate.xml` after `preference_key_layer_scooters_visible`: `<string name="preference_key_basemap">preference_basemap</string>`.

Append to `R/values/strings.xml` after the Task 17 block:

```xml
    <!-- Map layers sheet (DRT UI spec §3.9) -->
    <string name="map_layers_basemap_standard">Standard</string>
    <string name="map_layers_basemap_satellite">Satellite</string>
    <string name="map_layers_basemap_hybrid">Hybrid</string>
    <string name="map_layers_on_demand_zones">On-demand zones</string>
    <string name="map_layers_bikes">Bikes</string>
    <string name="map_layers_scooters">Scooters</string>
```

In `onebusaway-android/build.gradle.kts` inside `create("google") { … }` add `buildConfigField("boolean", "MAP_HAS_BASEMAP_CHOICE", "true")` and inside `create("maplibre") { … }` add `buildConfigField("boolean", "MAP_HAS_BASEMAP_CHOICE", "false")`.

Create `M/map/MapFlavourCapabilities.kt`:

```kotlin
package org.onebusaway.android.map

/** What the map SDK of this flavour can do that the other cannot (spec §3.9): only Google offers a basemap choice. */
data class MapFlavourCapabilities(val hasBasemapChoice: Boolean)
```

Create `M/map/Basemap.kt`:

```kotlin
package org.onebusaway.android.map

import androidx.annotation.StringRes
import org.onebusaway.android.R

/** The rider's basemap (spec §3.9), persisted under `preference_key_basemap`; only the Google flavour draws it. */
enum class Basemap(val prefValue: String, @StringRes val labelRes: Int) {
    STANDARD("standard", R.string.map_layers_basemap_standard),
    SATELLITE("satellite", R.string.map_layers_basemap_satellite),
    HYBRID("hybrid", R.string.map_layers_basemap_hybrid)
}

fun basemapFromPref(value: String?): Basemap = Basemap.entries.firstOrNull { it.prefValue == value } ?: Basemap.STANDARD
```

In `M/app/di/AppModule.kt` beside `provideTimeProvider`:

```kotlin
    @Provides
    @Singleton
    fun provideMapFlavourCapabilities(): MapFlavourCapabilities = MapFlavourCapabilities(hasBasemapChoice = BuildConfig.MAP_HAS_BASEMAP_CHOICE)
```

with imports `org.onebusaway.android.BuildConfig` and `org.onebusaway.android.map.MapFlavourCapabilities`.

- [ ] **Step 2: Write the failing tests**

Create `T/ui/home/map/MapLayersStateTest.kt`:

```kotlin
package org.onebusaway.android.ui.home.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.map.Basemap

class MapLayersStateTest {

    private val defaults = LayerPrefs.DEFAULTS

    @Test
    fun `defaults read zones on and both rental tiles off`() {
        val state = layersUiState(defaults, onDemandTileVisible = true, rentalsEnabled = true, hasBasemapChoice = true)
        assertEquals(Basemap.STANDARD, state.basemap)
        assertEquals(listOf(LayerTileId.ON_DEMAND_ZONES to true), state.transit.map { it.id to it.enabled })
        assertEquals(listOf(LayerTileId.BIKES to false, LayerTileId.SCOOTERS to false), state.rentals.map { it.id to it.enabled })
        assertFalse(state.differsFromDefaults)
        assertEquals(1, state.enabledCount)
    }

    @Test
    fun `groups and the basemap hide when unsupported, disabled or unavailable`() {
        val state = layersUiState(defaults, onDemandTileVisible = false, rentalsEnabled = false, hasBasemapChoice = false)
        assertNull(state.basemap)
        assertTrue(state.transit.isEmpty())
        assertTrue(state.rentals.isEmpty())
        assertTrue(state.isEmpty)
    }

    @Test
    fun `turning a rental tile on lights the master and off drops it when nothing else is on`() {
        val bikesOn = defaults.toggled(LayerTileId.BIKES)
        assertTrue(bikesOn.rentalsMaster)
        assertTrue(bikesOn.bikes)
        assertEquals(true, layersUiState(bikesOn, true, true, true).rentals.first { it.id == LayerTileId.BIKES }.enabled)
        val scootersToo = bikesOn.toggled(LayerTileId.SCOOTERS)
        val bikesOff = scootersToo.toggled(LayerTileId.BIKES)
        assertTrue("scooters still on keeps the master", bikesOff.rentalsMaster)
        assertFalse(bikesOff.bikes)
        val allOff = bikesOff.toggled(LayerTileId.SCOOTERS)
        assertFalse(allOff.rentalsMaster)
        assertFalse(allOff.scooters)
        assertTrue(allOff.differsFrom(defaults))
    }

    @Test
    fun `a fresh install with the bikes preference already true still reads off until the master is on`() {
        val state = layersUiState(defaults.copy(bikes = true), true, true, true)
        assertFalse(state.rentals.first { it.id == LayerTileId.BIKES }.enabled)
        assertFalse(state.differsFromDefaults)
    }
}
```

Create `T/ui/home/map/MapLayersViewModelTest.kt`:

```kotlin
package org.onebusaway.android.ui.home.map

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.onebusaway.android.R
import org.onebusaway.android.api.data.OnDemandSupport
import org.onebusaway.android.demo.FakeDemoModeState
import org.onebusaway.android.map.Basemap
import org.onebusaway.android.map.MapFlavourCapabilities
import org.onebusaway.android.region.FakeRegionRepository
import org.onebusaway.android.region.region
import org.onebusaway.android.testing.FakePreferencesRepository
import org.onebusaway.android.testing.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class MapLayersViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val endpoint = "https://maglev.example.org/"

    /** The shipped defaults, set explicitly because the fake seeds un-set booleans from `observeValue`. */
    private fun defaultPrefs(bikeshare: Boolean = true) = FakePreferencesRepository(observeValue = false).apply {
        setBoolean(R.string.preference_key_show_ondemand_zones, true)
        setBoolean(R.string.preference_key_layer_bikeshare_visible, false)
        setBoolean(R.string.preference_key_layer_bikes_visible, true)
        setBoolean(R.string.preference_key_layer_scooters_visible, false)
        if (bikeshare) setString(R.string.preference_key_otp_api_url, "https://otp.example.org")
    }

    private fun viewModel(prefs: FakePreferencesRepository, support: OnDemandSupport = OnDemandSupport(), basemap: Boolean = true) =
        MapLayersViewModel(prefs, FakeRegionRepository(region(id = 1, obaBaseUrl = endpoint)), support, FakeDemoModeState(), MapFlavourCapabilities(hasBasemapChoice = basemap))

    @Test
    fun `google shows the basemap, maplibre does not`() = runTest {
        val google = viewModel(defaultPrefs())
        advanceUntilIdle()
        assertEquals(Basemap.STANDARD, google.state.value.basemap)
        val maplibre = viewModel(defaultPrefs(), basemap = false)
        advanceUntilIdle()
        assertNull(maplibre.state.value.basemap)
    }

    @Test
    fun `an unsupported deployment hides the transit group and no bikeshare hides rentals`() = runTest {
        val support = OnDemandSupport().apply { recordAbsent(endpoint) }
        val vm = viewModel(defaultPrefs(bikeshare = false), support)
        advanceUntilIdle()
        assertTrue(vm.state.value.transit.isEmpty())
        assertTrue(vm.state.value.rentals.isEmpty())
    }

    @Test
    fun `toggling bikes writes the layer preference and the master, and the badge counts it`() = runTest {
        val prefs = defaultPrefs()
        val vm = viewModel(prefs)
        advanceUntilIdle()
        assertEquals(1, vm.state.value.enabledCount)
        vm.toggle(LayerTileId.BIKES)
        advanceUntilIdle()
        assertTrue(prefs.getBoolean(R.string.preference_key_layer_bikeshare_visible, false))
        assertTrue(prefs.getBoolean(R.string.preference_key_layer_bikes_visible, false))
        assertTrue(vm.state.value.rentals.first { it.id == LayerTileId.BIKES }.enabled)
        assertTrue(vm.state.value.differsFromDefaults)
        assertEquals(2, vm.state.value.enabledCount)

        vm.toggle(LayerTileId.BIKES)
        advanceUntilIdle()
        assertFalse(prefs.getBoolean(R.string.preference_key_layer_bikeshare_visible, true))
        assertFalse(vm.state.value.rentals.first { it.id == LayerTileId.BIKES }.enabled)
        // The bikes preference is now false where the default is true, so Reset stays offered.
        assertTrue(vm.state.value.differsFromDefaults)
        assertEquals(1, vm.state.value.enabledCount)
    }

    @Test
    fun `reset restores the defaults and the basemap`() = runTest {
        val prefs = defaultPrefs()
        val vm = viewModel(prefs)
        vm.setBasemap(Basemap.HYBRID)
        vm.toggle(LayerTileId.ON_DEMAND_ZONES)
        advanceUntilIdle()
        assertEquals(Basemap.HYBRID, vm.state.value.basemap)
        assertTrue(vm.state.value.differsFromDefaults)
        vm.reset()
        advanceUntilIdle()
        assertEquals(Basemap.STANDARD, vm.state.value.basemap)
        assertTrue(prefs.getBoolean(R.string.preference_key_show_ondemand_zones, false))
        assertFalse(vm.state.value.differsFromDefaults)
    }

    @Test
    fun `refresh notices a deployment recorded unsupported since the last emission`() = runTest {
        val support = OnDemandSupport()
        val vm = viewModel(defaultPrefs(), support)
        advanceUntilIdle()
        assertEquals(1, vm.state.value.transit.size)
        support.recordAbsent(endpoint)
        vm.refresh()
        advanceUntilIdle()
        assertTrue(vm.state.value.transit.isEmpty())
    }
}
```

- [ ] **Step 3: Run the tests to verify they fail**

Run: `./gradlew :onebusaway-android:testObaMaplibreDebugUnitTest --tests 'org.onebusaway.android.ui.home.map.MapLayersStateTest' --tests 'org.onebusaway.android.ui.home.map.MapLayersViewModelTest'`
Expected: compilation FAILS (`Unresolved reference: LayerPrefs`).

- [ ] **Step 4: The pure state**

Create `M/ui/home/map/MapLayersState.kt`:

```kotlin
package org.onebusaway.android.ui.home.map

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import org.onebusaway.android.R
import org.onebusaway.android.map.Basemap
import org.onebusaway.android.map.rental.RENTALS_VISIBLE_BY_DEFAULT
import org.onebusaway.android.map.rental.RentalLayer
import org.onebusaway.android.map.rental.defaultVisible

enum class LayerTileId { ON_DEMAND_ZONES, BIKES, SCOOTERS }

enum class LayerGroup { TRANSIT, RENTALS }

/** One tile of the sheet's grid (spec §3.9): what it is, and whether it reads On. */
data class LayerTile(val id: LayerTileId, @StringRes val titleRes: Int, @DrawableRes val iconRes: Int, val enabled: Boolean, val group: LayerGroup)

data class MapLayersUiState(
    /** Null when this flavour offers no basemap choice. */
    val basemap: Basemap?,
    val transit: List<LayerTile>,
    val rentals: List<LayerTile>,
    val differsFromDefaults: Boolean,
    /** Enabled tiles among the visible ones: the FAB's badge. */
    val enabledCount: Int
) {
    /** Nothing to show, so neither the sheet nor its button appears. */
    val isEmpty: Boolean get() = basemap == null && transit.isEmpty() && rentals.isEmpty()

    companion object {
        val EMPTY = MapLayersUiState(basemap = null, transit = emptyList(), rentals = emptyList(), differsFromDefaults = false, enabledCount = 0)
    }
}

/** The five preferences the sheet reads and writes; the existing keys keep their names and defaults (spec §2.10). */
data class LayerPrefs(val zones: Boolean, val rentalsMaster: Boolean, val bikes: Boolean, val scooters: Boolean, val basemap: Basemap) {

    fun differsFrom(other: LayerPrefs): Boolean = this != other

    /**
     * Spec §3.9: a tile's displayed state is `master && layerPref`. Turning a tile on sets its layer
     * preference and the master; turning it off clears its preference and drops the master when no
     * layer preference remains true. `RentalLayerController.syncFromPreferences()` reads the result unchanged.
     */
    fun toggled(id: LayerTileId): LayerPrefs = when (id) {
        LayerTileId.ON_DEMAND_ZONES -> copy(zones = !zones)
        LayerTileId.BIKES -> {
            val on = !(rentalsMaster && bikes)
            copy(bikes = on, rentalsMaster = on || scooters)
        }
        LayerTileId.SCOOTERS -> {
            val on = !(rentalsMaster && scooters)
            copy(scooters = on, rentalsMaster = on || bikes)
        }
    }

    companion object {
        val DEFAULTS = LayerPrefs(
            zones = true,
            rentalsMaster = RENTALS_VISIBLE_BY_DEFAULT,
            bikes = RentalLayer.BIKES.defaultVisible,
            scooters = RentalLayer.SCOOTERS.defaultVisible,
            basemap = Basemap.STANDARD
        )
    }
}

fun layersUiState(prefs: LayerPrefs, onDemandTileVisible: Boolean, rentalsEnabled: Boolean, hasBasemapChoice: Boolean): MapLayersUiState {
    val transit = if (onDemandTileVisible) {
        listOf(LayerTile(LayerTileId.ON_DEMAND_ZONES, R.string.map_layers_on_demand_zones, R.drawable.ic_directions_car, prefs.zones, LayerGroup.TRANSIT))
    } else {
        emptyList()
    }
    val rentals = if (rentalsEnabled) {
        listOf(
            LayerTile(LayerTileId.BIKES, R.string.map_layers_bikes, R.drawable.ic_directions_bike, prefs.rentalsMaster && prefs.bikes, LayerGroup.RENTALS),
            LayerTile(LayerTileId.SCOOTERS, R.string.map_layers_scooters, R.drawable.ic_kick_scooter, prefs.rentalsMaster && prefs.scooters, LayerGroup.RENTALS)
        )
    } else {
        emptyList()
    }
    return MapLayersUiState(
        basemap = prefs.basemap.takeIf { hasBasemapChoice },
        transit = transit,
        rentals = rentals,
        differsFromDefaults = prefs.differsFrom(LayerPrefs.DEFAULTS),
        enabledCount = (transit + rentals).count { it.enabled }
    )
}
```

- [ ] **Step 5: The view model, the host's basemap, the rental sync**

Create `M/ui/home/map/MapLayersViewModel.kt`:

```kotlin
package org.onebusaway.android.ui.home.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.onebusaway.android.R
import org.onebusaway.android.api.data.OnDemandSupport
import org.onebusaway.android.demo.DemoModeState
import org.onebusaway.android.map.Basemap
import org.onebusaway.android.map.MapFlavourCapabilities
import org.onebusaway.android.map.basemapFromPref
import org.onebusaway.android.map.onDemandDeployment
import org.onebusaway.android.map.rental.RENTALS_VISIBLE_BY_DEFAULT
import org.onebusaway.android.map.rental.RentalLayer
import org.onebusaway.android.map.rental.defaultVisible
import org.onebusaway.android.preferences.PreferencesRepository
import org.onebusaway.android.region.RegionRepository
import org.onebusaway.android.util.BikeshareAvailability

/**
 * The map layers sheet's state (spec §3.9), derived reactively from the five preferences, the region
 * and the on-demand support verdict. Writes go straight to the preferences; the rental loader
 * re-reads them through `MapViewModel.syncRentalLayersFromPreferences()` after each tap.
 */
@HiltViewModel
class MapLayersViewModel @Inject constructor(
    private val prefs: PreferencesRepository,
    regionRepo: RegionRepository,
    private val onDemandSupport: OnDemandSupport,
    demoMode: DemoModeState,
    capabilities: MapFlavourCapabilities
) : ViewModel() {

    private val _state = MutableStateFlow(MapLayersUiState.EMPTY)
    val state: StateFlow<MapLayersUiState> = _state.asStateFlow()

    // Bumped by [refresh] so a 404 recorded since the last emission is noticed when the sheet opens.
    private val refreshTick = MutableStateFlow(0)

    init {
        viewModelScope.launch {
            val layerPrefs = combine(
                prefs.observeBoolean(R.string.preference_key_show_ondemand_zones, true),
                prefs.observeBoolean(R.string.preference_key_layer_bikeshare_visible, RENTALS_VISIBLE_BY_DEFAULT),
                prefs.observeBoolean(R.string.preference_key_layer_bikes_visible, RentalLayer.BIKES.defaultVisible),
                prefs.observeBoolean(R.string.preference_key_layer_scooters_visible, RentalLayer.SCOOTERS.defaultVisible),
                prefs.observeString(R.string.preference_key_basemap, null)
            ) { zones, master, bikes, scooters, basemap -> LayerPrefs(zones, master, bikes, scooters, basemapFromPref(basemap)) }
            combine(
                layerPrefs,
                onDemandDeployment(regionRepo, prefs, demoMode),
                regionRepo.region.combine(demoMode.active) { region, demo -> region to demo },
                prefs.observeString(R.string.preference_key_otp_api_url, null),
                refreshTick
            ) { current, deployment, (region, demo), otpUrl, _ ->
                layersUiState(
                    prefs = current,
                    onDemandTileVisible = deployment != null && !onDemandSupport.isKnownUnsupported(deployment),
                    rentalsEnabled = demo || BikeshareAvailability.isStationLayerEnabled(region, otpUrl),
                    hasBasemapChoice = capabilities.hasBasemapChoice
                )
            }.distinctUntilChanged().collect { _state.value = it }
        }
    }

    /** Re-derive the state — called when the sheet opens, since a 404 verdict arrives without a preference change. */
    fun refresh() {
        refreshTick.value++
    }

    fun toggle(id: LayerTileId) = write(current().toggled(id))

    fun setBasemap(basemap: Basemap) = prefs.setString(R.string.preference_key_basemap, basemap.prefValue)

    /** Spec §3.9 Reset: zones on, rentals master off, bikes on, scooters off, basemap standard. */
    fun reset() = write(LayerPrefs.DEFAULTS)

    private fun current() = LayerPrefs(
        zones = prefs.getBoolean(R.string.preference_key_show_ondemand_zones, true),
        rentalsMaster = prefs.getBoolean(R.string.preference_key_layer_bikeshare_visible, RENTALS_VISIBLE_BY_DEFAULT),
        bikes = prefs.getBoolean(R.string.preference_key_layer_bikes_visible, RentalLayer.BIKES.defaultVisible),
        scooters = prefs.getBoolean(R.string.preference_key_layer_scooters_visible, RentalLayer.SCOOTERS.defaultVisible),
        basemap = basemapFromPref(prefs.getString(R.string.preference_key_basemap, null))
    )

    private fun write(next: LayerPrefs) {
        prefs.setBoolean(R.string.preference_key_show_ondemand_zones, next.zones)
        prefs.setBoolean(R.string.preference_key_layer_bikeshare_visible, next.rentalsMaster)
        prefs.setBoolean(R.string.preference_key_layer_bikes_visible, next.bikes)
        prefs.setBoolean(R.string.preference_key_layer_scooters_visible, next.scooters)
        prefs.setString(R.string.preference_key_basemap, next.basemap.prefValue)
    }
}
```

In `M/map/MapHost.kt` after `val renderState = MapRenderState()`:

```kotlin
    /** The rider's basemap choice (spec §3.9); only the Google adapter draws anything but [Basemap.STANDARD]. */
    val basemap: StateFlow<Basemap> = prefsRepository.observeString(R.string.preference_key_basemap, null)
        .map(::basemapFromPref)
        .stateIn(scope, SharingStarted.Eagerly, Basemap.STANDARD)
```

with imports `kotlinx.coroutines.flow.SharingStarted`, `kotlinx.coroutines.flow.map`, `kotlinx.coroutines.flow.stateIn` (and `StateFlow` if not already imported). In `G/compose/GoogleComposeAdapter.kt`, inside the `if (activeRenderer != null && activeInfoWindows != null) { … }` block after the on-demand zones `LaunchedEffect`:

```kotlin
            // Spec §3.9: the basemap choice is the google flavour's alone; maplibre keeps its style.
            val activeMap = googleMap
            if (activeMap != null) {
                LaunchedEffect(activeMap) { host.basemap.collect { activeMap.mapType = it.googleMapType() } }
            }
```

and at file level:

```kotlin
private fun Basemap.googleMapType(): Int = when (this) {
    Basemap.STANDARD -> GoogleMap.MAP_TYPE_NORMAL
    Basemap.SATELLITE -> GoogleMap.MAP_TYPE_SATELLITE
    Basemap.HYBRID -> GoogleMap.MAP_TYPE_HYBRID
}
```

with `import org.onebusaway.android.map.Basemap`. In `M/map/MapViewModel.kt` beside `setRentalLayerVisible`:

```kotlin
    /** Re-read the rental preferences after the layers sheet wrote them (spec §3.9). */
    fun syncRentalLayersFromPreferences() = rentalController.syncFromPreferences()
```

- [ ] **Step 6: Run the tests and the gates, commit**

Run: `./gradlew :onebusaway-android:testObaMaplibreDebugUnitTest --tests 'org.onebusaway.android.ui.home.map.MapLayersStateTest' --tests 'org.onebusaway.android.ui.home.map.MapLayersViewModelTest'`
Expected: PASS.
Run: `./gradlew :onebusaway-android:compileObaGoogleDebugKotlin :onebusaway-android:compileObaMaplibreDebugKotlin -PwarningsAsErrors=true`
Expected: BUILD SUCCESSFUL.

```bash
./gradlew spotlessApply
git add onebusaway-android/build.gradle.kts onebusaway-android/src/main onebusaway-android/src/google onebusaway-android/src/test
git commit -m "Derive the map layers sheet's state from preferences" -m "One view model reads the zones, rentals and basemap preferences with
the region and the on-demand verdict, and shows a rental tile as on
only when the master and its own preference agree, so the rental
loader keeps reading the preferences unchanged. The basemap is a new
preference the Google adapter applies; MapLibre has no choice."
```

---

### Task 19: The tile-grid layers sheet and the `LayersFab`

**Files:**
- Create: `M/ui/home/map/MapLayersSheet.kt`, `R/drawable/ic_layers.xml`, `R/drawable/ic_map.xml`, `R/drawable/ic_satellite.xml`
- Modify: `M/ui/home/map/MapChrome.kt` (replace `RentalsFab`/`ModeToggle`), `M/ui/home/map/MapChromeViewModel.kt`, `M/ui/home/map/MapFeature.kt:610-660`, `M/ui/settings/SettingsScreen.kt:311-312`, `R/values/strings.xml`
- Test: `T/ui/home/MapChromeViewModelTest.kt` (update constructions; add one test); `AT/ui/home/map/MapLayersSheetTest.kt`

**Interfaces:**
- Consumes: `MapLayersViewModel`, `MapLayersUiState`, `LayerTile`, `LayerTileId`, `Basemap`, `MapFlavourCapabilities` (Task 18); `onDemandDeployment`; `OnDemandSupport`; `SegmentedChoice`, `SheetDragHandle`; `ScriptedTutorial.KEY_RENTALS`.
- Produces: `@Composable fun MapLayersSheet(state: MapLayersUiState, onToggle: (LayerTileId) -> Unit, onBasemap: (Basemap) -> Unit, onReset: () -> Unit, onDismiss: () -> Unit)`; `object MapLayersTestTags { fun tile(id: LayerTileId): String }`; `MapChrome(zoomVisible, leftHandMode, layersVisible, layersBadge: Int, mapLoading, fabBottomInsetTarget, onMyLocation, onZoomIn, onZoomOut, onOpenLayers, onHideLayersButton)`; `MapChromeViewModel(prefsRepo, regionRepo, demoMode, onDemandSupport, capabilities)` with `layersFab = (rentalsEnabled || onDemandTileVisible || hasBasemapChoice) && shown`.

- [ ] **Step 1: Strings and glyphs**

Append to `R/values/strings.xml` after the Task 18 block:

```xml
    <string name="map_layers_title">Map</string>
    <string name="map_layers_reset">Reset</string>
    <string name="map_layers_done">Done</string>
    <string name="map_layers_group_transit">Transit</string>
    <string name="map_layers_group_rentals">Rentals</string>
    <string name="map_layers_state_on">On</string>
    <string name="map_layers_state_off">Off</string>
    <!-- Content description of the map layers button -->
    <string name="map_layers_open">Map layers</string>
    <string name="map_layers_hide_button">Hide this button</string>
    <string name="layers_button_hidden_toast">Map layers button hidden. Turn it back on in Settings.</string>
    <string name="preferences_show_layers_button_title">Show map layers button</string>
    <string name="preferences_show_layers_button_summary">Display the map layers button on the map</string>
```

Delete from `R/values/strings.xml` — and from every `R/values-*/strings.xml` that carries them (`grep -rln 'layers_rentals_' onebusaway-android/src/main/res/values-*`), since a translation of a deleted string fails lint as `ExtraTranslation` — the strings `layers_rentals_show`, `layers_rentals_hide`, `layers_rentals_loading`, `layers_rentals_hide_button`, `layers_rentals_hidden_toast`, `layers_bikes_label`, `layers_scooters_label`, `preferences_show_rental_button_title`, `preferences_show_rental_button_summary`. Before deleting each, confirm with `grep -rn '<key>' onebusaway-android/src --include=*.kt --include=*.java --include=*.xml` that its only uses are the ones this task removes.

Create the drawables (Material Icons, 24 dp, same wrapper as `ic_directions_car.xml`):

- `ic_layers.xml` path: `M11.99,18.54l-7.37,-5.73L3,14.07l9,7 9,-7 -1.63,-1.27 -7.38,5.74zM12,16l7.36,-5.73L21,9l-9,-7 -9,7 1.63,1.27L12,16z`
- `ic_map.xml` path: `M20.5,3l-0.16,0.03L15,5.1 9,3 3.36,4.9c-0.21,0.07 -0.36,0.25 -0.36,0.48V20.5c0,0.28 0.22,0.5 0.5,0.5l0.16,-0.03L9,18.9l6,2.1 5.64,-1.9c0.21,-0.07 0.36,-0.25 0.36,-0.48V3.5c0,-0.28 -0.22,-0.5 -0.5,-0.5zM15,19l-6,-2.11V5l6,2.11V19z`
- `ic_satellite.xml` path (Material `satellite`, standing in for Symbols' `satellite_alt`): `M19,3H5c-1.1,0 -2,0.9 -2,2v14c0,1.1 0.9,2 2,2h14c1.1,0 2,-0.9 2,-2V5c0,-1.1 -0.9,-2 -2,-2zM5,4.99h3C8,6.65 6.66,8 5,8V4.99zM5,12v-2c2.76,0 5,-2.25 5,-5.01h2C12,8.86 8.87,12 5,12zM5,18l3.5,-4.5 2.5,3.01L14.5,12l4.5,6H5z`

- [ ] **Step 2: Update and extend the chrome view-model tests**

In `T/ui/home/MapChromeViewModelTest.kt` every `MapChromeViewModel(prefs, FakeRegionRepository(), FakeDemoModeState())` construction gains two trailing arguments: `MapChromeViewModel(prefs, FakeRegionRepository(), FakeDemoModeState(), OnDemandSupport(), MapFlavourCapabilities(hasBasemapChoice = false))` (imports `org.onebusaway.android.api.data.OnDemandSupport`, `org.onebusaway.android.map.MapFlavourCapabilities`). Append:

```kotlin
    @Test
    fun `without bikeshare the layers button still shows when on-demand may be supported or a basemap choice exists`() = runTest {
        val prefs = rentalButtonShown()
        val endpoint = "https://maglev.example.org/"
        val regions = FakeRegionRepository(region(id = 1, obaBaseUrl = endpoint))
        val support = OnDemandSupport()
        val maybeSupported = MapChromeViewModel(prefs, regions, FakeDemoModeState(), support, MapFlavourCapabilities(hasBasemapChoice = false))
        advanceUntilIdle()
        assertTrue(maybeSupported.state.value.layersFab)

        support.recordAbsent(endpoint)
        val unsupported = MapChromeViewModel(prefs, regions, FakeDemoModeState(), support, MapFlavourCapabilities(hasBasemapChoice = false))
        advanceUntilIdle()
        assertFalse(unsupported.state.value.layersFab)

        val google = MapChromeViewModel(prefs, regions, FakeDemoModeState(), support, MapFlavourCapabilities(hasBasemapChoice = true))
        advanceUntilIdle()
        assertTrue(google.state.value.layersFab)
    }
```

Run: `./gradlew :onebusaway-android:testObaMaplibreDebugUnitTest --tests 'org.onebusaway.android.ui.home.MapChromeViewModelTest'`
Expected: compilation FAILS (the constructor has three parameters).

- [ ] **Step 3: The chrome view model**

In `M/ui/home/map/MapChromeViewModel.kt`:

```kotlin
@HiltViewModel
class MapChromeViewModel @Inject constructor(
    prefsRepo: PreferencesRepository,
    regionRepo: RegionRepository,
    demoMode: DemoModeState,
    onDemandSupport: OnDemandSupport,
    capabilities: MapFlavourCapabilities
) : ViewModel() {
```

and in the `combine`, replace the fifth flow `prefsRepo.observeString(R.string.preference_key_otp_api_url, null)` with `onDemandDeployment(regionRepo, prefsRepo, demoMode).combine(prefsRepo.observeString(R.string.preference_key_otp_api_url, null)) { deployment, otpUrl -> deployment to otpUrl }`, destructure it as `(deployment, otpUrl)`, and compute:

```kotlin
                val rentalsEnabled = demoActive || BikeshareAvailability.isStationLayerEnabled(region, otpUrl)
                val rentalsOn = rentalsEnabled && master
                // Spec §3.9: the button reaches the on-demand tile in regions without bikeshare, and the
                // basemap control on the google flavour; it hides only when the sheet would be empty.
                val onDemandTileVisible = deployment != null && !onDemandSupport.isKnownUnsupported(deployment)
                MapChromeState(
                    zoomControls = zoomControls,
                    leftHand = leftHand,
                    layersFab = (rentalsEnabled || onDemandTileVisible || capabilities.hasBasemapChoice) && shown,
                    rentalsActive = rentalsOn,
                    bikesActive = rentalsOn && bikes,
                    scootersActive = rentalsOn && scooters
                )
```

Imports: `org.onebusaway.android.api.data.OnDemandSupport`, `org.onebusaway.android.map.MapFlavourCapabilities`, `org.onebusaway.android.map.onDemandDeployment`.

- [ ] **Step 4: The sheet**

Create `M/ui/home/map/MapLayersSheet.kt`:

```kotlin
package org.onebusaway.android.ui.home.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.onebusaway.android.R
import org.onebusaway.android.map.Basemap
import org.onebusaway.android.ui.compose.components.SegmentedChoice
import org.onebusaway.android.ui.compose.components.SheetDragHandle

/** Stable handles for the on-device test. */
object MapLayersTestTags {
    fun tile(id: LayerTileId): String = "mapLayersTile_${id.name}"
}

private val TILE_HEIGHT = 62.dp
private val TILE_RADIUS = 16.dp
private val ICON_WELL = 34.dp

/**
 * The map layers sheet (spec §3.9, screen A): the basemap control (google only), then a 2-column tile
 * grid per group. A tile takes its group's tint when on; the group disappears when it has no tiles.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapLayersSheet(
    state: MapLayersUiState,
    onToggle: (LayerTileId) -> Unit,
    onBasemap: (Basemap) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        dragHandle = { SheetDragHandle() },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (state.differsFromDefaults) TextButton(onClick = onReset) { Text(stringResource(R.string.map_layers_reset)) }
                }
                Text(stringResource(R.string.map_layers_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.map_layers_done)) }
                }
            }
            state.basemap?.let { selected ->
                SegmentedChoice(
                    options = Basemap.entries,
                    selected = selected,
                    onChange = onBasemap,
                    label = stringResource(R.string.map_layers_title),
                    optionLabel = { it.labelRes },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                )
            }
            if (state.transit.isNotEmpty()) TileGroup(stringResource(R.string.map_layers_group_transit), state.transit, MaterialTheme.colorScheme.primary, onToggle)
            if (state.rentals.isNotEmpty()) TileGroup(stringResource(R.string.map_layers_group_rentals), state.rentals, colorResource(R.color.layer_bikeshare_color), onToggle)
        }
    }
}

@Composable
private fun TileGroup(title: String, tiles: List<LayerTile>, tint: Color, onToggle: (LayerTileId) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        tiles.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { tile -> LayerTileView(tile, tint, onToggle, Modifier.weight(1f)) }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun LayerTileView(tile: LayerTile, tint: Color, onToggle: (LayerTileId) -> Unit, modifier: Modifier = Modifier) {
    val on = tile.enabled
    val contentColor = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Surface(
        modifier = modifier
            .height(TILE_HEIGHT)
            .toggleable(value = on, role = Role.Switch, onValueChange = { onToggle(tile.id) })
            .testTag(MapLayersTestTags.tile(tile.id)),
        shape = RoundedCornerShape(TILE_RADIUS),
        color = if (on) tint else MaterialTheme.colorScheme.surface,
        contentColor = contentColor
    ) {
        Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier.size(ICON_WELL).background(if (on) Color.White.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(painterResource(tile.iconRes), contentDescription = null, tint = contentColor, modifier = Modifier.size(20.dp))
            }
            Column {
                Text(stringResource(tile.titleRes), style = MaterialTheme.typography.labelLarge)
                Text(stringResource(if (on) R.string.map_layers_state_on else R.string.map_layers_state_off), style = MaterialTheme.typography.labelSmall, color = contentColor.copy(alpha = 0.8f))
            }
        }
    }
}
```

- [ ] **Step 5: The FAB and the wiring**

In `M/ui/home/map/MapChrome.kt` change the signature and the layers branch to:

```kotlin
@Composable
fun MapChrome(
    zoomVisible: Boolean,
    leftHandMode: Boolean,
    layersVisible: Boolean,
    /** Enabled layer tiles, shown as the button's badge (0 hides it). */
    layersBadge: Int,
    mapLoading: Boolean,
    fabBottomInsetTarget: Dp,
    onMyLocation: () -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onOpenLayers: () -> Unit,
    onHideLayersButton: () -> Unit
) {
```

```kotlin
        if (layersVisible) {
            LayersFab(
                badgeCount = layersBadge,
                onClick = onOpenLayers,
                onHide = onHideLayersButton,
                modifier = Modifier
                    .align(sideAlign)
                    .padding(horizontal = marginHorizontal)
                    // Clear the my-location FAB below: it occupies marginBottom..marginBottom+FAB_SIZE.
                    .padding(bottom = marginBottom + FAB_SIZE + LAYERS_FAB_GAP + fabBottomInset)
                    // The scripted tour's micromobility step spotlights this control (#2164).
                    .tutorialAnchor(LocalTutorialState.current, ScriptedTutorial.KEY_RENTALS)
            )
        }
```

Replace `RentalsFab` and `ModeToggle` (and the `RENTAL_*` / `MODE_TOGGLE_*` constants) with:

```kotlin
/**
 * The map layers button (spec §3.9), replacing the rentals speed-dial: one white FAB with the layers
 * glyph and a badge counting the enabled tiles; long-press offers to hide it, as before.
 */
@Composable
private fun LayersFab(badgeCount: Int, onClick: () -> Unit, onHide: () -> Unit, modifier: Modifier = Modifier) {
    val accent = colorResource(R.color.theme_accent)
    var menuOpen by remember { mutableStateOf(false) }
    Box(modifier) {
        Surface(
            modifier = Modifier
                .size(FAB_SIZE)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { menuOpen = true },
                    onLongClickLabel = stringResource(R.string.map_layers_hide_button)
                ),
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            contentColor = accent,
            shadowElevation = 6.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_layers), contentDescription = stringResource(R.string.map_layers_open), modifier = Modifier.size(26.dp))
            }
        }
        if (badgeCount > 0) {
            Text(
                text = badgeCount.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .background(accent, CircleShape)
                    .padding(horizontal = 6.dp, vertical = 1.dp)
            )
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.map_layers_hide_button)) },
                onClick = {
                    menuOpen = false
                    onHide()
                }
            )
        }
    }
}

/** M3's standard FAB diameter, which the my-location button is. Not exposed as a token by Material 3. */
private val FAB_SIZE = 56.dp

/** Clear air between the layers button and the my-location FAB beneath it. */
private val LAYERS_FAB_GAP = 16.dp
```

Delete the imports the removed code used (`AnimatedVisibility`, `animateColorAsState`, `BorderStroke`, `border`, `toggleable`, `CircularProgressIndicator`, `Row`?, `width`, `ConversionUtils`, `unitsAreMetric`, `IconButton` if only `ZoomControls` uses it keep it, `Role`/`contentDescription`/`semantics` if unused) — `spotlessApply` strips unused imports.

In `M/ui/home/map/MapFeature.kt` replace the `MapChrome(...)` call and the `toggleRentals` helper with:

```kotlin
    val chrome by hiltViewModel<MapChromeViewModel>().state.collectAsStateWithLifecycle()
    val layersViewModel = hiltViewModel<MapLayersViewModel>()
    val layersState by layersViewModel.state.collectAsStateWithLifecycle()
    var layersSheetOpen by remember { mutableStateOf(false) }
    val mapLoading by mapViewModel.progress.collectAsStateWithLifecycle()
    MapChrome(
        zoomVisible = chrome.zoomControls,
        leftHandMode = chrome.leftHand,
        // Hidden while directions own the map (#2168) and when the sheet would be empty (spec §3.9).
        layersVisible = chrome.layersFab && currentFocus !is CurrentFocus.Directions && !layersState.isEmpty,
        layersBadge = layersState.enabledCount,
        mapLoading = mapLoading,
        fabBottomInsetTarget = fabBottomInset,
        onMyLocation = {
            // Reset the prefs that suppress the enable-location / permission prompts, then recenter.
            PreferenceUtils.saveBoolean(
                resources.getString(R.string.preference_key_never_show_location_dialog),
                false
            )
            PreferenceUtils.setUserDeniedLocationPermissions(context, false)
            mapViewModel.requestMyLocation(useDefaultZoom = true, animate = true)
            AnalyticsEntryPoint.get(context).reportUiEvent(
                PlausibleAnalytics.REPORT_MAP_EVENT_URL,
                resources.getString(R.string.analytics_label_button_press_location),
                null
            )
        },
        onZoomIn = { mapViewModel.zoomIn() },
        onZoomOut = { mapViewModel.zoomOut() },
        onOpenLayers = {
            // A 404 verdict recorded since the last preference change is only seen on a refresh.
            layersViewModel.refresh()
            layersSheetOpen = true
        },
        onHideLayersButton = {
            PreferenceUtils.saveBoolean(resources.getString(R.string.preference_key_show_rental_button), false)
            // The button is the only signpost to itself, so hiding it without saying where it went
            // would look like a bug. The toast names Settings, which is the one way back.
            Toast.makeText(context, R.string.layers_button_hidden_toast, Toast.LENGTH_LONG).show()
        }
    )
    if (layersSheetOpen) {
        MapLayersSheet(
            state = layersState,
            onToggle = { id ->
                val wasOn = (layersState.transit + layersState.rentals).firstOrNull { it.id == id }?.enabled == true
                layersViewModel.toggle(id)
                if (id != LayerTileId.ON_DEMAND_ZONES) {
                    mapViewModel.syncRentalLayersFromPreferences()
                    reportRentalLayerChange(context, activated = !wasOn)
                }
            },
            onBasemap = layersViewModel::setBasemap,
            onReset = {
                layersViewModel.reset()
                mapViewModel.syncRentalLayersFromPreferences()
            },
            onDismiss = { layersSheetOpen = false }
        )
    }
```

```kotlin
/**
 * Reports a rental tile change to the long-standing bikeshare analytics event rather than a new one,
 * so the series that has been counting "the rider changed the rental overlay" keeps counting the same thing.
 */
private fun reportRentalLayerChange(context: Context, activated: Boolean) {
    AnalyticsEntryPoint.get(context).reportUiEvent(
        PlausibleAnalytics.REPORT_MAP_EVENT_URL,
        context.getString(R.string.analytics_layer_bikeshare),
        context.getString(
            if (activated) R.string.analytics_label_bikeshare_activated else R.string.analytics_label_bikeshare_deactivated
        )
    )
}
```

Remove the now-unused `rentalsLoading` collection and the `RentalLayer` import if nothing else in the file uses them. In `M/ui/settings/SettingsScreen.kt:311-312` use `R.string.preferences_show_layers_button_title` / `R.string.preferences_show_layers_button_summary`.

- [ ] **Step 6: The on-device test**

Create `AT/ui/home/map/MapLayersSheetTest.kt`:

```kotlin
package org.onebusaway.android.ui.home.map

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.onebusaway.android.R
import org.onebusaway.android.ui.compose.createUnconfinedComposeRule
import org.onebusaway.android.ui.compose.theme.ObaTheme

class MapLayersSheetTest {

    @get:Rule
    val composeRule = createUnconfinedComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun tappingATileFlipsItsSubtitleAndResetAppears() {
        composeRule.setContent {
            var prefs by remember { mutableStateOf(LayerPrefs.DEFAULTS) }
            val state = layersUiState(prefs, onDemandTileVisible = true, rentalsEnabled = true, hasBasemapChoice = false)
            ObaTheme {
                MapLayersSheet(state = state, onToggle = { prefs = prefs.toggled(it) }, onBasemap = {}, onReset = { prefs = LayerPrefs.DEFAULTS }, onDismiss = {})
            }
        }
        val zones = composeRule.onNodeWithTag(MapLayersTestTags.tile(LayerTileId.ON_DEMAND_ZONES))
        zones.assertTextContains(context.getString(R.string.map_layers_state_on))
        zones.performClick()
        zones.assertTextContains(context.getString(R.string.map_layers_state_off))
        composeRule.onNodeWithText(context.getString(R.string.map_layers_reset)).assertIsDisplayed().performClick()
        zones.assertTextContains(context.getString(R.string.map_layers_state_on))
    }
}
```

- [ ] **Step 7: Run the tests and the gates, commit**

Run: `./gradlew :onebusaway-android:testObaMaplibreDebugUnitTest --tests 'org.onebusaway.android.ui.home.MapChromeViewModelTest' --tests 'org.onebusaway.android.ui.home.map.MapLayersViewModelTest'`
Expected: PASS.
Run: `./gradlew :onebusaway-android:compileObaGoogleDebugKotlin :onebusaway-android:compileObaMaplibreDebugKotlin :onebusaway-android:compileObaMaplibreDebugAndroidTestKotlin -PwarningsAsErrors=true`
Expected: BUILD SUCCESSFUL.

```bash
./gradlew spotlessApply
git add onebusaway-android/src/main onebusaway-android/src/test onebusaway-android/src/androidTest
git commit -m "Replace the rentals speed-dial with a map layers sheet" -m "One layers button opens a tile grid: the on-demand zones tile, the
rental tiles and, on Google, the basemap control, with Reset when
anything differs from the defaults. The button now shows wherever the
sheet has something to offer, so the zones tile is reachable in
regions without bikeshare."
```

---

### Task 20: Copy catalogue audit, full test runs and the manual emulator exercise

**Files:**
- Modify: `R/values/strings.xml` only if the audit finds a missing or leftover key.

**Interfaces:**
- Consumes: everything above; the local maglev at `/Users/aaron/repos/onebusaway/maglev` on branch `gtfs-flex` with `configs/flex-charlevoix.json`.
- Produces: a branch whose unit suites pass on both flavours and whose §6.3 checks have been walked on the emulator.

- [ ] **Step 1: Audit the catalogue keys**

Run from the worktree root:

```bash
S=onebusaway-android/src/main/res/values/strings.xml
for k in ondemand_card_eyebrow ondemand_status_open_until ondemand_status_open_now_until ondemand_status_open ondemand_status_opens ondemand_booking_opens ondemand_status_book_by ondemand_book_by ondemand_status_closed ondemand_tag_same_day ondemand_tag_advance ondemand_tag_no_notice ondemand_tag_eligibility ondemand_card_call_to_book ondemand_book_online ondemand_card_details ondemand_card_more_services ondemand_bar_inside ondemand_bar_inside_near_edge_north ondemand_bar_inside_near_edge_northeast ondemand_bar_inside_near_edge_east ondemand_bar_inside_near_edge_southeast ondemand_bar_inside_near_edge_south ondemand_bar_inside_near_edge_southwest ondemand_bar_inside_near_edge_west ondemand_bar_inside_near_edge_northwest ondemand_bar_outside_north ondemand_bar_outside_northeast ondemand_bar_outside_east ondemand_bar_outside_southeast ondemand_bar_outside_south ondemand_bar_outside_southwest ondemand_bar_outside_west ondemand_bar_outside_northwest ondemand_bar_badge ondemand_bar_a11y_show_edge ondemand_bar_a11y_zoom_out ondemand_bar_a11y_call ondemand_bar_a11y_more_services ondemand_picker_title ondemand_picker_title_nearby ondemand_picker_subtitle_location ondemand_picker_subtitle_center ondemand_picker_subtitle_point ondemand_picker_your_location ondemand_picker_map_center ondemand_picker_selected_place ondemand_picker_footer ondemand_detail_location_inside ondemand_detail_location_outside ondemand_detail_center_inside ondemand_detail_center_outside ondemand_detail_point_inside ondemand_detail_point_outside ondemand_detail_location_sub ondemand_detail_includes_location ondemand_where_title ondemand_detail_service_area ondemand_detail_drop_off ondemand_detail_zone_count ondemand_detail_no_service ondemand_open_agency_website ondemand_more_info ondemand_planner_section ondemand_planner_serves_both ondemand_planner_caption ondemand_planner_show_all ondemand_planner_empty ondemand_planner_status_now ondemand_address_inside ondemand_address_inside_more ondemand_address_outside ondemand_call preferences_show_layers_button_title layers_button_hidden_toast map_layers_group_transit map_layers_group_rentals map_layers_state_on map_layers_state_off map_layers_title map_layers_reset map_layers_done map_layers_on_demand_zones map_layers_bikes map_layers_scooters map_layers_basemap_standard map_layers_basemap_satellite map_layers_basemap_hybrid; do
  grep -q "name=\"$k\"" "$S" || echo "MISSING $k"
done
for k in layers_rentals_show layers_rentals_hide layers_rentals_loading layers_rentals_hide_button layers_rentals_hidden_toast layers_bikes_label layers_scooters_label preferences_show_rental_button_title preferences_show_rental_button_summary ondemand_details_title; do
  grep -rn "name=\"$k\"\|R.string.$k" onebusaway-android/src && echo "LEFTOVER $k"
done
```

Expected: no `MISSING` and no `LEFTOVER` lines. Fix any finding in `strings.xml` (or the translation files for a leftover) before continuing.

- [ ] **Step 2: Run every unit suite on both flavours and compile the on-device suite**

Run: `./gradlew :onebusaway-android:testObaMaplibreDebugUnitTest :onebusaway-android:testObaGoogleDebugUnitTest -PwarningsAsErrors=true`
Expected: BUILD SUCCESSFUL, every test green.
Run: `./gradlew :onebusaway-android:compileObaGoogleDebugKotlin :onebusaway-android:compileObaMaplibreDebugKotlin :onebusaway-android:compileObaGoogleDebugAndroidTestKotlin :onebusaway-android:compileObaMaplibreDebugAndroidTestKotlin -PwarningsAsErrors=true`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Start the local maglev and the emulator**

```bash
cd /Users/aaron/repos/onebusaway/maglev && git branch --show-current   # expect gtfs-flex
make build && ./bin/maglev -f configs/flex-charlevoix.json &            # port 4124
sleep 8 && curl -s "http://localhost:4124/api/ondemand/services-for-location.json?key=test&lat=45.3&lon=-85.2&radius=5000&geometryDetail=none" | head -c 400
emulator -avd Medium_Phone_API_35 -no-snapshot-load &
adb wait-for-device
cd /Users/aaron/repos/onebusaway/.worktrees/android-gtfs-flex && ./gradlew :onebusaway-android:installObaGoogleDebug
```

In the app: Settings → custom API URL `http://10.0.2.2:4124`; in Extended Controls → Location set `45.3, -85.2` (Charlevoix). The stop `CC_CC_Ironton_Ferry_East` is inside the Dial-a-Ride zone.

- [ ] **Step 4: Run the on-device tests this plan added**

```bash
./gradlew :onebusaway-android:connectedObaGoogleDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.onebusaway.android.ui.home.ondemand.OnDemandZoneCardTest,org.onebusaway.android.ui.home.ondemand.OnDemandDockBarTest,org.onebusaway.android.ui.home.directions.NavigateHereCoverageRenderTest,org.onebusaway.android.ui.home.map.MapLayersSheetTest
```

Expected: all pass.

- [ ] **Step 5: Walk spec §6.3 on the google build, then repeat 1, 3, 8 and 11 on `installObaMaplibreDebug`**

Check, in order, and note anything that differs:

1. Zoomed to the county (region level): the Charlevoix services draw as polygons with one labelled pin each, every pin inside its own polygon.
2. The zone card appears above the sheet edge with the top service and the correct "more services" count; its primary button opens the dialer.
3. Zoom in below the street gate (~4 km of visible height): pins vanish, strokes thicken to 4 dp (Google shows the halo; MapLibre shows the polylines), the bar appears; with two overlapping services it has page dots and a "1 of 2" badge.
4. Set the location 50 m inside an edge: within a probe the title reads "Inside · edge … <direction>" with a distance.
5. Set the location 500 m outside: the bar goes gray, the title reads "… <direction> of the zone", the chevron pans to the boundary.
6. Set the location 6 km outside: the bar disappears.
7. Long-press the bar: the picker lists every match in tier order with "N services nearby"; TalkBack exposes "All services here".
8. Open each detail: tags, the status line or the promoted deadline row, the location row, the thumbnail with the dot, Where (for a zone-to-zone service), When with a "No service" row for the absent weekdays, How to book; "More information" absent when it would equal the website.
9. Long-press the map inside and then just outside a zone: the bubble's third line reads "Inside …" / "Outside …" and opens the zone.
10. Plan a trip between two points inside one zone with no fixed route: the snackbar offers "On-demand options" and the sheet lists the service with "Serves both locations".
11. Open the layers sheet: toggle "On-demand zones" off — the polygons and the dock vanish, the long-pressed pin still reads Inside/Outside; Reset appears; Reset restores the tile.
12. With a tier-1 service on page 1, move the emulator clock past its `runningUntil`: within 2 s the title changes from "Pickups available here" to "Opens …".
13. Rotate to landscape: the dock sits bottom-start at 360 dp, never spans the map, and the FAB stack is not overlapped.
14. Point the custom URL at a server without `/api/ondemand` (a stock `main` maglev on another port): nothing new appears and the layers sheet has no on-demand tile.

- [ ] **Step 6: Commit any audit fix**

```bash
./gradlew spotlessApply
git add onebusaway-android/src/main/res
git commit -m "Complete the on-demand copy catalogue" -m "Adds the catalogue keys no earlier task referenced and removes the
rentals-menu strings the layers sheet replaced, so lint reports no
unused or extra translation."
```

(Skip the commit when the audit changed nothing.)

---

## Self-review notes

- **Spec coverage.** §2.1 → Tasks 1, 6, 9; §2.2 → 4, 7; §2.3 → 3, 4, 7, 8, 9; §2.4 → 6, 11, 13; §2.5 → 2; §2.6 → 3; §2.7 → 4, 6, 10; §2.8 → 5, 11, 12, 15 (and the catalogue audit in 20); §2.9 → 1, 2, 14; §2.10 → 6, 13, 17, 18; §3.1 → 4, 7, 8; §3.2 → 7, 8; §3.3 → 11, 13; §3.4 → 5, 6, 9, 10, 11, 13; §3.5 → 12, 13; §3.6 → 14, 15; §3.7 → 6, 16; §3.8 → 17; §3.9 → 18, 19; §5 → 3 (text colour), 11, 15; §6.2 → the tests of each task; §6.3 → 20.
- **Not planned, with the reason.** (1) The region-level pin bitmap is drawn with `android.graphics` (`ZonePinBitmaps`) rather than `ComposeBitmapRenderer` (§3.1): the existing renderer is asynchronous and single-flight, which cannot stamp a viewport of pins in one reconcile pass; the drawn result matches the spec. (2) Distances use a hand-rolled formatter, not `MeasureFormat` (§2.8): `android.icu.text.MeasureFormat` needs API 24 and minSdk is 23; the rounding rules are implemented and tested exactly. (3) The basemap uses Material's `satellite` glyph, and the rental tiles reuse the existing `ic_directions_bike` / `ic_kick_scooter` drawables, in place of Symbols' `satellite_alt` / `pedal_bike` / `electric_scooter` (§4.2 icons). (4) Drop-off names resolve against areas and location groups only; stop names are not carried on `OnDemandService`, so a bare stop id in `toIds` contributes no name (§3.6 item 5). (5) The spec's `probe(point)` on the controller stays private: no Android surface calls it. (6) `ZoneStyle` has five values (`REGION_HIGHLIGHTED` / `STREET_HIGHLIGHTED`) instead of the spec's single `HIGHLIGHTED`, because a highlight draws differently at each level.
- **Type consistency checked:** `OnDemandMatch`, `ProbePoint`, `LocationCheck(point)`, `OnDemandDockState.{Card,Bar}(matches, probe)`, `OnDemandProbeResult`, `ZoneEdge`, `ZoneStyle`, `TextSpec`/`PluralSpec`/`DistanceText`, `OnDemandDockActions`, `PickerRequest`, `PlannerFallbackState`, `LayerPrefs`/`MapLayersUiState`, and the `MapViewModel` surface (`onDemandDock`, `onDemandProbeResult`, `onDemandEdges`, `onDemandGeometry`, `onDemandColors`, `onDemandSupported`, `setOnDemandDockSuppressed`, `highlightOnDemandService`, `zoomOutToOnDemandZones`, `panToOnDemandEdge`, `probeOnDemandExact`, `navigateHereCoverage`, `syncRentalLayersFromPreferences`) are used with the same names and signatures in every task that consumes them.
