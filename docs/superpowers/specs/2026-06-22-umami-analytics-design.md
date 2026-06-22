# Umami Analytics Support — Design

**Issue:** [#1575](https://github.com/OneBusAway/onebusaway-android/issues/1575)
**Date:** 2026-06-22
**Status:** Approved design, pending implementation plan

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
- **Client:** hand-rolled OkHttp client (`UmamiAnalytics`), not an SDK. The
  issue mandates API-only POSTs to `/api/send`, fire-and-forget, fast timeouts,
  and a custom `User-Agent`. No well-maintained Umami Android SDK exists, and the
  existing Plausible integration is itself a custom fork.
- **Event scope:** mirror Plausible exactly. Wherever `ObaAnalytics` already
  receives a `Plausible` instance, it also receives the Umami client and emits
  the same events. No new event taxonomy.

## 1. Region model & persistence

The feed nests the config:

```json
"umamiAnalytics": { "url": "https://...", "id": "<website-uuid>" }
```

- Add a nested `UmamiAnalytics` element class (Jackson-bound `url`, `id`) inside
  `ObaRegionElement`, parallel to the existing `Bounds` / `Open311Server` nested
  elements.
- Add `getUmamiAnalyticsUrl()` and `getUmamiAnalyticsId()` to the `ObaRegion`
  interface, returning the nested object's values (or `null` when the object is
  absent). `ObaRegionElement` implements them by reading the nested object.
- Persist as two flat `TEXT` columns — `UMAMI_ANALYTICS_URL` and
  `UMAMI_ANALYTICS_ID` — in `ObaContract.RegionsColumns`, wired through
  `RegionUtils.toContentValues()` and the cursor-read path, exactly as
  `PLAUSIBLE_ANALYTICS_SERVER_URL` is handled today.
- Bump the content-provider database version and add a migration that adds the
  two columns on upgrade.
- **Disabled semantics:** if either field (or the `umamiAnalytics` object) is
  null/missing, Umami is disabled for that region.

**Files touched:** `io/elements/ObaRegion.java`,
`io/elements/ObaRegionElement.java`, `provider/ObaContract.java`,
`provider/ObaProvider.java` (DB version + migration),
`util/RegionUtils.java`.

## 2. The Umami client (`UmamiAnalytics`)

New class `org.onebusaway.android.io.UmamiAnalytics`, sibling to `ObaAnalytics`,
wrapping OkHttp.

- **Construction:** built from the current region's `url` + `id`. A factory
  helper returns `null` (or a no-op instance) when the region has no Umami
  config, so callers stay clean.
- **Public API:** mirrors how Plausible is used — `event(String name,
  Map<String,Object> props)` for custom events and `pageview(String path)`
  (no `name` field). `ObaAnalytics` calls these.
- **Payload** — JSON POST to `<url>/api/send`:

  ```json
  { "type": "event",
    "payload": { "website": "<id>", "hostname": "<host>", "url": "<path>",
                 "name": "<eventName>", "data": { ...props } } }
  ```

  Omitting `name` records a pageview; including it records a custom event.
- **Hostname / url path:** native apps have no real URL.
  - `hostname` = the host of the region's OBA base URL (stable per region;
    groups data correctly per website in the dashboard).
  - `url` = the screen/element path already passed into `reportUiEvent`
    (existing `pageURl` / `id` arguments).
- **User-Agent:** `OneBusAway/<versionName> (Android <Build.VERSION.RELEASE>;
  <Build.MODEL>)`, set explicitly on every request, overriding OkHttp's default
  so Umami's `isbot` check does not silently reject it.
- **Fail-safe:** asynchronous fire-and-forget (`enqueue`), short connect/read/
  write timeouts (~3–5s), all exceptions and non-2xx responses swallowed (at
  most logged). Never throws into callers.

## 3. `ObaAnalytics` integration & privacy

- Each `ObaAnalytics` method that currently takes a `Plausible plausible`
  parameter gains a parallel nullable `UmamiAnalytics umami` parameter. Right
  after the existing Plausible call, it makes the equivalent Umami call. Methods
  affected: `reportUiEvent`, `reportSearchEvent`, `reportViewStopEvent`,
  `setRegion`. Their call sites pass the Umami instance alongside the Plausible
  one they already pass.
- **Instance source:** the same places that build the `Plausible` object from
  the current region also build the `UmamiAnalytics` instance from the region's
  `url`/`id`, or leave it null when unconfigured.
- **Privacy opt-out:** reuse the existing gate. `ObaAnalytics` already consults
  the user's "send anonymous data" preference (`isAnalyticsActive()`); the Umami
  calls sit behind that same check. Opt-out suppresses Umami exactly as it does
  Firebase/Plausible. No new preference UI.
- **Null-safety:** every Umami call is guarded — a null client (unconfigured
  region) or an opted-out user is a no-op.

## 4. Testing

- **Region parsing** (androidTest, alongside existing region tests): feed
  fixture with `umamiAnalytics` present → getters populated; object absent/null
  → both getters null; only one of the two fields present → treated as disabled.
- **Persistence round-trip:** save a region via `RegionUtils.toContentValues()`,
  read it back through the provider, assert both new columns survive; verify the
  DB migration adds the columns on upgrade.
- **Payload construction:** unit-test the JSON body and the `User-Agent` header
  (pageview vs. named event; props serialization). Because the network call is
  fire-and-forget, assert on the request that *would* be sent, not on delivery.
- **Manual verification** (issue's real acceptance test): a debug build pointed
  at a region with live Umami config produces events in the dashboard under the
  correct website; an unconfigured region produces none.
- **No-regression:** Firebase and Plausible event paths are unchanged.

## Acceptance criteria (from issue #1575)

- App parses `umamiAnalytics` per region and disables analytics when null. ✅ §1
- Real device events appear in the Umami dashboard under the correct website.
  ✅ §2 / manual test §4
- No events emit for unconfigured regions. ✅ §1 disabled semantics, §3
  null-safety
- Analytics failures don't impact user-facing functionality. ✅ §2 fail-safe

## Out of scope (YAGNI)

- No new event types beyond what Plausible already receives.
- No batching, retry, or offline queue.
- No per-event opt-out UI (reuse existing preference).
- No removal of Plausible.
