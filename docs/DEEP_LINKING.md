# Deep Linking

OneBusAway for Android answers the same deep links as
[OneBusAway for iOS](https://developer.onebusaway.org/projects/ios), so a link shared from either app
opens the same screen on the other ([#2027](https://github.com/OneBusAway/onebusaway-android/issues/2027)).

Two families of link, both handled by `HomeActivity` (the app's single Activity):

| Link | Opens |
| --- | --- |
| `onebusaway://view-stop?stopID=1_75403&regionID=1` | That stop's arrivals |
| `onebusaway://add-region?name=…&oba-url=…&otp-url=…` | Applies custom API URLs, stays on the map |
| `https://onebusaway.co/regions/1/stops/1_75403/trips?trip_id=1_18196913&service_date=1698307200.0&stop_sequence=5` | That trip's details, scrolled to the stop in the path |

## Custom-scheme links

Every brand answers to the cross-platform `onebusaway://` scheme **and** to its own scheme
(`kiedybus://` for KiedyBus). A brand's scheme is declared once, as the `deepLinkScheme` manifest
placeholder — defaulted to `onebusaway` in `defaultConfig` and overridden in
`onebusaway-android/flavors/<brand>.gradle`. `BuildConfig.DEEP_LINK_SCHEME` (and through it
`ExternalDeepLinks.APP_SCHEMES`) is derived from that placeholder in `build.gradle.kts`, so the
manifest filter and the parser can't disagree about which scheme a brand advertises.

`view-stop` needs `stopID` (an agency-qualified stop id such as `1_75403`, percent-encoded).

`add-region` reads `oba-url` and `otp-url`; each is applied only if it validates. Ampersands inside a
nested URL must be percent-encoded as `%26`.

## Web links (Android App Links)

`https://` links on the OneBusAway web/sidecar hosts — `onebusaway.co`, `www.onebusaway.co`,
`sidecar.onebusaway.org` — mirroring the iOS app's associated domains. Only the trip endpoint is a
deep link:

```text
https://<host>/regions/{regionID}/stops/{stopID}/trips?trip_id=…&service_date=…&stop_sequence=…
```

The filter lives in `onebusaway-android/src/oba/AndroidManifest.xml` (the OBA brand only — a
white-label brand can't verify hosts it doesn't own; a brand with its own web host should add its own
flavor manifest with the same shape).

> **Server-side step still outstanding:** `android:autoVerify="true"` only takes effect once each host
> serves `/.well-known/assetlinks.json` listing the app's package name
> (`com.joulespersecond.seattlebusbot`) and its release signing-certificate SHA-256 fingerprint. Until
> then these links keep opening in the browser — nothing regresses, and the filter can still be
> exercised directly with `adb` (below). See
> [Verify Android App Links](https://developer.android.com/training/app-links/verify-android-applinks).

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
- `ui/nav/IntentRouteMapper.kt` — maps the parsed link to a NavHost route (`decide`, unit-tested in
  `IntentRouteMapperTest`).
- `ui/HomeActivity.kt` — runs the side effect `add-region` implies (`applyIntentSideEffects`).
- `ui/nav/DeepLinkUris.kt` — separate concern: the app's *internal* `content://` stop/route
  vocabulary, used by pinned launcher shortcuts and in-app launches.
