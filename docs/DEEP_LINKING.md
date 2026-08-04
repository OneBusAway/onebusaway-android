# Deep Linking

OneBusAway for Android answers the same deep links as
[OneBusAway for iOS](https://developer.onebusaway.org/projects/ios), so a link shared from either app
opens the same screen on the other ([#2027](https://github.com/OneBusAway/onebusaway-android/issues/2027)).

Two families of link, both handled by `HomeActivity` (the app's single Activity):

| Link | What it does |
| --- | --- |
| `onebusaway://view-stop?stopID=1_75403&regionID=1` | Opens that stop's arrivals |
| `onebusaway://add-region?name=…&oba-url=…` | Adds that named region and switches to it, after the rider confirms — see below |
| `https://onebusaway.co/regions/1/stops/1_75403/trips?trip_id=1_18196913&service_date=1698307200.0&stop_sequence=5` | Opens that trip's details, scrolled to the stop in the path |

## Custom-scheme links

Every brand answers to the cross-platform `onebusaway://` scheme **and** to its own scheme
(`kiedybus://` for KiedyBus). A brand's scheme is declared once, as the `deepLinkScheme` manifest
placeholder — defaulted to `onebusaway` in `defaultConfig` and overridden in
`onebusaway-android/flavors/<brand>.gradle`. `BuildConfig.DEEP_LINK_SCHEME` (and through it
`ExternalDeepLinks.APP_SCHEMES`) is derived from that placeholder by the `androidComponents.onVariants`
block in `build.gradle.kts`, so the manifest filter and the parser can't disagree about which scheme a
brand advertises.

`view-stop` needs `stopID` (an agency-qualified stop id such as `1_75403`, percent-encoded).

### `add-region`

Adds a **custom region** — a real, named, persisted region, the same thing the link creates on iOS — and
makes it current. Ampersands inside a nested URL must be percent-encoded as `%26`.

| Parameter | Required | Becomes |
| --- | --- | --- |
| `name` | **yes** | The region's display name, as shown in the region picker |
| `oba-url` | **yes** | `Region.obaBaseUrl` — the OBA REST server |
| `otp-url` | no | `Region.otpBaseUrl` — the trip-planning server |
| `sidecar-url` | no | `Region.sidecarBaseUrl` |
| `umami-url` / `umami-id` | no | `Region.umamiAnalytics` |
| `region-id` | no | `Region.sidecarRegionId` — the id the **sidecar** knows this deployment by |

`region-id` is what the sidecar itself emits on the links it generates
([obacloud#1040](https://github.com/OneBusAway/obacloud/pull/1040)). It is optional on both ends: a link
without it (or with a value that isn't a number) is accepted unchanged, it just leaves the region unable
to reach the sidecar's region-scoped endpoints. See "Two ids" below.

A custom region behaves like a directory region with four deliberate differences:

- **It survives regions-directory refreshes.** `RegionDao.replaceAll` deletes only `custom = 0` rows, so
  a refresh can't take the rider's own region with it, and `RegionCache.loadRegions` unions custom
  regions back into every result.
- **It is never auto-selected, and never auto-*replaced*.** It carries no bounds, so `getClosestRegion`
  can't measure a distance to it; and `resolveRegionStatus` leaves a current custom region alone
  regardless of what's nearest, because following an `add-region` link is an explicit choice that
  auto-selection must not silently undo.
- **Its id is negative** (counting down from `-2`), so it can't collide with a directory id (those are
  `>= 0`) or with the `-1` "no region" sentinel in the region-id preference. Ids are never reused, so a
  stale reference resolves to nothing rather than to somebody else's server. This stays true even when
  the link supplies `region-id` — see below.
- **It may carry two ids.** `Region.id` is ours; `Region.sidecarRegionId` is the server's.

Re-sending the same `oba-url` **updates that region in place** rather than adding a duplicate.

#### Two ids

`Region.id` is a primary key we assign, and for a custom region it is a locally-invented negative number
the sidecar has never heard of. That id is not inert — it is formatted straight into every region-scoped
sidecar URL (`…/api/v2/regions/{id}/push_registrations`, `…/api/v2/regions/{id}/alarms`, and the v1
weather / studies / alerts endpoints), so before
[#2165](https://github.com/OneBusAway/onebusaway-android/issues/2165) a deep-link-added region addressed
all of them with an id the server would 404. Service-alert push registration and arrival reminders could
never work for one.

`region-id` fixes that, and is kept as a **separate** field (`Region.sidecarRegionId`) rather than
adopted as `Region.id`:

- Every sidecar call reads `Region.sidecarId` (`sidecarRegionId ?: id`). A directory region has no
  override and needs none — our primary key *is* the directory's id.
- The local id stays negative, so the id-space invariant above survives intact. Adopting the server's id
  as the primary key would put a custom row back into the directory's space, where the next directory
  refresh carrying the same id collides with it: `RegionDao.replaceAll` deletes only `custom = 0` rows,
  so the custom row survives the delete and the directory insert lands on top of it.

This diverges from OneBusAway for iOS ([#1234](https://github.com/OneBusAway/onebusaway-ios/pull/1234)),
which uses the supplied id directly. The two ids genuinely mean different things — our primary key vs.
the sidecar's name for the same deployment — and conflating them is what created this bug.

Capability flags (`supportsObaDiscoveryApis`, `supportsObaRealtimeApis`) are set true because
`RegionUtils.isRegionUsable` requires them — they are *declarations*, not observations: nothing has
probed the server. If it doesn't serve those APIs, requests fail visibly like any unreachable server.
`contactEmail` is left empty rather than filled with a placeholder (iOS hardcodes
`example@example.com`), which correctly hides the "email a problem report" option instead of mailing a
made-up address.

Once added, a custom region appears in the region picker like any other. **Long-press it in the
forced-choice picker (`RegionPickerHost`) to remove it** — the way back out of a link you regret.
Removing the region you're currently on re-resolves as if none had been set.

Note the Settings → Agencies region list is a separate surface (`ui/regions/`), whose `RegionItem`
doesn't carry the `custom` flag, so it lists custom regions without marking them or offering removal.
Plumbing the flag through it is worth doing and is not done here.

The pre-existing Settings → Advanced custom-API-URL preferences are a separate, unchanged mechanism (see
[`CUSTOM_SERVERS.md`](CUSTOM_SERVERS.md)); a custom region supersedes them, since applying any region
clears the custom OBA URL preference.

#### The link is confirmed, not applied

The filter is `BROWSABLE`, so **any web page can fire an `add-region` link at the app**, and accepting one
repoints every transit request the app makes. `HomeActivity` therefore hands the parsed request to
`AddRegionViewModel`, which stages it and writes nothing until the rider accepts a dialog naming the
servers involved ([#2030](https://github.com/OneBusAway/onebusaway-android/issues/2030)). Declining
writes nothing at all.

This is a **deliberate divergence from iOS**, which applies the region with no prompt. The dialog leads
with the server URLs rather than the region name, because the name is attacker-supplied text and says
nothing about where the data would come from.

#### Breaking change: `name` is now required

Before this, `onebusaway://add-region?oba-url=…` (no `name`) set a pair of API-URL *preferences* and
created no region. That link **no longer resolves at all** — matching iOS, which requires `name`. A
nameless region has nothing to show in the picker, so there is no sensible region to build from it.
Links in the wild that omit `name` need one added.

It still navigates nowhere (`IntentRouteMapper` returns `RouteDecision.None`): the app opens on its usual
home screen, the map, with the new region active.

## Web links (Android App Links)

`https://` links on the OneBusAway web/sidecar hosts — `onebusaway.co`, `www.onebusaway.co`,
`sidecar.onebusaway.org` — mirroring the iOS app's associated domains. Only the trip endpoint is a
deep link:

```text
https://<host>/regions/{regionID}/stops/{stopID}/trips?trip_id=…&service_date=…&stop_sequence=…
```

The filter lives in `onebusaway-android/src/oba/AndroidManifest.xml`, the OBA brand only. These hosts
belong to the OneBusAway deployment, and only the brand that owns a host can verify it — App Links
verification matches the *installed* app's signing certificate against the host's `assetlinks.json`.
`ExternalDeepLinks.WEB_HOSTS` is likewise a single OBA-owned set, not a per-brand value: giving a brand
web links of its own means both a flavor manifest and a brand-specific host set, which is a feature to
design rather than a config knob, so it isn't half-wired.

> **Server-side step still outstanding:** `android:autoVerify="true"` only takes effect once each of
> `onebusaway.co`, `www.onebusaway.co`, and `sidecar.onebusaway.org` serves `/.well-known/assetlinks.json`
> listing the app's package name (`com.joulespersecond.seattlebusbot`) and its release
> signing-certificate SHA-256 fingerprint. See
> [Verify Android App Links](https://developer.android.com/training/app-links/verify-android-applinks).
>
> **Until that lands, the filter is not inert.** Verification failure means different things by API
> level, and `minSdk` is 23:
>
> - **API 31+** — an unverified domain is not offered to the app at all, so the link opens in the
>   browser exactly as before. No user-visible change.
> - **API 23–30** — there is no such gating. The app still *matches* the filter, so an unverified
>   `onebusaway.co` link falls into ordinary intent disambiguation: tapping one **may raise** the
>   browser-or-OneBusAway chooser rather than going straight to the browser. It won't if the rider
>   already has a default handler set for those links — a default, once chosen, is respected. Either
>   way it's a behaviour change for those users, and it persists until the `assetlinks.json` files are
>   live.
>
> The filter can be exercised directly with `adb` (below) regardless of verification state.

### Links the filter claims but the parser can't route

An intent-filter is necessarily a *superset* of `ExternalDeepLinks.parse`: it cannot inspect the query
string (so it can't require `trip_id`), and `pathPattern` is a `PATTERN_SIMPLE_GLOB` whose `.*` spans
`/` (so it can't require exactly one path segment per wildcard — `pathAdvancedPattern` can, but it is
API 31+). So the app can be launched for a `onebusaway.co` URL it has no screen for.

`ExternalDeepLinks.isUnhandledWebLink` identifies those, and `HomeActivity.applyIntentSideEffects` hands
them back to the browser via `ExternalIntents.openInBrowser` — which resolves an explicit browser
package first, since a plain `ACTION_VIEW` would match our own filter again and bounce straight back.
Without this, a trimmed or unrecognized link would silently strand the user on the map instead of
opening the page they tapped.

## What's deliberately *not* supported

- **Stop-only web paths** (`/regions/{id}/stops/{id}` with no `/trips`) are not deep links, matching
  iOS; they fall through to the browser.
- **Parameters no Android screen consumes** are recognized and ignored rather than required, so any
  link iOS accepts is accepted here: `regionID` (iOS parses it but also only ever searches the current
  region — an incoming link for a stop outside the active region will fail to load), and the trip
  link's `service_date`, `stop_sequence`, `title`, `vehicle_id`, and `destination_stop_id`.
- **iOS `NSUserActivity` handoff / Siri / Spotlight** types have no Android equivalent.

## Testing

An `adb` VIEW intent exercises a filter regardless of App Links verification:

```bash
# Stop
adb shell am start -a android.intent.action.VIEW \
  -d 'onebusaway://view-stop?stopID=1_75403&regionID=1' \
  com.joulespersecond.seattlebusbot

# Custom region (raises the confirmation dialog; nothing is written until you accept)
adb shell am start -a android.intent.action.VIEW \
  -d 'onebusaway://add-region?name=Test%20Deployment&oba-url=https://api.example.com' \
  com.joulespersecond.seattlebusbot

# Custom region naming the sidecar's own id, so its region-scoped endpoints resolve
adb shell am start -a android.intent.action.VIEW \
  -d 'onebusaway://add-region?name=Puget%20Sound&oba-url=https://api.pugetsound.onebusaway.org&sidecar-url=https://sidecar.onebusaway.org&region-id=1' \
  com.joulespersecond.seattlebusbot

# Trip (keep the URL single-quoted so the shell doesn't split on &)
adb shell am start -a android.intent.action.VIEW \
  -d 'https://onebusaway.co/regions/1/stops/1_75403/trips?trip_id=1_18196913&service_date=1698307200.0&stop_sequence=5' \
  com.joulespersecond.seattlebusbot
```

## Implementation

- `ui/nav/ExternalDeepLinks.kt` — owns this whole vocabulary (schemes, hosts, path shape, parameter
  names) and parses a link into what it means; `parse` is pure and unit-tested in
  `ExternalDeepLinksTest`. `WEB_HOSTS` must match the manifest filter's hosts.
  `ExternalDeepLinksUriTest` (instrumented) covers the `Uri` decomposition — opaque URIs, case
  normalization — that a JVM test can't reach.
- `ui/nav/IntentRouteMapper.kt` — maps the parsed link to a NavHost route (`decide`, unit-tested in
  `IntentRouteMapperTest`).
- `ui/HomeActivity.kt` — runs the side effects a link implies (`applyIntentSideEffects`): staging an
  `add-region` request for confirmation, and the browser handoff for an unroutable web link.
- `region/CustomRegions.kt` — the custom-region model (`CustomRegionRequest`, id allocation, and the
  `Region` built from a link), unit-tested in `CustomRegionsTest`.
- `ui/home/AddRegionViewModel.kt` + `ui/home/AddRegionDialog.kt` — the consent gate, unit-tested in
  `AddRegionViewModelTest`.
- `ui/home/RegionPickerHost.kt` — the picker, including long-press-to-remove for custom regions.
- `util/ExternalIntents.kt` — `openInBrowser`, the explicit-package browser handoff (and the `<queries>`
  element in `src/main/AndroidManifest.xml` that makes browsers visible to it on API 30+).
- `ui/nav/DeepLinkUris.kt` — separate concern: the app's *internal* `content://` stop/route
  vocabulary, used by pinned launcher shortcuts and in-app launches.
