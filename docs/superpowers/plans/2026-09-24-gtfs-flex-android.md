# GTFS-Flex (On-Demand Services) Android Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Teach OneBusAway Android to read maglev's `/api/ondemand` namespace and the `onDemandServiceIds` pointer fields, draw on-demand service zones on the home map, surface a stop's on-demand services on the arrivals screen, and show a service page with a client-computed booking deadline.

**Architecture:** New wire DTOs in `api/contract`, three `ObaWebService` endpoints, a wire→domain adapter, and an `OnDemandDataSource` with a three-way `Loaded / Unsupported / Failed` result (mirroring `NearbyArrivalsDataSource`, with `OnDemandSupport` remembering which deployments answered HTTP 404 on the probe). A pure `BookingDeadlineEvaluator` (java.time, agency timezone, noon-anchor service days) implements spec §6 and is verified against the shared vectors file. UI is an `OnDemandLayerController` mirroring `RentalLayerController` that publishes `MapRenderSnapshot.onDemandZones` for both flavor renderers, a Compose `OnDemandServiceScreen` destination, an arrivals-list card, and a Settings toggle.

**Tech Stack:** Kotlin 2.4 / Compose / Hilt / Retrofit + kotlinx.serialization / java.time (desugared) / JUnit4 + kotlinx-coroutines-test with hand-written fakes.

**Spec:** `/private/tmp/claude-501/-Users-aaron-repos-onebusaway-maglev/215fa362-5529-43a3-9cb6-317b7d70341f/scratchpad/spec.md` (§0, §1, §6 normative, §8 Android, §9) and the wire contract `/private/tmp/claude-501/-Users-aaron-repos-onebusaway-maglev/215fa362-5529-43a3-9cb6-317b7d70341f/scratchpad/wiki/GTFS-Flex-Support.md` (§2.1, §2.2, §2.4, §2.5, §3, §3.1, §3.3, §3.4 — JSON shapes there are normative).

Repo worktree: `/Users/aaron/repos/onebusaway/.worktrees/android-gtfs-flex` (branch `gtfs-flex`, off `origin/main` 8046352b). All paths below are relative to that worktree. `M` = `onebusaway-android/src/main/java/org/onebusaway/android`, `T` = `onebusaway-android/src/test/java/org/onebusaway/android`, `RAW` = `onebusaway-android/src/androidTest/res/raw`.

## Global Constraints

- minSdk 23 / JDK 17 / Kotlin 2.4 / Compose. `java.time` is available on minSdk 23 via core-library desugaring (already enabled).
- Compiler and lint warnings are errors. Gate every task with `./gradlew :onebusaway-android:compileObaGoogleDebugKotlin -PwarningsAsErrors=true`. Never add `@Suppress` without a rationale and issue link.
- Both platform flavors must compile: also run `./gradlew :onebusaway-android:compileObaMaplibreDebugKotlin -PwarningsAsErrors=true` whenever flavor source (`src/google`, `src/maplibre`) or anything they consume changes.
- Unit tests: `./gradlew :onebusaway-android:testObaGoogleDebugUnitTest` (JUnit4 + kotlinx-coroutines-test, hand-written fakes; no Mockito, no Robolectric). `android.util.Log` is **unmocked** in JVM tests — code under JVM test must not reach a `Log.*` call.
- Run `./gradlew spotlessApply` before every commit (ktlint `android_studio` style; don't hand-format).
- Use `runCatchingCancellable` (`org.onebusaway.android.util`), never bare `runCatching`, in suspend code. `ObaApiProvider.call` already does this.
- Server timestamps go through the typed time classes in `M/time/TypedTime.kt` (`ServerTime`, `WallTime`, `ElapsedTime`). The booking evaluator's `now` is minted from `WallTime.now()` by the caller (spec §6.3) and converted to `java.time.Instant` at that boundary only; no epoch-ms arithmetic inside the evaluator.
- No unsanctioned heuristics. "Unsupported" is decided **only** by a raw HTTP 404 on `services-for-location` via the existing `isEndpointAbsent` (`M/api/data/NearbyArrivalsDataSource.kt:174`). A 404 on `service/{id}` or `services-for-agency` is an ordinary not-found. Clients never infer `serviceKind` from rules.
- All user-facing strings go in `onebusaway-android/src/main/res/values/strings.xml`; preference keys in `values/donottranslate.xml`. No app-name text, so no `%1$s` app-name placeholders needed.
- Commit style: imperative, sentence case subject (e.g. `Add on-demand wire models`), no `Co-Authored-By` lines.
- The maglev server on branch `gtfs-flex` must have the `/api/ondemand` handlers and `testdata/flex-booking-vectors.json` **before** Task 0 runs (spec §0: client fixtures are captured from the running server).

## Review Focus

1. **A service with zero rules** (degenerate feed, wiki §2.3) opened from a zone tap: the service page must still render name, kind, areas and booking info with an empty "When" section, not crash or show an error — pinned in Task 8 (`presentService` with empty rules).
2. **A MultiPolygon zone with a hole**: the hole must not be filled, and a tap inside the hole must not open the service — pinned in Task 7 (`ZonePolygon.contains` test) and Task 1 (`polygons()` decodes MultiPolygon).
3. **A newer server sends an unknown `serviceKind` / `matchReason`** string: decode must succeed and map to `UNKNOWN` rather than throw — pinned in Task 3 (`fromWire` tests).
4. **`geometryDetail=none` responses omit the `geometry` key entirely** (wiki §3.4): `ServiceAreaDto.polygons()` must return empty and `bbox` still decode — pinned in Task 1 (inline-JSON test).
5. **A stop whose pointer names a service the server then fails to return** (transient error): the arrivals screen must still show arrivals and simply omit the card — pinned in Task 9 (`loadOnDemandItems` drops failures).

---

## File Structure

| File | Responsibility | Action |
|---|---|---|
| `RAW/ondemand_*.json`, `RAW/stop_with_ondemand_pointer.json`, `RAW/route_with_ondemand_pointer.json`, `RAW/flex_booking_vectors.json` | Captured maglev responses + shared booking vectors (JVM decode fixtures) | Create (Task 0) |
| `M/api/contract/OnDemandApiModels.kt` | Wire DTOs for `/api/ondemand` (+ GeoJSON polygon decoder) | Create (Task 1) |
| `M/api/contract/ObaApiModels.kt` | `References` four new lists + finders; `StopReference`/`RouteReference.onDemandServiceIds` | Modify (Task 1) |
| `M/api/contract/ObaWebService.kt` | Three `/api/ondemand` endpoints | Modify (Task 2) |
| `M/demo/DemoObaWebService.kt` | Demo answers for the three endpoints | Modify (Task 2) |
| `M/models/OnDemandService.kt` | Domain model (service, rule, area, booking rule, calendar, `ServiceDayTime`, enums) | Create (Task 3) |
| `M/models/ObaStop.kt`, `M/models/ObaRoute.kt` | `onDemandServiceIds` (default empty) | Modify (Task 3) |
| `M/api/adapters/OnDemandAdapters.kt` | DTO → domain, resolving references | Create (Task 3) |
| `M/api/adapters/StopAdapters.kt`, `RouteAdapters.kt` | `DtoStop`/`DtoRoute` expose pointers | Modify (Task 3) |
| `M/api/data/OnDemandSupport.kt` | `@Singleton` unsupported-deployment memory (keyed by OBA base URL) | Create (Task 4) |
| `M/api/data/OnDemandDataSource.kt` | `OnDemandResult`, `OnDemandDataSource`, `DefaultOnDemandDataSource`, `toOnDemandResult` | Create (Task 4) |
| `M/app/di/RepositoryModule.kt` | `@Binds` for the data source | Modify (Task 4) |
| `M/ondemand/BookingDeadlineEvaluator.kt` | Spec §6 algorithm (pure, java.time) | Create (Task 5) |
| `M/map/render/MapRenderState.kt` | `ZonePolygon`, `MapRenderSnapshot.onDemandZones`, setters | Modify (Task 6) |
| `M/map/OnDemandLayerController.kt` | Viewport-driven zone loader (mirrors `RentalLayerController`) | Create (Task 6) |
| `M/map/MapViewModel.kt` | Construct/start/stop/hide the controller beside the rental one | Modify (Task 6) |
| `values/donottranslate.xml` | `preference_key_show_ondemand_zones` | Modify (Task 6) |
| `M/map/render/ZoneGeometry.kt` | Fill/stroke colours + point-in-polygon hit test | Create (Task 7) |
| `src/google/.../GoogleMapRenderer.kt`, `src/google/.../GoogleComposeAdapter.kt` | Native `Polygon` rendering + polygon click | Modify (Task 7) |
| `src/maplibre/.../MapLibreRenderer.kt`, `src/maplibre/.../MapLibreComposeAdapter.kt` | Classic `PolygonOptions` rendering + hit-tested map click | Modify (Task 7) |
| `M/map/compose/ObaMapCallbacks.kt` | `onOnDemandZoneClick` | Modify (Task 7) |
| `M/ui/nav/NavRoutes.kt` | `ONDEMAND_SERVICE` route | Modify (Task 8) |
| `M/ui/ondemand/OnDemandServiceViewModel.kt`, `OnDemandServicePresentation.kt`, `OnDemandServiceScreen.kt`, `OnDemandDestinations.kt` | Service page | Create (Task 8) |
| `M/ui/home/HomeNavHost.kt`, `HomeScreen.kt`, `M/ui/home/map/MapFeature.kt` | Register graph; wire zone tap → navigation | Modify (Task 8) |
| `M/ui/arrivals/OnDemandItems.kt` | `OnDemandServiceItem` + `loadOnDemandItems` | Create (Task 9) |
| `M/ui/arrivals/ArrivalsRepository.kt`, `ArrivalsUiState.kt`, `ArrivalsViewModel.kt`, `ArrivalsContent.kt`, `ArrivalsDestinations.kt`, `components/ArrivalsPanel.kt`, `components/OnDemandServicesCard.kt`, `M/ui/home/arrivals/ArrivalsSheetHost.kt` | Arrivals card | Modify/Create (Task 9) |
| `M/ui/settings/SettingsUiState.kt`, `SettingsViewModel.kt`, `SettingsScreen.kt` | Zones toggle | Modify (Task 10) |
| `docs/SYSTEM_ARCHITECTURE.md` | Deployment note | Modify (Task 11) |

---

### Task 0: Capture fixtures from a local maglev and copy the booking vectors

**Files:**
- Create: `RAW/ondemand_service_alexandria.json`, `RAW/ondemand_services_for_location_point.json`, `RAW/ondemand_services_for_location_viewport.json`, `RAW/ondemand_services_for_agency_alexandria.json`, `RAW/ondemand_services_for_agency_charlevoix.json`, `RAW/stop_with_ondemand_pointer.json`, `RAW/route_with_ondemand_pointer.json`, `RAW/flex_booking_vectors.json`

**Interfaces:**
- Consumes: maglev on branch `gtfs-flex` at `/Users/aaron/repos/onebusaway/maglev` with `/api/ondemand` implemented; `testdata/alexandria-flex.zip`, `testdata/charlevoix-flex.zip`, `testdata/flex-booking-vectors.json`.
- Produces: the eight fixture files above. Tests read them by relative path (`File("src/androidTest/res/raw/<name>.json")`), exactly as `T/api/StopsMapDecodeTest.kt:46` does.

Steps 1–4 share the shell variables `SCRATCH`, `RAW` and `B`; run them in **one** shell session.

- [ ] **Step 1: Build maglev and write two config files**

```bash
cd /Users/aaron/repos/onebusaway/maglev && git branch --show-current   # expect gtfs-flex
ls internal/restapi | grep -i ondemand                                  # must list the ondemand handlers; stop if empty
ls testdata/flex-booking-vectors.json                                   # must exist; stop if missing
make build
SCRATCH=/private/tmp/claude-501/-Users-aaron-repos-onebusaway-maglev/215fa362-5529-43a3-9cb6-317b7d70341f/scratchpad
cat > "$SCRATCH/config-alexandria.json" <<'EOF'
{ "port": 4000, "env": "development", "api-keys": ["test"], "rate-limit": 1000,
  "gtfs-static-feed": { "url": "testdata/alexandria-flex.zip" }, "gtfs-rt-feeds": [] }
EOF
cat > "$SCRATCH/config-charlevoix.json" <<'EOF'
{ "port": 4000, "env": "development", "api-keys": ["test"], "rate-limit": 1000,
  "gtfs-static-feed": { "url": "testdata/charlevoix-flex.zip" }, "gtfs-rt-feeds": [] }
EOF
```

- [ ] **Step 2: Run against Alexandria and capture five responses**

```bash
cd /Users/aaron/repos/onebusaway/maglev && bin/maglev -f "$SCRATCH/config-alexandria.json" &
MAGLEV_PID=$!; sleep 8; curl -s http://localhost:4000/healthz
RAW=/Users/aaron/repos/onebusaway/.worktrees/android-gtfs-flex/onebusaway-android/src/androidTest/res/raw
B='http://localhost:4000'
curl -s "$B/api/ondemand/service/5088_77652.json?key=test" > "$RAW/ondemand_service_alexandria.json"
curl -s "$B/api/ondemand/services-for-location.json?key=test&lat=38.83&lon=-77.05&radius=600" > "$RAW/ondemand_services_for_location_point.json"
curl -s "$B/api/ondemand/services-for-location.json?key=test&lat=38.83&lon=-77.05&latSpan=0.1&lonSpan=0.1" > "$RAW/ondemand_services_for_location_viewport.json"
curl -s "$B/api/ondemand/services-for-agency/5088.json?key=test" > "$RAW/ondemand_services_for_agency_alexandria.json"
kill $MAGLEV_PID; wait $MAGLEV_PID 2>/dev/null
```

Expected: `ondemand_service_alexandria.json` contains `"id":"5088_77652"`, `"serviceKind":"zone"`, two rules, `"phoneNumber":"703-746-5222"`, `"bbox":[-77.5372039,38.617508,-76.9092198,39.057831]` (wiki §3.4 worked example). Verify with `python3 -c "import json;d=json.load(open('$RAW/ondemand_service_alexandria.json'));e=d['data']['entry'];print(e['id'],e['serviceKind'],len(e['rules']),d['data']['references']['bookingRules'][0]['phoneNumber'])"`.

- [ ] **Step 3: Run against Charlevoix and capture three responses**

Charlevoix's `agency.txt` has `agency_id = CC`; its stop ids already begin with `CC_`, so the combined ids are `CC_CC_Ironton_Ferry_West` and route `CC_CC3`.

```bash
cd /Users/aaron/repos/onebusaway/maglev && bin/maglev -f "$SCRATCH/config-charlevoix.json" &
MAGLEV_PID=$!; sleep 8
curl -s "$B/api/ondemand/services-for-agency/CC.json?key=test" > "$RAW/ondemand_services_for_agency_charlevoix.json"
curl -s "$B/api/where/stop/CC_CC_Ironton_Ferry_West.json?key=test" > "$RAW/stop_with_ondemand_pointer.json"
curl -s "$B/api/where/route/CC_CC3.json?key=test" > "$RAW/route_with_ondemand_pointer.json"
kill $MAGLEV_PID; wait $MAGLEV_PID 2>/dev/null
grep -c '"onDemandServiceIds":\["CC_CC3"\]' "$RAW/stop_with_ondemand_pointer.json" "$RAW/route_with_ondemand_pointer.json"
```

Expected: both greps report `1` (spec §1.1: stop pointer `["CC_CC3"]` on `CC_Ironton_Ferry_West`; route `CC3` carries its own id). The agency list has four services (`CC_CC1`, `CC_CC2_med`, `CC_CC3`, `CC_CC4`).

- [ ] **Step 4: Copy the vectors file**

```bash
cp /Users/aaron/repos/onebusaway/maglev/testdata/flex-booking-vectors.json "$RAW/flex_booking_vectors.json"
python3 -c "import json;d=json.load(open('$RAW/flex_booking_vectors.json'));print(d['timezone'],len(d['calendars']),len(d['vectors']))"
```

Expected: `America/Los_Angeles`, at least one calendar, at least twelve vectors (spec §6 minimum list).

- [ ] **Step 5: Commit**

```bash
cd /Users/aaron/repos/onebusaway/.worktrees/android-gtfs-flex
git add onebusaway-android/src/androidTest/res/raw/ondemand_*.json onebusaway-android/src/androidTest/res/raw/stop_with_ondemand_pointer.json onebusaway-android/src/androidTest/res/raw/route_with_ondemand_pointer.json onebusaway-android/src/androidTest/res/raw/flex_booking_vectors.json
git commit -m "Add captured on-demand API fixtures and booking vectors"
```

---

### Task 1: On-demand wire models and pointer fields

**Files:**
- Create: `M/api/contract/OnDemandApiModels.kt`
- Modify: `M/api/contract/ObaApiModels.kt:62-98` (`References`), `:100-114` (`RouteReference`), `:146-161` (`StopReference`)
- Test: `T/api/OnDemandDecodeTest.kt`; modify `T/api/StopsMapDecodeTest.kt:44-56`

**Interfaces:**
- Consumes: `org.onebusaway.android.util.GeoPoint(latitude, longitude)` (`M/util/GeoPoint.kt:24`).
- Produces (exact, used by Tasks 2–9):

```kotlin
package org.onebusaway.android.api.contract

@Serializable data class OnDemandServiceDto(
    val id: String = "", val agencyId: String = "", val routeId: String? = null, val name: String = "",
    val serviceKind: String = "unknown", val description: String? = null, val url: String? = null,
    val rules: List<AvailabilityRuleDto> = emptyList(), val matchReason: String? = null)
@Serializable data class AvailabilityRuleDto(
    val fromIds: List<String> = emptyList(), val toIds: List<String> = emptyList(),
    val startPickupTime: String? = null, val endPickupTime: String? = null, val endDropOffTime: String? = null,
    val calendarIds: List<String> = emptyList(), val pickupType: Int = 0, val dropOffType: Int = 0,
    val pickupBookingRuleId: String? = null, val dropOffBookingRuleId: String? = null,
    val safeDurationFactor: Double? = null, val safeDurationOffset: Double? = null)
@Serializable data class ServiceAreaDto(
    val id: String = "", val name: String? = null, val description: String? = null,
    val bbox: List<Double> = emptyList(), val geometry: JsonElement? = null,
    val distanceToArea: Double? = null, val nearestPointOnBoundary: List<Double>? = null) {
    fun polygons(): List<List<List<GeoPoint>>>   // [polygon][ring][point]; ring 0 exterior, 1.. holes; empty when no/unknown geometry
}
@Serializable data class LocationGroupDto(val id: String = "", val name: String? = null, val stopIds: List<String> = emptyList())
@Serializable data class BookingRuleDto(
    val id: String = "", val bookingType: Int = 0, val priorNoticeDurationMin: Int? = null, val priorNoticeDurationMax: Int? = null,
    val priorNoticeLastDay: Int? = null, val priorNoticeLastTime: String? = null, val priorNoticeStartDay: Int? = null,
    val priorNoticeStartTime: String? = null, val priorNoticeCalendarId: String? = null, val message: String? = null,
    val pickupMessage: String? = null, val dropOffMessage: String? = null, val phoneNumber: String? = null,
    val infoUrl: String? = null, val bookingUrl: String? = null)
@Serializable data class FlexCalendarDto(val id: String = "", val days: List<String> = emptyList(),
    val startDate: String = "", val endDate: String = "", val exceptedDates: List<String> = emptyList())

// on References:
val serviceAreas: List<ServiceAreaDto> = emptyList(); val locationGroups: List<LocationGroupDto> = emptyList()
val bookingRules: List<BookingRuleDto> = emptyList(); val calendars: List<FlexCalendarDto> = emptyList()
fun serviceArea(id: String): ServiceAreaDto?; fun locationGroup(id: String): LocationGroupDto?
fun bookingRule(id: String): BookingRuleDto?; fun calendar(id: String): FlexCalendarDto?
// on StopReference and RouteReference:
val onDemandServiceIds: List<String> = emptyList()
```

- [ ] **Step 1: Write the failing decode test**

```kotlin
// T/api/OnDemandDecodeTest.kt
package org.onebusaway.android.api

import java.io.File
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.api.contract.EntryWithReferences
import org.onebusaway.android.api.contract.ListWithReferences
import org.onebusaway.android.api.contract.ObaEnvelope
import org.onebusaway.android.api.contract.OnDemandServiceDto
import org.onebusaway.android.api.contract.RouteReference
import org.onebusaway.android.api.contract.ServiceAreaDto
import org.onebusaway.android.api.contract.StopReference

/**
 * Decodes the responses captured from maglev against the Alexandria and Charlevoix flex feeds (see
 * the wiki's §3.4 worked example) and pins the wire contract the on-demand feature reads.
 */
class OnDemandDecodeTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private fun fixture(name: String) = File("src/androidTest/res/raw/$name").readText()

    private val pointModeReasons = setOf("areaContainsPoint", "stopWithinRadius", "areaNearby")
    private val viewportModeReasons = setOf("areaIntersectsViewport", "stopWithinViewport")

    @Test
    fun `service entry decodes the worked example`() {
        val data = json.decodeFromString<ObaEnvelope<EntryWithReferences<OnDemandServiceDto>>>(fixture("ondemand_service_alexandria.json")).requireData()
        val entry = data.entry
        assertEquals("5088_77652", entry.id)
        assertEquals("5088", entry.agencyId)
        assertEquals("5088_77652", entry.routeId)
        assertEquals("zone", entry.serviceKind)
        assertNull(entry.matchReason)
        assertEquals(2, entry.rules.size)
        val first = entry.rules[0]
        assertEquals(listOf("5088_area_1449"), first.fromIds)
        assertEquals("05:00:00", first.startPickupTime)
        assertEquals("24:50:00", first.endPickupTime)
        assertEquals("25:00:00", first.endDropOffTime)
        assertEquals(listOf("5088_c_71675_b_85952_d_63"), first.calendarIds)
        assertEquals(2, first.pickupType)
        assertEquals("5088_booking_route_77652", first.pickupBookingRuleId)
        assertEquals(1.0, first.safeDurationFactor)

        val refs = data.references
        val booking = requireNotNull(refs.bookingRule("5088_booking_route_77652"))
        assertEquals(2, booking.bookingType)
        assertNull(booking.priorNoticeDurationMin)
        assertEquals(1, booking.priorNoticeLastDay)
        assertEquals("17:00:00", booking.priorNoticeLastTime)
        assertEquals(14, booking.priorNoticeStartDay)
        assertEquals("00:00:00", booking.priorNoticeStartTime)
        assertEquals("703-746-5222", booking.phoneNumber)
        assertNotNull(booking.infoUrl)

        val calendar = requireNotNull(refs.calendar("5088_c_71675_b_85952_d_63"))
        assertEquals(listOf("mon", "tue", "wed", "thu", "fri", "sat"), calendar.days)
        assertEquals(listOf("sun"), refs.calendar("5088_c_71675_b_85952_d_64")?.days)
        assertTrue(calendar.exceptedDates.isEmpty())

        assertEquals("America/Los_Angeles", refs.agency("5088")?.timezone)
        assertEquals(listOf("5088_77652"), refs.route("5088_77652")?.onDemandServiceIds)
        assertTrue(refs.locationGroups.isEmpty())
    }

    @Test
    fun `service area decodes bbox and full polygon geometry`() {
        val refs = json.decodeFromString<ObaEnvelope<EntryWithReferences<OnDemandServiceDto>>>(fixture("ondemand_service_alexandria.json")).requireData().references
        val area = requireNotNull(refs.serviceArea("5088_area_1449"))
        assertEquals(listOf(-77.5372039, 38.617508, -76.9092198, 39.057831), area.bbox)
        assertNull(area.distanceToArea)
        val polygons = area.polygons()
        assertEquals(1, polygons.size)
        val ring = polygons[0][0]
        assertTrue(ring.size >= 4)
        for (point in ring) {
            assertTrue(point.longitude in area.bbox[0]..area.bbox[2])
            assertTrue(point.latitude in area.bbox[1]..area.bbox[3])
        }
    }

    @Test
    fun `services-for-location point mode carries match reason and distance`() {
        val data = json.decodeFromString<ObaEnvelope<ListWithReferences<OnDemandServiceDto>>>(fixture("ondemand_services_for_location_point.json")).requireData()
        assertEquals(1, data.list.size)
        assertEquals("5088_77652", data.list[0].id)
        assertTrue(data.list[0].matchReason in pointModeReasons)
        val area = requireNotNull(data.references.serviceArea("5088_area_1449"))
        assertNotNull(area.distanceToArea)
        assertTrue(area.polygons().isNotEmpty())
    }

    @Test
    fun `services-for-location viewport mode uses simplified geometry and null distance`() {
        val data = json.decodeFromString<ObaEnvelope<ListWithReferences<OnDemandServiceDto>>>(fixture("ondemand_services_for_location_viewport.json")).requireData()
        assertEquals(1, data.list.size)
        assertTrue(data.list[0].matchReason in viewportModeReasons)
        val area = requireNotNull(data.references.serviceArea("5088_area_1449"))
        assertNull(area.distanceToArea)
        assertNull(area.nearestPointOnBoundary)
        val ring = area.polygons()[0][0]
        assertTrue(ring.size in 4..257)
    }

    @Test
    fun `services-for-agency lists every service sorted by id`() {
        val data = json.decodeFromString<ObaEnvelope<ListWithReferences<OnDemandServiceDto>>>(fixture("ondemand_services_for_agency_charlevoix.json")).requireData()
        assertEquals(listOf("CC_CC1", "CC_CC2_med", "CC_CC3", "CC_CC4"), data.list.map { it.id })
        val group = data.list.first { it.id == "CC_CC3" }
        assertEquals("stopGroup", group.serviceKind)
        assertNull(group.matchReason)
        val groupId = group.rules.single().fromIds.single()
        assertEquals(2, data.references.locationGroup(groupId)?.stopIds?.size)
        assertEquals(1, json.decodeFromString<ObaEnvelope<ListWithReferences<OnDemandServiceDto>>>(fixture("ondemand_services_for_agency_alexandria.json")).requireData().list.size)
    }

    @Test
    fun `stop and route entries carry pointer fields`() {
        val stop = json.decodeFromString<ObaEnvelope<EntryWithReferences<StopReference>>>(fixture("stop_with_ondemand_pointer.json")).requireData().entry
        assertEquals("CC_CC_Ironton_Ferry_West", stop.id)
        assertEquals(listOf("CC_CC3"), stop.onDemandServiceIds)
        val route = json.decodeFromString<ObaEnvelope<EntryWithReferences<RouteReference>>>(fixture("route_with_ondemand_pointer.json")).requireData().entry
        assertEquals("CC_CC3", route.id)
        assertEquals(listOf("CC_CC3"), route.onDemandServiceIds)
    }

    @Test
    fun `geometry omitted or multipolygon decodes without throwing`() {
        val none = json.decodeFromString<ServiceAreaDto>("""{"id":"a","name":null,"description":null,"bbox":[0.0,0.0,1.0,1.0]}""")
        assertTrue(none.polygons().isEmpty())
        assertEquals(4, none.bbox.size)
        val multi = json.decodeFromString<ServiceAreaDto>(
            """{"id":"m","bbox":[0,0,3,3],"geometry":{"type":"MultiPolygon","coordinates":[
                 [[[0,0],[1,0],[1,1],[0,1],[0,0]],[[0.2,0.2],[0.4,0.2],[0.4,0.4],[0.2,0.4],[0.2,0.2]]],
                 [[[2,2],[3,2],[3,3],[2,3],[2,2]]]]}}"""
        )
        val polygons = multi.polygons()
        assertEquals(2, polygons.size)
        assertEquals(2, polygons[0].size)
        assertEquals(0.2, polygons[0][1][0].longitude, 0.0)
        assertEquals(2.0, polygons[1][0][0].latitude, 0.0)
        val point = json.decodeFromString<ServiceAreaDto>("""{"id":"p","bbox":[0,0,0,0],"geometry":{"type":"Point","coordinates":[1,2]}}""")
        assertTrue(point.polygons().isEmpty())
    }
}
```

Also append to `T/api/StopsMapDecodeTest.kt` inside `stopsForLocationDecodesWithReferences()` after the agency assertion (line 55):

```kotlin
        // Pre-flex payloads carry no pointer fields; they must decode to empty, not fail.
        assertTrue(first.onDemandServiceIds.isEmpty())
        assertTrue(route.onDemandServiceIds.isEmpty())
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :onebusaway-android:testObaGoogleDebugUnitTest --tests "org.onebusaway.android.api.OnDemandDecodeTest" --tests "org.onebusaway.android.api.StopsMapDecodeTest"`
Expected: compilation FAILS with `Unresolved reference: OnDemandServiceDto` / `onDemandServiceIds`.

- [ ] **Step 3: Write the DTOs**

```kotlin
// M/api/contract/OnDemandApiModels.kt
/*
 * Copyright (C) 2026 Open Transit Software Foundation
 * (Apache 2.0 header as in ObaApiModels.kt)
 */
package org.onebusaway.android.api.contract

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.onebusaway.android.util.GeoPoint

/**
 * Wire model of an `/api/ondemand` service (the entry of `service/{id}` and each element of the two
 * list endpoints). [serviceKind] and [matchReason] stay wire strings here; the adapter mints the enums
 * with an `UNKNOWN` fallback so a newer server can add a value without breaking decode.
 * [matchReason] is present only on `services-for-location` elements.
 */
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
    val matchReason: String? = null
)

/** One availability rule (wiki §2.2). Times are `"HH:MM:SS"` service-day strings and may exceed 24h. */
@Serializable
data class AvailabilityRuleDto(
    val fromIds: List<String> = emptyList(),
    val toIds: List<String> = emptyList(),
    val startPickupTime: String? = null,
    val endPickupTime: String? = null,
    val endDropOffTime: String? = null,
    val calendarIds: List<String> = emptyList(),
    val pickupType: Int = 0,
    val dropOffType: Int = 0,
    val pickupBookingRuleId: String? = null,
    val dropOffBookingRuleId: String? = null,
    val safeDurationFactor: Double? = null,
    val safeDurationOffset: Double? = null
)

/**
 * A `references.serviceAreas` element. [bbox] is `[minLon, minLat, maxLon, maxLat]` and always present;
 * [geometry] is the raw GeoJSON geometry object, absent at `geometryDetail=none`, and decoded on demand
 * by [polygons] rather than eagerly — a county zone can be hundreds of KB. [distanceToArea] /
 * [nearestPointOnBoundary] (`[lon, lat]`) are non-null only in point-mode `services-for-location`.
 */
@Serializable
data class ServiceAreaDto(
    val id: String = "",
    val name: String? = null,
    val description: String? = null,
    val bbox: List<Double> = emptyList(),
    val geometry: JsonElement? = null,
    val distanceToArea: Double? = null,
    val nearestPointOnBoundary: List<Double>? = null
) {
    /**
     * The geometry as polygons: `[polygon][ring][point]`, ring 0 the exterior and the rest holes, in
     * the ring order the feed published. A `Polygon` yields one polygon, a `MultiPolygon` one per
     * member; a missing or non-polygonal geometry yields none. Malformed coordinates throw, which the
     * data source's failure path reports rather than drawing a half-decoded zone.
     */
    fun polygons(): List<List<List<GeoPoint>>> {
        val geometryObject = geometry as? JsonObject ?: return emptyList()
        val coordinates = geometryObject["coordinates"] as? JsonArray ?: return emptyList()
        return when (geometryObject["type"]?.jsonPrimitive?.contentOrNull) {
            "Polygon" -> listOf(coordinates.toRings())
            "MultiPolygon" -> coordinates.map { it.jsonArray.toRings() }
            else -> emptyList()
        }
    }

    private fun JsonArray.toRings(): List<List<GeoPoint>> = map { ring -> ring.jsonArray.map { it.jsonArray.toGeoPoint() } }

    // GeoJSON positions are [lon, lat].
    private fun JsonArray.toGeoPoint(): GeoPoint = GeoPoint(latitude = this[1].jsonPrimitive.double, longitude = this[0].jsonPrimitive.double)
}

/** A `references.locationGroups` element; members also appear in `references.stops`. */
@Serializable
data class LocationGroupDto(
    val id: String = "",
    val name: String? = null,
    val stopIds: List<String> = emptyList()
)

/** A `references.bookingRules` element — the GTFS-Flex booking vocabulary camelCased (wiki §2.4). */
@Serializable
data class BookingRuleDto(
    val id: String = "",
    val bookingType: Int = 0,
    val priorNoticeDurationMin: Int? = null,
    val priorNoticeDurationMax: Int? = null,
    val priorNoticeLastDay: Int? = null,
    val priorNoticeLastTime: String? = null,
    val priorNoticeStartDay: Int? = null,
    val priorNoticeStartTime: String? = null,
    val priorNoticeCalendarId: String? = null,
    val message: String? = null,
    val pickupMessage: String? = null,
    val dropOffMessage: String? = null,
    val phoneNumber: String? = null,
    val infoUrl: String? = null,
    val bookingUrl: String? = null
)

/** A `references.calendars` element: [days] from `mon..sun`, dates as `YYYY-MM-DD`. */
@Serializable
data class FlexCalendarDto(
    val id: String = "",
    val days: List<String> = emptyList(),
    val startDate: String = "",
    val endDate: String = "",
    val exceptedDates: List<String> = emptyList()
)
```

- [ ] **Step 4: Extend `References`, `RouteReference`, `StopReference`**

In `M/api/contract/ObaApiModels.kt`, replace the `References` class (lines 61–98) with:

```kotlin
/**
 * The shared reference pool returned alongside an entry. Only the reference kinds a migrated
 * endpoint actually consumes are modeled; unmodeled kinds are tolerated on the wire via
 * `ignoreUnknownKeys`. The four on-demand pools are present only on `/api/ondemand` responses and
 * default to empty everywhere else.
 */
@Serializable
data class References(
    val agencies: List<AgencyReference> = emptyList(),
    val stops: List<StopReference> = emptyList(),
    val routes: List<RouteReference> = emptyList(),
    val trips: List<TripReference> = emptyList(),
    val situations: List<SituationReference> = emptyList(),
    val serviceAreas: List<ServiceAreaDto> = emptyList(),
    val locationGroups: List<LocationGroupDto> = emptyList(),
    val bookingRules: List<BookingRuleDto> = emptyList(),
    val calendars: List<FlexCalendarDto> = emptyList()
) {
    // Index each pool by id (lazily, once per response) so repeated resolution — the per-arrival
    // projections and the per-frame vehicle sampler — is O(1) instead of a linear scan.
    private val agencyById by lazy { agencies.associateBy { it.id } }
    private val stopById by lazy { stops.associateBy { it.id } }
    private val routeById by lazy { routes.associateBy { it.id } }
    private val tripById by lazy { trips.associateBy { it.id } }
    private val situationById by lazy { situations.associateBy { it.id } }
    private val serviceAreaById by lazy { serviceAreas.associateBy { it.id } }
    private val locationGroupById by lazy { locationGroups.associateBy { it.id } }
    private val bookingRuleById by lazy { bookingRules.associateBy { it.id } }
    private val calendarById by lazy { calendars.associateBy { it.id } }

    /** Resolves an agency in this pool by id, or null when absent. */
    fun agency(id: String): AgencyReference? = agencyById[id]

    /** Resolves a stop in this pool by id, or null when absent. */
    fun stop(id: String): StopReference? = stopById[id]

    /** Resolves a route in this pool by id, or null when absent. */
    fun route(id: String): RouteReference? = routeById[id]

    /** Resolves a trip in this pool by id, or null when absent. */
    fun trip(id: String): TripReference? = tripById[id]

    /** Resolves a situation in this pool by id, or null when absent. */
    fun situation(id: String): SituationReference? = situationById[id]

    /** Resolves an on-demand service area by id, or null when absent. */
    fun serviceArea(id: String): ServiceAreaDto? = serviceAreaById[id]

    /** Resolves an on-demand location group by id, or null when absent. */
    fun locationGroup(id: String): LocationGroupDto? = locationGroupById[id]

    /** Resolves an on-demand booking rule by id, or null when absent. */
    fun bookingRule(id: String): BookingRuleDto? = bookingRuleById[id]

    /** Resolves an on-demand calendar by id, or null when absent. */
    fun calendar(id: String): FlexCalendarDto? = calendarById[id]
}
```

Add to `RouteReference` after `val agencyId: String = ""` (line 113):

```kotlin
    val agencyId: String = "",
    // Ids of the on-demand services this route is flex-involved in (wiki §3.1). Omitted on the wire
    // when empty, so pre-flex servers decode to an empty list.
    val onDemandServiceIds: List<String> = emptyList()
```

Add to `StopReference` after `val wheelchairBoarding: String? = null` (line 160):

```kotlin
    val wheelchairBoarding: String? = null,
    // Ids of the on-demand services whose rules or location groups reference this stop (wiki §3.1).
    val onDemandServiceIds: List<String> = emptyList()
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :onebusaway-android:testObaGoogleDebugUnitTest --tests "org.onebusaway.android.api.OnDemandDecodeTest" --tests "org.onebusaway.android.api.StopsMapDecodeTest"`
Expected: PASS (7 + 2 tests).

- [ ] **Step 6: Commit**

```bash
./gradlew spotlessApply
git add onebusaway-android/src/main/java/org/onebusaway/android/api/contract/OnDemandApiModels.kt onebusaway-android/src/main/java/org/onebusaway/android/api/contract/ObaApiModels.kt onebusaway-android/src/test/java/org/onebusaway/android/api/OnDemandDecodeTest.kt onebusaway-android/src/test/java/org/onebusaway/android/api/StopsMapDecodeTest.kt
git commit -m "Add on-demand wire models and pointer fields"
```

---

### Task 2: `/api/ondemand` endpoints on `ObaWebService` and the demo service

**Files:**
- Modify: `M/api/contract/ObaWebService.kt` (append before the closing `}` at line 291)
- Modify: `M/demo/DemoObaWebService.kt` (imports at lines 23–58; add overrides after `stopIdsForAgency` at line 106)

**Interfaces:**
- Consumes: Task 1 DTOs.
- Produces:

```kotlin
@GET("api/ondemand/service/{serviceId}.json")
suspend fun onDemandService(@Path("serviceId") serviceId: String, @Query("geometryDetail") geometryDetail: String? = null): ObaEnvelope<EntryWithReferences<OnDemandServiceDto>>
@GET("api/ondemand/services-for-agency/{agencyId}.json")
suspend fun onDemandServicesForAgency(@Path("agencyId") agencyId: String, @Query("geometryDetail") geometryDetail: String? = null): ObaEnvelope<ListWithReferences<OnDemandServiceDto>>
@GET("api/ondemand/services-for-location.json")
suspend fun onDemandServicesForLocation(@Query("lat") lat: Double, @Query("lon") lon: Double, @Query("radius") radius: Int? = null, @Query("latSpan") latSpan: Double? = null, @Query("lonSpan") lonSpan: Double? = null, @Query("geometryDetail") geometryDetail: String? = null): ObaEnvelope<ListWithReferences<OnDemandServiceDto>>
```

No Retrofit round-trip test pattern exists in `T/api` (all decode tests parse bodies directly), so the Task 1 decode test is the contract test; this task is gated by compiling both flavors.

- [ ] **Step 1: Add the endpoints**

Append inside `interface ObaWebService` (before line 291's `}`):

```kotlin

    /**
     * ondemand service — one on-demand (GTFS-Flex) service with full rules and references (wiki §3).
     * [geometryDetail] is `none|simplified|full`; the server default here is `full`, so screens pass
     * `simplified` (a county zone's full ring can be ~750 KB). A 404 is an ordinary not-found.
     */
    @GET("api/ondemand/service/{serviceId}.json")
    suspend fun onDemandService(
        @Path("serviceId") serviceId: String,
        @Query("geometryDetail") geometryDetail: String? = null
    ): ObaEnvelope<EntryWithReferences<OnDemandServiceDto>>

    /** ondemand services-for-agency — every on-demand service of [agencyId]; 404 for an unknown agency. */
    @GET("api/ondemand/services-for-agency/{agencyId}.json")
    suspend fun onDemandServicesForAgency(
        @Path("agencyId") agencyId: String,
        @Query("geometryDetail") geometryDetail: String? = null
    ): ObaEnvelope<ListWithReferences<OnDemandServiceDto>>

    /**
     * ondemand services-for-location — services covering a point ([radius] mode) or a viewport
     * ([latSpan]/[lonSpan] mode; radius wins when both are given). Each element carries `matchReason`.
     * **This is the probe**: a deployment without the namespace answers HTTP 404 here, which
     * [org.onebusaway.android.api.data.OnDemandDataSource] reads as "this region doesn't serve it".
     */
    @GET("api/ondemand/services-for-location.json")
    suspend fun onDemandServicesForLocation(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("radius") radius: Int? = null,
        @Query("latSpan") latSpan: Double? = null,
        @Query("lonSpan") lonSpan: Double? = null,
        @Query("geometryDetail") geometryDetail: String? = null
    ): ObaEnvelope<ListWithReferences<OnDemandServiceDto>>
```

- [ ] **Step 2: Add the demo overrides**

In `M/demo/DemoObaWebService.kt` add `import org.onebusaway.android.api.contract.OnDemandServiceDto` (alphabetically after `NoData`), and after `stopIdsForAgency` (line 106):

```kotlin

    // The demo transit system publishes no on-demand service: the lists are empty and a lookup is the
    // same 404-coded envelope a real deployment answers with, so the zone layer and the arrivals card
    // simply show nothing during the tour.
    override suspend fun onDemandService(
        serviceId: String,
        geometryDetail: String?
    ): ObaEnvelope<EntryWithReferences<OnDemandServiceDto>> = notFound()

    override suspend fun onDemandServicesForAgency(
        agencyId: String,
        geometryDetail: String?
    ): ObaEnvelope<ListWithReferences<OnDemandServiceDto>> = if (agencyId == fixture.agency.id) ok(ListWithReferences(references = references())) else notFound()

    override suspend fun onDemandServicesForLocation(
        lat: Double,
        lon: Double,
        radius: Int?,
        latSpan: Double?,
        lonSpan: Double?,
        geometryDetail: String?
    ): ObaEnvelope<ListWithReferences<OnDemandServiceDto>> = ok(ListWithReferences(references = references()))
```

- [ ] **Step 3: Compile both flavors and rerun the decode test**

Run: `./gradlew :onebusaway-android:compileObaGoogleDebugKotlin :onebusaway-android:compileObaMaplibreDebugKotlin -PwarningsAsErrors=true && ./gradlew :onebusaway-android:testObaGoogleDebugUnitTest --tests "org.onebusaway.android.api.OnDemandDecodeTest"`
Expected: BUILD SUCCESSFUL, tests PASS.

- [ ] **Step 4: Commit**

```bash
./gradlew spotlessApply
git add onebusaway-android/src/main/java/org/onebusaway/android/api/contract/ObaWebService.kt onebusaway-android/src/main/java/org/onebusaway/android/demo/DemoObaWebService.kt
git commit -m "Add /api/ondemand endpoints to the web service"
```

---

### Task 3: Domain model, adapters, and stop/route pointers

**Files:**
- Create: `M/models/OnDemandService.kt`, `M/api/adapters/OnDemandAdapters.kt`
- Modify: `M/models/ObaStop.kt:55` (after `wheelchairBoarding`), `M/models/ObaRoute.kt:51` (after `agencyId`), `M/api/adapters/StopAdapters.kt:41-43` (`DtoStop`), `M/api/adapters/RouteAdapters.kt:26-36` (`DtoRoute`)
- Test: `T/api/adapters/OnDemandAdaptersTest.kt`, `T/models/ServiceDayTimeTest.kt`

**Interfaces:**
- Consumes: Task 1 DTOs and `References` finders; `RouteReference.colorArgb()` (`M/api/adapters/RouteAdapters.kt:43`).
- Produces (exact):

```kotlin
package org.onebusaway.android.models

@JvmInline value class ServiceDayTime(val seconds: Int) : Comparable<ServiceDayTime> {
    companion object { fun parse(hms: String): ServiceDayTime }   // "HH:MM:SS", hours may exceed 24
}
enum class OnDemandServiceKind(val wire: String) { ZONE("zone"), ZONE_TO_ZONE("zoneToZone"), STOP_GROUP("stopGroup"), DEVIATED_ROUTE("deviatedRoute"), UNKNOWN("unknown");
    companion object { fun fromWire(wire: String?): OnDemandServiceKind } }
enum class OnDemandMatchReason(val wire: String) { AREA_CONTAINS_POINT("areaContainsPoint"), STOP_WITHIN_RADIUS("stopWithinRadius"), AREA_NEARBY("areaNearby"), AREA_INTERSECTS_VIEWPORT("areaIntersectsViewport"), STOP_WITHIN_VIEWPORT("stopWithinViewport"), UNKNOWN("");
    companion object { fun fromWire(wire: String): OnDemandMatchReason } }
enum class BookingType(val wire: Int) { REAL_TIME(0), SAME_DAY(1), PRIOR_DAYS(2); companion object { fun fromWire(wire: Int): BookingType? } }
data class AvailabilityRule(val fromIds: List<String>, val toIds: List<String>, val startPickupTime: ServiceDayTime?, val endPickupTime: ServiceDayTime?, val endDropOffTime: ServiceDayTime?, val calendarIds: List<String>, val pickupType: Int, val dropOffType: Int, val pickupBookingRuleId: String?, val dropOffBookingRuleId: String?, val safeDurationFactor: Double?, val safeDurationOffset: Double?)
data class ServiceArea(val id: String, val name: String?, val description: String?, val southWest: GeoPoint, val northEast: GeoPoint, val polygons: List<List<List<GeoPoint>>>, val distanceToAreaMeters: Double?, val nearestPointOnBoundary: GeoPoint?)
data class LocationGroup(val id: String, val name: String?, val stopIds: List<String>)
data class BookingRule(val id: String, val bookingType: BookingType, val priorNoticeDurationMin: Int?, val priorNoticeDurationMax: Int?, val priorNoticeLastDay: Int?, val priorNoticeLastTime: ServiceDayTime?, val priorNoticeStartDay: Int?, val priorNoticeStartTime: ServiceDayTime?, val priorNoticeCalendarId: String?, val message: String?, val pickupMessage: String?, val dropOffMessage: String?, val phoneNumber: String?, val infoUrl: String?, val bookingUrl: String?)
data class FlexCalendar(val id: String, val days: Set<DayOfWeek>, val startDate: LocalDate, val endDate: LocalDate, val exceptedDates: Set<LocalDate>) { fun isActiveOn(date: LocalDate): Boolean }
data class OnDemandService(val id: String, val agencyId: String, val routeId: String?, val name: String, val kind: OnDemandServiceKind, val description: String? = null, val url: String? = null, val rules: List<AvailabilityRule> = emptyList(), val matchReason: OnDemandMatchReason? = null, val areas: List<ServiceArea> = emptyList(), val locationGroups: List<LocationGroup> = emptyList(), val bookingRules: Map<String, BookingRule> = emptyMap(), val calendars: Map<String, FlexCalendar> = emptyMap(), val agencyTimezone: String? = null, val routeColor: Int? = null) {
    fun pickupBookingRule(rule: AvailabilityRule): BookingRule?
}
// ObaStop / ObaRoute:
val onDemandServiceIds: List<String> get() = emptyList()   // interface default; DtoStop/DtoRoute override

package org.onebusaway.android.api.adapters
internal fun EntryWithReferences<OnDemandServiceDto>.toOnDemandService(): OnDemandService
internal fun ListWithReferences<OnDemandServiceDto>.toOnDemandServices(): List<OnDemandService>
internal fun AvailabilityRuleDto.toAvailabilityRule(): AvailabilityRule
internal fun BookingRuleDto.toBookingRule(): BookingRule?        // null for a booking type outside 0..2
internal fun FlexCalendarDto.toFlexCalendar(): FlexCalendar
internal fun ServiceAreaDto.toServiceArea(): ServiceArea
```

- [ ] **Step 1: Write the failing tests**

```kotlin
// T/models/ServiceDayTimeTest.kt
package org.onebusaway.android.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ServiceDayTimeTest {
    @Test
    fun `parses hours past midnight`() {
        assertEquals(ServiceDayTime(0), ServiceDayTime.parse("00:00:00"))
        assertEquals(ServiceDayTime(5 * 3600), ServiceDayTime.parse("05:00:00"))
        assertEquals(ServiceDayTime(24 * 3600 + 50 * 60), ServiceDayTime.parse("24:50:00"))
        assertEquals(ServiceDayTime(25 * 3600), ServiceDayTime.parse("25:00:00"))
        assertTrue(ServiceDayTime.parse("24:50:00") < ServiceDayTime.parse("25:00:00"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects a malformed time`() {
        ServiceDayTime.parse("5pm")
    }
}
```

```kotlin
// T/api/adapters/OnDemandAdaptersTest.kt
package org.onebusaway.android.api.adapters

import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.api.contract.EntryWithReferences
import org.onebusaway.android.api.contract.ListWithReferences
import org.onebusaway.android.api.contract.ObaEnvelope
import org.onebusaway.android.api.contract.OnDemandServiceDto
import org.onebusaway.android.api.contract.StopReference
import org.onebusaway.android.api.requireData
import org.onebusaway.android.models.BookingType
import org.onebusaway.android.models.OnDemandMatchReason
import org.onebusaway.android.models.OnDemandServiceKind
import org.onebusaway.android.models.ServiceDayTime

class OnDemandAdaptersTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private fun entry(name: String) = json.decodeFromString<ObaEnvelope<EntryWithReferences<OnDemandServiceDto>>>(File("src/androidTest/res/raw/$name").readText()).requireData()

    private fun list(name: String) = json.decodeFromString<ObaEnvelope<ListWithReferences<OnDemandServiceDto>>>(File("src/androidTest/res/raw/$name").readText()).requireData()

    @Test
    fun `entry adapts and resolves every reference`() {
        val service = entry("ondemand_service_alexandria.json").toOnDemandService()
        assertEquals("5088_77652", service.id)
        assertEquals(OnDemandServiceKind.ZONE, service.kind)
        assertNull(service.matchReason)
        assertEquals("America/Los_Angeles", service.agencyTimezone)
        assertEquals(2, service.rules.size)
        val rule = service.rules[0]
        assertEquals(ServiceDayTime(5 * 3600), rule.startPickupTime)
        assertEquals(ServiceDayTime(24 * 3600 + 50 * 60), rule.endPickupTime)
        assertEquals(ServiceDayTime(25 * 3600), rule.endDropOffTime)
        val booking = requireNotNull(service.pickupBookingRule(rule))
        assertEquals(BookingType.PRIOR_DAYS, booking.bookingType)
        assertEquals(1, booking.priorNoticeLastDay)
        assertEquals(ServiceDayTime(17 * 3600), booking.priorNoticeLastTime)
        assertEquals(14, booking.priorNoticeStartDay)
        assertEquals("703-746-5222", booking.phoneNumber)
        val calendar = requireNotNull(service.calendars["5088_c_71675_b_85952_d_63"])
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY), calendar.days)
        assertEquals(LocalDate.of(2025, 12, 1), calendar.startDate)
        assertTrue(calendar.isActiveOn(LocalDate.of(2026, 3, 11)))
        assertTrue(!calendar.isActiveOn(LocalDate.of(2026, 3, 15)))
        val area = service.areas.single()
        assertEquals("5088_area_1449", area.id)
        assertEquals(-77.5372039, area.southWest.longitude, 0.0)
        assertEquals(39.057831, area.northEast.latitude, 0.0)
        assertTrue(area.polygons.isNotEmpty())
    }

    @Test
    fun `list adapts match reasons and viewport geometry`() {
        val services = list("ondemand_services_for_location_viewport.json").toOnDemandServices()
        assertEquals(1, services.size)
        assertTrue(services[0].matchReason in setOf(OnDemandMatchReason.AREA_INTERSECTS_VIEWPORT, OnDemandMatchReason.STOP_WITHIN_VIEWPORT))
        assertNull(services[0].areas.single().distanceToAreaMeters)
        val group = list("ondemand_services_for_agency_charlevoix.json").toOnDemandServices().first { it.id == "CC_CC3" }
        assertEquals(OnDemandServiceKind.STOP_GROUP, group.kind)
        assertEquals(2, group.locationGroups.single().stopIds.size)
        assertTrue(group.areas.isEmpty())
    }

    @Test
    fun `unknown wire enums fall back rather than throw`() {
        assertEquals(OnDemandServiceKind.UNKNOWN, OnDemandServiceKind.fromWire("teleport"))
        assertEquals(OnDemandServiceKind.UNKNOWN, OnDemandServiceKind.fromWire(null))
        assertEquals(OnDemandMatchReason.UNKNOWN, OnDemandMatchReason.fromWire("somethingNew"))
        assertNull(BookingType.fromWire(7))
    }

    @Test
    fun `stop and route adapters expose the pointers`() {
        val stop = json.decodeFromString<ObaEnvelope<EntryWithReferences<StopReference>>>(File("src/androidTest/res/raw/stop_with_ondemand_pointer.json").readText()).requireData()
        assertEquals(listOf("CC_CC3"), DtoStop(stop.entry).onDemandServiceIds)
        val route = requireNotNull(entry("ondemand_service_alexandria.json").references.route("5088_77652"))
        assertEquals(listOf("5088_77652"), DtoRoute(route).onDemandServiceIds)
        assertNotNull(ObaStopElement().onDemandServiceIds)
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :onebusaway-android:testObaGoogleDebugUnitTest --tests "org.onebusaway.android.models.ServiceDayTimeTest" --tests "org.onebusaway.android.api.adapters.OnDemandAdaptersTest"`
Expected: compilation FAILS with `Unresolved reference: ServiceDayTime`.

- [ ] **Step 3: Write the domain model**

```kotlin
// M/models/OnDemandService.kt
/* Apache 2.0 header */
package org.onebusaway.android.models

import java.time.DayOfWeek
import java.time.LocalDate
import org.onebusaway.android.util.GeoPoint

/**
 * A GTFS service-day time of day: seconds after the service day's anchor, allowed past 24:00:00
 * (`"25:00:00"` is 1 AM the following calendar day). It is a duration from an anchor, not a clock
 * instant; `BookingDeadlineEvaluator` turns it into a `java.time.Instant` for a date and zone.
 */
@JvmInline
value class ServiceDayTime(val seconds: Int) : Comparable<ServiceDayTime> {
    override fun compareTo(other: ServiceDayTime): Int = seconds.compareTo(other.seconds)

    val hours: Int get() = seconds / 3600
    val minutesOfHour: Int get() = (seconds % 3600) / 60

    companion object {
        /** Parses the wire `"HH:MM:SS"` form; hours may exceed 24. Throws on any other shape. */
        fun parse(hms: String): ServiceDayTime {
            val parts = hms.split(":")
            require(parts.size == 3) { "expected HH:MM:SS, got '$hms'" }
            val (h, m, s) = parts.map { it.toIntOrNull() ?: throw IllegalArgumentException("expected HH:MM:SS, got '$hms'") }
            require(h >= 0 && m in 0..59 && s in 0..59) { "expected HH:MM:SS, got '$hms'" }
            return ServiceDayTime(h * 3600 + m * 60 + s)
        }
    }
}

/** The shape of an on-demand service, classified by the server at import (wiki §2.3). Never inferred here. */
enum class OnDemandServiceKind(val wire: String) {
    ZONE("zone"),
    ZONE_TO_ZONE("zoneToZone"),
    STOP_GROUP("stopGroup"),
    DEVIATED_ROUTE("deviatedRoute"),
    UNKNOWN("unknown");

    companion object {
        fun fromWire(wire: String?): OnDemandServiceKind = entries.firstOrNull { it.wire == wire } ?: UNKNOWN
    }
}

/** Why a `services-for-location` element matched (wiki §3); [UNKNOWN] for a value this build doesn't know. */
enum class OnDemandMatchReason(val wire: String) {
    AREA_CONTAINS_POINT("areaContainsPoint"),
    STOP_WITHIN_RADIUS("stopWithinRadius"),
    AREA_NEARBY("areaNearby"),
    AREA_INTERSECTS_VIEWPORT("areaIntersectsViewport"),
    STOP_WITHIN_VIEWPORT("stopWithinViewport"),
    UNKNOWN("");

    companion object {
        fun fromWire(wire: String): OnDemandMatchReason = entries.firstOrNull { it.wire == wire } ?: UNKNOWN
    }
}

/** GTFS `booking_type`. */
enum class BookingType(val wire: Int) {
    REAL_TIME(0),
    SAME_DAY(1),
    PRIOR_DAYS(2);

    companion object {
        fun fromWire(wire: Int): BookingType? = entries.firstOrNull { it.wire == wire }
    }
}

/** One availability rule (wiki §2.2). All three window times null means the service runs all hours. */
data class AvailabilityRule(
    val fromIds: List<String>,
    val toIds: List<String>,
    val startPickupTime: ServiceDayTime?,
    val endPickupTime: ServiceDayTime?,
    val endDropOffTime: ServiceDayTime?,
    val calendarIds: List<String>,
    val pickupType: Int,
    val dropOffType: Int,
    val pickupBookingRuleId: String?,
    val dropOffBookingRuleId: String?,
    val safeDurationFactor: Double?,
    val safeDurationOffset: Double?
)

/**
 * A service area. [polygons] is `[polygon][ring][point]` (ring 0 exterior, then holes) at whatever
 * detail the request asked for — display only; containment is the server's `matchReason`.
 */
data class ServiceArea(
    val id: String,
    val name: String?,
    val description: String?,
    val southWest: GeoPoint,
    val northEast: GeoPoint,
    val polygons: List<List<List<GeoPoint>>>,
    val distanceToAreaMeters: Double?,
    val nearestPointOnBoundary: GeoPoint?
)

data class LocationGroup(val id: String, val name: String?, val stopIds: List<String>)

/** A booking rule (wiki §2.4). Conditionally-required fields may be null in real feeds (spec §6.2). */
data class BookingRule(
    val id: String,
    val bookingType: BookingType,
    val priorNoticeDurationMin: Int?,
    val priorNoticeDurationMax: Int?,
    val priorNoticeLastDay: Int?,
    val priorNoticeLastTime: ServiceDayTime?,
    val priorNoticeStartDay: Int?,
    val priorNoticeStartTime: ServiceDayTime?,
    val priorNoticeCalendarId: String?,
    val message: String?,
    val pickupMessage: String?,
    val dropOffMessage: String?,
    val phoneNumber: String?,
    val infoUrl: String?,
    val bookingUrl: String?
)

/** A compiled service calendar (wiki §2.4): weekdays inside a date range minus [exceptedDates]. */
data class FlexCalendar(
    val id: String,
    val days: Set<DayOfWeek>,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val exceptedDates: Set<LocalDate>
) {
    fun isActiveOn(date: LocalDate): Boolean = !date.isBefore(startDate) && !date.isAfter(endDate) &&
        date.dayOfWeek in days && date !in exceptedDates
}

/**
 * An on-demand service with the references it needs resolved onto it, so screens never touch the
 * wire pool. [agencyTimezone] is the agency's `timezone` — the zone every service-day time is in.
 * [routeColor] is the route's GTFS colour as ARGB, for the zone fill.
 */
data class OnDemandService(
    val id: String,
    val agencyId: String,
    val routeId: String?,
    val name: String,
    val kind: OnDemandServiceKind,
    val description: String? = null,
    val url: String? = null,
    val rules: List<AvailabilityRule> = emptyList(),
    val matchReason: OnDemandMatchReason? = null,
    val areas: List<ServiceArea> = emptyList(),
    val locationGroups: List<LocationGroup> = emptyList(),
    val bookingRules: Map<String, BookingRule> = emptyMap(),
    val calendars: Map<String, FlexCalendar> = emptyMap(),
    val agencyTimezone: String? = null,
    val routeColor: Int? = null
) {
    /** The booking rule that governs booking for [rule] — the pickup side (wiki §2.5). */
    fun pickupBookingRule(rule: AvailabilityRule): BookingRule? = rule.pickupBookingRuleId?.let(bookingRules::get)
}
```

Add to `ObaStop` (after line 55) and `ObaRoute` (after line 51):

```kotlin
    /** Ids of the on-demand services this stop takes part in (wiki §3.1); empty for every pre-flex source. */
    val onDemandServiceIds: List<String> get() = emptyList()
```

```kotlin
    /** Ids of the on-demand services this route is flex-involved in (wiki §3.1); empty for every pre-flex source. */
    val onDemandServiceIds: List<String> get() = emptyList()
```

Override in `DtoStop` (after `wheelchairBoarding`) and `DtoRoute` (after `agencyId`):

```kotlin
    override val onDemandServiceIds: List<String> get() = ref.onDemandServiceIds
```

- [ ] **Step 4: Write the adapters**

```kotlin
// M/api/adapters/OnDemandAdapters.kt
/* Apache 2.0 header */
package org.onebusaway.android.api.adapters

import java.time.DayOfWeek
import java.time.LocalDate
import org.onebusaway.android.api.contract.AvailabilityRuleDto
import org.onebusaway.android.api.contract.BookingRuleDto
import org.onebusaway.android.api.contract.EntryWithReferences
import org.onebusaway.android.api.contract.FlexCalendarDto
import org.onebusaway.android.api.contract.ListWithReferences
import org.onebusaway.android.api.contract.OnDemandServiceDto
import org.onebusaway.android.api.contract.References
import org.onebusaway.android.api.contract.ServiceAreaDto
import org.onebusaway.android.models.AvailabilityRule
import org.onebusaway.android.models.BookingRule
import org.onebusaway.android.models.BookingType
import org.onebusaway.android.models.FlexCalendar
import org.onebusaway.android.models.LocationGroup
import org.onebusaway.android.models.OnDemandMatchReason
import org.onebusaway.android.models.OnDemandService
import org.onebusaway.android.models.OnDemandServiceKind
import org.onebusaway.android.models.ServiceArea
import org.onebusaway.android.models.ServiceDayTime
import org.onebusaway.android.util.GeoPoint

/**
 * The entry response holds exactly one service, so every reference in its pool belongs to it — which
 * is what lets a zero-rule (degenerate, wiki §2.3) service still carry its areas.
 */
internal fun EntryWithReferences<OnDemandServiceDto>.toOnDemandService(): OnDemandService = entry.toOnDemandService(references, allReferencesBelongToService = true)

/** A list response shares one pool across services, so each takes only what its rules reference. */
internal fun ListWithReferences<OnDemandServiceDto>.toOnDemandServices(): List<OnDemandService> = list.map { it.toOnDemandService(references, allReferencesBelongToService = false) }

private fun OnDemandServiceDto.toOnDemandService(references: References, allReferencesBelongToService: Boolean): OnDemandService {
    val rules = this.rules.map { it.toAvailabilityRule() }
    val placeIds = rules.flatMapTo(mutableSetOf()) { it.fromIds + it.toIds }
    val bookingRules = rules.flatMap { listOfNotNull(it.pickupBookingRuleId, it.dropOffBookingRuleId) }
        .toSet()
        .mapNotNull { references.bookingRule(it)?.toBookingRule() }
        .associateBy { it.id }
    val calendarIds = rules.flatMapTo(mutableSetOf()) { it.calendarIds } +
        bookingRules.values.mapNotNull { it.priorNoticeCalendarId }
    val areas = references.serviceAreas.filter { allReferencesBelongToService || it.id in placeIds }
    val groups = references.locationGroups.filter { allReferencesBelongToService || it.id in placeIds }
    return OnDemandService(
        id = id,
        agencyId = agencyId,
        routeId = routeId,
        name = name,
        kind = OnDemandServiceKind.fromWire(serviceKind),
        description = description,
        url = url,
        rules = rules,
        matchReason = matchReason?.let(OnDemandMatchReason::fromWire),
        areas = areas.map { it.toServiceArea() },
        locationGroups = groups.map { LocationGroup(it.id, it.name, it.stopIds) },
        bookingRules = bookingRules,
        calendars = calendarIds.mapNotNull { references.calendar(it)?.toFlexCalendar() }.associateBy { it.id },
        agencyTimezone = references.agency(agencyId)?.timezone,
        routeColor = routeId?.let { references.route(it)?.colorArgb() }
    )
}

internal fun AvailabilityRuleDto.toAvailabilityRule(): AvailabilityRule = AvailabilityRule(
    fromIds = fromIds,
    toIds = toIds,
    startPickupTime = startPickupTime?.let(ServiceDayTime::parse),
    endPickupTime = endPickupTime?.let(ServiceDayTime::parse),
    endDropOffTime = endDropOffTime?.let(ServiceDayTime::parse),
    calendarIds = calendarIds,
    pickupType = pickupType,
    dropOffType = dropOffType,
    pickupBookingRuleId = pickupBookingRuleId,
    dropOffBookingRuleId = dropOffBookingRuleId,
    safeDurationFactor = safeDurationFactor,
    safeDurationOffset = safeDurationOffset
)

/** Null for a booking type outside `0..2`: a rule this build cannot evaluate is better absent than misread. */
internal fun BookingRuleDto.toBookingRule(): BookingRule? {
    val type = BookingType.fromWire(bookingType) ?: return null
    return BookingRule(
        id = id,
        bookingType = type,
        priorNoticeDurationMin = priorNoticeDurationMin,
        priorNoticeDurationMax = priorNoticeDurationMax,
        priorNoticeLastDay = priorNoticeLastDay,
        priorNoticeLastTime = priorNoticeLastTime?.let(ServiceDayTime::parse),
        priorNoticeStartDay = priorNoticeStartDay,
        priorNoticeStartTime = priorNoticeStartTime?.let(ServiceDayTime::parse),
        priorNoticeCalendarId = priorNoticeCalendarId,
        message = message,
        pickupMessage = pickupMessage,
        dropOffMessage = dropOffMessage,
        phoneNumber = phoneNumber,
        infoUrl = infoUrl,
        bookingUrl = bookingUrl
    )
}

private val WIRE_DAYS = mapOf(
    "mon" to DayOfWeek.MONDAY, "tue" to DayOfWeek.TUESDAY, "wed" to DayOfWeek.WEDNESDAY,
    "thu" to DayOfWeek.THURSDAY, "fri" to DayOfWeek.FRIDAY, "sat" to DayOfWeek.SATURDAY, "sun" to DayOfWeek.SUNDAY
)

internal fun FlexCalendarDto.toFlexCalendar(): FlexCalendar = FlexCalendar(
    id = id,
    days = days.mapNotNullTo(mutableSetOf()) { WIRE_DAYS[it] },
    startDate = LocalDate.parse(startDate),
    endDate = LocalDate.parse(endDate),
    exceptedDates = exceptedDates.mapTo(mutableSetOf()) { LocalDate.parse(it) }
)

internal fun ServiceAreaDto.toServiceArea(): ServiceArea {
    require(bbox.size == 4) { "serviceArea $id bbox must be [minLon, minLat, maxLon, maxLat]" }
    return ServiceArea(
        id = id,
        name = name,
        description = description,
        southWest = GeoPoint(latitude = bbox[1], longitude = bbox[0]),
        northEast = GeoPoint(latitude = bbox[3], longitude = bbox[2]),
        polygons = polygons(),
        distanceToAreaMeters = distanceToArea,
        nearestPointOnBoundary = nearestPointOnBoundary?.let { GeoPoint(latitude = it[1], longitude = it[0]) }
    )
}
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :onebusaway-android:testObaGoogleDebugUnitTest --tests "org.onebusaway.android.models.ServiceDayTimeTest" --tests "org.onebusaway.android.api.adapters.OnDemandAdaptersTest"`
Expected: PASS (2 + 4). Then `./gradlew :onebusaway-android:compileObaGoogleDebugKotlin -PwarningsAsErrors=true` — BUILD SUCCESSFUL (no other `ObaStop`/`ObaRoute` implementer needs a change thanks to the interface default).

- [ ] **Step 6: Commit**

```bash
./gradlew spotlessApply
git add onebusaway-android/src/main/java/org/onebusaway/android/models/OnDemandService.kt onebusaway-android/src/main/java/org/onebusaway/android/models/ObaStop.kt onebusaway-android/src/main/java/org/onebusaway/android/models/ObaRoute.kt onebusaway-android/src/main/java/org/onebusaway/android/api/adapters/OnDemandAdapters.kt onebusaway-android/src/main/java/org/onebusaway/android/api/adapters/StopAdapters.kt onebusaway-android/src/main/java/org/onebusaway/android/api/adapters/RouteAdapters.kt onebusaway-android/src/test/java/org/onebusaway/android/models/ServiceDayTimeTest.kt onebusaway-android/src/test/java/org/onebusaway/android/api/adapters/OnDemandAdaptersTest.kt
git commit -m "Add on-demand domain model and adapters"
```

---

### Task 4: `OnDemandSupport` and `OnDemandDataSource`

**Files:**
- Create: `M/api/data/OnDemandSupport.kt`, `M/api/data/OnDemandDataSource.kt`
- Modify: `M/app/di/RepositoryModule.kt` (imports near line 26–41; a `@Binds` after `bindNearbyArrivalsDataSource` at line 176–179)
- Test: `T/api/data/OnDemandResultTest.kt`, `T/api/data/OnDemandSupportTest.kt`

**Interfaces:**
- Consumes: `ObaApiProvider.call` (`M/api/net/ObaApiProvider.kt:70`), `isEndpointAbsent` (`M/api/data/NearbyArrivalsDataSource.kt:174`, same package), `CameraSnapshot` (`M/map/render/CameraSnapshot.kt`: `center: GeoPoint, zoom, latSpan, lonSpan, southWest, northEast`), Task 3 adapters.
- Produces (exact):

```kotlin
package org.onebusaway.android.api.data
sealed interface OnDemandResult<out T> {
    data class Loaded<T>(val value: T) : OnDemandResult<T>
    data object Unsupported : OnDemandResult<Nothing>
    data class Failed(val cause: Throwable) : OnDemandResult<Nothing>
}
interface OnDemandDataSource {
    suspend fun servicesForViewport(viewport: CameraSnapshot): OnDemandResult<List<OnDemandService>>   // the probe; simplified geometry
    suspend fun service(id: String): OnDemandResult<OnDemandService>                                    // never Unsupported
    suspend fun servicesForAgency(agencyId: String): OnDemandResult<List<OnDemandService>>              // never Unsupported
}
internal fun <T> Result<T>.toOnDemandResult(probe: Boolean): OnDemandResult<T>
const val GEOMETRY_DETAIL_SIMPLIFIED = "simplified"
@Singleton class OnDemandSupport @Inject constructor() { fun isKnownUnsupported(obaBaseUrl: String?): Boolean; fun recordAbsent(obaBaseUrl: String?) }
```

- [ ] **Step 1: Write the failing tests**

```kotlin
// T/api/data/OnDemandResultTest.kt
package org.onebusaway.android.api.data

import java.io.IOException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.api.ObaApiException
import retrofit2.HttpException
import retrofit2.Response

/**
 * How a failed on-demand call is classified. Only the probe (`services-for-location`) may conclude
 * "this deployment has no `/api/ondemand`", and only from a raw HTTP 404 (`isEndpointAbsent`); every
 * other failure — including a 404 on `service/{id}`, which is an ordinary not-found — is transient.
 */
class OnDemandResultTest {

    private fun http(code: Int) = HttpException(Response.error<Unit>(code, "".toResponseBody("text/html".toMediaType())))

    @Test
    fun `success is Loaded`() {
        assertEquals(OnDemandResult.Loaded(listOf("a")), Result.success(listOf("a")).toOnDemandResult(probe = true))
    }

    @Test
    fun `a raw 404 on the probe is Unsupported`() {
        assertEquals(OnDemandResult.Unsupported, Result.failure<Unit>(http(404)).toOnDemandResult(probe = true))
    }

    @Test
    fun `a raw 404 off the probe is a transient failure`() {
        val cause = http(404)
        val result = Result.failure<Unit>(cause).toOnDemandResult(probe = false)
        assertTrue(result is OnDemandResult.Failed)
        assertSame(cause, (result as OnDemandResult.Failed).cause)
    }

    @Test
    fun `an OBA envelope 404 is not an absent endpoint even on the probe`() {
        val result = Result.failure<Unit>(ObaApiException(404)).toOnDemandResult(probe = true)
        assertTrue(result is OnDemandResult.Failed)
    }

    @Test
    fun `other failures are transient`() {
        assertTrue(Result.failure<Unit>(http(500)).toOnDemandResult(probe = true) is OnDemandResult.Failed)
        assertTrue(Result.failure<Unit>(IOException("offline")).toOnDemandResult(probe = true) is OnDemandResult.Failed)
    }
}
```

```kotlin
// T/api/data/OnDemandSupportTest.kt
package org.onebusaway.android.api.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnDemandSupportTest {
    @Test
    fun `verdict is scoped to the deployment that answered`() {
        val support = OnDemandSupport()
        assertFalse(support.isKnownUnsupported("https://api.pugetsound.onebusaway.org/"))
        support.recordAbsent("https://api.pugetsound.onebusaway.org/")
        assertTrue(support.isKnownUnsupported("https://api.pugetsound.onebusaway.org/"))
        assertFalse(support.isKnownUnsupported("https://api.tampa.onebusaway.org/"))
        assertFalse(support.isKnownUnsupported(null))
        support.recordAbsent(null)
        assertFalse(support.isKnownUnsupported(null))
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :onebusaway-android:testObaGoogleDebugUnitTest --tests "org.onebusaway.android.api.data.OnDemandResultTest" --tests "org.onebusaway.android.api.data.OnDemandSupportTest"`
Expected: compilation FAILS with `Unresolved reference: OnDemandResult`.

- [ ] **Step 3: Write the support memory**

```kotlin
// M/api/data/OnDemandSupport.kt
/* Apache 2.0 header */
package org.onebusaway.android.api.data

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Which OBA deployments have been found not to serve `/api/ondemand` — the same shape, key and
 * lifetime as [NearbyArrivalsSupport], for the same reasons: the namespace is a property of the
 * server (maglev has it, onebusaway-application-modules does not), no directory field records it, so
 * it is discovered by the probe and believed only from an explicit HTTP 404 ([isEndpointAbsent]).
 * In memory only, keyed by the OBA base URL, so a region switch needs no reset and an upgraded
 * server is re-probed on the next launch.
 */
@Singleton
class OnDemandSupport @Inject constructor() {

    private val unsupported = mutableSetOf<String>()

    /** Whether [obaBaseUrl] is already known not to serve the namespace; unknown and null read as un-probed. */
    @Synchronized
    fun isKnownUnsupported(obaBaseUrl: String?): Boolean = obaBaseUrl != null && obaBaseUrl in unsupported

    /** Record that the deployment at [obaBaseUrl] answered HTTP 404 on `services-for-location`. */
    @Synchronized
    fun recordAbsent(obaBaseUrl: String?) {
        obaBaseUrl?.let(unsupported::add)
    }
}
```

- [ ] **Step 4: Write the data source**

```kotlin
// M/api/data/OnDemandDataSource.kt
/* Apache 2.0 header */
package org.onebusaway.android.api.data

import android.util.Log
import javax.inject.Inject
import org.onebusaway.android.api.adapters.toOnDemandService
import org.onebusaway.android.api.adapters.toOnDemandServices
import org.onebusaway.android.api.net.ObaApiProvider
import org.onebusaway.android.api.requireData
import org.onebusaway.android.map.render.CameraSnapshot
import org.onebusaway.android.models.OnDemandService

/** The `geometryDetail` every screen asks for: drawable straight onto the map, ~15 KB per zone. */
const val GEOMETRY_DETAIL_SIMPLIFIED = "simplified"

/** One on-demand fetch, or the verdict that this region cannot serve the namespace. */
sealed interface OnDemandResult<out T> {

    /** The response, resolved to domain objects. A list may be empty — nothing covers this viewport. */
    data class Loaded<T>(val value: T) : OnDemandResult<T>

    /**
     * This deployment does not implement `/api/ondemand`: `services-for-location` answered HTTP 404.
     * A durable fact about the server, not an error to retry — see [isEndpointAbsent].
     */
    data object Unsupported : OnDemandResult<Nothing>

    /** A transient failure (transport, timeout, a non-OK OBA envelope code, a not-found). Retry later. */
    data class Failed(val cause: Throwable) : OnDemandResult<Nothing>
}

/** Fetches on-demand services (GTFS-Flex) from the modernized OBA client. Never throws. */
interface OnDemandDataSource {

    /**
     * Every service whose area or stops intersect [viewport], with simplified geometry. **The only
     * probe**: a raw HTTP 404 here yields [OnDemandResult.Unsupported]; nothing else does.
     */
    suspend fun servicesForViewport(viewport: CameraSnapshot): OnDemandResult<List<OnDemandService>>

    /** One service by combined id, with simplified geometry. A 404 is [OnDemandResult.Failed] (not found). */
    suspend fun service(id: String): OnDemandResult<OnDemandService>

    /** Every on-demand service of an agency. A 404 is [OnDemandResult.Failed] (unknown agency). */
    suspend fun servicesForAgency(agencyId: String): OnDemandResult<List<OnDemandService>>
}

/**
 * Classifies a call's outcome. Only a [probe] may read a raw HTTP 404 as an absent endpoint; an OBA
 * envelope 404 (`ObaApiException`) and every other failure stay transient. Pure, so the policy is
 * JVM-tested without a network or `android.util.Log` (which the data source below adds).
 */
internal fun <T> Result<T>.toOnDemandResult(probe: Boolean): OnDemandResult<T> = fold(
    onSuccess = { OnDemandResult.Loaded(it) },
    onFailure = { cause -> if (probe && isEndpointAbsent(cause)) OnDemandResult.Unsupported else OnDemandResult.Failed(cause) }
)

class DefaultOnDemandDataSource @Inject constructor(
    private val api: ObaApiProvider
) : OnDemandDataSource {

    override suspend fun servicesForViewport(viewport: CameraSnapshot): OnDemandResult<List<OnDemandService>> = api.call { service ->
        service.onDemandServicesForLocation(
            lat = viewport.center.latitude,
            lon = viewport.center.longitude,
            latSpan = viewport.latSpan,
            lonSpan = viewport.lonSpan,
            geometryDetail = GEOMETRY_DETAIL_SIMPLIFIED
        ).requireData().toOnDemandServices()
    }.toOnDemandResult(probe = true).logged("services-for-location")

    override suspend fun service(id: String): OnDemandResult<OnDemandService> = api.call { service ->
        service.onDemandService(id, GEOMETRY_DETAIL_SIMPLIFIED).requireData().toOnDemandService()
    }.toOnDemandResult(probe = false).logged("service($id)")

    override suspend fun servicesForAgency(agencyId: String): OnDemandResult<List<OnDemandService>> = api.call { service ->
        service.onDemandServicesForAgency(agencyId, GEOMETRY_DETAIL_SIMPLIFIED).requireData().toOnDemandServices()
    }.toOnDemandResult(probe = false).logged("services-for-agency($agencyId)")

    private fun <T> OnDemandResult<T>.logged(what: String): OnDemandResult<T> = also {
        when (it) {
            is OnDemandResult.Failed -> Log.e(TAG, "on-demand $what failed", it.cause)
            OnDemandResult.Unsupported -> Log.i(TAG, "Region does not serve /api/ondemand")
            is OnDemandResult.Loaded -> Unit
        }
    }

    private companion object {
        const val TAG = "OnDemandDataSource"
    }
}
```

- [ ] **Step 5: Bind it**

In `M/app/di/RepositoryModule.kt` add imports `org.onebusaway.android.api.data.DefaultOnDemandDataSource` and `org.onebusaway.android.api.data.OnDemandDataSource` (alphabetical among the existing `api.data` imports), and after `bindNearbyArrivalsDataSource` (line 179):

```kotlin

    @Binds
    abstract fun bindOnDemandDataSource(
        impl: DefaultOnDemandDataSource
    ): OnDemandDataSource
```

- [ ] **Step 6: Run the tests and compile**

Run: `./gradlew :onebusaway-android:testObaGoogleDebugUnitTest --tests "org.onebusaway.android.api.data.OnDemandResultTest" --tests "org.onebusaway.android.api.data.OnDemandSupportTest" && ./gradlew :onebusaway-android:compileObaGoogleDebugKotlin -PwarningsAsErrors=true`
Expected: PASS (5 + 1), BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
./gradlew spotlessApply
git add onebusaway-android/src/main/java/org/onebusaway/android/api/data/OnDemandSupport.kt onebusaway-android/src/main/java/org/onebusaway/android/api/data/OnDemandDataSource.kt onebusaway-android/src/main/java/org/onebusaway/android/app/di/RepositoryModule.kt onebusaway-android/src/test/java/org/onebusaway/android/api/data/OnDemandResultTest.kt onebusaway-android/src/test/java/org/onebusaway/android/api/data/OnDemandSupportTest.kt
git commit -m "Add on-demand data source and support memory"
```

---

### Task 5: `BookingDeadlineEvaluator` verified against the shared vectors

**Files:**
- Create: `M/ondemand/BookingDeadlineEvaluator.kt`
- Test: `T/ondemand/BookingDeadlineEvaluatorTest.kt` (reads `RAW/flex_booking_vectors.json`)

**Interfaces:**
- Consumes: `AvailabilityRule`, `BookingRule`, `BookingType`, `FlexCalendar`, `ServiceDayTime` (Task 3); test uses `BookingRuleDto.toBookingRule()`, `FlexCalendarDto.toFlexCalendar()` (Task 3 adapters, `internal` in the same module).
- Produces (exact):

```kotlin
package org.onebusaway.android.ondemand
enum class BookingState { NOT_YET_OPEN, OPEN, CLOSED_FOR_DATE, UNKNOWN }
data class BookingEvaluation(val state: BookingState, val cutoffInstant: Instant?, val openInstant: Instant?)
object BookingDeadlineEvaluator {
    fun serviceDayAnchor(date: LocalDate, zone: ZoneId): Instant
    fun instantOf(date: LocalDate, time: ServiceDayTime, zone: ZoneId): Instant
    fun countBack(date: LocalDate, days: Int, calendar: FlexCalendar?): LocalDate
    fun evaluate(rule: AvailabilityRule, bookingRule: BookingRule?, travelDate: LocalDate, now: Instant, zone: ZoneId, calendars: Map<String, FlexCalendar>): BookingEvaluation
    fun nextBookableServiceDate(rule: AvailabilityRule, bookingRule: BookingRule?, from: LocalDate, now: Instant, zone: ZoneId, calendars: Map<String, FlexCalendar>): LocalDate?
}
```

- [ ] **Step 1: Write the failing vectors-driven test**

```kotlin
// T/ondemand/BookingDeadlineEvaluatorTest.kt
package org.onebusaway.android.ondemand

import java.io.File
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.api.adapters.toBookingRule
import org.onebusaway.android.api.adapters.toFlexCalendar
import org.onebusaway.android.api.contract.BookingRuleDto
import org.onebusaway.android.api.contract.FlexCalendarDto
import org.onebusaway.android.models.AvailabilityRule
import org.onebusaway.android.models.ServiceDayTime

/**
 * Runs every vector in the shared `flex-booking-vectors.json` (mirrored verbatim from maglev's
 * testdata; spec §6) so Android, iOS and the server agree on one deadline algorithm.
 */
class BookingDeadlineEvaluatorTest {

    @Serializable
    private data class RuleVector(val startPickupTime: String? = null, val endPickupTime: String? = null, val calendarIds: List<String> = emptyList())

    @Serializable
    private data class Expected(val state: String, val cutoffInstant: String? = null, val openInstant: String? = null, val nextBookableServiceDate: String? = null)

    @Serializable
    private data class Vector(
        val name: String,
        val timezone: String? = null,
        val bookingRule: BookingRuleDto? = null,
        val rule: RuleVector,
        val travelDate: String,
        val now: String,
        val expected: Expected
    )

    @Serializable
    private data class VectorsFile(val timezone: String, val calendars: List<FlexCalendarDto> = emptyList(), val vectors: List<Vector>)

    private val json = Json { ignoreUnknownKeys = true }

    private val file = json.decodeFromString<VectorsFile>(File("src/androidTest/res/raw/flex_booking_vectors.json").readText())

    private val calendars = file.calendars.map { it.toFlexCalendar() }.associateBy { it.id }

    private fun state(wire: String) = when (wire) {
        "notYetOpen" -> BookingState.NOT_YET_OPEN
        "open" -> BookingState.OPEN
        "closedForDate" -> BookingState.CLOSED_FOR_DATE
        "unknown" -> BookingState.UNKNOWN
        else -> error("unknown state '$wire'")
    }

    private fun instant(iso: String?) = iso?.let { OffsetDateTime.parse(it).toInstant() }

    @Test
    fun `the vectors file has the spec's minimum coverage`() {
        assertTrue(file.vectors.size >= 12)
    }

    @Test
    fun `every vector evaluates as expected`() {
        val failures = mutableListOf<String>()
        for (vector in file.vectors) {
            val zone = ZoneId.of(vector.timezone ?: file.timezone)
            val rule = AvailabilityRule(
                fromIds = emptyList(), toIds = emptyList(),
                startPickupTime = vector.rule.startPickupTime?.let(ServiceDayTime::parse),
                endPickupTime = vector.rule.endPickupTime?.let(ServiceDayTime::parse),
                endDropOffTime = null, calendarIds = vector.rule.calendarIds,
                pickupType = 2, dropOffType = 2, pickupBookingRuleId = vector.bookingRule?.id,
                dropOffBookingRuleId = null, safeDurationFactor = null, safeDurationOffset = null
            )
            val bookingRule = vector.bookingRule?.toBookingRule()
            val now = instant(vector.now)!!
            val travelDate = LocalDate.parse(vector.travelDate)
            val actual = BookingDeadlineEvaluator.evaluate(rule, bookingRule, travelDate, now, zone, calendars)
            val expected = BookingEvaluation(state(vector.expected.state), instant(vector.expected.cutoffInstant), instant(vector.expected.openInstant))
            if (actual != expected) failures += "${vector.name}: expected $expected, got $actual"
            vector.expected.nextBookableServiceDate?.let { expectedDate ->
                val from = now.atZone(zone).toLocalDate()
                val next = BookingDeadlineEvaluator.nextBookableServiceDate(rule, bookingRule, from, now, zone, calendars)
                if (next != LocalDate.parse(expectedDate)) failures += "${vector.name}: expected next bookable $expectedDate, got $next"
            }
        }
        assertEquals(failures.joinToString("\n"), 0, failures.size)
    }

    @Test
    fun `noon anchor survives a DST transition`() {
        val zone = ZoneId.of("America/Detroit")
        // 2026-03-08 springs forward at 02:00; the service day still starts at local midnight.
        val anchor = BookingDeadlineEvaluator.serviceDayAnchor(LocalDate.of(2026, 3, 8), zone)
        assertEquals(OffsetDateTime.parse("2026-03-08T00:00:00-05:00").toInstant(), anchor)
        // 25:00:00 on that day is 02:00 EDT on the 9th — 24 real hours later, not 25.
        val lateDropOff = BookingDeadlineEvaluator.instantOf(LocalDate.of(2026, 3, 8), ServiceDayTime.parse("25:00:00"), zone)
        assertEquals(OffsetDateTime.parse("2026-03-09T02:00:00-04:00").toInstant(), lateDropOff)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :onebusaway-android:testObaGoogleDebugUnitTest --tests "org.onebusaway.android.ondemand.BookingDeadlineEvaluatorTest"`
Expected: compilation FAILS with `Unresolved reference: BookingDeadlineEvaluator`.

- [ ] **Step 3: Write the evaluator**

```kotlin
// M/ondemand/BookingDeadlineEvaluator.kt
/* Apache 2.0 header */
package org.onebusaway.android.ondemand

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.onebusaway.android.models.AvailabilityRule
import org.onebusaway.android.models.BookingRule
import org.onebusaway.android.models.BookingType
import org.onebusaway.android.models.FlexCalendar
import org.onebusaway.android.models.ServiceDayTime

/** Whether a ride on a given travel date can be booked right now (wiki §2.5, spec §6). */
enum class BookingState {
    /** Booking has not opened yet ([BookingEvaluation.openInstant] is in the future). */
    NOT_YET_OPEN,
    OPEN,
    /** The cutoff for this travel date has passed. */
    CLOSED_FOR_DATE,
    /** The feed omits a field the booking type needs (spec §6.2); no deadline can be stated. */
    UNKNOWN
}

data class BookingEvaluation(
    val state: BookingState,
    val cutoffInstant: Instant?,
    val openInstant: Instant?
)

/**
 * The client-side booking deadline algorithm, implemented once here and verified against the vectors
 * every client shares. Everything is in **service days in the agency timezone**: a service day is
 * anchored at local noon minus twelve hours (GTFS's DST-safe convention), and a [ServiceDayTime] is
 * added to that anchor — so `25:00:00` lands one real hour after `24:00:00` even across a
 * spring-forward. `now` is the device wall clock (never the envelope `currentTime`, which is cached
 * for hours), handed in as an `Instant` so no epoch-millis arithmetic happens here.
 */
object BookingDeadlineEvaluator {

    private val MIDNIGHT = ServiceDayTime(0)
    private val END_OF_SERVICE_DAY = ServiceDayTime(24 * 3600)

    /** Local noon of [date] in [zone], minus twelve hours. */
    fun serviceDayAnchor(date: LocalDate, zone: ZoneId): Instant = date.atTime(LocalTime.NOON).atZone(zone).toInstant().minus(Duration.ofHours(12))

    fun instantOf(date: LocalDate, time: ServiceDayTime, zone: ZoneId): Instant = serviceDayAnchor(date, zone).plusSeconds(time.seconds.toLong())

    /**
     * [date] minus [days]: calendar days when [calendar] is null, otherwise service days of that
     * calendar (holidays on it push the result earlier). Stepping back past the calendar's start date
     * can never find another active day, so it stops there rather than walking to the epoch.
     */
    fun countBack(date: LocalDate, days: Int, calendar: FlexCalendar?): LocalDate {
        if (calendar == null) return date.minusDays(days.toLong())
        var current = date
        var remaining = days
        while (remaining > 0) {
            current = current.minusDays(1)
            if (current.isBefore(calendar.startDate)) return current
            if (calendar.isActiveOn(current)) remaining--
        }
        return current
    }

    /**
     * Evaluates one (rule, travel date) pair. The pickup side governs booking, so [bookingRule] is the
     * rule's pickup booking rule; null means no notice is required.
     */
    fun evaluate(
        rule: AvailabilityRule,
        bookingRule: BookingRule?,
        travelDate: LocalDate,
        now: Instant,
        zone: ZoneId,
        calendars: Map<String, FlexCalendar>
    ): BookingEvaluation {
        if (bookingRule == null) return BookingEvaluation(BookingState.OPEN, cutoffInstant = null, openInstant = null)
        val latestPickup = instantOf(travelDate, rule.endPickupTime ?: END_OF_SERVICE_DAY, zone)
        val window: Pair<Instant, Instant?> = when (bookingRule.bookingType) {
            // Real-time: booked at ride time. Any prior-notice fields the feed carries are forbidden
            // for this type and ignored (Charlevoix's booking_rule_CC4).
            BookingType.REAL_TIME -> latestPickup to null
            BookingType.SAME_DAY -> {
                // A missing minimum notice can't be defaulted to zero: that would state the latest
                // possible deadline, the one failure a rider can't recover from (spec §6.2).
                val durationMin = bookingRule.priorNoticeDurationMin ?: return unknown()
                val cutoff = latestPickup.minus(Duration.ofMinutes(durationMin.toLong()))
                val open = when {
                    bookingRule.priorNoticeDurationMax != null ->
                        instantOf(travelDate, rule.startPickupTime ?: MIDNIGHT, zone)
                            .minus(Duration.ofMinutes(bookingRule.priorNoticeDurationMax.toLong()))
                    bookingRule.priorNoticeStartDay != null ->
                        instantOf(travelDate.minusDays(bookingRule.priorNoticeStartDay.toLong()), bookingRule.priorNoticeStartTime ?: MIDNIGHT, zone)
                    else -> null
                }
                cutoff to open
            }
            BookingType.PRIOR_DAYS -> {
                val lastDay = bookingRule.priorNoticeLastDay ?: return unknown()
                // Honoured only for this booking type, and for both counts (spec §6.1).
                val noticeCalendar = bookingRule.priorNoticeCalendarId?.let(calendars::get)
                // A missing last time is read as the start of the last day — never later than any
                // deadline the feed could have meant (spec §6.2).
                val cutoff = instantOf(countBack(travelDate, lastDay, noticeCalendar), bookingRule.priorNoticeLastTime ?: MIDNIGHT, zone)
                val open = bookingRule.priorNoticeStartDay?.let { startDay ->
                    instantOf(countBack(travelDate, startDay, noticeCalendar), bookingRule.priorNoticeStartTime ?: MIDNIGHT, zone)
                }
                cutoff to open
            }
        }
        val (cutoff, open) = window
        val state = when {
            open != null && now.isBefore(open) -> BookingState.NOT_YET_OPEN
            now.isAfter(cutoff) -> BookingState.CLOSED_FOR_DATE
            else -> BookingState.OPEN
        }
        return BookingEvaluation(state, cutoff, open)
    }

    /**
     * The earliest active service day of the rule's calendars, searching from [from] to the latest
     * calendar end date, on which [evaluate] is [BookingState.OPEN]; null when there is none, or when
     * the rule's notice is [BookingState.UNKNOWN] and so no date can be promised.
     */
    fun nextBookableServiceDate(
        rule: AvailabilityRule,
        bookingRule: BookingRule?,
        from: LocalDate,
        now: Instant,
        zone: ZoneId,
        calendars: Map<String, FlexCalendar>
    ): LocalDate? {
        val ruleCalendars = rule.calendarIds.mapNotNull(calendars::get)
        val end = ruleCalendars.maxOfOrNull { it.endDate } ?: return null
        var date = from
        while (!date.isAfter(end)) {
            if (ruleCalendars.any { it.isActiveOn(date) }) {
                when (evaluate(rule, bookingRule, date, now, zone, calendars).state) {
                    BookingState.OPEN -> return date
                    BookingState.UNKNOWN -> return null
                    BookingState.NOT_YET_OPEN, BookingState.CLOSED_FOR_DATE -> Unit
                }
            }
            date = date.plusDays(1)
        }
        return null
    }

    private fun unknown() = BookingEvaluation(BookingState.UNKNOWN, cutoffInstant = null, openInstant = null)
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :onebusaway-android:testObaGoogleDebugUnitTest --tests "org.onebusaway.android.ondemand.BookingDeadlineEvaluatorTest"`
Expected: PASS (3 tests). If a vector fails, the failure message names it; the vectors are normative — fix the evaluator, not the file.

- [ ] **Step 5: Commit**

```bash
./gradlew spotlessApply
git add onebusaway-android/src/main/java/org/onebusaway/android/ondemand/BookingDeadlineEvaluator.kt onebusaway-android/src/test/java/org/onebusaway/android/ondemand/BookingDeadlineEvaluatorTest.kt
git commit -m "Add booking deadline evaluator with shared vectors"
```

---

### Task 6: `OnDemandLayerController` and `MapRenderSnapshot.onDemandZones`

**Files:**
- Modify: `M/map/render/MapRenderState.kt` (add `ZonePolygon` before `MapRenderSnapshot` at line 481; field on `MapRenderSnapshot`; setters after `clearRentals()` at line 806)
- Create: `M/map/OnDemandLayerController.kt`
- Modify: `M/map/MapViewModel.kt:118-133` (constructor), `:211-218` (construct beside `rentalController`), and every `rentalController.start()/stop()/hide()` site (`:341`, `:374`, `:414`, `:635`, `:724`)
- Modify: `onebusaway-android/src/main/res/values/donottranslate.xml:52` (after `preference_key_show_rental_button`)
- Test: `T/map/OnDemandLayerControllerTest.kt`

**Interfaces:**
- Consumes: `MapHost.settledCamera()` (`M/map/MapDecisions.kt:48`, internal), `MapRenderState`, `OnDemandDataSource`/`OnDemandResult`/`OnDemandSupport` (Task 4), `PreferencesRepository.observeBoolean`, `RegionRepository.region: StateFlow<Region?>` (`obaBaseUrl`), `DemoModeState.active`.
- Produces (exact):

```kotlin
package org.onebusaway.android.map.render
data class ZonePolygon(val serviceId: String, val serviceName: String, val rings: List<List<GeoPoint>>, val color: Int?)  // rings[0] exterior, rest holes
// MapRenderSnapshot: val onDemandZones: List<ZonePolygon> = emptyList()
// MapRenderState: fun setOnDemandZones(zones: List<ZonePolygon>); fun clearOnDemandZones()

package org.onebusaway.android.map
class OnDemandLayerController(settledCamera: Flow<CameraSnapshot>, renderState: MapRenderState, dataSource: OnDemandDataSource, support: OnDemandSupport, prefsRepository: PreferencesRepository, regionRepository: RegionRepository, demoMode: DemoModeState, scope: CoroutineScope) { fun start(); fun stop(); fun hide() }
internal fun zonePolygons(services: List<OnDemandService>): List<ZonePolygon>
// R.string.preference_key_show_ondemand_zones -> "preference_show_ondemand_zones", default true
```

- [ ] **Step 1: Write the failing test**

```kotlin
// T/map/OnDemandLayerControllerTest.kt
package org.onebusaway.android.map

import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.onebusaway.android.R
import org.onebusaway.android.api.data.OnDemandDataSource
import org.onebusaway.android.api.data.OnDemandResult
import org.onebusaway.android.api.data.OnDemandSupport
import org.onebusaway.android.demo.DemoModeState
import org.onebusaway.android.map.render.CameraSnapshot
import org.onebusaway.android.map.render.MapRenderState
import org.onebusaway.android.models.OnDemandService
import org.onebusaway.android.models.OnDemandServiceKind
import org.onebusaway.android.models.ServiceArea
import org.onebusaway.android.region.FakeRegionRepository
import org.onebusaway.android.region.region
import org.onebusaway.android.testing.FakePreferencesRepository
import org.onebusaway.android.testing.MainDispatcherRule
import org.onebusaway.android.util.GeoPoint

@OptIn(ExperimentalCoroutinesApi::class)
class OnDemandLayerControllerTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(UnconfinedTestDispatcher())

    private class FakeDataSource(var result: OnDemandResult<List<OnDemandService>>) : OnDemandDataSource {
        val requests = mutableListOf<CameraSnapshot>()
        override suspend fun servicesForViewport(viewport: CameraSnapshot): OnDemandResult<List<OnDemandService>> {
            requests += viewport
            return result
        }
        override suspend fun service(id: String): OnDemandResult<OnDemandService> = OnDemandResult.Failed(IOException("unused"))
        override suspend fun servicesForAgency(agencyId: String): OnDemandResult<List<OnDemandService>> = OnDemandResult.Failed(IOException("unused"))
    }

    private class FakeDemoMode : DemoModeState {
        override val active: StateFlow<Boolean> = MutableStateFlow(false)
        override val isActive: Boolean get() = active.value
    }

    private val endpoint = "https://maglev.example.org/"
    private val camera = MutableSharedFlow<CameraSnapshot>(replay = 1)
    private val renderState = MapRenderState()
    private val prefs = FakePreferencesRepository()
    private val support = OnDemandSupport()
    private val regions = FakeRegionRepository(region(id = 1, obaBaseUrl = endpoint))

    private val viewport = CameraSnapshot(
        center = GeoPoint(38.83, -77.05), zoom = 12.0, latSpan = 0.1, lonSpan = 0.1,
        southWest = GeoPoint(38.78, -77.10), northEast = GeoPoint(38.88, -77.00)
    )

    private val square = listOf(GeoPoint(38.8, -77.1), GeoPoint(38.8, -77.0), GeoPoint(38.9, -77.0), GeoPoint(38.9, -77.1), GeoPoint(38.8, -77.1))

    private fun service(id: String = "5088_77652", polygons: Int = 1) = OnDemandService(
        id = id, agencyId = "5088", routeId = id, name = "DOT Paratransit", kind = OnDemandServiceKind.ZONE,
        areas = listOf(ServiceArea("5088_area_1449", null, null, GeoPoint(38.8, -77.1), GeoPoint(38.9, -77.0), List(polygons) { listOf(square) }, null, null)),
        routeColor = 0xFF112233.toInt()
    )

    private fun controller(source: OnDemandDataSource, scope: kotlinx.coroutines.CoroutineScope) = OnDemandLayerController(camera, renderState, source, support, prefs, regions, FakeDemoMode(), scope)

    @Test
    fun `a settled viewport loads zones with the route colour`() = runTest {
        val source = FakeDataSource(OnDemandResult.Loaded(listOf(service())))
        val subject = controller(source, backgroundScope)
        subject.start()
        camera.emit(viewport)
        advanceTimeBy(1)

        assertEquals(1, source.requests.size)
        val zones = renderState.snapshot.value.onDemandZones
        assertEquals(1, zones.size)
        assertEquals("5088_77652", zones[0].serviceId)
        assertEquals(0xFF112233.toInt(), zones[0].color)
        assertEquals(square, zones[0].rings[0])
        subject.stop()
    }

    @Test
    fun `a multipolygon area becomes one zone per polygon`() {
        assertEquals(2, zonePolygons(listOf(service(polygons = 2))).size)
    }

    @Test
    fun `the preference off clears zones and stops requesting`() = runTest {
        val source = FakeDataSource(OnDemandResult.Loaded(listOf(service())))
        val subject = controller(source, backgroundScope)
        subject.start()
        camera.emit(viewport)
        advanceTimeBy(1)
        assertEquals(1, renderState.snapshot.value.onDemandZones.size)

        prefs.setBoolean(R.string.preference_key_show_ondemand_zones, false)
        advanceTimeBy(1)
        assertTrue(renderState.snapshot.value.onDemandZones.isEmpty())
        camera.emit(viewport.copy(center = GeoPoint(38.84, -77.06)))
        advanceTimeBy(1)
        assertEquals(1, source.requests.size)
        subject.stop()
    }

    @Test
    fun `unsupported is recorded and not re-probed`() = runTest {
        val source = FakeDataSource(OnDemandResult.Unsupported)
        val subject = controller(source, backgroundScope)
        subject.start()
        camera.emit(viewport)
        advanceTimeBy(1)
        assertTrue(support.isKnownUnsupported(endpoint))
        assertTrue(renderState.snapshot.value.onDemandZones.isEmpty())

        camera.emit(viewport.copy(center = GeoPoint(38.84, -77.06)))
        advanceTimeBy(1)
        assertEquals(1, source.requests.size)
        subject.stop()
    }

    @Test
    fun `a transient failure keeps the previous zones and does not disable the region`() = runTest {
        val source = FakeDataSource(OnDemandResult.Loaded(listOf(service())))
        val subject = controller(source, backgroundScope)
        subject.start()
        camera.emit(viewport)
        advanceTimeBy(1)

        source.result = OnDemandResult.Failed(IOException("slow"))
        camera.emit(viewport.copy(center = GeoPoint(38.84, -77.06)))
        advanceTimeBy(1)
        assertEquals(2, source.requests.size)
        assertEquals(1, renderState.snapshot.value.onDemandZones.size)
        assertFalse(support.isKnownUnsupported(endpoint))
        subject.stop()
    }

    @Test
    fun `switching regions re-queries the new deployment`() = runTest {
        val source = FakeDataSource(OnDemandResult.Loaded(listOf(service())))
        val subject = controller(source, backgroundScope)
        subject.start()
        camera.emit(viewport)
        advanceTimeBy(1)
        assertEquals(1, source.requests.size)

        regions.emit(region(id = 2, obaBaseUrl = "https://other.example.org/"))
        advanceTimeBy(1)
        assertEquals(2, source.requests.size)
        subject.stop()
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :onebusaway-android:testObaGoogleDebugUnitTest --tests "org.onebusaway.android.map.OnDemandLayerControllerTest"`
Expected: compilation FAILS with `Unresolved reference: OnDemandLayerController`.

- [ ] **Step 3: Add the preference key and the render model**

`values/donottranslate.xml`, after line 52:

```xml
    <string name="preference_key_show_ondemand_zones">preference_show_ondemand_zones</string>
```

`M/map/render/MapRenderState.kt`, before `MapRenderSnapshot` (line 481):

```kotlin
/**
 * One on-demand service-area polygon (GTFS-Flex, wiki §2.4), drawn as a translucent fill in the
 * service's route colour. [rings] follow GeoJSON: ring 0 is the exterior, any others are holes. A
 * MultiPolygon area produces one of these per member, all carrying the same [serviceId], which is what
 * a tap on any of them opens.
 */
data class ZonePolygon(
    val serviceId: String,
    val serviceName: String,
    val rings: List<List<GeoPoint>>,
    /** The route's GTFS colour (ARGB), or null for the default line colour. */
    val color: Int?
)
```

In `MapRenderSnapshot`, after `val rentalsVisible: Boolean = false,`:

```kotlin
    // On-demand service zones for the viewport (Task 6's OnDemandLayerController). Drawn beneath the
    // stops on the static layer; empty when the layer is off or the region has no flex data.
    val onDemandZones: List<ZonePolygon> = emptyList(),
```

After `clearRentals()` (line 806):

```kotlin

    // --- On-demand zones: the viewport's flex service areas, written by OnDemandLayerController. ---

    fun setOnDemandZones(zones: List<ZonePolygon>) {
        _snapshot.update { it.copy(onDemandZones = zones) }
    }

    fun clearOnDemandZones() {
        _snapshot.update { it.copy(onDemandZones = emptyList()) }
    }
```

- [ ] **Step 4: Write the controller**

```kotlin
// M/map/OnDemandLayerController.kt
/* Apache 2.0 header */
package org.onebusaway.android.map

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.onebusaway.android.R
import org.onebusaway.android.api.data.OnDemandDataSource
import org.onebusaway.android.api.data.OnDemandResult
import org.onebusaway.android.api.data.OnDemandSupport
import org.onebusaway.android.demo.DemoModeState
import org.onebusaway.android.map.render.CameraSnapshot
import org.onebusaway.android.map.render.MapRenderState
import org.onebusaway.android.map.render.ZonePolygon
import org.onebusaway.android.models.OnDemandService
import org.onebusaway.android.preferences.PreferencesRepository
import org.onebusaway.android.region.RegionRepository

/**
 * The on-demand zone overlay (GTFS-Flex): a cold driver that loads the flex service areas covering
 * the settled viewport whenever the layer preference is on, and publishes them as
 * [org.onebusaway.android.map.render.MapRenderSnapshot.onDemandZones]. Mirrors [RentalLayerController]:
 * [start] launches the loader for a view, [stop] cancels it, [hide] additionally clears the map.
 *
 * Takes the settled-camera flow and the render state rather than the whole [MapHost] so it is
 * JVM-constructible; [MapViewModel] hands it `mapHost.settledCamera()` and `mapHost.renderState`.
 *
 * Whether the deployment serves the namespace at all is discovered here: the viewport query is the
 * probe, and an [OnDemandResult.Unsupported] answer is recorded in [OnDemandSupport] (keyed by the OBA
 * base URL, like the transit-centre drawer) so this process never asks that server again.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OnDemandLayerController(
    private val settledCamera: Flow<CameraSnapshot>,
    private val renderState: MapRenderState,
    private val dataSource: OnDemandDataSource,
    private val support: OnDemandSupport,
    private val prefsRepository: PreferencesRepository,
    private val regionRepository: RegionRepository,
    private val demoMode: DemoModeState,
    private val scope: CoroutineScope
) {

    private var loadJob: Job? = null

    // The last response and the viewport + deployment that produced it; confined to the loader
    // coroutine, so no synchronization.
    private var cachedViewport: CameraSnapshot? = null
    private var cachedDeployment: String? = null
    private var cachedZones: List<ZonePolygon>? = null

    /** (Re)start the loader for the current view. */
    fun start() {
        loadJob?.cancel()
        loadJob = scope.launch {
            combine(
                settledCamera,
                prefsRepository.observeBoolean(R.string.preference_key_show_ondemand_zones, true),
                // The deployment is an input, not a fact read once: a region switch changes who answers.
                // The demo transit system has no flex data, so demo mode reads as "no deployment".
                combine(regionRepository.region.map { it?.obaBaseUrl }.distinctUntilChanged(), demoMode.active) { url, demo -> if (demo) null else url }
            ) { camera, enabled, deployment -> Triple(camera, enabled, deployment) }
                // A newer viewport cancels an in-flight load.
                .collectLatest { (camera, enabled, deployment) ->
                    if (!enabled || deployment == null || support.isKnownUnsupported(deployment)) {
                        clearZones()
                        return@collectLatest
                    }
                    zonesFor(camera, deployment)?.let(renderState::setOnDemandZones)
                }
        }
    }

    /** Stop the loader, dropping the cache so the next [start] can't redraw another server's zones. */
    fun stop() {
        loadJob?.cancel()
        loadJob = null
        cachedViewport = null
        cachedDeployment = null
        cachedZones = null
    }

    /** Leave the map with no zones on it and the loader off. */
    fun hide() {
        stop()
        clearZones()
    }

    /**
     * The zones for [camera] on [deployment]: from cache when unchanged, else fetched. Null when there
     * is nothing new to draw — a transient failure keeps whatever is on screen (a pan must not blank
     * the layer), and an unsupported answer has already cleared it.
     */
    private suspend fun zonesFor(camera: CameraSnapshot, deployment: String): List<ZonePolygon>? {
        cachedZones?.takeIf { cachedViewport == camera && cachedDeployment == deployment }?.let { return it }
        return when (val result = dataSource.servicesForViewport(camera)) {
            is OnDemandResult.Loaded -> zonePolygons(result.value).also {
                cachedViewport = camera
                cachedDeployment = deployment
                cachedZones = it
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

/** One [ZonePolygon] per polygon of every area of every service, in the route's colour. */
internal fun zonePolygons(services: List<OnDemandService>): List<ZonePolygon> = services.flatMap { service ->
    service.areas.flatMap { area ->
        area.polygons.map { rings -> ZonePolygon(service.id, service.name, rings, service.routeColor) }
    }
}
```

- [ ] **Step 5: Wire it into `MapViewModel`**

Constructor (`M/map/MapViewModel.kt:118-133`): add two parameters after `rentalPlacesRepository: RentalPlacesRepository,`:

```kotlin
    private val onDemandDataSource: OnDemandDataSource,
    private val onDemandSupport: OnDemandSupport,
```

with imports `org.onebusaway.android.api.data.OnDemandDataSource`, `org.onebusaway.android.api.data.OnDemandSupport`. Hilt supplies both (the `@Binds` from Task 4 and the `@Singleton`).

Directly after the `rentalController` construction (line 218):

```kotlin

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
        scope = viewModelScope
    )
```

Then add one line beside each rental call:

- line 341 (`showNearbyStops`): after `rentalController.start()` add `onDemandController.start()`
- line 374 (`showRoute`): after `rentalController.start()` add `onDemandController.start()`
- line 414 (`leaveCurrentView`): after `rentalController.stop()` add `onDemandController.stop()`
- line 635 (`showItinerary`): after `rentalController.hide()` add `onDemandController.hide()` (directions draws no zones, for the reason given there for rentals)
- line 724 (`clearAllFocus`): after `rentalController.start()` add `onDemandController.start()`

- [ ] **Step 6: Run the test and compile**

Run: `./gradlew :onebusaway-android:testObaGoogleDebugUnitTest --tests "org.onebusaway.android.map.OnDemandLayerControllerTest" && ./gradlew :onebusaway-android:compileObaGoogleDebugKotlin :onebusaway-android:compileObaMaplibreDebugKotlin -PwarningsAsErrors=true`
Expected: PASS (6 tests), BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
./gradlew spotlessApply
git add onebusaway-android/src/main/java/org/onebusaway/android/map/OnDemandLayerController.kt onebusaway-android/src/main/java/org/onebusaway/android/map/MapViewModel.kt onebusaway-android/src/main/java/org/onebusaway/android/map/render/MapRenderState.kt onebusaway-android/src/main/res/values/donottranslate.xml onebusaway-android/src/test/java/org/onebusaway/android/map/OnDemandLayerControllerTest.kt
git commit -m "Load on-demand zones for the settled viewport"
```

---

### Task 7: Render zones on both flavors and report zone taps

**Files:**
- Create: `M/map/render/ZoneGeometry.kt`
- Modify: `M/map/compose/ObaMapCallbacks.kt:33-69` (new default method)
- Modify: `onebusaway-android/src/google/java/org/onebusaway/android/map/googlemapsv2/GoogleMapRenderer.kt` (imports; `clearStatic` at `:270-277`; `renderStatic` at `:280-324`; companion constants at `:944-958`)
- Modify: `onebusaway-android/src/google/java/org/onebusaway/android/map/googlemapsv2/compose/GoogleComposeAdapter.kt:300-320` (`wireClicks`)
- Modify: `onebusaway-android/src/maplibre/java/org/onebusaway/android/map/maplibre/MapLibreRenderer.kt` (imports `:30-39`; `renderStatic` at `:274-327`)
- Modify: `onebusaway-android/src/maplibre/java/org/onebusaway/android/map/maplibre/compose/MapLibreComposeAdapter.kt:302-320` (`wireClicks`)
- Test: `T/map/render/ZoneGeometryTest.kt`

**Interfaces:**
- Consumes: `ZonePolygon`, `MapRenderSnapshot.onDemandZones` (Task 6), `DEFAULT_ROUTE_LINE_COLOR` (`MapRenderState.kt:57`), each flavor's existing `GeoPoint.toLatLng()` extension.
- Produces (exact):

```kotlin
package org.onebusaway.android.map.render
fun zoneFillColor(routeColor: Int?): Int      // 20 % alpha over the route colour (or the default line colour)
fun zoneStrokeColor(routeColor: Int?): Int    // 80 % alpha
fun pointInRing(point: GeoPoint, ring: List<GeoPoint>): Boolean
fun ZonePolygon.contains(point: GeoPoint): Boolean   // inside the exterior and outside every hole
const val ZONE_STROKE_WIDTH_PX = 3f

// ObaMapCallbacks
fun onOnDemandZoneClick(zone: ZonePolygon) {}
// GoogleMapRenderer
fun zoneForPolygon(polygon: com.google.android.gms.maps.model.Polygon): ZonePolygon?
// MapLibreRenderer
fun zoneAt(point: org.maplibre.android.geometry.LatLng): ZonePolygon?
```

- [ ] **Step 1: Write the failing geometry test**

```kotlin
// T/map/render/ZoneGeometryTest.kt
package org.onebusaway.android.map.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.util.GeoPoint

class ZoneGeometryTest {

    private val outer = listOf(GeoPoint(0.0, 0.0), GeoPoint(0.0, 10.0), GeoPoint(10.0, 10.0), GeoPoint(10.0, 0.0), GeoPoint(0.0, 0.0))
    private val hole = listOf(GeoPoint(4.0, 4.0), GeoPoint(4.0, 6.0), GeoPoint(6.0, 6.0), GeoPoint(6.0, 4.0), GeoPoint(4.0, 4.0))
    private val zone = ZonePolygon("svc", "Zone", listOf(outer, hole), null)

    @Test
    fun `a point inside the exterior and outside the hole is contained`() {
        assertTrue(zone.contains(GeoPoint(1.0, 1.0)))
        assertTrue(zone.contains(GeoPoint(7.0, 5.0)))
    }

    @Test
    fun `a point inside the hole is not contained`() {
        assertFalse(zone.contains(GeoPoint(5.0, 5.0)))
    }

    @Test
    fun `a point outside the exterior is not contained`() {
        assertFalse(zone.contains(GeoPoint(11.0, 5.0)))
        assertFalse(zone.contains(GeoPoint(-1.0, -1.0)))
    }

    @Test
    fun `an unclosed ring still works`() {
        assertTrue(pointInRing(GeoPoint(1.0, 1.0), outer.dropLast(1)))
    }

    @Test
    fun `fill and stroke keep the route hue and set alpha`() {
        assertEquals(0x33112233, zoneFillColor(0xFF112233.toInt()))
        assertEquals(0xCC112233.toInt(), zoneStrokeColor(0xFF112233.toInt()))
        assertEquals(0x33 shl 24 or (DEFAULT_ROUTE_LINE_COLOR and 0x00FFFFFF), zoneFillColor(null))
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :onebusaway-android:testObaGoogleDebugUnitTest --tests "org.onebusaway.android.map.render.ZoneGeometryTest"`
Expected: compilation FAILS with `Unresolved reference: contains` / `zoneFillColor`.

- [ ] **Step 3: Write the shared geometry + colours**

```kotlin
// M/map/render/ZoneGeometry.kt
/* Apache 2.0 header */
package org.onebusaway.android.map.render

import org.onebusaway.android.util.GeoPoint

/** Stroke width of a zone outline, in pixels, on both flavors. */
const val ZONE_STROKE_WIDTH_PX = 3f

private const val FILL_ALPHA = 0x33
private const val STROKE_ALPHA = 0xCC
private const val RGB_MASK = 0x00FFFFFF

/** The zone fill: the route's colour at ~20 % opacity, so stops and lines stay legible over it. */
fun zoneFillColor(routeColor: Int?): Int = withAlpha(routeColor ?: DEFAULT_ROUTE_LINE_COLOR, FILL_ALPHA)

/** The zone outline: the same hue, ~80 % opaque. */
fun zoneStrokeColor(routeColor: Int?): Int = withAlpha(routeColor ?: DEFAULT_ROUTE_LINE_COLOR, STROKE_ALPHA)

private fun withAlpha(argb: Int, alpha: Int): Int = (alpha shl 24) or (argb and RGB_MASK)

/**
 * Even-odd ray cast of [point] against [ring] (closed or not). Planar lat/lon is exact enough for a
 * tap hit-test on a drawn polygon; it is **not** the service's containment semantics — those are the
 * server's `matchReason`.
 */
fun pointInRing(point: GeoPoint, ring: List<GeoPoint>): Boolean {
    if (ring.size < 3) return false
    var inside = false
    var j = ring.size - 1
    for (i in ring.indices) {
        val a = ring[i]
        val b = ring[j]
        val crosses = (a.latitude > point.latitude) != (b.latitude > point.latitude)
        if (crosses) {
            val lonAtLat = (b.longitude - a.longitude) * (point.latitude - a.latitude) / (b.latitude - a.latitude) + a.longitude
            if (point.longitude < lonAtLat) inside = !inside
        }
        j = i
    }
    return inside
}

/** True inside the exterior ring and outside every hole. */
fun ZonePolygon.contains(point: GeoPoint): Boolean {
    val exterior = rings.firstOrNull() ?: return false
    return pointInRing(point, exterior) && rings.drop(1).none { pointInRing(point, it) }
}
```

Add to `ObaMapCallbacks` (after `onRouteBadgeClick`, line 65), with `import org.onebusaway.android.map.render.ZonePolygon`:

```kotlin

    /** A tap inside an on-demand zone — the host opens that service's page. */
    fun onOnDemandZoneClick(zone: ZonePolygon) {}
```

- [ ] **Step 4: Google renderer**

In `GoogleMapRenderer.kt` add imports `com.google.android.gms.maps.model.Polygon`, `com.google.android.gms.maps.model.PolygonOptions`, `org.onebusaway.android.map.render.ZONE_STROKE_WIDTH_PX`, `org.onebusaway.android.map.render.ZonePolygon`, `org.onebusaway.android.map.render.zoneFillColor`, `org.onebusaway.android.map.render.zoneStrokeColor`. Next to `private val rentalByMarker = HashMap<Marker, RentalMarker>()` (line 119) add:

```kotlin
    private val staticPolygons = mutableListOf<Polygon>()
    private val zoneByPolygon = HashMap<Polygon, ZonePolygon>()
```

In `clearStatic()` (line 270–277) after `staticPolylines.clear()`:

```kotlin
        staticPolygons.forEach { it.remove() }
        staticPolygons.clear()
        zoneByPolygon.clear()
```

In `renderStatic` (line 280), directly after `clearStatic()` and **before** the stop layers so zones sit beneath stops:

```kotlin
        for (zone in snapshot.onDemandZones) {
            val exterior = zone.rings.firstOrNull() ?: continue
            val options = PolygonOptions()
                .addAll(exterior.map { it.toLatLng() })
                .fillColor(zoneFillColor(zone.color))
                .strokeColor(zoneStrokeColor(zone.color))
                .strokeWidth(ZONE_STROKE_WIDTH_PX)
                .clickable(true)
                .zIndex(ZONE_Z_INDEX)
            for (hole in zone.rings.drop(1)) options.addHole(hole.map { it.toLatLng() })
            val polygon = map.addPolygon(options)
            staticPolygons.add(polygon)
            zoneByPolygon[polygon] = zone
        }
```

Add near `rentalForMarker` (line 900):

```kotlin
    /** The zone a native polygon draws, for the adapter's polygon-click dispatch. */
    fun zoneForPolygon(polygon: Polygon): ZonePolygon? = zoneByPolygon[polygon]
```

In the companion (near line 950) add a z-index below the route polylines (which draw at the SDK default of 0) so zones sit under every line and marker:

```kotlin
        /** Beneath route lines (z 0) and every marker: zones are context, not content. */
        private const val ZONE_Z_INDEX = -1f
```

In `GoogleComposeAdapter.wireClicks` (after `setOnMapLongClickListener`, line 313):

```kotlin
    map.setOnPolygonClickListener { polygon ->
        infoWindows.clear()
        renderer.zoneForPolygon(polygon)?.let(cb::onOnDemandZoneClick)
    }
```

- [ ] **Step 5: MapLibre renderer**

In `MapLibreRenderer.kt` add imports `org.maplibre.android.annotations.PolygonOptions`, `org.onebusaway.android.map.render.ZonePolygon`, `org.onebusaway.android.map.render.contains`, `org.onebusaway.android.map.render.zoneFillColor`, `org.onebusaway.android.map.render.zoneStrokeColor`, `org.onebusaway.android.util.GeoPoint` (if not already imported). In `renderStatic` (line 274), after the `routeBadgeByMarker.clear()` and before `stopMarkerLayer.render(...)` (classic annotations draw in add order, so adding first puts zones underneath):

```kotlin
        for (zone in snapshot.onDemandZones) {
            val exterior = zone.rings.firstOrNull() ?: continue
            val options = PolygonOptions()
                .addAll(exterior.map { it.toLatLng() })
                .fillColor(zoneFillColor(zone.color))
                .strokeColor(zoneStrokeColor(zone.color))
                .alpha(1f)
            for (hole in zone.rings.drop(1)) options.addHole(hole.map { it.toLatLng() })
            staticAnnotations.add(map.addPolygon(options))
        }
```

(The classic `PolygonOptions` has no stroke width, so `ZONE_STROKE_WIDTH_PX` is Google-only and deliberately not imported here — an unused import is a warning, and warnings are errors.)

Add near `rentalForMarker` (line 762):

```kotlin
    /** The topmost zone under [point], for the adapter's map-click dispatch (classic polygons have no click listener). */
    fun zoneAt(point: LatLng): ZonePolygon? {
        val geoPoint = GeoPoint(point.latitude, point.longitude)
        return renderState.snapshot.value.onDemandZones.lastOrNull { it.contains(geoPoint) }
    }
```

In `MapLibreComposeAdapter.wireClicks` (line 308–317), inside `addOnMapClickListener` after the `routeStopAt` block and before `infoWindows.clear(); callbacks.onMapClick(...)`:

```kotlin
        renderer.zoneAt(point)?.let { zone ->
            infoWindows.clear()
            callbacks.onOnDemandZoneClick(zone)
            return@addOnMapClickListener true
        }
```

- [ ] **Step 6: Run the test and compile both flavors**

Run: `./gradlew :onebusaway-android:testObaGoogleDebugUnitTest --tests "org.onebusaway.android.map.render.ZoneGeometryTest" && ./gradlew :onebusaway-android:compileObaGoogleDebugKotlin :onebusaway-android:compileObaMaplibreDebugKotlin -PwarningsAsErrors=true`
Expected: PASS (5 tests), BUILD SUCCESSFUL for both flavors.

- [ ] **Step 7: Commit**

```bash
./gradlew spotlessApply
git add onebusaway-android/src/main/java/org/onebusaway/android/map/render/ZoneGeometry.kt onebusaway-android/src/main/java/org/onebusaway/android/map/compose/ObaMapCallbacks.kt onebusaway-android/src/google/java/org/onebusaway/android/map/googlemapsv2/GoogleMapRenderer.kt onebusaway-android/src/google/java/org/onebusaway/android/map/googlemapsv2/compose/GoogleComposeAdapter.kt onebusaway-android/src/maplibre/java/org/onebusaway/android/map/maplibre/MapLibreRenderer.kt onebusaway-android/src/maplibre/java/org/onebusaway/android/map/maplibre/compose/MapLibreComposeAdapter.kt onebusaway-android/src/test/java/org/onebusaway/android/map/render/ZoneGeometryTest.kt
git commit -m "Draw on-demand zones on both map flavors"
```

---

### Task 8: `OnDemandServiceScreen` destination and zone-tap navigation

**Files:**
- Modify: `M/ui/nav/NavRoutes.kt:180-188` (after `routeInfo`)
- Create: `M/ui/ondemand/OnDemandServicePresentation.kt`, `M/ui/ondemand/OnDemandServiceViewModel.kt`, `M/ui/ondemand/OnDemandServiceScreen.kt`, `M/ui/ondemand/OnDemandDestinations.kt`
- Modify: `M/ui/home/HomeNavHost.kt:292` (register graph), `:259` (callback lambda); `M/ui/home/HomeScreen.kt:188` (`HomeCallbacks`), `:887-894` (`MapFeature` call); `M/ui/home/map/MapFeature.kt:144-158` (parameter), `:310` area (callback override)
- Modify: `onebusaway-android/src/main/res/values/strings.xml` (after the rental block ending at line 1454)
- Test: `T/ui/ondemand/OnDemandServicePresentationTest.kt`, `T/ui/ondemand/OnDemandServiceViewModelTest.kt`

**Interfaces:**
- Consumes: `OnDemandDataSource` (Task 4), `BookingDeadlineEvaluator` (Task 5), `ZonePolygon` + `ObaMapCallbacks.onOnDemandZoneClick` (Task 7), `ExternalIntents.goToPhoneDialer(context, "tel:…")` and `ExternalIntents.goToUrl(context, url)` (`M/util/ExternalIntents.kt:44,177`), `ObaTopAppBar(title, onBack)` (`M/ui/compose/components/ObaTopAppBar.kt:38`), `LoadingContent()` / `ErrorContent(onRetry)` (`M/ui/compose/components/StateContent.kt:40,46`), `navigateFromHome` (`M/ui/nav/MapReveal.kt:84`).
- Produces (exact):

```kotlin
// NavRoutes
const val ARG_ONDEMAND_SERVICE_ID = "onDemandServiceId"
const val ONDEMAND_SERVICE = "onDemandService/{$ARG_ONDEMAND_SERVICE_ID}"
fun onDemandService(serviceId: String): String

package org.onebusaway.android.ui.ondemand
data class WhenRow(val calendarId: String, val days: Set<DayOfWeek>, val start: ServiceDayTime?, val end: ServiceDayTime?)
data class BookingSummary(val travelDate: LocalDate?, val evaluation: BookingEvaluation?, val zone: ZoneId, val phoneNumber: String?, val bookingUrl: String?, val infoUrl: String?, val messages: List<String>)
sealed interface OnDemandServiceUiState { Loading; NotFound; Error; data class Content(val service: OnDemandService, val whenRows: List<WhenRow>, val booking: BookingSummary?) }
internal fun presentService(service: OnDemandService, now: Instant): OnDemandServiceUiState.Content
@HiltViewModel class OnDemandServiceViewModel @Inject constructor(savedState: SavedStateHandle, dataSource: OnDemandDataSource) { val state: StateFlow<OnDemandServiceUiState>; fun retry() }
@Composable fun OnDemandServiceScreen(state: OnDemandServiceUiState, onBack: () -> Unit, onRetry: () -> Unit, onCall: (String) -> Unit, onOpenUrl: (String) -> Unit)
fun NavGraphBuilder.onDemandGraph(navController: NavHostController)
// HomeCallbacks / MapFeature
val onOpenOnDemandService: (serviceId: String) -> Unit = {}
```

- [ ] **Step 1: Write the failing tests**

```kotlin
// T/ui/ondemand/OnDemandServicePresentationTest.kt
package org.onebusaway.android.ui.ondemand

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.models.AvailabilityRule
import org.onebusaway.android.models.BookingRule
import org.onebusaway.android.models.BookingType
import org.onebusaway.android.models.FlexCalendar
import org.onebusaway.android.models.OnDemandService
import org.onebusaway.android.models.OnDemandServiceKind
import org.onebusaway.android.models.ServiceDayTime
import org.onebusaway.android.ondemand.BookingState

class OnDemandServicePresentationTest {

    private val weekdays = FlexCalendar(
        "5088_c_63", setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY),
        LocalDate.of(2025, 12, 1), LocalDate.of(2026, 12, 1), emptySet()
    )
    private val sunday = FlexCalendar("5088_c_64", setOf(DayOfWeek.SUNDAY), LocalDate.of(2025, 12, 1), LocalDate.of(2026, 12, 1), emptySet())
    private val booking = BookingRule(
        "5088_booking", BookingType.PRIOR_DAYS, null, null, priorNoticeLastDay = 1, priorNoticeLastTime = ServiceDayTime.parse("17:00:00"),
        priorNoticeStartDay = 14, priorNoticeStartTime = ServiceDayTime(0), priorNoticeCalendarId = null,
        message = "DOT is the City of Alexandria's paratransit program", pickupMessage = null, dropOffMessage = null,
        phoneNumber = "703-746-5222", infoUrl = "https://www.alexandriava.gov/Paratransit", bookingUrl = null
    )

    private fun rule(calendarId: String, start: String) = AvailabilityRule(
        listOf("5088_area_1449"), listOf("5088_area_1449"), ServiceDayTime.parse(start), ServiceDayTime.parse("24:50:00"), ServiceDayTime.parse("25:00:00"),
        listOf(calendarId), 2, 2, "5088_booking", "5088_booking", 1.0, 0.0
    )

    private val alexandria = OnDemandService(
        id = "5088_77652", agencyId = "5088", routeId = "5088_77652", name = "DOT Paratransit", kind = OnDemandServiceKind.ZONE,
        rules = listOf(rule("5088_c_63", "05:00:00"), rule("5088_c_64", "07:00:00")),
        bookingRules = mapOf("5088_booking" to booking), calendars = mapOf("5088_c_63" to weekdays, "5088_c_64" to sunday),
        agencyTimezone = "America/Los_Angeles"
    )

    @Test
    fun `when rows follow rule order and booking names the next bookable date`() {
        // Tuesday 2026-03-10 at 16:00 Pacific: tomorrow's 17:00 cutoff is still an hour away.
        val now = OffsetDateTime.parse("2026-03-10T16:00:00-07:00").toInstant()
        val content = presentService(alexandria, now)
        assertEquals(2, content.whenRows.size)
        assertEquals(weekdays.days, content.whenRows[0].days)
        assertEquals(ServiceDayTime.parse("05:00:00"), content.whenRows[0].start)
        val summary = requireNotNull(content.booking)
        assertEquals(LocalDate.of(2026, 3, 11), summary.travelDate)
        assertEquals(BookingState.OPEN, summary.evaluation?.state)
        assertEquals(OffsetDateTime.parse("2026-03-10T17:00:00-07:00").toInstant(), summary.evaluation?.cutoffInstant)
        assertEquals("703-746-5222", summary.phoneNumber)
        assertEquals(listOf("DOT is the City of Alexandria's paratransit program"), summary.messages)
    }

    @Test
    fun `after the cutoff the next bookable date moves on`() {
        val now = OffsetDateTime.parse("2026-03-10T17:30:00-07:00").toInstant()
        assertEquals(LocalDate.of(2026, 3, 12), presentService(alexandria, now).booking?.travelDate)
    }

    @Test
    fun `a degenerate service with no rules still presents`() {
        val content = presentService(alexandria.copy(rules = emptyList()), OffsetDateTime.parse("2026-03-10T16:00:00-07:00").toInstant())
        assertTrue(content.whenRows.isEmpty())
        assertNull(content.booking)
    }

    @Test
    fun `a same-day rule without a minimum notice yields no deadline but keeps the contact`() {
        val unknownNotice = booking.copy(bookingType = BookingType.SAME_DAY, priorNoticeLastDay = null, priorNoticeLastTime = null, priorNoticeStartDay = null, priorNoticeStartTime = null)
        val service = alexandria.copy(bookingRules = mapOf("5088_booking" to unknownNotice))
        val summary = requireNotNull(presentService(service, OffsetDateTime.parse("2026-03-10T16:00:00-07:00").toInstant()).booking)
        assertNull(summary.travelDate)
        assertNull(summary.evaluation)
        assertEquals("703-746-5222", summary.phoneNumber)
    }
}
```

```kotlin
// T/ui/ondemand/OnDemandServiceViewModelTest.kt
package org.onebusaway.android.ui.ondemand

import androidx.lifecycle.SavedStateHandle
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.onebusaway.android.api.ObaApiException
import org.onebusaway.android.api.data.OnDemandDataSource
import org.onebusaway.android.api.data.OnDemandResult
import org.onebusaway.android.map.render.CameraSnapshot
import org.onebusaway.android.models.OnDemandService
import org.onebusaway.android.models.OnDemandServiceKind
import org.onebusaway.android.testing.MainDispatcherRule
import org.onebusaway.android.ui.nav.NavRoutes

@OptIn(ExperimentalCoroutinesApi::class)
class OnDemandServiceViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(UnconfinedTestDispatcher())

    private class FakeDataSource(var result: OnDemandResult<OnDemandService>) : OnDemandDataSource {
        val requested = mutableListOf<String>()
        override suspend fun servicesForViewport(viewport: CameraSnapshot): OnDemandResult<List<OnDemandService>> = OnDemandResult.Loaded(emptyList())
        override suspend fun service(id: String): OnDemandResult<OnDemandService> {
            requested += id
            return result
        }
        override suspend fun servicesForAgency(agencyId: String): OnDemandResult<List<OnDemandService>> = OnDemandResult.Loaded(emptyList())
    }

    private val service = OnDemandService("5088_77652", "5088", "5088_77652", "DOT Paratransit", OnDemandServiceKind.ZONE, agencyTimezone = "America/Los_Angeles")

    private fun viewModel(source: OnDemandDataSource) = OnDemandServiceViewModel(SavedStateHandle(mapOf(NavRoutes.ARG_ONDEMAND_SERVICE_ID to "5088_77652")), source)

    @Test
    fun `loads the service named by the nav arg`() = runTest {
        val source = FakeDataSource(OnDemandResult.Loaded(service))
        val vm = viewModel(source)
        assertEquals(listOf("5088_77652"), source.requested)
        val content = vm.state.value as OnDemandServiceUiState.Content
        assertEquals("DOT Paratransit", content.service.name)
    }

    @Test
    fun `an OBA 404 is not found and a transport failure is an error`() = runTest {
        assertEquals(OnDemandServiceUiState.NotFound, viewModel(FakeDataSource(OnDemandResult.Failed(ObaApiException(404)))).state.value)
        assertEquals(OnDemandServiceUiState.Error, viewModel(FakeDataSource(OnDemandResult.Failed(IOException("offline")))).state.value)
        assertEquals(OnDemandServiceUiState.Error, viewModel(FakeDataSource(OnDemandResult.Unsupported)).state.value)
    }

    @Test
    fun `retry re-requests`() = runTest {
        val source = FakeDataSource(OnDemandResult.Failed(IOException("offline")))
        val vm = viewModel(source)
        source.result = OnDemandResult.Loaded(service)
        vm.retry()
        assertEquals(2, source.requested.size)
        assertTrue(vm.state.value is OnDemandServiceUiState.Content)
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :onebusaway-android:testObaGoogleDebugUnitTest --tests "org.onebusaway.android.ui.ondemand.*"`
Expected: compilation FAILS with `Unresolved reference: presentService` / `OnDemandServiceViewModel` / `ARG_ONDEMAND_SERVICE_ID`.

- [ ] **Step 3: Nav route, presentation, view model**

`NavRoutes.kt` after `fun routeInfo(...)` (line 188):

```kotlin

    // --- On-demand (GTFS-Flex) service page ---
    const val ARG_ONDEMAND_SERVICE_ID = "onDemandServiceId"
    const val ONDEMAND_SERVICE = "onDemandService/{$ARG_ONDEMAND_SERVICE_ID}"

    /** Builds a navigable [ONDEMAND_SERVICE] route; service ids equal route ids and may contain `/`. */
    fun onDemandService(serviceId: String): String = "onDemandService/${Uri.encode(serviceId)}"
```

```kotlin
// M/ui/ondemand/OnDemandServicePresentation.kt
/* Apache 2.0 header */
package org.onebusaway.android.ui.ondemand

import java.time.DayOfWeek
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.onebusaway.android.models.OnDemandService
import org.onebusaway.android.models.ServiceDayTime
import org.onebusaway.android.ondemand.BookingDeadlineEvaluator
import org.onebusaway.android.ondemand.BookingEvaluation
import org.onebusaway.android.ondemand.BookingState

/** One "When" line: the days of [calendarId] and the pickup window (null ends = all hours). */
data class WhenRow(val calendarId: String, val days: Set<DayOfWeek>, val start: ServiceDayTime?, val end: ServiceDayTime?)

/**
 * The "How to book" section. [travelDate] is the next bookable service day and [evaluation] its
 * verdict (the earliest cutoff among the rules active that day — conservative, wiki §2.5); both null
 * when no date can be promised (every rule's notice is unknown, or the calendars have ended).
 */
data class BookingSummary(
    val travelDate: LocalDate?,
    val evaluation: BookingEvaluation?,
    val zone: ZoneId,
    val phoneNumber: String?,
    val bookingUrl: String?,
    val infoUrl: String?,
    val messages: List<String>
)

sealed interface OnDemandServiceUiState {
    data object Loading : OnDemandServiceUiState
    data object NotFound : OnDemandServiceUiState
    data object Error : OnDemandServiceUiState
    data class Content(val service: OnDemandService, val whenRows: List<WhenRow>, val booking: BookingSummary?) : OnDemandServiceUiState
}

/**
 * Projects a service for the page at [now] (the device wall clock, minted by the caller). Pure, so
 * the deadline line is JVM-tested with a fixed instant.
 */
internal fun presentService(service: OnDemandService, now: Instant): OnDemandServiceUiState.Content {
    val zone = agencyZone(service.agencyTimezone)
    val whenRows = service.rules.flatMap { rule ->
        rule.calendarIds.mapNotNull { id -> service.calendars[id]?.let { WhenRow(id, it.days, rule.startPickupTime, rule.endPickupTime) } }
    }
    val bookingRules = service.rules.mapNotNull(service::pickupBookingRule).distinctBy { it.id }
    val booking = if (service.rules.isEmpty()) {
        null
    } else {
        val today = now.atZone(zone).toLocalDate()
        val travelDate = service.rules.mapNotNull { rule ->
            BookingDeadlineEvaluator.nextBookableServiceDate(rule, service.pickupBookingRule(rule), today, now, zone, service.calendars)
        }.minOrNull()
        val evaluation = travelDate?.let { date ->
            service.rules
                .filter { rule -> rule.calendarIds.any { service.calendars[it]?.isActiveOn(date) == true } }
                .map { rule -> BookingDeadlineEvaluator.evaluate(rule, service.pickupBookingRule(rule), date, now, zone, service.calendars) }
                .filter { it.state != BookingState.UNKNOWN }
                .minByOrNull { it.cutoffInstant ?: Instant.MAX }
        }
        BookingSummary(
            travelDate = travelDate,
            evaluation = evaluation,
            zone = zone,
            phoneNumber = bookingRules.firstNotNullOfOrNull { it.phoneNumber },
            bookingUrl = bookingRules.firstNotNullOfOrNull { it.bookingUrl },
            infoUrl = bookingRules.firstNotNullOfOrNull { it.infoUrl },
            messages = bookingRules.flatMap { listOfNotNull(it.message, it.pickupMessage, it.dropOffMessage) }.distinct()
        )
    }
    return OnDemandServiceUiState.Content(service, whenRows, booking)
}

/**
 * The agency timezone is required by GTFS and always in the references; the device zone is only a
 * last resort for a feed that published an id `java.time` doesn't know, so the page still renders.
 */
private fun agencyZone(timezone: String?): ZoneId = try {
    timezone?.let(ZoneId::of) ?: ZoneId.systemDefault()
} catch (_: DateTimeException) {
    ZoneId.systemDefault()
}
```

```kotlin
// M/ui/ondemand/OnDemandServiceViewModel.kt
/* Apache 2.0 header */
package org.onebusaway.android.ui.ondemand

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.onebusaway.android.api.data.OnDemandDataSource
import org.onebusaway.android.api.data.OnDemandResult
import org.onebusaway.android.api.isNotFound
import org.onebusaway.android.time.WallTime
import org.onebusaway.android.ui.nav.NavRoutes

/** Loads the service named by [NavRoutes.ARG_ONDEMAND_SERVICE_ID] and presents it for the page. */
@HiltViewModel
class OnDemandServiceViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val dataSource: OnDemandDataSource
) : ViewModel() {

    private val serviceId: String = requireNotNull(savedState[NavRoutes.ARG_ONDEMAND_SERVICE_ID]) { "on-demand service page requires a service id" }

    private val _state = MutableStateFlow<OnDemandServiceUiState>(OnDemandServiceUiState.Loading)
    val state: StateFlow<OnDemandServiceUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun retry() = load()

    private fun load() {
        _state.value = OnDemandServiceUiState.Loading
        viewModelScope.launch {
            _state.value = when (val result = dataSource.service(serviceId)) {
                // The deadline is computed against the device wall clock (spec §6.3), never the
                // envelope's currentTime, which sits on the long-cache tier.
                is OnDemandResult.Loaded -> presentService(result.value, Instant.ofEpochMilli(WallTime.now().epochMs))
                is OnDemandResult.Failed -> if (result.cause.isNotFound) OnDemandServiceUiState.NotFound else OnDemandServiceUiState.Error
                OnDemandResult.Unsupported -> OnDemandServiceUiState.Error
            }
        }
    }
}
```

- [ ] **Step 4: Screen, destination, strings**

Strings — append to `values/strings.xml` after the rental block (after line 1454, before the `<!-- Note: %1$s is replaced with app_name ... -->` comment):

```xml

    <!-- On-demand (GTFS-Flex) services -->
    <string name="ondemand_service_title">On-demand service</string>
    <string name="ondemand_kind_zone">Dial-a-ride zone</string>
    <string name="ondemand_kind_zone_to_zone">Zone to zone</string>
    <string name="ondemand_kind_stop_group">Between designated stops</string>
    <string name="ondemand_kind_deviated_route">Route deviation</string>
    <string name="ondemand_kind_unknown">On-demand</string>
    <string name="ondemand_when_title">When</string>
    <string name="ondemand_all_hours">All hours</string>
    <!-- %1$s and %2$s are clock times, e.g. 5:00 AM – 12:50 AM -->
    <string name="ondemand_time_window">%1$s – %2$s</string>
    <!-- %1$s is a clock time that falls after midnight on the following day -->
    <string name="ondemand_time_next_day">%1$s (next day)</string>
    <string name="ondemand_how_to_book_title">How to book</string>
    <!-- %1$s is a date and time, %2$s is a date -->
    <string name="ondemand_book_by">Book by %1$s for a ride on %2$s</string>
    <string name="ondemand_booking_opens">Booking opens %1$s for a ride on %2$s</string>
    <string name="ondemand_book_at_ride_time">Book when you are ready to ride</string>
    <string name="ondemand_no_deadline_published">This agency has not published a booking deadline. Contact them to book.</string>
    <string name="ondemand_no_rules">No service hours are published for this service.</string>
    <!-- %1$s is a phone number -->
    <string name="ondemand_call">Call %1$s</string>
    <string name="ondemand_book_online">Book online</string>
    <string name="ondemand_more_info">More information</string>
    <string name="ondemand_details_title">Details</string>
    <string name="ondemand_not_found">This on-demand service is not available in this region.</string>
```

```kotlin
// M/ui/ondemand/OnDemandServiceScreen.kt
/* Apache 2.0 header */
package org.onebusaway.android.ui.ondemand

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale
import org.onebusaway.android.R
import org.onebusaway.android.models.OnDemandServiceKind
import org.onebusaway.android.models.ServiceDayTime
import org.onebusaway.android.ondemand.BookingState
import org.onebusaway.android.ui.compose.components.ErrorContent
import org.onebusaway.android.ui.compose.components.LoadingContent
import org.onebusaway.android.ui.compose.components.ObaTopAppBar

@StringRes
private fun OnDemandServiceKind.labelRes(): Int = when (this) {
    OnDemandServiceKind.ZONE -> R.string.ondemand_kind_zone
    OnDemandServiceKind.ZONE_TO_ZONE -> R.string.ondemand_kind_zone_to_zone
    OnDemandServiceKind.STOP_GROUP -> R.string.ondemand_kind_stop_group
    OnDemandServiceKind.DEVIATED_ROUTE -> R.string.ondemand_kind_deviated_route
    OnDemandServiceKind.UNKNOWN -> R.string.ondemand_kind_unknown
}

/** The service page: name, kind, when it runs, how to book, and the feed's own notes. */
@Composable
fun OnDemandServiceScreen(
    state: OnDemandServiceUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onCall: (String) -> Unit,
    onOpenUrl: (String) -> Unit
) {
    val title = (state as? OnDemandServiceUiState.Content)?.service?.name ?: stringResource(R.string.ondemand_service_title)
    Scaffold(topBar = { ObaTopAppBar(title = title, onBack = onBack) }) { padding ->
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
    // Configuration.locales is API 24; on minSdk 23 the JVM default locale is the app locale.
    val locale = Locale.getDefault()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(stringResource(content.service.kind.labelRes()), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        content.service.description?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }

        Text(stringResource(R.string.ondemand_when_title), style = MaterialTheme.typography.titleMedium)
        if (content.whenRows.isEmpty()) {
            Text(stringResource(R.string.ondemand_no_rules), style = MaterialTheme.typography.bodyMedium)
        }
        for (row in content.whenRows) {
            Text(formatDays(row.days, locale), style = MaterialTheme.typography.bodyMedium)
            Text(formatWindow(row.start, row.end, locale), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        content.booking?.let { booking ->
            Text(stringResource(R.string.ondemand_how_to_book_title), style = MaterialTheme.typography.titleMedium)
            Text(deadlineLine(booking, locale), style = MaterialTheme.typography.bodyMedium)
            booking.phoneNumber?.let { phone ->
                Button(onClick = { onCall(phone) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.ondemand_call, phone)) }
            }
            booking.bookingUrl?.let { url ->
                OutlinedButton(onClick = { onOpenUrl(url) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.ondemand_book_online)) }
            }
            booking.infoUrl?.let { url ->
                OutlinedButton(onClick = { onOpenUrl(url) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.ondemand_more_info)) }
            }
            if (booking.messages.isNotEmpty()) {
                Text(stringResource(R.string.ondemand_details_title), style = MaterialTheme.typography.titleMedium)
                for (message in booking.messages) Text(message, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun deadlineLine(booking: BookingSummary, locale: Locale): String {
    val evaluation = booking.evaluation
    val travelDate = booking.travelDate
    if (evaluation == null || travelDate == null) return stringResource(R.string.ondemand_no_deadline_published)
    val dateTime = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(locale).withZone(booking.zone)
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

private fun formatDays(days: Set<DayOfWeek>, locale: Locale): String = if (days.size == 7) {
    DayOfWeek.entries.joinToString(", ") { it.getDisplayName(TextStyle.SHORT, locale) }
} else {
    DayOfWeek.entries.filter { it in days }.joinToString(", ") { it.getDisplayName(TextStyle.SHORT, locale) }
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

```kotlin
// M/ui/ondemand/OnDemandDestinations.kt
/* Apache 2.0 header */
package org.onebusaway.android.ui.ondemand

import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import org.onebusaway.android.ui.compose.theme.ObaTheme
import org.onebusaway.android.ui.nav.NavRoutes
import org.onebusaway.android.util.ExternalIntents

/** The on-demand service page ([NavRoutes.ONDEMAND_SERVICE]); reached from a zone tap or the arrivals card. */
fun NavGraphBuilder.onDemandGraph(navController: NavHostController) {
    composable(
        NavRoutes.ONDEMAND_SERVICE,
        arguments = listOf(navArgument(NavRoutes.ARG_ONDEMAND_SERVICE_ID) { type = NavType.StringType })
    ) {
        val context = LocalContext.current
        val viewModel: OnDemandServiceViewModel = hiltViewModel()
        val state by viewModel.state.collectAsStateWithLifecycle()
        ObaTheme {
            OnDemandServiceScreen(
                state = state,
                onBack = { navController.popBackStack() },
                onRetry = viewModel::retry,
                // ACTION_DIAL never places the call itself; the rider confirms in the dialer.
                onCall = { phone -> ExternalIntents.goToPhoneDialer(context, "tel:$phone") },
                onOpenUrl = { url -> ExternalIntents.goToUrl(context, url) }
            )
        }
    }
}
```

Register in `HomeNavHost.kt` after `routeInfoGraph(navController)` (line 292): `onDemandGraph(navController)` with `import org.onebusaway.android.ui.ondemand.onDemandGraph`.

- [ ] **Step 5: Wire the zone tap through Home**

`HomeScreen.kt` `HomeCallbacks` (after `val onOpenSurvey` at line 188):

```kotlin
    // A tap on a drawn on-demand zone, or the arrivals card: open that service's page.
    val onOpenOnDemandService: (serviceId: String) -> Unit = {},
```

`HomeNavHost.kt` after the `onOpenSurvey = ...` line (259):

```kotlin
                    onOpenOnDemandService = { id -> navController.navigateFromHome(NavRoutes.onDemandService(id)) },
```

`MapFeature.kt` parameter list (after `onStopsBannerHeight` at line 158):

```kotlin
    // A tap inside an on-demand zone opens that service's page; the host owns navigation.
    onOpenOnDemandService: (serviceId: String) -> Unit = {}
```

and inside the `object : ObaMapCallbacks` (after `onRouteBadgeClick`), with `import org.onebusaway.android.map.render.ZonePolygon`:

```kotlin
            override fun onOnDemandZoneClick(zone: ZonePolygon) {
                dismissNavigateHere()
                onOpenOnDemandService(zone.serviceId)
            }
```

`HomeScreen.kt` `MapFeature(` call (line 887): add `onOpenOnDemandService = onOpenOnDemandService,` after `onStopsBannerHeight`.

- [ ] **Step 6: Run the tests and compile**

Run: `./gradlew :onebusaway-android:testObaGoogleDebugUnitTest --tests "org.onebusaway.android.ui.ondemand.*" && ./gradlew :onebusaway-android:compileObaGoogleDebugKotlin :onebusaway-android:compileObaMaplibreDebugKotlin -PwarningsAsErrors=true`
Expected: PASS (4 + 3 tests), BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
./gradlew spotlessApply
git add onebusaway-android/src/main/java/org/onebusaway/android/ui/ondemand onebusaway-android/src/main/java/org/onebusaway/android/ui/nav/NavRoutes.kt onebusaway-android/src/main/java/org/onebusaway/android/ui/home/HomeNavHost.kt onebusaway-android/src/main/java/org/onebusaway/android/ui/home/HomeScreen.kt onebusaway-android/src/main/java/org/onebusaway/android/ui/home/map/MapFeature.kt onebusaway-android/src/main/res/values/strings.xml onebusaway-android/src/test/java/org/onebusaway/android/ui/ondemand
git commit -m "Add on-demand service page and open it from zone taps"
```

---

### Task 9: On-demand card on the arrivals screen

**Files:**
- Create: `M/ui/arrivals/OnDemandItems.kt`, `M/ui/arrivals/components/OnDemandServicesCard.kt`
- Modify: `M/ui/arrivals/ArrivalsRepository.kt:107-131` (`ArrivalsData`), `:226-237` (constructor), `:325-379` (`toData`); `M/ui/arrivals/ArrivalsUiState.kt:86-113` (`Content`); `M/ui/arrivals/ArrivalsViewModel.kt:240-266` (`toContent`); `M/ui/arrivals/ArrivalsContent.kt:158-288` (`ArrivalsList`); `M/ui/arrivals/ArrivalsDestinations.kt:227-242`; `M/ui/arrivals/components/ArrivalsPanel.kt:45-99`; `M/ui/home/arrivals/ArrivalsSheetHost.kt:94-95,168-184`; `M/ui/home/HomeScreen.kt` (the `ArrivalsSheetHost(` call site — grep for it)
- Modify: `values/strings.xml` (on-demand block from Task 8)
- Test: `T/ui/arrivals/OnDemandItemsTest.kt`, `T/ui/arrivals/ArrivalsListLayoutTest.kt`; update `T/ui/arrivals/ArrivalsViewModelTest.kt:145-158` (`data()` builder) and `T/ui/arrivals/DefaultArrivalsRepositoryTest.kt` (constructor gains a fake data source)

**Interfaces:**
- Consumes: `OnDemandDataSource.service(id)` / `OnDemandResult` (Task 4), `OnDemandService` (Task 3), `ObaStop.onDemandServiceIds` (Task 3), `NavRoutes.onDemandService` (Task 8), `HomeCallbacks.onOpenOnDemandService` (Task 8).
- Produces (exact):

```kotlin
package org.onebusaway.android.ui.arrivals
data class OnDemandServiceItem(val id: String, val name: String, val kind: OnDemandServiceKind, val phoneNumber: String?)
internal fun OnDemandService.toItem(): OnDemandServiceItem
internal suspend fun loadOnDemandItems(ids: List<String>, cache: MutableMap<String, OnDemandServiceItem>, fetch: suspend (String) -> OnDemandResult<OnDemandService>): List<OnDemandServiceItem>
internal fun firstRouteIndex(hasModeSwitch: Boolean, alertsBeforeRoutes: Boolean, onDemandBeforeRoutes: Boolean, directionBeforeRoutes: Boolean): Int
// ArrivalsData / ArrivalsUiState.Content: val onDemandServices: List<OnDemandServiceItem> = emptyList()
// ArrivalsList / ArrivalsPanel / ArrivalsSheetHost: onOpenOnDemandService: (serviceId: String) -> Unit = {}
// DefaultArrivalsRepository constructor gains: private val onDemandDataSource: OnDemandDataSource
```

- [ ] **Step 1: Write the failing tests**

```kotlin
// T/ui/arrivals/OnDemandItemsTest.kt
package org.onebusaway.android.ui.arrivals

import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.onebusaway.android.api.data.OnDemandResult
import org.onebusaway.android.models.AvailabilityRule
import org.onebusaway.android.models.BookingRule
import org.onebusaway.android.models.BookingType
import org.onebusaway.android.models.OnDemandService
import org.onebusaway.android.models.OnDemandServiceKind

class OnDemandItemsTest {

    private fun service(id: String, phone: String?) = OnDemandService(
        id = id, agencyId = "5088", routeId = id, name = "Service $id", kind = OnDemandServiceKind.ZONE,
        rules = listOf(AvailabilityRule(emptyList(), emptyList(), null, null, null, emptyList(), 2, 2, "b", null, null, null)),
        bookingRules = mapOf("b" to BookingRule("b", BookingType.REAL_TIME, null, null, null, null, null, null, null, null, null, null, phone, null, null))
    )

    @Test
    fun `items keep pointer order and drop failures`() = runTest {
        val cache = mutableMapOf<String, OnDemandServiceItem>()
        val items = loadOnDemandItems(listOf("a", "b", "c"), cache) { id ->
            if (id == "b") OnDemandResult.Failed(IOException("slow")) else OnDemandResult.Loaded(service(id, "555"))
        }
        assertEquals(listOf("a", "c"), items.map { it.id })
        assertEquals("555", items[0].phoneNumber)
        assertEquals(setOf("a", "c"), cache.keys)
    }

    @Test
    fun `a cached item is not fetched again`() = runTest {
        val cache = mutableMapOf("a" to OnDemandServiceItem("a", "Cached", OnDemandServiceKind.ZONE, null))
        var fetches = 0
        val items = loadOnDemandItems(listOf("a"), cache) { fetches++; OnDemandResult.Loaded(service("a", null)) }
        assertEquals(0, fetches)
        assertEquals("Cached", items.single().name)
    }
}
```

```kotlin
// T/ui/arrivals/ArrivalsListLayoutTest.kt
package org.onebusaway.android.ui.arrivals

import org.junit.Assert.assertEquals
import org.junit.Test

/** The index of the first route row, which the promoted-row scroll targets — one per optional head item. */
class ArrivalsListLayoutTest {
    @Test
    fun `counts every optional item ahead of the route rows`() {
        assertEquals(0, firstRouteIndex(hasModeSwitch = false, alertsBeforeRoutes = false, onDemandBeforeRoutes = false, directionBeforeRoutes = false))
        assertEquals(1, firstRouteIndex(hasModeSwitch = false, alertsBeforeRoutes = false, onDemandBeforeRoutes = true, directionBeforeRoutes = false))
        assertEquals(4, firstRouteIndex(hasModeSwitch = true, alertsBeforeRoutes = true, onDemandBeforeRoutes = true, directionBeforeRoutes = true))
    }
}
```

In `T/ui/arrivals/ArrivalsViewModelTest.kt`, add a parameter `onDemandServices: List<OnDemandServiceItem> = emptyList()` to the `data(...)` builder (line 145) and pass `onDemandServices = onDemandServices` into the `ArrivalsData(...)` construction; then add:

```kotlin
    @Test
    fun `on-demand services reach the content state`() = runTest {
        val item = OnDemandServiceItem("5088_77652", "DOT Paratransit", org.onebusaway.android.models.OnDemandServiceKind.ZONE, "703-746-5222")
        val viewModel = ArrivalsViewModel("1_100", FakeArrivalsRepository(Result.success(data(onDemandServices = listOf(item)))))
        viewModel.refresh()
        assertEquals(listOf(item), (viewModel.state.value as ArrivalsUiState.Content).onDemandServices)
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :onebusaway-android:testObaGoogleDebugUnitTest --tests "org.onebusaway.android.ui.arrivals.OnDemandItemsTest" --tests "org.onebusaway.android.ui.arrivals.ArrivalsListLayoutTest" --tests "org.onebusaway.android.ui.arrivals.ArrivalsViewModelTest"`
Expected: compilation FAILS with `Unresolved reference: loadOnDemandItems` / `firstRouteIndex` / `onDemandServices`.

- [ ] **Step 3: Items helper and state plumbing**

```kotlin
// M/ui/arrivals/OnDemandItems.kt
/* Apache 2.0 header */
package org.onebusaway.android.ui.arrivals

import org.onebusaway.android.api.data.OnDemandResult
import org.onebusaway.android.models.OnDemandService
import org.onebusaway.android.models.OnDemandServiceKind

/** One on-demand service named by the stop's pointer field, as the arrivals card shows it. */
data class OnDemandServiceItem(
    val id: String,
    val name: String,
    val kind: OnDemandServiceKind,
    /** The pickup booking rule's phone number, in rule order, or null when none publishes one. */
    val phoneNumber: String?
)

internal fun OnDemandService.toItem(): OnDemandServiceItem = OnDemandServiceItem(
    id = id,
    name = name,
    kind = kind,
    phoneNumber = rules.firstNotNullOfOrNull { pickupBookingRule(it)?.phoneNumber }
)

/**
 * Resolves a stop's pointer [ids] to card items, in pointer order, through [cache] (services are
 * static data; a 60-second poll must not refetch them). A service that fails to load is simply left
 * off the card — the arrivals themselves are the screen's job, and the pointer will be tried again on
 * the next load.
 */
internal suspend fun loadOnDemandItems(
    ids: List<String>,
    cache: MutableMap<String, OnDemandServiceItem>,
    fetch: suspend (String) -> OnDemandResult<OnDemandService>
): List<OnDemandServiceItem> = ids.mapNotNull { id ->
    cache[id] ?: (fetch(id) as? OnDemandResult.Loaded)?.value?.toItem()?.also { cache[id] = it }
}
```

`ArrivalsRepository.kt`:
- `ArrivalsData` (line 107): add `val onDemandServices: List<OnDemandServiceItem> = emptyList()` as the last property.
- `DefaultArrivalsRepository` constructor (line 226–237): add `private val onDemandDataSource: OnDemandDataSource` after `private val demoMode: DemoModeState` (import `org.onebusaway.android.api.data.OnDemandDataSource`). Update the construction in `T/ui/arrivals/DefaultArrivalsRepositoryTest.kt` (grep `DefaultArrivalsRepository(` there) to pass a new last argument `NoOnDemandDataSource()`, declared once at the bottom of that test file:

```kotlin
/** No stop under test carries a pointer, so nothing is ever fetched; a call is a test bug. */
private class NoOnDemandDataSource : OnDemandDataSource {
    override suspend fun servicesForViewport(viewport: CameraSnapshot): OnDemandResult<List<OnDemandService>> = OnDemandResult.Loaded(emptyList())
    override suspend fun service(id: String): OnDemandResult<OnDemandService> = error("unexpected on-demand fetch for $id")
    override suspend fun servicesForAgency(agencyId: String): OnDemandResult<List<OnDemandService>> = OnDemandResult.Loaded(emptyList())
}
```

(imports `org.onebusaway.android.api.data.OnDemandDataSource`, `org.onebusaway.android.api.data.OnDemandResult`, `org.onebusaway.android.map.render.CameraSnapshot`, `org.onebusaway.android.models.OnDemandService`).
- Field: `private val onDemandItemsById = mutableMapOf<String, OnDemandServiceItem>()` next to `stopRecorded` (line 266).
- In `toData` (line 325), after `val stop = snapshot.stop`:

```kotlin
        val onDemandServices = loadOnDemandItems(stop?.onDemandServiceIds.orEmpty(), onDemandItemsById, onDemandDataSource::service)
```

and pass `onDemandServices = onDemandServices` into the `ArrivalsData(...)` construction (after `stopLon`).

`ArrivalsUiState.kt` `Content` (line 86): add `val onDemandServices: List<OnDemandServiceItem> = emptyList()` after `stopLon`. `ArrivalsViewModel.toContent` (line 248): pass `onDemandServices = onDemandServices`.

- [ ] **Step 4: The card and the list**

```kotlin
// M/ui/arrivals/components/OnDemandServicesCard.kt
/* Apache 2.0 header */
package org.onebusaway.android.ui.arrivals.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.onebusaway.android.R
import org.onebusaway.android.ui.arrivals.OnDemandServiceItem

/** The stop's on-demand services (GTFS-Flex pointers), one tappable row each, ahead of the route rows. */
@Composable
internal fun OnDemandServicesCard(
    items: List<OnDemandServiceItem>,
    onOpen: (serviceId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
        Text(
            text = stringResource(R.string.ondemand_card_title),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        )
        for (item in items) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp).clickable { onOpen(item.id) }
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(item.name, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = item.phoneNumber?.let { stringResource(R.string.ondemand_card_phone, it) } ?: stringResource(R.string.ondemand_card_tap),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}
```

Strings (append to the on-demand block from Task 8):

```xml
    <string name="ondemand_card_title">On-demand service at this stop</string>
    <string name="ondemand_card_tap">Tap for hours and booking</string>
    <!-- %1$s is a phone number -->
    <string name="ondemand_card_phone">Book by phone: %1$s</string>
```

`ArrivalsContent.kt`:
- Add a top-level function (near `REFRESH_PERIOD_MS`):

```kotlin
/**
 * The index of the first route row: every optional head item — the display-mode switch, the alert
 * section, the on-demand card, the direction line — pushes it down by one. Kept pure so the count
 * can't drift from the `item(...)` order in [ArrivalsList] without a failing test.
 */
internal fun firstRouteIndex(hasModeSwitch: Boolean, alertsBeforeRoutes: Boolean, onDemandBeforeRoutes: Boolean, directionBeforeRoutes: Boolean): Int = listOf(hasModeSwitch, alertsBeforeRoutes, onDemandBeforeRoutes, directionBeforeRoutes).count { it }
```

- `ArrivalsList` signature: add `onOpenOnDemandService: (serviceId: String) -> Unit = {},` after `modeSwitchModifier`.
- Replace the `firstRouteIndex` arithmetic at lines 224–230 with:

```kotlin
            val alertsBeforeRoutes = content.hasAlerts && showAlerts
            val onDemandBeforeRoutes = content.onDemandServices.isNotEmpty()
            val directionBeforeRoutes = showDirection && content.header.direction != null
            listState.scrollToItem(firstRouteIndex(onDisplayModeChange != null, alertsBeforeRoutes, onDemandBeforeRoutes, directionBeforeRoutes))
```

- In the `LazyColumn`, between the `"alerts"` item (ends line 252) and the `if (showDirection)` block:

```kotlin
        if (content.onDemandServices.isNotEmpty()) {
            item(key = "ondemand") {
                OnDemandServicesCard(content.onDemandServices, onOpenOnDemandService, Modifier.animateItem())
            }
        }
```

(import `org.onebusaway.android.ui.arrivals.components.OnDemandServicesCard`.)

- `ArrivalsDestinations.kt` `ArrivalsList(` call (line 227): add `onOpenOnDemandService = { navController.navigate(NavRoutes.onDemandService(it)) },`.
- `ArrivalsPanel.kt`: add parameter `onOpenOnDemandService: (serviceId: String) -> Unit = {}` after `modeSwitchModifier`; pass `onOpenOnDemandService = onOpenOnDemandService` into `ArrivalsList(`.
- `ArrivalsSheetHost.kt`: add parameter `onOpenOnDemandService: (serviceId: String) -> Unit` after `onEditReminder` (line 95); add `val openOnDemandState = rememberUpdatedState(onOpenOnDemandService)` beside the other `rememberUpdatedState` lines (115–116); pass `onOpenOnDemandService = { openOnDemandState.value(it) }` into `ArrivalsPanel(` (line 168).
- `HomeScreen.kt`: at the `ArrivalsSheetHost(` call, add `onOpenOnDemandService = onOpenOnDemandService,` next to `onShowTrip = onShowTrip,` (`HomeCallbacks` is in scope via `with(callbacks)`).

- [ ] **Step 5: Run the tests and compile**

Run: `./gradlew :onebusaway-android:testObaGoogleDebugUnitTest --tests "org.onebusaway.android.ui.arrivals.*" && ./gradlew :onebusaway-android:compileObaGoogleDebugKotlin -PwarningsAsErrors=true`
Expected: PASS (all arrivals tests including the three new ones), BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
./gradlew spotlessApply
git add onebusaway-android/src/main/java/org/onebusaway/android/ui/arrivals onebusaway-android/src/main/java/org/onebusaway/android/ui/home/arrivals/ArrivalsSheetHost.kt onebusaway-android/src/main/java/org/onebusaway/android/ui/home/HomeScreen.kt onebusaway-android/src/main/res/values/strings.xml onebusaway-android/src/test/java/org/onebusaway/android/ui/arrivals
git commit -m "Show a stop's on-demand services on the arrivals screen"
```

---

### Task 10: Settings toggle for the zones layer

**Files:**
- Modify: `M/ui/settings/SettingsUiState.kt:39-57` (`SettingsPrefSnapshot`), `:71-99` (`SettingsUiState`), `:107-135` (`buildSettingsUiState`); `M/ui/settings/SettingsViewModel.kt:96-121` (`readSnapshot`), `:180` (setter); `M/ui/settings/SettingsScreen.kt:149` (actions), `:189-190` (actions class), `:307-313` (row)
- Modify: `values/strings.xml` (after `preferences_show_rental_button_summary`, line 687)
- Test: `T/ui/settings/SettingsOnDemandZonesTest.kt`

**Interfaces:**
- Consumes: `R.string.preference_key_show_ondemand_zones` (Task 6, default `true`).
- Produces: `SettingsPrefSnapshot.showOnDemandZones: Boolean`, `SettingsUiState.showOnDemandZones: Boolean`, `SettingsViewModel.onShowOnDemandZonesChanged(value: Boolean)`, `SettingsActions.onShowOnDemandZones: (Boolean) -> Unit`.

- [ ] **Step 1: Write the failing test**

```kotlin
// T/ui/settings/SettingsOnDemandZonesTest.kt
package org.onebusaway.android.ui.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsOnDemandZonesTest {

    private fun snapshot(showOnDemandZones: Boolean) = SettingsPrefSnapshot(
        autoSelectRegion = true, showNegativeArrivals = true, hideAlerts = false, showZoomControls = false,
        compactStopIcons = false, showRentalButton = true, showOnDemandZones = showOnDemandZones,
        displayWeatherView = true, showAvailableStudies = true, leftHandMode = false, vibrateAllowed = true,
        tripPlanNotifications = true, analyticsEnabled = true, preferredUnits = null, preferredTempUnits = null, appTheme = null
    )

    private val env = SettingsEnvironment(useFixedRegion = false, sdkInt = 33, isObaFlavor = true, isGoogleMaps = true)

    @Test
    fun `the zones toggle flows from the snapshot to the ui state`() {
        assertTrue(buildSettingsUiState(snapshot(true), null, env, "custom").showOnDemandZones)
        assertFalse(buildSettingsUiState(snapshot(false), null, env, "custom").showOnDemandZones)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :onebusaway-android:testObaGoogleDebugUnitTest --tests "org.onebusaway.android.ui.settings.SettingsOnDemandZonesTest"`
Expected: compilation FAILS with `No parameter with name 'showOnDemandZones'`.

- [ ] **Step 3: Thread the preference through**

- `SettingsPrefSnapshot`: add `val showOnDemandZones: Boolean,` after `showRentalButton`.
- `SettingsUiState`: add `val showOnDemandZones: Boolean,` after `showRentalButton`.
- `buildSettingsUiState`: add `showOnDemandZones = prefs.showOnDemandZones,` after `showRentalButton = prefs.showRentalButton,`.
- `SettingsViewModel.readSnapshot()`: add `showOnDemandZones = prefs.getBoolean(R.string.preference_key_show_ondemand_zones, true),` after the `showRentalButton` line (106); add after line 180:

```kotlin
    fun onShowOnDemandZonesChanged(value: Boolean) = prefs.setBoolean(R.string.preference_key_show_ondemand_zones, value)
```

- `SettingsScreen.kt`: in the actions class (line 189–190) add `val onShowOnDemandZones: (Boolean) -> Unit,` after `onShowRentalButton`; at the construction (line 149) add `onShowOnDemandZones = viewModel::onShowOnDemandZonesChanged,`; after the rental `SwitchPreferenceItem` (line 313) add:

```kotlin
                SwitchPreferenceItem(
                    title = stringResource(R.string.preferences_show_ondemand_zones_title),
                    summary = stringResource(R.string.preferences_show_ondemand_zones_summary),
                    checked = state.showOnDemandZones,
                    onCheckedChange = actions.onShowOnDemandZones
                )
```

- Strings after line 687:

```xml
    <string name="preferences_show_ondemand_zones_title">Show on-demand zones</string>
    <string name="preferences_show_ondemand_zones_summary">Draw dial-a-ride service areas on the map where the region publishes them</string>
```

Grep for any other `SettingsPrefSnapshot(`/`SettingsUiState(` constructions (`grep -rn "SettingsPrefSnapshot(" onebusaway-android/src`) — each needs the new argument; previews and existing tests are the likely sites.

- [ ] **Step 4: Run the test and compile**

Run: `./gradlew :onebusaway-android:testObaGoogleDebugUnitTest --tests "org.onebusaway.android.ui.settings.*" && ./gradlew :onebusaway-android:compileObaGoogleDebugKotlin -PwarningsAsErrors=true`
Expected: PASS, BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
./gradlew spotlessApply
git add onebusaway-android/src/main/java/org/onebusaway/android/ui/settings onebusaway-android/src/main/res/values/strings.xml onebusaway-android/src/test/java/org/onebusaway/android/ui/settings/SettingsOnDemandZonesTest.kt
git commit -m "Add a settings toggle for on-demand zones"
```

---

### Task 11: Final verification and documentation

**Files:**
- Modify: `docs/SYSTEM_ARCHITECTURE.md` (new section after "Add issue reporting via Open311-compliant system", before "Configure your own servers")

**Interfaces:**
- Consumes: everything above.
- Produces: a green branch.

- [ ] **Step 1: Document the deployment option**

Insert into `docs/SYSTEM_ARCHITECTURE.md` after the Open311 section (before `## Configure your own servers`):

```markdown
## Add on-demand (GTFS-Flex) services *(Optional)*

A region running [maglev](https://github.com/OneBusAway/maglev) can ingest GTFS-Flex feeds and serve
demand-responsive services through the `/api/ondemand` namespace (see the maglev wiki page *GTFS-Flex
Support*). When it does, the app draws service zones on the home map (Settings › "Show on-demand
zones"), lists a stop's on-demand services on the arrivals screen, and opens a service page with hours
and a booking deadline computed on the device in the agency's timezone.

Nothing is configured per region: the app probes `services-for-location` once per launch, and a
deployment that answers HTTP 404 (every onebusaway-application-modules server today) is remembered as
not serving the namespace for the rest of that process. Booking itself happens out of band — by phone
or the agency's booking site — the app never places a booking.
```

- [ ] **Step 2: Format, compile both flavors, run everything**

```bash
./gradlew spotlessApply
./gradlew :onebusaway-android:compileObaGoogleDebugKotlin :onebusaway-android:compileObaMaplibreDebugKotlin -PwarningsAsErrors=true
./gradlew :onebusaway-android:testObaGoogleDebugUnitTest
git status --short   # must be clean apart from the docs edit
```

Expected: BUILD SUCCESSFUL for both compiles; all unit tests PASS.

- [ ] **Step 3: Commit**

```bash
git add docs/SYSTEM_ARCHITECTURE.md
git commit -m "Document on-demand service support"
git log --oneline 8046352b..HEAD
```

Expected: eleven commits in the task order above.

---

## Self-review notes

- **Spec coverage.** §8 API/data: Tasks 1–5. §8 UI (layer controller, both renderers, service screen, arrivals card, settings toggle): Tasks 6–10. §8 tests: decode (T1), support classification (T4), evaluator vectors (T5), controller (T6), arrivals view-model (T9), `StopsMapDecodeTest` regression (T1). `services-for-agency` is fetched by the data source (T4) but has no Android screen — spec §8 lists none; iOS's agencies action sheet is iOS-only (§7).
- **Resolved ambiguities** (flag for the reviewer): (1) a zero-rule service's areas come from *all* references only on the single-entry endpoint (a fact of the envelope, not a heuristic); list responses attach only rule-referenced areas. (2) A `priorNoticeStartTime` absent alongside a `priorNoticeStartDay` reads as `00:00:00`. (3) `countBack` stops at the notice calendar's `startDate`. (4) Booking URLs open via `ExternalIntents.goToUrl` (`ACTION_VIEW`); Custom Tabs would need a new `androidx.browser` dependency the catalog doesn't carry. (5) Charlevoix combined ids are `CC_CC_Ironton_Ferry_West` / `CC_CC3` because the feed's own ids already begin with `CC_`. (6) No map snippet on the Android service page; zones are on the home map. (7) The MapLibre classic polygon has no click listener, so its tap is hit-tested with `ZonePolygon.contains`; Google uses the native polygon click.
- **Type consistency.** `OnDemandResult<out T>` with `Loaded<T>` / `Unsupported` / `Failed` is used identically in Tasks 4, 6, 8, 9. `ZonePolygon(serviceId, serviceName, rings, color)` in Tasks 6, 7, 8. `onOpenOnDemandService: (serviceId: String) -> Unit` in Tasks 8 and 9. `preference_key_show_ondemand_zones` in Tasks 6 and 10.
