# Umami Analytics Support — Design

**Issue:** [#1575](https://github.com/OneBusAway/onebusaway-android/issues/1575)
**Date:** 2026-06-22
**Status:** Approved design, pending implementation plan
**Reference implementation:** iOS shipped this on its `umami` branch — see
`~/repos/onebusaway/ios` (`Apps/Shared/Analytics/UmamiAnalytics.swift`,
`Apps/Shared/Analytics/AnalyticsOrchestrator.swift`,
`OBAKitCore/Models/Region.swift`, `OBAKit/Analytics/Analytics.swift`,
`docs/superpowers/specs/2026-06-21-umami-analytics-design.md`). This design
aims for wire-level and behavioral parity with iOS.

## Overview

Add native Umami analytics as a **third parallel analytics sink** alongside the
existing Firebase and Plausible integrations. Umami configuration is discovered
per-region through the feed the app already fetches. Events are emitted via
direct API calls (no JavaScript tracker), fire-and-forget, and analytics
failures never affect user-facing functionality.

Umami is structurally a near-twin of the existing Plausible integration:
`ObaRegion` already exposes `getPlausibleAnalyticsServerUrl()`, persisted as a
content-provider column, and `ObaAnalytics` methods already accept a `Plausible`
instance alongside `FirebaseAnalytics`. Umami follows the same pattern, with two
differences: it needs **two** region fields (`url` + `id`) nested under a
`umamiAnalytics` object, and a hand-rolled API client that sends a device-like
`User-Agent`.

### Decisions

- **Umami's role:** additive. Firebase and Plausible are untouched; Umami
  becomes a third sink wired into the same `ObaAnalytics` methods. Plausible is
  **not** removed as part of this work.
- **Client:** hand-rolled client over `HttpURLConnection` (the app's existing
  `ObaApi` networking primitive) on a background executor — **no new
  dependency.** OkHttp is not a declared dependency or imported anywhere in the
  app, and adding one for fire-and-forget telemetry isn't warranted. The issue
  mandates API-only POSTs to `/api/send`, fire-and-forget, fast timeouts, and a
  custom `User-Agent`. No well-maintained Umami Android SDK exists, and the
  existing Plausible integration is itself a custom fork.
- **Event scope:** mirror Plausible exactly. Wherever `ObaAnalytics` already
  receives a `Plausible` instance, it also receives the Umami client and emits
  the same events. No new event taxonomy.
- **Region name on every event:** match iOS — the client holds persistent
  "default data" (region name) that is merged into every event's `data`.

## 1. Region model & persistence

The feed nests the config:

```json
"umamiAnalytics": { "url": "https://...", "id": "<website-uuid>" }
```

- Add a nested Jackson-bound element class to `ObaRegionElement` named
  **`UmamiAnalyticsConfig`** (`url`, `id`) — deliberately *not* `UmamiAnalytics`,
  to avoid colliding with the OkHttp client class (iOS hit and renamed around
  this exact collision). It binds by field name like the existing `Bounds` /
  `Open311Server` nested elements (`JacksonSerializer` uses `ANY` field
  visibility and `FAIL_ON_UNKNOWN_PROPERTIES=false`).
- Add `getUmamiAnalyticsUrl()` and `getUmamiAnalyticsId()` to the `ObaRegion`
  interface, returning the config's values (or `null` when absent).
- Persist as two flat `TEXT` columns — `UMAMI_ANALYTICS_URL` and
  `UMAMI_ANALYTICS_ID` — in `ObaContract.RegionsColumns`, exactly as
  `PLAUSIBLE_ANALYTICS_SERVER_URL` is handled today.
- Bump the content-provider database version (currently 33 → 34) and add an
  `if (oldVersion == 33)` migration block with two `ALTER TABLE ... ADD COLUMN`
  statements, mirroring the Plausible migration at `oldVersion == 32`.
- **Flat-vs-nested round-trip (important):** JSON parsing produces a nested
  `UmamiAnalyticsConfig` object, but the content provider **rebuilds**
  `ObaRegionElement` from the two flat `TEXT` columns — it does not reconstruct
  the nested object. The getters must therefore return correct values whether
  the element was JSON-parsed (nested object present) or DB-rebuilt (flat values
  only). Concretely: `ObaRegionElement` carries the two flat string fields, the
  getters read those, and the JSON path also populates them from the nested
  object. (Plausible sidesteps this by being flat end-to-end; Umami cannot,
  because the feed is nested.)
- **Disabled semantics:** if the `umamiAnalytics` object or either field is
  null/missing, Umami is disabled for that region.

**Files touched:**

- `io/elements/ObaRegion.java` — two new getters.
- `io/elements/ObaRegionElement.java` — nested `UmamiAnalyticsConfig`, two flat
  fields, getters, **and both constructors** (the no-arg default and the full
  positional constructor — every caller of the positional constructor updates).
- `provider/ObaContract.java` — column constants **and** the second, positional
  cursor reader at `~:1438-1462` (`new ObaRegionElement(... c.getString(22))`):
  append the two columns at indices 23/24. *This reader is independent of
  `RegionUtils` and silently drops columns if missed.*
- `provider/ObaProvider.java` — DB version bump + migration.
- `util/RegionUtils.java` — `toContentValues()` writes the two columns, and the
  positional cursor reader at `~:401-479` (projection currently ends at index 22)
  reads them at indices 23/24.

## 2. The Umami client

New class `org.onebusaway.android.io.UmamiAnalytics`, sibling to `ObaAnalytics`,
wrapping `HttpURLConnection` calls dispatched on a background executor.

- **Construction:** built from the current region's `url` + `id`, plus mutable
  default data (region name). A factory helper returns `null` (or a no-op
  instance) when the region has no Umami config, so callers stay clean.
- **Public API:** mirrors how Plausible is used — `event(String name,
  Map<String,Object> props)` for custom events and `pageview(String path)`
  (no `name` field) — plus a `setDefaultData`/`setRegionName` setter for the
  persistent region-name property. `ObaAnalytics` calls these.
- **Payload** — JSON POST to `<url>/api/send` (identical to iOS):

  ```json
  { "type": "event",
    "payload": { "website": "<id>", "hostname": "<host>", "url": "<path>",
                 "name": "<eventName>", "data": { ...defaultData, ...props } } }
  ```

  Omitting `name` records a pageview; including it records a custom event.
  `data` is omitted when empty. Default data (region name) is merged into every
  event.
- **Hostname / url path:** native apps have no real URL.
  - `hostname` = the host of the region's OBA base URL. Derive it **without
    throwing** — swallow a malformed-URI error and skip the send, rather than
    rethrowing as `RuntimeException` the way the existing Plausible path does
    (`Application.buildPlausibleInstance`). This is fire-and-forget telemetry.
  - `url` = a **reduced path**, matching iOS. Reduce the existing `pageURl`/`id`
    argument (e.g. `app://localhost/map`) to its path (`/map`) via
    `new URI(pageUrl).getPath()`; pathless → `/`; drop any query. Without this,
    Android would report `app://localhost/...` while iOS reports `/...`, so the
    same screen would appear as different pages in the dashboard.
- **User-Agent:** `OneBusAway/<versionName> (Android <Build.VERSION.RELEASE>;
  <Build.MODEL>)`, set explicitly on every request, overriding OkHttp's default
  (`okhttp/<version>`). Must be non-empty and device-like so Umami's `isbot`
  filter does not reject it. (`Build.MODEL` is acceptable — it is device-like.)
- **Success / "beep-boop" detection (critical):** a dropped event returns
  **HTTP 200**, not an error. Umami replies `{"beep":"boop"}` (or a body lacking
  `cache`/`sessionId`/`visitId`) when it rejects the request — typically a
  bot-like User-Agent or bad config. Inspect the 200 body and treat such a
  response as a (logged, swallowed) failure, mirroring iOS `isSuccessfulIngest`.
  Without this, a broken User-Agent fails silently with no signal.
- **User-Agent:** must override `HttpURLConnection`'s default agent string,
  which is `Dalvik/...` / `Java/...`-style and would otherwise risk Umami's
  `isbot` rejection.
- **Fail-safe:** fire-and-forget on a background `Executor`, short connect/read
  timeouts (~3–5s), all exceptions and rejection responses swallowed (at most
  logged). Serialize props safely: drop or stringify any non-JSON value in the
  `Map<String,Object>` so a stray prop can't throw on the analytics path. Never
  throws into callers.

## 3. `ObaAnalytics` integration & privacy

- **No call-site changes.** Rather than threading a new parameter through all
  ~79 `ObaAnalytics` call sites, each affected `ObaAnalytics` method fetches the
  Umami instance internally via `Application.get().getUmamiInstance()` (the class
  already calls `Application.get()` for prefs/strings). Right after the existing
  Plausible call, it makes the equivalent Umami call through a
  `UmamiAnalyticsReporter` helper. Methods affected: `reportUiEvent`
  (`io/ObaAnalytics.java:83`), `reportSearchEvent` (`:119`),
  `reportViewStopEvent` (`:180`), `setRegion` (`:198`).
- **`setRegion` behavior:** in addition to the Firebase region property, it sets
  the Umami client's persistent default data to the region name, so every
  subsequent Umami event carries it (iOS parity).
- **Instance source:** add `getUmamiInstance()` / `buildUmamiInstance()` to
  `app/Application.java`, parallel to the existing `getPlausibleInstance()` /
  `buildPlausibleInstance()` (`~:367,379-388`) — the single construction seam.
  It returns null when the region has no Umami config.
- **Privacy opt-out:** reuse the existing gate. `ObaAnalytics` already consults
  the user's "send anonymous data" preference (`isAnalyticsActive()`,
  `preferences_key_analytics`, default true); the Umami calls sit behind that
  same check. Opt-out suppresses Umami exactly as it does Firebase/Plausible. No
  new preference UI.
- **Null-safety:** every Umami call is guarded — a null client (unconfigured
  region) or an opted-out user is a no-op.

## 4. Testing

- **Region parsing** (androidTest, alongside existing region tests): feed
  fixture with `umamiAnalytics` present → getters populated; object absent/null
  → both getters null; only one of the two fields present → treated as disabled.
- **Persistence round-trip:** save a region via `RegionUtils.toContentValues()`,
  read it back through the provider, assert both new columns survive **and that
  the DB-rebuilt `ObaRegionElement` (not just a JSON-parsed one) yields populated
  Umami getters**; verify the migration adds the columns on upgrade.
- **Payload construction:** unit-test the JSON body, the reduced `url` path
  (`app://localhost/map` → `/map`, pathless → `/`), the merged default data, and
  the `User-Agent` header (pageview vs. named event; props serialization;
  non-JSON props dropped). Because the network call is fire-and-forget, assert on
  the request that *would* be sent, not on delivery.
- **Success detection:** test that a `{"beep":"boop"}` 200 body is treated as a
  failure (logged, swallowed) and a valid ingest body as success.
- **Manual verification** (issue's real acceptance test): a debug build pointed
  at a region with live Umami config produces events in the dashboard under the
  correct website; an unconfigured region produces none.
- **No-regression:** Firebase and Plausible event paths are unchanged.

## Acceptance criteria (from issue #1575)

- App parses `umamiAnalytics` per region and disables analytics when null. ✅ §1
- Real device events appear in the Umami dashboard under the correct website.
  ✅ §2 (incl. beep-boop detection) / manual test §4
- No events emit for unconfigured regions. ✅ §1 disabled semantics, §3
  null-safety
- Analytics failures don't impact user-facing functionality. ✅ §2 fail-safe

## Out of scope (YAGNI)

- No new event types beyond what Plausible already receives.
- No batching, retry, or offline queue.
- No per-event opt-out UI (reuse existing preference).
- No removal of Plausible.
