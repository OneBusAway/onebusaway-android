# Deep Linking

OneBusAway for Android answers the same deep links as
[OneBusAway for iOS](https://developer.onebusaway.org/projects/ios), so a link shared from either app
opens the same screen on the other ([#2027](https://github.com/OneBusAway/onebusaway-android/issues/2027)).

Two families of link, both handled by `HomeActivity` (the app's single Activity):

| Link | Opens |
| --- | --- |
| `onebusaway://view-stop?stopID=1_75403&regionID=1` | That stop's arrivals |
| `onebusaway://add-region?oba-url=…&otp-url=…` | Applies custom API URLs, stays on the map |
| `https://onebusaway.co/regions/1/stops/1_75403/trips?trip_id=1_18196913&service_date=1698307200.0&stop_sequence=5` | That trip's details, scrolled to the stop in the path |

## Custom-scheme links

Every brand answers to the cross-platform `onebusaway://` scheme **and** to its own scheme
(`kiedybus://` for KiedyBus). A brand's scheme is declared once, as the `deepLinkScheme` manifest
placeholder — defaulted to `onebusaway` in `defaultConfig` and overridden in
`onebusaway-android/flavors/<brand>.gradle`. `BuildConfig.DEEP_LINK_SCHEME` (and through it
`ExternalDeepLinks.APP_SCHEMES`) is derived from that placeholder by the `androidComponents.onVariants`
block in `build.gradle.kts`, so the manifest filter and the parser can't disagree about which scheme a
brand advertises.

`view-stop` needs `stopID` (an agency-qualified stop id such as `1_75403`, percent-encoded).

`add-region` reads `oba-url` and `otp-url`; each is applied only if it validates. Ampersands inside a
nested URL must be percent-encoded as `%26`.

> Note that "validates" here means *well-formed*, not *trusted*, and this filter is `BROWSABLE` — so any
> web page can repoint the app's API server with no confirmation. That predates this vocabulary (it was
> the old `SettingsActivity` VIEW filter) and is tracked in
> [#2030](https://github.com/OneBusAway/onebusaway-android/issues/2030).

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
> - **API 23–30** — there is no such gating. The app still *matches* the filter, so tapping a
>   `onebusaway.co` link raises the browser-or-OneBusAway **disambiguation chooser** instead of going
>   straight to the browser. That is a real behaviour change for those users, and it persists until the
>   `assetlinks.json` files are live.
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
- **`add-region`'s `name` / `sidecar-url` / `umami-url` / `umami-id` parameters** are ignored. Android's
  custom-server mechanism is a pair of API-URL preferences (see
  [`CUSTOM_SERVERS.md`](CUSTOM_SERVERS.md)), not a synthetic named region, so it has nowhere to put
  them. Full parity here needs a real custom-region model.
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
- `ui/HomeActivity.kt` — runs the side effects a link implies (`applyIntentSideEffects`): the
  `add-region` URL apply, and the browser handoff for an unroutable web link.
- `util/ExternalIntents.kt` — `openInBrowser`, the explicit-package browser handoff (and the `<queries>`
  element in `src/main/AndroidManifest.xml` that makes browsers visible to it on API 30+).
- `ui/nav/DeepLinkUris.kt` — separate concern: the app's *internal* `content://` stop/route
  vocabulary, used by pinned launcher shortcuts and in-app launches.
