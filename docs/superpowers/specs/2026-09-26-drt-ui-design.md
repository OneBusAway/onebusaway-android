# DRT UI design (iOS and Android)

Binding design for demand-responsive transit (GTFS-Flex on-demand zones) and the redesigned map-layers sheet in OneBusAway iOS (OBAKit, both map shells) and Android (Compose, google and maplibre flavours). It follows rulings R1–R16 in `decisions.md`; where this document and a ruling appear to differ, the ruling wins and the difference is listed under "Open risks". Data comes only from the `/api/ondemand` contract in `api-contract.md`; nothing here requires a server change.

## 1. Purpose and scope

The feature answers three rider questions without booking anything: *Is there an on-demand service where I am (or where I am looking)?*, *Can I use it now, and if not, when?*, and *How do I book it?* Every flow ends in a phone call, a booking URL, or an agency web page. OBA does not take bookings.

In scope: region-level zone polygons and pins; a street-level docked status bar; the zone card above search; the overlap picker; the zone detail page; the address check on a dropped pin or search result; the "On-demand options" trip-planner fallback; the tile-grid map-layers sheet; the pure availability, sort and geometry functions that feed them.

Out of scope (R16): dark-mode-specific design (surfaces use system colours and follow the system appearance, but no dark-mode-only treatment is defined); the visual for an unavailable tile beyond the existing 0.5 dimming; custom icon assets; tapping a polygon at region level (the pin carries the tap); any server change; reverse-geocoded block counts (distances are shown as measurements). The one external change in scope is the minimal OTPKit notification required by R11 (3.8). Also out of scope, by R10: the "Zone service" kind badge. Also not built: an on-demand entry on the Android agencies screen.

## 2. Shared concepts

These sections are platform-neutral. Each is implemented once per platform as a pure function or a single controller; surfaces consume them and never re-derive them.

### 2.1 Probe point and cadence (R1, R2)

The **probe point** is the rider's location when location is authorized and a fix exists; otherwise the map centre. A **probe** is one point-mode call: `GET /api/ondemand/services-for-location.json?lat=&lon=&radius=5000&geometryDetail=none`. The response's `list[].matchReason` and `references.serviceAreas[].distanceToArea` / `nearestPointOnBoundary` are the only source of "inside", "near" and "how far". Client polygons are never used to decide containment for anything a rider reads.

The probe runs when:
1. the map settles (iOS `regionDidChangeAnimated`; Android `settledCamera()`) and the probe point has moved ≥ 100 m from the point of the last probe;
2. location authorization changes;
3. the app returns to the foreground;
4. the current dock state's `nextChangeInstant` passes (see 2.5);
5. (R2 clarification) a location update arrives while the probe source is the rider and the new fix is ≥ 100 m from the point of the last probe, or it is the first fix after there was none (the probe point switches from map centre to rider). A fix with horizontal accuracy worse than 100 m is ignored for this trigger and the last state is kept, so GPS drift does not flip the near-edge copy.

**Rider/centre probe cache.** Results for triggers 1–5 are cached per `(deployment, probe point rounded to 3 decimal places)` for 10 minutes; R2's 100 m movement threshold bounds the error of that rounding. Trigger 4 and any trigger that hits a live cache entry recompute availability from the cached response without a network call. The address check (3.7) and the planner probes (3.8) **never read this cache**: they answer questions about an exact point, so each keeps its own cache keyed on `(deployment, coordinate rounded to 5 decimal places)` with the same 10-minute lifetime. Recorded as a ruling amendment to R2.

A 404 marks the deployment unsupported through the existing `OnDemandSupport` tracker exactly as the layer's viewport fetch does; every surface in this document is then hidden for the process lifetime. Any other error keeps the last state on screen only when the new probe point is within 100 m of the probe point that produced that state; otherwise the dock becomes hidden. Either way the probe retries at the next trigger.

A probe result is the list of **matches**: `(service, matchReason, distanceToArea, nearestPointOnBoundary, availability)`. `distanceToArea` for a service is the minimum over the service's areas; `nearestPointOnBoundary` is the one belonging to that minimum. A service with no area (a pure stop group) has a null distance.

### 2.2 Zoom levels (R3)

Three levels, decided from the visible map height alone:

| Level | iOS (map points) | Android (metres of visible height, `visibleHeightMeters(latSpan)`) |
|---|---|---|
| Street | height ≤ 40,000 (`MapRegionManager.requiredHeightToShowStops`) | ≤ 4,000 (`ONDEMAND_STREET_LEVEL_MAX_HEIGHT_METERS`) |
| Region | 40,000 < height ≤ 600,000 (`OnDemandMapLayer` zoom window) | 4,000 < height ≤ 65,000 (`ONDEMAND_MAX_VISIBLE_HEIGHT_METERS`) |
| Hidden | > 600,000 | > 65,000 |

Per-zone switching is not attempted. The level is published as a value (`OnDemandZoomLevel` on both platforms) so the layer, the dock and tests all read one answer.

### 2.3 What each level draws (R4)

**Region**: every service area polygon filled at alpha 0.2 and stroked 2 pt in the service colour (route colour, else brand), holes cut out, overlaps blended by ordinary alpha compositing; one pin per service at its **label point**, a marker in the service colour with the `car.fill` glyph and the service name as a visible label. The pin is placed in the service's largest polygon (shoelace area in the local projection of 2.7). Tapping a pin opens the zone detail page.

**Street**: the same polygons with fill alpha 0 and a 4 pt stroke; under it, where the platform allows a second overlay, a 10 pt halo stroke at alpha 0.25 of the same colour. Pins are hidden. The boundary line therefore appears exactly when an edge is in view; the docked bar carries the status.

**Label point**: take the bbox mid-latitude; intersect that horizontal line with every ring of the polygon (exterior and holes); sort crossings by longitude; consecutive pairs (even-odd) are runs inside the polygon; return the midpoint of the widest run. If there are no crossings, return the exterior ring's centroid; if that is outside the ring or the ring is degenerate, return the bbox centre. Client point-in-ring is allowed here only.

**Highlight**: one service id may be highlighted (picker row tapped, bar page swiped). Highlighted polygons draw at stroke 4 pt (region) or 4 pt plus halo (street) at full alpha; while a highlight exists, other services' street strokes drop to alpha 0.6. The highlight clears when the detail page or picker closes, and whenever the dock leaves the bar state (hidden, card, level change).

**Colour collisions**: when two services in the same match list or viewport resolve to the same colour (no route colour, or identical route colours), the second and later ones take colours in order from a fixed fallback palette: `#3b82f6`, `#d97706`, `#7c3aed`, `#db2777`, `#0891b2`, `#65a30d`; assignment is by service id order so it is stable across refreshes. The bar, card icon, pin and polygon of a service always share the one resolved colour.

### 2.4 The dock slot (R5)

One slot per screen, at the top edge of the bottom sheet, 16 pt side gutters in compact width. It shows exactly one of:

- **Zone card** (3.3) at region level when the probe point is inside ≥ 1 service (`matchReason == areaContainsPoint`).
- **Docked bar** (3.4) at street level when at least one match is inside, or outside with a finite `distanceToArea` ≤ 5,000 m (`areaNearby` or `stopWithinRadius` with a finite distance).
- **Planner fallback card** (3.8) while the trip planner is showing an empty result; this is the one content that appears while a directions sheet is up (R11 over R5).
- **Nothing** otherwise; nothing at the hidden level; nothing while a stop, route, trip or directions sheet has focus (Android: `CurrentFocus.Stop`, `.Route`, `.BikeStation`, `.Directions`; iOS classic: any semi-modal panel or search results; iOS panel: top route ≠ `.home`); nothing while the on-demand layer is off; nothing while the survey launcher card is shown (the survey card wins the anchor); nothing when the visible map area is under 50 % of the screen because of the sheet.

The layer toggle affects only map drawing and the dock; the address line (3.7) and the planner fallback (3.8) remain available with the layer off, because they answer a question the rider asked. Matches with a null distance (stop groups without areas) are never shown in the dock or the card. Transitions between the contents animate as a cross-fade of 200 ms; the slot never shows two contents at once.

**Regular width and landscape.** iOS classic regular width (the 291 pt left-side panel): the dock takes the panel's width and x-origin and sits 12 pt above the panel's top edge; when the panel is full height it floats bottom-leading at 360 pt maximum width. iOS panel shell in regular width: same 360 pt maximum, bottom-leading. Android landscape: the dock is bottom-start with 360 dp maximum width and the same padding rules. The bottom control stack (iOS panel `mapControlsCluster` / `myTripButton`; Android FABs through `fabBottomInsetTarget`) is offset upward by the dock's measured height so nothing overlaps.

### 2.5 Availability model (R7)

`OnDemandAvailability` is computed from one service, its rules, calendars and booking rules, the agency timezone (`references.agencies[].timezone` through the service's agency) and `now` from the device clock. `currentTime` from the envelope is never used. When the agency timezone is missing, every field is null/false, `status` is `.unknown` and the tier is 5.

Definitions, all in the agency timezone `tz`:
- `today` = the service date returned by `BookingDeadlineEvaluator.serviceDate(for: now)` (iOS) / the agency-local `LocalDate` of `now` (Android).
- `instant(D, t)` = the evaluator's `anchor(D) + t`; `t` may exceed 24:00:00.
- A calendar is **active on D** when `D` is within `[startDate, endDate]`, `D`'s weekday is in `days`, and `D` is not in `exceptedDates`. `_added_` calendars need no special case: they are ordinary calendars with `startDate == endDate`.
- A rule is **active on D** when any of its `calendarIds` is active on D.
- A rule's **pickup window on D** is `[instant(D, startPickupTime ?? 00:00:00), instant(D, endPickupTime ?? 24:00:00))`. Both null means the whole service day; the previous day's window `[D−1 00:00, D 00:00)` abuts it so an all-day service is continuous across midnight.

Fields:
- `runningNow: Bool` — true when some rule active on `today` or on `today − 1` has a pickup window containing `now`. The previous day is checked because windows pass 24:00.
- `runningUntil: Date?` — when running, the latest window end among the windows that contain `now`; null when not running, and also null when that end abuts the start of a window on the next active service day (a continuous, all-hours service), so that the copy reads "Open" rather than "until 12:00 AM".
- `nextRunStart: Date?` — when not running, the earliest window start after `now` over rules active on any day from `today − 1` through `today + 400`; null when none exists.
- `bookingTier: realTime | sameDay | advance` — from the pickup booking rules (`pickupBookingRuleId`; a rule with a null id or an unresolvable id counts as `realTime` when the id is null and is skipped when the id is dangling). Consider only rules active on the next date on which any rule is active (today if today). Mixed types resolve to the least demanding: `realTime` < `sameDay` < `advance`. `bookingType` 0 → realTime, 1 → sameDay, 2 → advance.
- `bookingLine` — the existing summary line (`.bookBy` / `.opensAt` / `.noNoticeRequired` / `.closed` / `.unknown`), unchanged.
- `bookableNow: Bool` — some rule whose window contains `now` evaluates to state `open` for the service date whose window contains `now` (`today − 1` at 00:30 inside a window that runs to 25:00, not `today`).
- `nextBookableServiceDate: ServiceDate?` at service level — the minimum over the rules of the evaluator's per-rule `nextBookableServiceDate`; null when every rule returns null.
- `status`, chosen in this order:
  1. `bookingTier == advance` → `.bookBy(deadline, travelDate)` when `bookingLine` is `.bookBy`; `.bookingOpens(openInstant)` when it is `.opensAt` (the booking window, not the service, opens then); `.closed` when `.closed`; else `.unknown`.
  2. `runningNow && bookableNow` → `.openNow(until: runningUntil)`.
  3. `nextRunStart != nil` → `.opensAt(nextRunStart)`.
  4. no rule active on any day through `today + 400` → `.closed`.
  5. otherwise (for example `rules` empty) → `.unknown`.
- `tags: [Tag]` — exactly one booking tag: `noNoticeNeeded` (realTime), `sameDayBooking` (sameDay), `advanceBooking` (advance); plus `eligibilityRequired` when `eligibility.requirement == certificationRequired`. Never anything for absent eligibility.
- `nextChangeInstant: Date?` — the minimum of `runningUntil`, `nextRunStart` and the summary's `nextChangeInstant`; the dock re-evaluates at that instant plus one second.
- `usabilityTier: 1…5` (2.6).

### 2.6 Sort order (R6)

`usabilityTier` is an ordered decision; the first matching line wins:
1. tier **4 (Eligibility required)** when the `eligibilityRequired` tag is present — never produced in v1 because the field is absent;
2. else tier **1 (Open now)** when `status` is `.openNow`;
3. else tier **5 (Unknown or closed)** when `status` is `.closed` or `.unknown`, or the service-level `nextBookableServiceDate` (2.5) is null;
4. else tier **2 (Same-day)** when `nextBookableServiceDate == today`;
5. else tier **3 (Advance)**.

`sortSoonestUsable(matches)` orders by tier ascending, then `distanceToArea` ascending (0 inside; null last), then `name` with the platform's locale-aware natural comparison. The picker, the bar stack, the card (which shows the first match) and the planner fallback all call this one function.

### 2.7 Nearest-edge geometry (R9)

Used only for the inside-near-edge title and the outside "pan to edge" action, and only for services in the current match list. Geometry comes from `GET /api/ondemand/service/{id}.json?geometryDetail=full`, cached per service id for the process lifetime and fetched lazily for matched services. Simplified geometry is never used for distance.

Projection: local equirectangular with origin at the probe point `(lat0, lon0)`: `x = (lon − lon0) · cos(lat0) · R`, `y = (lat − lat0) · R`, `R = 6,371,000 m`. For every segment of every ring (exterior and holes) of every area referenced by any of the service's rules, compute the point-to-segment distance with the projection parameter clamped to `[0, 1]`; take the minimum. Convert the nearest point back with the inverse transform. Bearing from the probe point to that point = `atan2(dx, dy)` in degrees from north, normalised to `[0, 360)`; the compass bucket is `floor((bearing + 22.5) / 45) mod 8` → north, northeast, east, southeast, south, southwest, west, northwest.

Two directions are derived from that bearing and they are not interchangeable:
- `edgeDirection` = the bucket of the bearing itself (probe → boundary). It is the direction the rider must walk to reach the edge and feeds the inside near-edge title ("edge 300 ft north").
- `riderDirection` = the bucket of `(bearing + 180) mod 360` (boundary → probe). It is where the rider stands relative to the zone and feeds the outside title ("0.3 mi south of the zone"). When the server supplies `nearestPointOnBoundary`, the bearing is computed from the probe point to that point and reversed the same way.

Inside: the server reports 0 and null, so the client value is the only one. Outside: prefer the server's `distanceToArea` and `nearestPointOnBoundary`; use the client value only when either is null. **Near edge** means inside with client distance < 100 m.

Full geometry is used for exactly three things: the inside near-edge title, the outside pan-to-edge fallback, and the dock thumbnail (3.4). It is cached per `(deployment, serviceId)`; the deployment is the same key `OnDemandSupport` uses. On a deployment change (region switch or custom URL change) the controller cancels in-flight probes and geometry fetches, discards any result whose deployment is not the current one, sets the dock to hidden and clears the highlight.

### 2.8 Copy catalogue (R8)

All strings are localized. iOS keys are in `Strings+OnDemand.swift` under `on_demand.*` (layers sheet under `map_layers.*`); Android keys are in `strings.xml` as `ondemand_*` (`map_layers_*`). Parameters are listed in order. Distances use the locale's unit system: imperial shows whole feet rounded to 10 below 0.1 mi and miles with one decimal at or above; metric shows metres rounded to 10 below 1,000 m and kilometres with one decimal at or above (iOS `MeasurementFormatter`, Android `MeasureFormat`). Times are the short time style in the agency timezone; relative day words ("today", "tomorrow", weekday) come from the existing `SummaryFormatters` / `deadlineLine` helpers.

| iOS key | Android key | English | Parameters |
|---|---|---|---|
| `on_demand.card.eyebrow` | `ondemand_card_eyebrow` | On-demand service here | — |
| `on_demand.status.open_until` | `ondemand_status_open_until` | Open · until %@ | end time |
| `on_demand.status.open_now_until` | `ondemand_status_open_now_until` | Open now · until %@ | end time |
| `on_demand.status.open` | `ondemand_status_open` | Open | — (running with null `runningUntil`, a continuous service) |
| `on_demand.status.opens` | `ondemand_status_opens` | Opens %@ | relative day + time, e.g. "tomorrow at 7:20 AM" (service starts running) |
| existing `onDemandBookingOpensFormat` | existing `ondemand_booking_opens` | (existing text) | booking window opens; used for `.bookingOpens` |
| `on_demand.status.book_by` | `ondemand_status_book_by` | Book by %@ | relative deadline, e.g. "tomorrow 3:10 PM" |
| existing `onDemandBookByFormat` | existing `ondemand_book_by` | Book by %1$@ for a ride on %2$@ | deadline, travel date |
| `on_demand.status.closed` | `ondemand_status_closed` | Closed | — |
| `on_demand.tag.same_day` | `ondemand_tag_same_day` | Same-day booking | — |
| `on_demand.tag.advance` | `ondemand_tag_advance` | Advance booking | — |
| `on_demand.tag.no_notice` | `ondemand_tag_no_notice` | No notice needed | — |
| `on_demand.tag.eligibility` | `ondemand_tag_eligibility` | Eligibility required | — |
| `on_demand.card.call_to_book` | `ondemand_card_call_to_book` | Call to Book | — |
| existing `onDemandBookOnline` | existing `ondemand_book_online` | Book online | — |
| `on_demand.card.details` | `ondemand_card_details` | Details | — |
| `on_demand.card.more_services` (stringsdict) | `ondemand_card_more_services` (plurals) | %d more service here / %d more services here | count |
| `on_demand.bar.inside` | `ondemand_bar_inside` | Pickups available here | — |
| `on_demand.bar.inside_near_edge.north` … `.northwest` (8 keys, one full sentence per compass point) | `ondemand_bar_inside_near_edge_north` … (8 keys) | Inside · edge %@ north / … northeast / … east / … southeast / … south / … southwest / … west / … northwest | distance |
| `on_demand.bar.outside.north` … `.northwest` (8 keys) | `ondemand_bar_outside_north` … (8 keys) | %@ north of the zone / … northeast … / … east … / … southeast … / … south … / … southwest … / … west … / … northwest of the zone | distance |
| `on_demand.bar.badge` | `ondemand_bar_badge` | %1$d of %2$d | index, count (also the accessibility value of the paged bar) |
| `on_demand.bar.a11y.show_edge` | `ondemand_bar_a11y_show_edge` | Show nearest zone edge | — (outside chevron label) |
| `on_demand.bar.a11y.zoom_out` | `ondemand_bar_a11y_zoom_out` | Show whole zone | — (thumbnail label) |
| `on_demand.bar.a11y.call` | `ondemand_bar_a11y_call` | Call %@ | service name (phone button label) |
| `on_demand.bar.a11y.more_services` | `ondemand_bar_a11y_more_services` | All services here | — (custom accessibility action that opens the picker) |
| `on_demand.picker.title` (stringsdict) | `ondemand_picker_title` (plurals) | %d service here / %d services here | count (inside-only list) |
| `on_demand.picker.title_nearby` (stringsdict) | `ondemand_picker_title_nearby` (plurals) | %d service nearby / %d services nearby | count (bar long-press list) |
| `on_demand.picker.subtitle_location` | `ondemand_picker_subtitle_location` | %@ · Your location | locality |
| `on_demand.picker.subtitle_center` | `ondemand_picker_subtitle_center` | %@ · Map center | locality |
| `on_demand.picker.subtitle_point` (R8 addendum) | `ondemand_picker_subtitle_point` | %@ · Selected place | locality |
| `on_demand.picker.your_location` | `ondemand_picker_your_location` | Your location | — |
| `on_demand.picker.map_center` | `ondemand_picker_map_center` | Map center | — |
| `on_demand.picker.selected_place` (R8 addendum) | `ondemand_picker_selected_place` | Selected place | — |
| `on_demand.picker.footer` | `ondemand_picker_footer` | Sorted by soonest available. | — |
| `on_demand.detail.location_inside` | `ondemand_detail_location_inside` | Your location is in this zone | — |
| `on_demand.detail.location_outside` | `ondemand_detail_location_outside` | Your location is outside this zone | — |
| `on_demand.detail.center_inside` | `ondemand_detail_center_inside` | Map center is in this zone | — |
| `on_demand.detail.center_outside` | `ondemand_detail_center_outside` | Map center is outside this zone | — |
| `on_demand.detail.point_inside` (R8 addendum) | `ondemand_detail_point_inside` | This place is in this zone | — |
| `on_demand.detail.point_outside` (R8 addendum) | `ondemand_detail_point_outside` | This place is outside this zone | — |
| `on_demand.detail.location_sub` | `ondemand_detail_location_sub` | Pickups available in %@ | locality (inside only) |
| `on_demand.detail.includes_location` | `ondemand_detail_includes_location` | %@ — includes your location | area names (rider source, inside only) |
| `on_demand.detail.where_header` | `ondemand_where_title` | Where | — |
| `on_demand.detail.service_area` | `ondemand_detail_service_area` | Service area | — |
| `on_demand.detail.drop_off` | `ondemand_detail_drop_off` | Drop-off | — |
| `on_demand.detail.zone_count` (stringsdict) | `ondemand_detail_zone_count` (plurals) | %d zone / %d zones | count |
| `on_demand.detail.no_service` | `ondemand_detail_no_service` | No service | — |
| `on_demand.detail.open_agency_website` | `ondemand_open_agency_website` | Open Agency Website | — |
| existing `onDemandMoreInfo` | existing `ondemand_more_info` | More information | — |
| `on_demand.planner.section` | `ondemand_planner_section` | On-demand options | — |
| `on_demand.planner.serves_both` | `ondemand_planner_serves_both` | Serves both locations | — |
| `on_demand.planner.caption` | `ondemand_planner_caption` | Services that need eligibility or cover only one end are hidden. | — |
| `on_demand.planner.show_all` | `ondemand_planner_show_all` | Show all in the zone picker | — |
| `on_demand.planner.empty` | `ondemand_planner_empty` | No on-demand service covers both locations. | — |
| `on_demand.planner.status_now` | `ondemand_planner_status_now` | Status shown for now. | — |
| `on_demand.address.inside` | `ondemand_address_inside` | Inside %@ | service name |
| `on_demand.address.inside_more` (stringsdict) | `ondemand_address_inside_more` (plurals) | Inside %1$@ and %2$d more | service name, extra count |
| `on_demand.address.outside` | `ondemand_address_outside` | Outside %@ | service name |
| existing `onDemandCallFormat` | existing `ondemand_call` | Call %@ | phone number |
| — | `preferences_show_layers_button_title` | Show map layers button | — |
| — | `layers_button_hidden_toast` (replaces `layers_rentals_hidden_toast`) | Map layers button hidden. Turn it back on in Settings. | — |
| `map_layers.group.transit` | `map_layers_group_transit` | Transit | — |
| `map_layers.group.rentals` | `map_layers_group_rentals` | Rentals | — |
| `map_layers.state.on` / `.off` | `map_layers_state_on` / `_off` | On / Off | — |
| `map_layers.min_range` | — | Min. range | — |
| — | `map_layers_title` / `_reset` / `_done` | Map / Reset / Done | — (iOS reuses existing) |
| — | `map_layers_on_demand_zones` | On-demand zones | — (iOS reuses `onDemandZonesLayer`) |
| — | `map_layers_bikes` / `_scooters` | Bikes / Scooters | — |
| — | `map_layers_basemap_standard` / `_satellite` / `_hybrid` | Standard / Satellite / Hybrid | — |

Composition rules:
- **Card meta and picker first line**: `status` rendered as `open_until` (or `open`), `opens`, `onDemandBookingOpensFormat` (for `.bookingOpens`), `book_by` or `closed`; `.unknown` renders nothing. The card meta joins the status and the booking tag with " · ", omitting empty segments. "Open" is rendered in the open-green weight described in 5.
- **Picker second line**: the area names joined with ", " (unnamed areas omitted; when none is named, `zone_count`), then " · ", then the booking tag.
- **Bar title precedence**: outside → `bar.outside.<riderDirection>`; inside and near edge → `bar.inside_near_edge.<edgeDirection>`; tier 4 → `tag.eligibility`; tier 3 → `status.book_by` (or the `.bookingOpens`/`closed` rendering when the status is one of those); tier 2 → `status.opens`; tier 5 with `.closed` → `status.closed`; tier 1 → `bar.inside`. For `.unknown` no title is rendered: the service name moves to the title position and the eyebrow is empty. Eyebrow is otherwise always the service name.
- **Detail status line** uses `open_now_until`; the other statuses use the same keys as the card.
- **Locality** is the reverse-geocoded locality of the probe point when it arrives within 1 s of the request (iOS `CLGeocoder`; Android `Geocoder`); otherwise the subtitle falls back to `your_location` / `map_center` and the location row has no sub line.

### 2.9 Eligibility handling

The model gains an optional `eligibility { requirement: open | certificationRequired | unknown, infoUrl: String? }` decoded only when present. Absent or `unknown`: nothing is shown anywhere and the tier is unaffected. `open`: nothing is shown. `certificationRequired`: the `eligibilityRequired` tag appears in every tags row and the bar title; the tier becomes 4; the service is excluded from the planner fallback; when the booking rule has no `infoUrl`, `eligibility.infoUrl` feeds the "More information" row. Booking-rule `message`, `pickupMessage` and `dropOffMessage` are shown verbatim as the detail footnote and are never pattern-matched.

### 2.10 Backwards compatibility (R15)

Every surface is gated on the existing `OnDemandSupport` tracker. A server that returns 404 for `services-for-location` shows nothing new: no polygons, no dock, no address line, no planner section, and no "On-demand zones" tile. A server with the namespace but no flex data returns empty lists and shows nothing new either. No new feature flags. Both iOS shells (classic and `OBAUseMapPanelExperience`) get the dock (including the bar long-press and its accessibility action), the address line and the redesigned sheet; the classic shell alone gets the planner fallback and the map long-press (dropped pin). Android google and maplibre both get everything; only google gets the basemap control. Existing UserDefaults and SharedPreferences keys keep their names and defaults.

## 3. Surfaces

### 3.1 Region-level map (pins and polygons)

**Layout.** As 2.3, region branch. Pins: iOS classic `MKMarkerAnnotationView` with `glyphImage = car.fill`, `markerTintColor = service colour`, `titleVisibility = .visible`, `subtitleVisibility = .hidden`, `displayPriority = .defaultLow`; iOS panel `Marker(service.name, systemImage: "car.fill").tint(colour)`; Android a marker bitmap from `ComposeBitmapRenderer` drawing a 38 dp circle in the service colour with a 3 dp white border, the `directions_car` glyph, and the name below in 12 sp semibold with a white halo.

**Data.** The existing viewport fetch (`geometryDetail=simplified`) unchanged. The pin coordinate is the label point of the service's largest polygon, replacing today's bbox centre.

**States.** Layer unsupported: nothing. Layer unavailable with content already drawn: content stays. Layer off: nothing.

**Interactions.** Tap pin → zone detail (existing `detailViewController` / `onOnDemandZoneClick` routes). Polygon tap: iOS none (R16); Android keeps the existing polygon click **at region level only** — Google sets `clickable(style == REGION)`, MapLibre skips `zoneAt` unless the tapped zone's style is `REGION` — so that at street level, where the polygon covers the screen, background taps keep dismissing pins and focus.

**Tested.** Label point lies inside its ring for a convex, a concave (U-shaped) and a holed polygon; fallback order on a degenerate ring; one pin per service for a two-polygon service; pin tint equals route colour or brand when absent; Android: a polygon tap at street level does not open the detail.

### 3.2 Street-level map (stroke only)

**Layout.** As 2.3, street branch: fill alpha 0, stroke 4 pt, halo 10 pt at 0.25 alpha (iOS: a second `MKPolygon` overlay tagged as the halo, or a second `MapPolygon` beneath in the panel; Android google: a second `Polygon` with `strokeWidth` 10 px·density and the halo colour, `zIndex` −2; Android maplibre: legacy `PolygonOptions` has no stroke width, so the renderer draws a fill-only polygon plus one `PolylineOptions` per ring with `width` 2 dp at region level and 4 dp at street level, and no halo). No pins.

**Data.** Same polygons as region level; the level value decides the style. No extra fetch.

**States.** Highlight as 2.3.

**Interactions.** None on the line.

**Tested.** Renderer/style for a polygon at each level (fill alpha 0.2 vs 0; stroke 2 vs 4 on iOS and Android google; polyline width 2 vs 4 on maplibre); annotations empty at street level; highlight raises alpha of one service only.

### 3.3 Zone card (screen 1)

**Layout.** White card, radius 22, padding 16. Row: 40 pt circle in the service colour with `car.fill` in white; eyebrow `card.eyebrow` (13 pt semibold, brand accent); title = service name (17 pt semibold, up to 2 lines); meta line (14 pt secondary, "Open" in open-green semibold); trailing `chevron.right`. Button row: primary pill (40 pt, brand accent background, white text, `phone.fill`) reading `card.call_to_book` when the first rule's pickup booking rule has a `phoneNumber`, else `onDemandBookOnline` with `arrow.up.right.square` when a `bookingUrl` exists, else no primary; secondary pill `card.details` (light tint `#eef4e6`, accent text). When only the secondary exists it fills the row. Footer row above a hairline: `card.more_services` with `chevron.right`, shown only when more than one match is inside.

**Data.** The first match of `sortSoonestUsable(matches where matchReason == areaContainsPoint)`.

**States.** Shown only per 2.4. The primary button opens `tel:` or the URL through `application.open` / `ExternalIntents`.

**Interactions.** Tap card body or Details → zone detail for that service, with the probe's location facts passed in. Tap footer → overlap picker.

**Tested.** Card model for one inside match; footer count for three; primary button choice for phone, URL-only, and neither; hidden when the best match is `areaNearby`.

### 3.4 Docked bar (A–E)

**Layout.** Bar radius 20, padding 8, height 72 pt, text in the bar text colour (5). Leading 56 pt thumbnail (radius 12, 2 pt white border at 85 %). Middle: eyebrow (12 pt semibold, 85 % opacity) = service name, title (17 pt bold; 1 line truncating tail, 2 lines at accessibility Dynamic Type / font-scale sizes). Trailing circular button, 44 pt on iOS and 48 dp on Android, white at 20 % background. Background = service colour when inside, `#636366` when outside. Shadow: 0 4 pt 12 pt black at 18 %.

**Thumbnail.** A Canvas drawing, not a map: the full-geometry rings (2.7) of every service in the stack, filled at 0.35 and stroked 1 pt in each service's colour, projected with the 2.7 transform, scaled so that the union of the stack's bboxes plus the probe point fits in the square with a 4 pt inset, centred on the probe point; the probe point drawn as a 6 pt blue dot with a 1.5 pt white ring. Until the full geometry of every service in the stack has loaded, and whenever a geometry fetch fails, the thumbnail is a placeholder: a disc in the page's service colour with a white `car.fill`. The drawing updates whenever the match list or the probe point changes. Outside, the same drawing (the zone is off-centre). A "%1$d of %2$d" badge (11 pt bold, `#3a3a3c` at 90 %, 1 pt white border, radius 9) sits at the thumbnail's bottom-left only when the stack has more than one page.

**Stack.** When any match is inside, the stack holds only the inside matches; only when none is inside does it hold the outside matches with a finite distance ≤ 5,000 m. Each set is ordered by `sortSoonestUsable` (2.6). Pages scroll horizontally with paging; 7 pt page dots below the bar (`#c7c7cc`, current `#3a3a3c`) when more than one page. Each page has its own colour and trailing button. Swiping to a page highlights that service on the map (2.3).

**States per page.**
- Inside, ≥ 100 m from the edge: title per the 2.8 precedence; trailing `phone.fill` when a phone number exists, else `arrow.up.right.square` when a booking URL exists, else `chevron.right`.
- Inside, near edge: title `bar.inside_near_edge.<edgeDirection>` with the client distance; same trailing rule.
- Outside: gray; title `bar.outside.<riderDirection>` with the server distance (client fallback); trailing `chevron.right`. When the server point is null and the client geometry is unavailable or failed, the chevron is hidden.
- Geometry not yet cached (near-edge unknown) or geometry fetch failed: the plain precedence title is shown; when geometry later arrives it is updated in place.

**Interactions.** Tap thumbnail → zoom out to region level: fit the union bbox of the stack's services with 20 % padding, never smaller than twice the street gate (80,000 map points; Android latSpan for 8,000 m) and never larger than 0.9 × the outer window (540,000 map points; 58.5 km), centred on the probe point when clamped. Tap elsewhere on the page → zone detail for that service. Tap trailing: phone → dial; URL → open; chevron (outside) → pan so that the nearest boundary point is centred, keeping the current zoom; chevron (inside, no contact) → zone detail. Long-press anywhere on the bar (0.5 s) → overlap picker with the full match list (`picker.title_nearby`). Swipe → next page.

**Accessibility.** The thumbnail is a button labelled `bar.a11y.zoom_out`; the outside chevron `bar.a11y.show_edge`; the phone button `bar.a11y.call`; the URL button `onDemandBookOnline`; the inside chevron `card.details`. The bar is one adjustable element (iOS `.adjustable` trait; Android `semantics { stateDescription; customActions }`) whose value is `bar.badge` and whose increment/decrement move pages; it exposes a custom action `bar.a11y.more_services` that opens the picker, so long-press has an accessible equivalent.

**Tested.** Title selection for each state and tier, including tier 2 → `status.opens` and tier 5 → `status.closed`; a point due south of a square yields the `outside.south` key, a point just inside the north edge yields `inside_near_edge.north`; badge only when > 1 page; inside-only stack when any inside match exists; page order equals the sort function; thumbnail scale keeps every ring vertex and the probe point inside the square; placeholder before geometry; outside chevron target equals `nearestPointOnBoundary`; accessibility labels equal the keys above.

### 3.5 Overlap picker (screen 2)

**Layout.** Sheet at `[.medium, .large]` (iOS) / `ModalBottomSheet` (Android). Header: `picker.title` for an inside-only list or `picker.title_nearby` for the full list (22 pt bold), subtitle (14 pt secondary) `picker.subtitle_location`, `picker.subtitle_center` or `picker.subtitle_point` by probe source (fallback keys without locality), circular close. Grouped list, radius 18, one row per service: 40 pt icon circle in the service colour with `car.fill`, name (17 pt semibold), first line = status, second line = areas · tag (both 14 pt secondary, "Open" in open-green), `chevron.right`. Footer `picker.footer` (13 pt secondary).

**Data.** From the card footer and the planner "Show all" link: the inside-only matches (`areaContainsPoint`) at that probe point, so the count agrees with the card footer. From the bar long-press: the full current match list (inside and nearby). Both sorted by 2.6.

**Interactions.** Tap row → highlight that service (2.3) and push the zone detail. Close → clears the highlight.

**Tested.** Row order equals the sort; subtitle variants by probe source and locality presence; row second line composition.

### 3.6 Zone detail page (screens 3–4)

Extends the existing `OnDemandServiceView` / `OnDemandServiceScreen`. Sections in order; a section is omitted when its data is empty:

1. **Header card**: name (26 pt bold, wraps), close button; tags row (13 pt semibold; booking tag on `#e9f1dd` with accent text; `eligibility` on warn `#fbefd5` with `#8a5a00`); `description` paragraph (15 pt secondary) when present; status line (15 pt, `open_now_until` / `open`, `opens`, or `closed`) **when the tier is 1, 2 or 5 with `.closed`** (nothing for `.unknown`); primary pill (46 pt) "Call %@" (existing `onDemandCallFormat` / Android `ondemand_call`, `phone.fill`) when a phone exists, else `onDemandBookOnline`, else none.
2. **Promoted deadline row** — **only when the tier is 3**: `clock` icon and the full `onDemandBookByFormat` line (or `onDemandBookingOpensFormat` / `closed`). This is the promoted fact for advance services; for tiers 1–2 the promoted fact is the status line in the header card.
3. **Location row**: `checkmark.circle.fill` (green) with `detail.location_inside` / `detail.center_inside` / `detail.point_inside`, or `xmark.circle.fill` (`#636366`) with `detail.location_outside` / `detail.center_outside` / `detail.point_outside`, by `ProbeSource` (rider, map centre, point); sub `detail.location_sub` only when inside and a locality is known. Shown only when the page was opened with a probe result for this service (from the card, bar, picker, pin at region level, address check, or planner). From the stop page or agency list the row is omitted.
4. **Thumbnail** (height 210, radius 18): iOS the existing `Map` restyled with the service colour (fill 0.2, stroke 2) plus an `Annotation` at the `OnDemandLocationCheck` point — a blue dot with white ring for the rider source, a gray dot for the map centre, a `mappin.and.ellipse` glyph for a point — replacing `UserAnnotation()` so the dot always agrees with the location row; Android `ZoneThumbnail` (R14) drawing the same point with the same source styling when it lies inside the bbox.
5. **Where** — shown when the service has more than one area, or any rule has `toIds ≠ fromIds`: row `detail.service_area` (`checkmark.circle.fill` green when inside, `mappin.and.ellipse` otherwise) with the sub = names of all areas in `fromIds` (fallback `zone_count`), formatted through `detail.includes_location` when the probe source is the rider and inside; row `detail.drop_off` (`mappin.and.ellipse`) with the sub = names of `toIds` members resolved against `serviceAreas`, `locationGroups`, then `stops`, only when `toIds` differs from `fromIds` in some rule.
6. **When** (existing header): rows built from the rules' weekday sets, not from formatted strings — iOS `ServiceWindow` gains `weekdays: [Weekday]`, Android `WhenRow.days: Set<DayOfWeek>` already exists — merged by identical hours into weekday ranges, then formatted with the existing `daysText` / `formatDays`; then a muted `detail.no_service` row for the weekdays absent from every calendar whose `endDate` is today or later (or open-ended), merged into one range string (e.g. "Sun", "Sat–Sun").
7. **How to book** (existing header): `clock` row with the booking line (existing text); `arrow.up.right.square` `detail.open_agency_website` when `url` exists; `info.circle` `onDemandMoreInfo` when an `infoUrl` (booking rule, else eligibility) exists **and differs from `url`** (the existing `infoURL ?? service.url` fallback is not used for this row, so the two rows never open the same page).
8. **Footnote**: `message`, `pickupMessage`, `dropOffMessage` verbatim, each on its own line.

The page refreshes its availability at `nextChangeInstant + 1 s`, on foreground and on appear, as the summary already does.

**Tested.** Section presence per tier (status line for 1, 2 and closed 5; promoted row for 3); "No service" rows for a Mon–Sat calendar (Sun) and a Mon–Fri one (Sat–Sun); When merge of "Mon–Fri" and "Sat" with equal hours into "Mon–Sat"; Where only for zone-to-zone or multi-area; location row copy for the six cases, sub only when inside, and omission without a probe; "More information" hidden when it equals `url`.

### 3.7 Address check (R12)

A dropped pin (iOS classic long-press; Android long-press "navigate here" pin) or a search result / POI (iOS both shells; Android search results get no address line, per R12, which names only `NavigateHereBubble`) gets one extra line in its card: `address.inside` or `address.outside` with the name of the nearest service within 5,000 m, from a point probe at the exact pin coordinate using the address-check cache of 2.1 (never the rider/centre cache); nothing when no service is within 5,000 m, when the deployment is unsupported, while the probe is in flight, or when the probe fails. When several services contain the pin (distance 0), the named one is the first by `sortSoonestUsable` and the line uses `address.inside_more` with the count of the others. iOS: the line sits under the header inside `MapItemView`, `xmark.circle.fill` red-tinted for outside and `checkmark.circle.fill` green for inside; Android: a third line inside `NavigateHereBubble`'s offer with the same icons (`cancel` / `check_circle`). Tapping the line opens the zone detail with `ProbeSource.point` at the pin. On Android, long-press is gated by the existing `refuseTripPlanIfUnavailable()`, so in a region without trip planning no pin drops and no address check exists; this is accepted and the existing code is left untouched.

**Tested.** Line text for inside, outside, none and error; `inside_more` for a pin in two zones; a pin dropped 20 m across the edge from the rider's last probe reads "Outside" (cache isolation); line hidden when unsupported.

### 3.8 Trip planner fallback (R11)

**Eligibility of a service.** Run the point probe at the origin and at the destination through the planner's own exact-coordinate cache (2.1). A service qualifies when it appears in both results with `matchReason == areaContainsPoint`, it is not tier 4, and some rule has `fromIds` intersecting the ids of origin-containing areas (`distanceToArea == 0` at the origin) and `toIds` intersecting the ids of destination-containing areas. Order by 2.6. Availability is evaluated at `now`, not at the planned departure time; the section therefore carries the caption `planner.status_now`.

**Card.** Section header `planner.section`; one card per qualifying service in the screen-1 style with meta line 1 `planner.serves_both`, meta line 2 the status · tag composition, and a single primary "Call to Book" / "Book online" pill; when the service has neither a phone nor a booking URL there is no pill and the card body opens the detail page. Caption `planner.status_now`, then `planner.caption` followed by the `planner.show_all` link, which opens the picker with the inside-only list at the origin (`ProbeSource.point`). When nothing qualifies but the caption's hidden set is non-empty, only the captions and link show; when both are empty, `planner.empty` shows.

**iOS (classic shell only; the panel shell has no trip planner).** OTPKit posts `Notifications.itinerariesUpdated` only on a successful plan; an OTP error such as PATH_NOT_FOUND goes to `showError` with no notification, so OBAKit cannot detect "no trips found" from the package as published. R11 therefore mandates a minimal OTPKit change on a branch of `github.com/OneBusAway/otpkit`: `TripPlannerViewModel.handlePlanResponse` posts a new public `Notifications.tripPlanEmpty` whenever the response carries an error or zero itineraries, with `userInfo["reason"]` equal to `"error"` or `"empty"` and (R11 amendment) `userInfo["originLatitude"]`, `["originLongitude"]`, `["destinationLatitude"]`, `["destinationLongitude"]` as `Double`s taken from OTPKit's own current `selectedOrigin` / `selectedDestination`, so an endpoint the rider edited inside the planner form is the one reported. Nothing else in OTPKit changes: no view, no public property. The iOS project's OTPKit package dependency is pinned to that branch (the same pattern as the go-gtfs pseudo-version in phase 1) until the branch merges, and the pin is reverted to `main` after merge.

In OBAKit, `MapViewController+TripPlanner` observes `tripPlanEmpty` next to its existing `itinerariesUpdated` and `tripStarted` observers. On `tripPlanEmpty` with either reason it runs the two point probes at the coordinates carried in `userInfo`, falling back to the origin and destination it passed to `showTripPlanner(origin:destination:)` (resolved through `TripPlannerEndpoints`, current location when the origin is nil) only when a `userInfo` coordinate is missing, and sets the dock to `OnDemandDockState.planner(result)`. In that state the classic dock host re-anchors to the trip-planner semi-modal panel's `surfaceView.topAnchor` and is exempt from the semi-modal hide rule. On a later `itinerariesUpdated` the card is removed. The card is dismissed with the planner panel. When neither source yields a coordinate for both ends the card is not shown.

**Android.** `DirectionsErrorSnackbar` gains an action `planner.section` when `error.category` is `NO_ROUTE` or `SCHEDULE`, both `formState.from` and `formState.to` have coordinates, and the deployment is not known unsupported. The action opens `OnDemandPlannerFallbackSheet`, a `ModalBottomSheet` that shows `LoadingContent` while the two probes run, then the cards, caption and link, or `planner.empty`.

**Tested.** Qualification for: single zone containing both ends; zone-to-zone A→B with a rule from A to B (qualifies) and only B→A (does not); tier-4 excluded; one end outside excluded; no-contact card has no pill. iOS: `userInfo` coordinates preferred over the `showTripPlanner` originals; `OnDemandDockState.planner` survives the semi-modal hide rule. Snackbar action presence by category and coordinates.

### 3.9 Map layers sheet (R13)

**iOS (`MapSheetView`, both shells).** A `NavigationStack` with a `ScrollView`; background `#f2f2f7`. Toolbar: Reset (leading, only when `mapLayersDifferFromDefaults`), title "Map" inline, Done (trailing). Content, top to bottom:
1. Basemap segmented control: Standard (`map`), Satellite (`globe.americas.fill`), Hybrid (`map.fill`), bound to `selectedBaseType`.
2. Group `map_layers.group.transit` (13 pt semibold uppercase secondary) → 2-column `LazyVGrid` of tiles for every `.transit` layer whose availability is not `.unsupported`, in registration order (classic: Transit stops, On-demand zones, Route lines & vehicles, Followed trip; panel: the first two).
3. Group `map_layers.group.rentals` → tiles for `.otherModes` layers (Bikes, Scooters) not `.unsupported`; the whole group, header included, disappears when no such layer is registered.
4. "Min. range" chips (`bolt` icon label, then one chip per `RentalRangePreset.presets()`), shown only when the Rentals group is visible; the selected chip is filled in rentals purple. Selecting a chip calls `selectRangePreset`.
5. A card row "Points of Interest" (`mappin.and.ellipse`) with a `Toggle`, bound to `showsPointsOfInterest`.

Tile: 62 pt tall, radius 16; 34 pt circular icon well; title 15 pt semibold; subtitle `state.on`/`state.off` 12 pt. Off: white background, well `#e9e9ee`, icon and text primary. On: background = group tint (transit = brand accent, rentals = `.rentalPurple`), white text, well white at 22 %. Unavailable: opacity 0.5, the reason as a 12 pt caption replacing the On/Off subtitle, tap disabled when off, enabled when on (so it can still be switched off). Tap toggles `setEnabled`. Icons: the layer's `iconName` (`bus.fill`, `car.fill` for on-demand — the layer's `iconName` changes from `car.circle` to `car.fill` —, `arrow.triangle.branch`, the trip layer's existing icon, `bicycle`, `scooter`). The sheet opens at `.medium` in both shells; detents `[.medium, .large]`. The toolbar badge on the map-type button keeps showing `enabledMapLayerCount`.

**Android (new `MapLayersSheet`, replaces the rentals FAB menu).** A `ModalBottomSheet` with a `SheetDragHandle`, title `map_layers_title`, a Reset `TextButton` (only when different from defaults) and Done. Content:
1. Basemap `SegmentedChoice` Standard / Satellite / Hybrid — **google flavour only**; the maplibre flavour has no basemap choice and hides the control (the flavour source set provides `hasBasemapChoice = false`).
2. Group `map_layers_group_transit` → tile "On-demand zones" (`directions_car`), bound to `preference_key_show_ondemand_zones`. The tile and the group are hidden when `OnDemandSupport.isKnownUnsupported(deployment)` is true or there is no deployment (demo mode).
3. Group `map_layers_group_rentals` → tiles Bikes (`pedal_bike`, `preference_key_layer_bikes_visible`) and Scooters (`electric_scooter`, `preference_key_layer_scooters_visible`). The group is hidden when `rentalsEnabled` is false (`BikeshareAvailability.isStationLayerEnabled(region, otpUrl)`, or demo). A tile's displayed state is `master && layerPref`, where `master` is the existing `preference_key_layer_bikeshare_visible` and the per-layer prefs are the remembered selection. Turning a tile on sets its layer pref true and the master true; turning a tile off sets its layer pref false, and sets the master false when no layer pref remains true. `RentalLayerController.syncFromPreferences()` is unchanged. On a fresh install (master false, bikes pref true) both tiles read Off, and turning Bikes on draws bikes.
No stops, routes, followed-trip, POI or range controls exist on Android, so none appear. Reset restores the existing defaults: zones on, master off (so both rental tiles show Off), bikes pref true, scooters pref false, basemap standard. `differsFromDefaults` compares the zones pref, the master, both layer prefs and the basemap. The sheet is empty only when every group is hidden; then the FAB is hidden too.

Tile visuals as iOS, with M3 tokens: off = `surface` with `surfaceVariant` well; on = group tint (transit = `md_theme_primary`, rentals = `layer_bikeshare_color`) with `onPrimary` text. The FAB (`layers` icon, replacing `RentalsFab`) carries a badge with the count of enabled visible tiles. Its visibility rule in `MapChromeViewModel` becomes `layersFab = (rentalsEnabled || onDemandTileVisible || hasBasemapChoice) && preference_key_show_rental_button`, so the on-demand tile is reachable in regions without bikeshare; the long-press "hide button" menu stays, its toast becomes `layers_button_hidden_toast`, and the Settings row title changes to `preferences_show_layers_button_title`. `layersVisible` still excludes `CurrentFocus.Directions`.

**Tested.** iOS: `MapSheetModel` tile lists per shell; Rentals group and chips absent without rental layers; unavailable tile toggling rule; Reset visibility. Android: `MapLayersViewModel` state for supported/unsupported, bikeshare on/off, google/maplibre; displayed state `master && layerPref` on defaults (Bikes Off) and after turning Bikes on; master transitions; badge count; `MapChromeViewModel` "no bikeshare, on-demand supported" shows the FAB; Compose test that a tap flips the tile subtitle.

## 4. Platform mapping

### 4.1 iOS

| Surface | Existing type (extends/replaces) | New type, responsibility and signature |
|---|---|---|
| Point probe | `RESTAPIService+OnDemand`, `RESTAPIURLBuilder` | `public func getOnDemandServices(near coordinate: CLLocationCoordinate2D, radiusMeters: Double, geometryDetail: OnDemandGeometryDetail) async throws -> RESTAPIResponse<[OnDemandService]>`; records absence on 404 like the viewport call |
| Availability | `OnDemandServiceSummary` (gains `availability`; `ServiceWindow` gains `weekdays: [Weekday]`), `BookingDeadlineEvaluator` (reused) | `OBAKitCore/Models/OnDemand/OnDemandAvailability.swift`: `public struct OnDemandAvailability: Equatable, Sendable { runningNow, runningUntil, nextRunStart, bookingTier, bookableNow, nextBookableServiceDate, status, tags, usabilityTier, nextChangeInstant }`; `public enum OnDemandStatus { case openNow(until: Date?), opensAt(Date), bookingOpens(Date), bookBy(deadline: Date, travelDate: ServiceDate), closed, unknown }`; `public static func evaluate(service: OnDemandService, timeZone: TimeZone, now: Date) -> OnDemandAvailability` |
| Sort | — | `OBAKitCore/Models/OnDemand/OnDemandServiceMatch.swift`: `public struct OnDemandServiceMatch { service, matchReason, distanceToArea: Double?, nearestPointOnBoundary: CLLocationCoordinate2D?, availability }`; `public func sortedSoonestUsable(_ matches: [OnDemandServiceMatch]) -> [OnDemandServiceMatch]` |
| Geometry | `ServiceArea` (unchanged) | `OBAKitCore/Models/OnDemand/OnDemandGeometry.swift`: `public enum OnDemandGeometry { static func labelPoint(polygon: [[CLLocationCoordinate2D]], bbox: BoundingBox) -> CLLocationCoordinate2D; static func nearestBoundaryPoint(from: CLLocationCoordinate2D, areas: [ServiceArea]) -> OnDemandEdge?; static func compassDirection(bearingDegrees: Double) -> CompassDirection }`; `public struct OnDemandEdge { distanceMeters, point, bearingDegrees; var edgeDirection: CompassDirection; var riderDirection: CompassDirection }` |
| Eligibility | `OnDemandService` | `public struct OnDemandEligibility: Decodable { requirement: Requirement; infoURL: URL? }`; `OnDemandService.eligibility: OnDemandEligibility?` |
| Zoom level | `MapRegionManager.requiredHeightToShowStops`, `OnDemandMapLayer.zoomWindow` | `OBAKit/Mapping/Layers/OnDemand/OnDemandZoomLevel.swift`: `public enum OnDemandZoomLevel { hidden, region, street; static func level(forVisibleHeight: Double) -> OnDemandZoomLevel }` |
| Probe controller | `OnDemandSupport`, `MapRegionManager` (probe point sources), `LocationService` (trigger 5) | `OBAKit/OnDemand/OnDemandProbeController.swift`: `@MainActor public final class OnDemandProbeController: ObservableObject { init(apiService:, locationService:, onDemandSupport:, geometryCache:, now: @escaping () -> Date); @Published var dockState: OnDemandDockState; func update(probePoint: CLLocationCoordinate2D, source: ProbeSource, zoomLevel: OnDemandZoomLevel); func locationDidUpdate(_ location: CLLocation); func deploymentDidChange(_ deployment: String); func probe(at:) async -> [OnDemandServiceMatch]; func probeExact(at:) async -> [OnDemandServiceMatch] }` (`probeExact` uses the 5-decimal cache for the address check and planner); `public enum ProbeSource { rider, mapCenter, point(label: String?) }`; `public enum OnDemandDockState { hidden, card([OnDemandServiceMatch]), bar([OnDemandServiceMatch]), planner(OnDemandPlannerResult) }` |
| Geometry cache | `RESTAPIService.getOnDemandService(id:geometryDetail:)` | `OBAKit/OnDemand/OnDemandGeometryCache.swift`: `public actor OnDemandGeometryCache { func areas(deployment: String, serviceID: String) async throws -> [ServiceArea]; func cancelAll() }` |
| Region/street drawing | `OnDemandMapLayer` (label point placement; `presentation: OnDemandZoomLevel`; `highlightedServiceID: String?`; `iconName = "car.fill"`; halo overlay), `MapRegionManager` (renderer dispatch unchanged), `MapPanelLayersModel` (`onDemandZones` gains style), `MapPanelRootView.onDemandZoneContent` | `OnDemandZoneShape` gains `style: OnDemandZoneStyle` (`regionFill`, `streetStroke`, `halo`, `highlighted`) |
| Dock host, classic | `MapViewController` (pattern of `presentMapSurveyCard`; `presentMapSurveyCard` hides the dock while the survey card is up), `OBAFloatingPanelController.surfaceView`, the trip-planner semi-modal panel | `OBAKit/OnDemand/Dock/OnDemandDockHostController.swift`: a `UIHostingController<OnDemandDockView>` with two anchors — for `card`/`bar` states `bottomAnchor == floatingPanel.surfaceView.topAnchor − 12`, hidden when the visible map area is under 50 %, a semi-modal panel is presented, or the survey card is shown; for the `planner` state `bottomAnchor == <trip planner panel>.surfaceView.topAnchor − 12`, exempt from the semi-modal hide rule; regular width per 2.4 |
| Dock host, panel | `MapPanelRootView` (`.floatingOverSheet`; `mapControlsCluster` and `myTripButton` gain a bottom offset equal to the dock's measured height), `HomeSheetView` (unchanged) | `.overlay(alignment: .bottom) { OnDemandDockView(...).floatingOverSheet(height: sheetHeight) }`, hidden when `coordinator` top route ≠ `.home`; `@State dockHeight: CGFloat` published through a `PreferenceKey` |
| Dock views | `StopPageTintedCard` (not reused; the card is a plain white card) | `OBAKit/OnDemand/Dock/OnDemandDockView.swift` (switches on `OnDemandDockState`), `OnDemandZoneCardView(match:moreCount:actions:)`, `OnDemandDockBarView(matches:probePoint:actions:)` (paged), `OnDemandZoneThumbnailView(areasByService:colours:probePoint:)` (SwiftUI `Canvas`); `public struct OnDemandDockActions { openDetail, openPicker, call, openURL, zoomOut, panTo }` |
| Picker | `OnDemandServicesListView` (row style reused), `ViewRouter` | `OBAKit/OnDemand/OnDemandPickerView.swift` + `OnDemandPickerViewController(application:matches:probeSource:locality:)`; panel route `AppSheetRoute.onDemandPicker([OnDemandServiceMatch])` at `[.medium, .large]` |
| Detail | `OnDemandServiceView` (`init(service:summary:locationCheck:onOpenURL:)`; new sections), `OnDemandServiceViewController` (`init(application:service:locationCheck:now:)`), `OnDemandServiceHost`, `AppSheetRoute.onDemandService` (gains `locationCheck`) | `public struct OnDemandLocationCheck { source: ProbeSource; isInside: Bool; locality: String? }` |
| Address check | `MapItemViewModel` (`coverage: OnDemandCoverageLine?`, loaded in `init`), `MapItemView`, `MapItemSheetView` (renders the same line) | `public struct OnDemandCoverageLine { serviceName, isInside, match }` |
| Planner fallback | `MapViewController+TripPlanner` (new `tripPlanEmpty` observer beside `itinerariesUpdated`), `TripPlannerEndpoints`, OTPKit `TripPlannerViewModel.handlePlanResponse` (branch change) and `Package.resolved` (pinned to the branch) | OTPKit: `public extension Notifications { static let tripPlanEmpty: Notification.Name }` posted with `userInfo: ["reason": "error" \| "empty"]`; OBAKit: `OBAKit/OnDemand/Planner/OnDemandPlannerFallbackController.swift`: `func qualifyingServices(origin: CLLocationCoordinate2D, destination: CLLocationCoordinate2D) async -> OnDemandPlannerResult`; `OnDemandPlannerFallbackView(result:actions:)` |
| Layers sheet | `MapSheetView`, `MapSheetModel` (gains `tiles(in group:) -> [MapLayerTile]`, `showsRentalsGroup`, `showsRangeChips`), `MapRegionManager` (persistence unchanged), `MapControlsCluster`, `toggleMapTypeButton` | `MapLayerTileView(tile:onTap:)`, `RentalRangeChipRow(presets:selectedID:onSelect:)` |
| Strings | `Strings+OnDemand.swift` (+ `Localizable.stringsdict` for plurals) | keys per 2.8 |
| Theme | `ThemeColors.shared.brand`, `UIColor.rentalPurple` | `ThemeColors.onDemandOutside` (`#636366`), `ThemeColors.onDemandOpenGreen` (`#248a3d`), `ThemeColors.brandAccent` — a themed colour resolved per build the same way `brand` is, defaulting to `#486621` for OBA and overridable by white-label themes |

### 4.2 Android

| Surface | Existing type (extends/replaces) | New type, responsibility and signature |
|---|---|---|
| Point probe | `OnDemandDataSource`, `ObaWebService.onDemandServicesForLocation` (already takes `radius`) | `suspend fun servicesNear(point: GeoPoint, radiusMeters: Int, geometryDetail: String = GEOMETRY_DETAIL_NONE): OnDemandResult<List<OnDemandService>>`; 404 → `Unsupported` |
| Availability | `BookingDeadlineEvaluator`, `OnDemandServicePresentation` (`BookingSummary` reused for the booking line) | `ondemand/OnDemandAvailability.kt`: `data class OnDemandAvailability(runningNow, runningUntil: Instant?, nextRunStart: Instant?, bookingTier: BookingTier, bookableNow, nextBookableServiceDate: LocalDate?, status: OnDemandStatus, tags: Set<OnDemandTag>, usabilityTier: Int, nextChangeInstant: Instant?)`; `sealed interface OnDemandStatus { OpenNow(until), OpensAt(at), BookingOpens(at), BookBy(deadline, travelDate: LocalDate), Closed, Unknown }`; `fun computeAvailability(service: OnDemandService, now: Instant): OnDemandAvailability` |
| Sort | — | `ondemand/OnDemandMatch.kt`: `data class OnDemandMatch(service, matchReason, distanceToAreaMeters: Double?, nearestPointOnBoundary: GeoPoint?, availability)`; `fun List<OnDemandMatch>.sortedSoonestUsable(): List<OnDemandMatch>` |
| Geometry | `ZoneGeometry.kt` (`pointInRing` reused for the label point), `GeoMath.kt` | `fun labelPoint(rings: List<List<GeoPoint>>, bbox: Pair<GeoPoint, GeoPoint>): GeoPoint`; `fun nearestBoundaryPoint(from: GeoPoint, areas: List<ServiceArea>): ZoneEdge?`; `data class ZoneEdge(distanceMeters, point, bearingDegrees) { val edgeDirection: CompassDirection; val riderDirection: CompassDirection }`; `fun compassDirection(bearingDegrees: Double): CompassDirection` |
| Eligibility | `OnDemandApiModels.kt`, `OnDemandAdapters.kt`, `OnDemandService` | `data class OnDemandEligibility(requirement: EligibilityRequirement, infoUrl: String?)`; `OnDemandService.eligibility: OnDemandEligibility?` |
| Zoom level | `OnDemandLayerController` (`ONDEMAND_MAX_VISIBLE_HEIGHT_METERS`) | `const val ONDEMAND_STREET_LEVEL_MAX_HEIGHT_METERS = 4_000.0`; `enum class OnDemandZoomLevel { HIDDEN, REGION, STREET }`; `fun onDemandZoomLevel(latSpan: Double): OnDemandZoomLevel`; `OnDemandLayerController.zoomLevel: StateFlow<OnDemandZoomLevel>` and `highlightedServiceId: MutableStateFlow<String?>` |
| Probe controller | `OnDemandSupport`, `LocationRepository` (`location` flow feeds trigger 5 with the `Location.accuracy` gate), `MapHost.settledCamera()`, `TimeProvider` | `map/OnDemandProbeController.kt`: `class OnDemandProbeController(probePoint: Flow<ProbePoint>, zoomLevel: Flow<OnDemandZoomLevel>, focus: Flow<CurrentFocus>, deployment: Flow<String?>, dataSource, support, geometryCache: OnDemandGeometryCache, prefsRepository, regionRepository, demoMode, timeProvider, scope)`; `val dockState: StateFlow<OnDemandDockState>`; `suspend fun probe(point: GeoPoint): List<OnDemandMatch>`; `suspend fun probeExact(point: GeoPoint): List<OnDemandMatch>` (5-decimal cache; address check and planner); `sealed interface OnDemandDockState { Hidden; Card(matches); Bar(matches) }` (the Android planner fallback is a sheet, so no `Planner` case); `data class ProbePoint(point: GeoPoint, source: ProbeSource)`; `sealed interface ProbeSource { Rider; MapCenter; Point(label: String?) }` |
| Geometry cache | `OnDemandDataSource.service(id, geometryDetail)` | `GEOMETRY_DETAIL_FULL = "full"` (new constant in `OnDemandDataSource.kt`); `ondemand/OnDemandGeometryCache.kt`: `suspend fun areas(deployment: String, serviceId: String): List<ServiceArea>?`; `fun clear()` |
| Render state | `ZonePolygon` (gains `labelPoint: GeoPoint`, `showsPin: Boolean`, `style: ZoneStyle`), `MapRenderSnapshot.onDemandZones`, `MapRenderState.setOnDemandZones` | `enum class ZoneStyle { REGION, STREET, STREET_DIMMED, HIGHLIGHTED }`; `val ZoneStyle.clickable get() = this == REGION` |
| Renderers | `GoogleMapRenderer.addZonePolygon` (fill/stroke by style, `clickable(style.clickable)`, halo polygon, pin marker via `ComposeBitmapRenderer`), `MapLibreRenderer.addZonePolygon` (fill-only `PolygonOptions` plus one `PolylineOptions` per ring with `width` by style, pin marker; `zoneAt` returns only `REGION` zones), `ZonePolygonReconciler` (keys on style too) | `map/render/ZonePinBitmaps.kt`: `fun zonePinBitmap(context, name: String, color: Int): Bitmap` |
| Dock | `HomeMapOverlays` (new `BottomCenter` child, bottom padding = sheet peek + 12 dp, reports height into `fabBottomInsetTarget`), `HomeViewModel.currentFocus`, `StopsBanner` (unchanged; no new case needed because the dock replaces a banner for this purpose) | `ui/home/ondemand/OnDemandDockFeature.kt`: `@Composable fun OnDemandDockFeature(state: OnDemandDockState, probePoint: GeoPoint?, actions: OnDemandDockActions, modifier)`; `OnDemandZoneCard`, `OnDemandDockBar` (`HorizontalPager` + dots), `ZoneThumbnail(rings: List<Pair<ZoneRings, Int>>, probePoint: GeoPoint?, modifier)` (Canvas; shared with the detail page per R14); `data class OnDemandDockActions(openDetail, openPicker, call, openUrl, zoomOut, panTo)` |
| Picker | — | `ui/home/ondemand/OnDemandPickerSheet.kt`: `@Composable fun OnDemandPickerSheet(matches, probeSource, locality, onSelect, onDismiss)` |
| Detail | `OnDemandServicePresentation` (`Content` gains `availability`, `tags`, `whereRows: WhereRows?`, `noServiceDays: String?`, `locationCheck: LocationCheck?`), `OnDemandServiceScreen` (new sections), `OnDemandServiceViewModel` (accepts an optional `LocationCheck` from `SavedStateHandle`), `NavRoutes.onDemandService(id, locationCheck?)` | `data class LocationCheck(source: ProbeSource, isInside: Boolean, locality: String?)` (serialized as route args `inside`, `source`, `locality`) |
| Address check | `NavigateHereBubble` / `NavigateHereOffer` (third line), `MapViewModel.navigateHerePin` | `MapViewModel.navigateHereCoverage: StateFlow<OnDemandCoverageLine?>`; `data class OnDemandCoverageLine(serviceName, isInside, match)` |
| Planner fallback | `DirectionsErrorSnackbar(error, onDismiss, onOnDemandOptions: (() -> Unit)?)`, `TripPlanViewModel.formState`, `HomeScreen` (hosts the sheet) | `ui/home/directions/OnDemandPlannerFallbackSheet.kt`: `@Composable fun OnDemandPlannerFallbackSheet(state: PlannerFallbackState, actions, onDismiss)`; `ondemand/OnDemandPlannerQualifier.kt`: `fun qualifyingServices(origin: List<OnDemandMatch>, originAreas: Set<String>, destination: List<OnDemandMatch>, destinationAreas: Set<String>): PlannerQualification` |
| Layers sheet | `MapChrome` (`RentalsFab` → `LayersFab(badgeCount, onClick, onLongPress)`), `MapChromeViewModel`/`MapChromeState` (`layersFab = (rentalsEnabled || onDemandTileVisible || hasBasemapChoice) && show_rental_button`), `MapFeature` (opens the sheet; toast `layers_button_hidden_toast`), `RentalLayerPreferences`, `SettingsScreen` (row title) | `ui/home/map/MapLayersSheet.kt`: `@Composable fun MapLayersSheet(state: MapLayersUiState, onToggle: (LayerTile) -> Unit, onBasemap: (Basemap) -> Unit, onReset, onDismiss)`; `ui/home/map/MapLayersViewModel.kt`: `@HiltViewModel class MapLayersViewModel(prefs, regionRepository, onDemandSupport, demoMode, flavourCapabilities: MapFlavourCapabilities)`, `val state: StateFlow<MapLayersUiState>`; `data class MapLayersUiState(basemap: Basemap?, transit: List<LayerTile>, rentals: List<LayerTile>, differsFromDefaults: Boolean, enabledCount: Int)`; `data class LayerTile(id, titleRes, iconRes, enabled, group)`; `interface MapFlavourCapabilities { val hasBasemapChoice: Boolean }` (google `true`, maplibre `false`, provided per source set) |
| Preferences | `donottranslate.xml` | `preference_key_basemap` = `preference_basemap` (values `standard`, `satellite`, `hybrid`, default `standard`; read only by the google flavour's `GoogleComposeAdapter`) |
| Strings | `strings.xml`, `plurals` | keys per 2.8 plus `preferences_show_layers_button_title` |
| Icons | `res/drawable` (`ic_customer_service_phone` exists) | vector drawables `ic_directions_car`, `ic_call`, `ic_chevron_right`, `ic_check_circle`, `ic_cancel`, `ic_schedule`, `ic_open_in_new`, `ic_info`, `ic_location_on`, `ic_directions_bus`, `ic_pedal_bike`, `ic_electric_scooter`, `ic_map`, `ic_satellite_alt`, `ic_layers`, `ic_bolt` from Material Symbols (outlined, 24 dp) |

Probe point source on Android: `LocationRepository.location.value` when non-null and permission granted, else the settled camera centre. `MapViewModel` owns the probe controller and starts/stops it alongside `OnDemandLayerController` (`showNearbyStops`, `enterRoute`, `clearAllFocus` start; `leaveCurrentView` stops; `showItinerary` hides).

## 5. Visual tokens

Colours:

| Token | Value | iOS | Android |
|---|---|---|---|
| brand | `#78aa36` | `ThemeColors.shared.brand` | `brand_color` (white-label flavours override) |
| brand accent | `#486621` | `ThemeColors.brandAccent` (new) | `md_theme_primary` |
| zone colour | route `color`, else brand | `service.route?.color ?? brand` | `service.routeColor ?: brand` |
| outside gray | `#636366` | `ThemeColors.onDemandOutside` | `ondemand_outside` (new colour resource) |
| open green | `#248a3d` | `ThemeColors.onDemandOpenGreen` | `ondemand_open_green` |
| rentals tint | `#7B4FD1` (iOS) / `layer_bikeshare_color #6070d6` (Android) | `UIColor.rentalPurple` | `layer_bikeshare_color` |
| tag fill / text | `#e9f1dd` / accent | — | — |
| warn tag fill / text | `#fbefd5` / `#8a5a00` | — | — |
| secondary tile / details fill | `#eef4e6` | — | `primaryContainer` |
| sheet background | `#f2f2f7` | `.systemGroupedBackground` | `surfaceContainerLow` |
| page dots | `#c7c7cc` / `#3a3a3c` | — | `outlineVariant` / `onSurface` |

Radii: zone card 22; bar 20; thumbnail 12; picker list and detail lists 18; sheets 38 (system default where not settable); tile 16; tag 12; badge 9; basemap tiles 12 (existing).

Type (mock pt → platform):

| Mock | iOS text style | Android M3 |
|---|---|---|
| 26 bold | `.title` bold | `headlineSmall` bold |
| 22 bold | `.title2` bold | `titleLarge` bold |
| 17 semibold/bold | `.headline` | `titleMedium` |
| 16 semibold | `.callout` semibold | `bodyLarge` semibold |
| 15 | `.subheadline` | `bodyMedium` |
| 14 | `.subheadline` | `bodyMedium` |
| 13 semibold | `.footnote` semibold | `labelMedium` |
| 12 | `.caption` | `labelSmall` |
| 11 bold | `.caption2` bold | `labelSmall` bold |

All text scales with Dynamic Type / font scale; the bar and card grow in height rather than truncate; the bar title's line limit is 1 at standard sizes and 2 at accessibility sizes (3.4).

**Bar text colour.** The bar's eyebrow, title and trailing glyph use the route's `textColor` when the service has one; otherwise white or black, whichever gives a contrast ratio ≥ 4.5:1 (WCAG) against the resolved bar colour, computed once per service. The outside gray always uses white.

Icons (SF Symbol → Material Symbol): `car.fill` → `directions_car`; `phone.fill` → `call`; `chevron.right` → `chevron_right`; `checkmark.circle.fill` → `check_circle`; `xmark.circle.fill` → `cancel`; `clock` → `schedule`; `arrow.up.right.square` → `open_in_new`; `info.circle` → `info`; `mappin.and.ellipse` → `location_on`; `bus.fill` → `directions_bus`; `arrow.triangle.branch` → `conversion_path`; `bicycle` → `pedal_bike`; `scooter` → `electric_scooter`; `map` → `map`; `globe.americas.fill` → `satellite_alt`; `map.fill` → `layers`; `bolt` → `bolt`. Android uses the vector drawables listed in 4.2; the map basemap icons use the three Material icons of screen A.

Touch targets are at least 44 pt / 48 dp; every icon-only button has an accessibility label equal to its action's copy.

## 6. Testing strategy

### 6.1 iOS (Swift Testing, `@Test`, `OBATestCase`)

Pure functions (`OBAKitTests/OnDemand/`):
- `OnDemandAvailabilityTests`: built from the charlevoix and alexandria fixtures rewritten through `alexandria(rewriting:_:)`-style transforms with a `TestClock`; cases: running now inside a window; window crossing 24:00 checked from the previous day at 00:30, including `bookableNow` evaluated for `today − 1` with a type-1 rule; all-null times (all day) running at 03:00 with `runningUntil == nil` because the windows abut, and non-nil when the next day is not active; `exceptedDates` removing today; an `_added_` calendar adding a Sunday; `nextRunStart` tomorrow when today's window has ended; `bookingTier` for each type and for a mix; `status` precedence for advance while running and `.bookingOpens` versus `.opensAt`; service-level `nextBookableServiceDate` as the minimum over rules; missing timezone → unknown; `nextChangeInstant` equals the earliest boundary.
- `OnDemandSortTests`: tier order 1→5, the overlap cases (advance with no bookable date → 5, eligibility with open now → 4, closed with a bookable date → 5), distance tie-break, name tie-break with natural order.
- `OnDemandGeometryTests`: label point inside for convex, U-shape, holed and two-polygon cases; fallbacks; nearest edge for a point inside a square (distance to the near side), outside, and against a hole edge; `edgeDirection` north for a point just inside the north edge, `riderDirection` south for a point due south of the square; the eight compass buckets at their boundaries (22.5°, 67.5°, …).
- `OnDemandCopyTests`: card meta composition with and without status; bar titles for each state and tier, including `outside.south` and `inside_near_edge.north`; distance formatting in en_US and en_GB/metric; badge; plurals; accessibility labels.
Layer and controller (`OBAKitTests/Mapping/`, extending `OnDemandMapLayerTests` with `MockDataLoader` and `GatedDataLoader`): pin at the label point; style per `presentation`; markers hidden at street level; halo overlays present at street level; highlight, and its clearing when the dock leaves the bar state; `OnDemandProbeControllerTests`: probe URL carries `radius=5000&geometryDetail=none`; no refetch under 100 m; a location update 150 m away re-probes and one with 200 m accuracy does not; cache hit at `nextChangeInstant`; `probeExact` never returns the rider cache's entry for a point 20 m away; a failed probe 2 km from the last one yields `.hidden` while one 50 m away keeps the state; a deployment change cancels in-flight work and yields `.hidden`; 404 records absence and yields `.hidden`; dock state per zoom level and match reason; `.planner` state; `MapPanelLayersModelTests` for the panel bridge; `MapSheetModelTests` for tiles, groups, chips and Reset; `OnDemandServiceViewTests` for section presence per tier and the "No service" row; `MapItemViewModelTests` for the coverage line including error and ties; `OnDemandPlannerFallbackTests` for qualification and `userInfo` coordinate preference. Fixture: a new `ondemand_services_for_location_point_near.json` (one `areaNearby` match with a finite distance).

### 6.2 Android (JUnit4 JVM with fakes; Compose tests in `androidTest`)

Pure functions (`src/test/.../ondemand/`): `OnDemandAvailabilityTest`, `OnDemandSortTest`, `ZoneGeometryTest` (label point, nearest edge, compass), `OnDemandCopyTest` (formatting through a `Resources`-free formatter that returns key + args, since `android.util.Log` and resources are unmocked) with the same cases as iOS. Controllers: `OnDemandProbeControllerTest` using the existing `FakeDataSource` recording pattern, `FakeDemoMode`, `FakePreferencesRepository` (note its `observeBoolean` ignores the default and returns `observeValue`, so tests set the fake explicitly), `MutableSharedFlow<CameraSnapshot>(replay = 1)`; cases mirror iOS (location trigger and accuracy gate, exact-cache isolation, error distance rule, deployment change). `OnDemandLayerControllerTest` gains zoom-level, style and `clickable` cases; the google renderer test asserts `clickable` false at street level and the maplibre test asserts `zoneAt` returns null for a street-level zone. `MapLayersViewModelTest` covers google/maplibre, bikeshare on/off, unsupported, displayed state `master && layerPref`, master transitions, enabled count and Reset; `MapChromeViewModelTest` covers "no bikeshare, on-demand supported" showing the FAB. `OnDemandServicePresentationTest` gains tags, where rows, no-service days and location check. `OnDemandPlannerQualifierTest`. Compose (`createUnconfinedComposeRule`): `OnDemandDockBarTest` (titles, badge, paging), `OnDemandZoneCardTest`, `MapLayersSheetTest` (tile subtitle flips, groups hidden), `NavigateHereBubbleRenderTest` extended with the coverage line, `ZoneThumbnailTest` (draws without crashing for empty and multi-ring input). Build gates: both flavour compiles with `-PwarningsAsErrors=true`, `testObaGoogleDebugUnitTest`, `spotlessApply`.

### 6.3 Manual verification against local maglev

Run maglev with `config.json` pointing `gtfs-static-feed.url` at `testdata/charlevoix-flex.zip` (`make run`; API key `test`; `http://localhost:4000`), then point the iOS simulator (custom region base URL) and the Android emulator (custom API URL) at it with the simulated location set inside Charlevoix County. Check, in order: (1) at region level the four CC services draw as polygons with one labelled pin each, every pin inside its own polygon; (2) the zone card appears with the top service and the correct "more services" count; (3) zoom in below the gate: pins vanish, strokes become 4 pt, the bar appears with page dots and a badge; (4) move the simulated location 50 m inside an edge: the near-edge title shows a distance and direction; (5) move 500 m outside: the bar goes gray, the chevron pans to the boundary; (6) move 6 km outside: the bar disappears; (7) long-press the bar: the picker lists all matches in tier order; (8) open each detail: tags, status line or promoted deadline, location row, thumbnail, Where/When/How to book, "No service" for absent weekdays; (9) long-press the map (iOS classic, Android): the callout reads Inside/Outside; (10) plan a trip between two points inside one zone with no fixed route: the fallback card appears; (11) the layers sheet: toggle "On-demand zones" off — the polygons and the dock vanish, the long-pressed pin still reads Inside/Outside; Reset appears; (12) with a tier-1 service on page 1, change the device clock past its `runningUntil`: within 2 s the title changes from "Pickups available here" to "Opens …"; (13) on an iPad (classic regular width) and in landscape on both platforms, the dock sits above the side panel at its width, never spans the map, and the control stack is not overlapped. Repeat (1), (3), (8) and (10) with `manistee-flex.zip`: the zone-to-zone service (MC2, 18 rules) shows the Where section with Drop-off, and the planner fallback qualifies only rules with the right direction. Finally, point at a server without `/api/ondemand` (a stock `main` build of maglev): nothing new appears and the layers sheet has no on-demand tile.

## 7. Open risks

1. **Fixed zoom gate instead of "map smaller than zone" (R3).** A zone a few blocks wide shows the bar while still fully visible. Mitigation chosen: accept; the bar's thumbnail still shows the whole zone.
2. **Stop-group services never appear in the dock (R5 requires a finite `distanceToArea`, which only areas carry).** Mitigation chosen: accept for v1; these services remain reachable from the stop page card and the agency list.
3. **R5 says nothing shows in the dock while a directions sheet has focus; R11 puts the planner fallback in the same slot.** Resolution: R5 governs probe-driven content; the planner fallback is the one exception, carried as `OnDemandDockState.planner` and anchored to the trip-planner panel on iOS classic. Reported as a wording conflict.
4. **Re-probing at `nextChangeInstant` (R2) meets the per-point cache (R2).** Resolution: the trigger recomputes availability from the cached response; the 10-minute cache lifetime bounds staleness of the match list. The rounded rider/centre cache is never consulted for the address check or planner probes, which use an exact-coordinate cache (ruling amendment, 2.1).
5. **OTPKit gives no empty-results signal as published.** Mitigation chosen: the minimal `tripPlanEmpty` notification on an OTPKit branch and a temporary package pin (3.8); the pin is reverted once the branch merges, and until then the iOS build depends on an unmerged branch.
6. **Zone-to-zone direction.** The server does not say which role a matched area plays. Mitigation chosen: derive origin- and destination-containing area ids from the server's per-area `distanceToArea == 0` and require a rule whose `fromIds`/`toIds` intersect them; no client containment.
7. **Full-geometry fetches for near-edge copy.** One extra request per matched service, cached for the process. Mitigation chosen: fetch only for matched services; show the plain inside title until the geometry arrives.
8. **The Android home sheet is hidden when nothing is focused.** The dock therefore sits above the FAB inset rather than on a sheet edge. Mitigation chosen: `HomeMapOverlays` bottom-centre with the peek height as padding, so it still attaches to the sheet when one is shown.
9. **Reverse geocoding latency.** The picker subtitle and location sub-line wait at most 1 s and then omit the locality.
10. **Rider eligibility is unknown in v1.** Tier 4 and the eligibility tag never appear; the mock's "Eligibility required" bar title for Medical Trips renders as "Book by …" instead. Mitigation chosen: the model and copy are in place so a server that adds `eligibility` needs no client redesign.
11. **Android rentals master preference.** Folding the on/off switch into two tiles changes what `preference_key_layer_bikeshare_visible` means. Mitigation chosen: the sheet keeps the master in sync (true when any rental tile is on), so `RentalLayerController` is untouched.
12. **Dynamic Type and long service names** can push the bar past 72 pt. Mitigation chosen: the bar grows and the sheet inset follows its measured height on both platforms.
13. **The rider's own movement.** R2 as written never re-probed while the map was untouched. Mitigation chosen (R2 clarification): trigger 5 in 2.1 with the 100 m move and ≤ 100 m accuracy gate.
14. **Edited planner endpoints on iOS.** OTPKit's form can change the endpoints OBAKit passed in. Mitigation chosen (R11 amendment): the `tripPlanEmpty` notification carries OTPKit's own coordinates and OBAKit prefers them.
15. **Android address check without trip planning.** The long-press pin is gated by `refuseTripPlanIfUnavailable()`, so no address check exists in such regions. Accepted, leaving the existing code untouched.

## Review log

Findings from `spec-review.md`, all applied; the section changed is given for each.

- 1 → 2.7 (`edgeDirection` / `riderDirection`), 3.4, 4.1, 4.2, 6.1, 6.2.
- 2 → 2.1 (exact-coordinate cache for address check and planner; ruling amendment), 3.7, 3.8, 4.1, 4.2, 6.1, 7.4.
- 3 → 2.8 bar title precedence (tier 2 → opens, tier 5 → closed, unknown → no title), 6.3 step 12.
- 4 → 2.4 (planner content), 3.8 iOS, 4.1 dock host (second anchor, `planner` case), 6.1, 7.3.
- 5 → 3.9 Android (`layersFab` rule, toast), 4.2, 2.8 (toast key), 6.2.
- 6 → 3.9 Android (displayed state `master && layerPref`, Reset, `differsFromDefaults`), 6.2.
- 7 → 3.2 (maplibre polylines), 4.2 renderers.
- 8 → 3.1 (region-only polygon tap), 4.2 render state and renderers, 6.2.
- 9 → 2.5 `bookableNow`, 6.1.
- 10 → 2.6 ordered decision, 2.5 service-level `nextBookableServiceDate`, 4.1, 4.2, 6.1.
- 11 → 2.8 (`ProbeSource.point`, `detail.point_*`, `picker.subtitle_point`, `picker.selected_place`; R8 addendum), 3.5, 3.6, 3.7, 4.1, 4.2.
- 12 → 2.7 (cache key, deployment change), 4.1, 4.2, 6.1, 6.2.
- 13 → 2.1 trigger 5 (R2 clarification), 4.1, 4.2, 6.1, 7.13.
- 14 → 2.1 (error rule), 3.4 (chevron hidden), 3.7 (error hides line), 6.1.
- 15 → 2.4 (survey card wins; control stack offset), 4.1.
- 16 → 2.4 (regular width and landscape; visible-map-area rule), 4.1, 6.3 step 13.
- 17 → 2.8 (`bar.a11y.*` keys), 3.4 Accessibility, 5 (48 dp), 6.1.
- 18 → 3.4 Stack (inside-only when any inside).
- 19 → 3.8 iOS (`userInfo` coordinates; R11 amendment), 6.1, 7.14.
- 20 → 3.7 (accepted, existing code untouched), 7.15.
- 21 → 3.5 Data, 2.8 (`picker.title_nearby`), 3.4.
- 22 → 3.4 Interactions (0.9 × outer window clamp).
- 23 → 2.7 (thumbnail listed), 3.4 Thumbnail (placeholder).
- 24 → 3.6 item 1 (status line for tiers 1, 2 and closed 5).
- 25 → 2.5 `runningUntil`, 2.8, 6.1.
- 26 → 3.6 item 7.
- 27 → 4.2 geometry cache (`GEOMETRY_DETAIL_FULL` new).
- 28 → 3.4 Layout, 5.
- 29 → 3.6 item 3.
- 30 → 2.8 (eight full templates per sentence; `ondemand_call`; `preferences_show_layers_button_title`), 3.6 item 1.
- 31 → 2.5 `today`.
- 32 → 3.7 (Android search results excluded, per R12).
- 33 → 2.8 (`detail.includes_location`), 3.6 item 5.
- 34 → 2.3 (fallback palette), 5 (bar text colour).
- 35 → 4.1 Theme.
- 36 → 2.3 Highlight.
- 37 → 2.4 (layer toggle scope).
- 38 → 3.8 (`planner.status_now`; no-contact card), 2.8.
- 39 → 3.6 item 4 (location-check point drawn with its source; R14 wording).
- 40 → 2.5 status step 1 (`.bookingOpens`), 2.8, 4.1, 4.2.
- 41 → 2.4 (Android `CurrentFocus` values).
- 42 → 3.7 (ties by sort; `address.inside_more`), 2.8.
- 43 → 3.6 item 6 (weekday sets; `ServiceWindow.weekdays`), 4.1.
- 44 → 2.10 (map long-press vs bar long-press).
