# Building the project

This page covers building and running the app for development. Shipping a release is a separate
process — see [RELEASING.md](RELEASING.md).

## Build variants

We use [Gradle product flavors](https://developer.android.com/build/build-variants) in two
dimensions.

**Platform** — which map SDK the app is built on:

* **google** = Google Maps, the version published on Google Play (the default)
* **maplibre** = [MapLibre](https://maplibre.org/) with OpenFreeMap tiles, no Google Maps dependency

**Brand** — the name, look, and feel of the app:

* **oba** = the original OneBusAway brand, the one that ships
* **agencyX** = a sample rebrand for a fictitious multi-region "Agency X"
* **agencyY** = a sample rebrand for a fictitious fixed-region "Agency Y"
* **kiedybus** = KiedyBus, a third-party brand (Poland)

Together with the debug/release build types, that makes 4 × 2 × 2 = 16 variants. You'll almost
always want **`obaGoogleDebug`**, and the instructions below assume it. The brands differ only in
resources, manifest, and application ID; see the [Rebranding page](REBRANDING.md) to build one of the
others.

## Prerequisites

1. Clone this repository.
1. Install a **JDK 17 or newer** to run Gradle (CI uses Temurin 21). Android Studio ships with a
   suitable JDK, so you can skip this if you only build from the IDE. The app itself targets Java 17
   bytecode, set by `jdk` in [`gradle/libs.versions.toml`](../gradle/libs.versions.toml).
1. Install the Android SDK platform for the `compileSdk` level in
   [`gradle/libs.versions.toml`](../gradle/libs.versions.toml). Android Studio's SDK Manager offers it
   when you first sync the project.

Every dependency, plugin, and SDK level is declared in `gradle/libs.versions.toml`, so you don't need
to install anything else by hand.

## Building in Android Studio

1. Install and run the latest stable [Android Studio](https://developer.android.com/studio).
1. Choose **File → Open** and select the root of this repository. Let Gradle sync finish.
1. Connect an Android device with
   [USB debugging enabled](https://developer.android.com/studio/run/device), or create a virtual
   device in **Tools → Device Manager**.
1. In the **Build Variants** window, choose **obaGoogleDebug**.
1. Click **Run** (the green play button).

## Building from the command line

1. Set `JAVA_HOME` to your JDK, and `ANDROID_HOME` to your Android SDK. You can also put the SDK
   location in a `local.properties` file at the project root as `sdk.dir=/path/to/sdk`. Android
   Studio writes that file for you.
1. Build the app and install it on a connected device:

   ```bash
   ./gradlew installObaGoogleDebug
   ```

1. Start it, or tap its icon on the device:

   ```bash
   adb shell am start -n com.joulespersecond.seattlebusbot/org.onebusaway.android.ui.HomeActivity
   ```

(The application ID `com.joulespersecond.seattlebusbot` is historical and has to stay that way for
Google Play. The code's namespace is `org.onebusaway.android`.)

## Pelias API key for geocoding

Trip planning uses [geocode.earth](https://geocode.earth/) (Pelias) to search for addresses. Without
a key the app still builds and runs, but address search in the trip planner won't work. To enable it,
add this line to `onebusaway-android/gradle.properties`. Create the file if it doesn't exist; it's
gitignored, so your key stays local.

```
Pelias_oba=XXXXXX
```

...where `XXXXXX` is your API key. The suffix names the brand, so `Pelias_agencyX` sets the key for
the `agencyX` flavor.

## Checking your work

These are the commands you'll use before opening a pull request. The
[Contributing Guide](../.github/CONTRIBUTING.md#before-you-open-a-pull-request) explains what CI
enforces and why.

| Command | What it does |
|---|---|
| `./gradlew spotlessApply` | Formats your changes (ktlint and google-java-format) |
| `./gradlew test check -x lint -PwarningsAsErrors=true` | JVM unit tests, formatting check, and compile — with warnings treated as errors, as CI does |
| `./gradlew connectedObaGoogleDebugAndroidTest` | Instrumented tests; needs a connected device or emulator |
| `./gradlew :onebusaway-android:lintObaGoogleDebug -PwarningsAsErrors=true` | Android Lint (slow, ~5–15 minutes; CI runs it nightly rather than on PRs) |

By default `test` and `check` run only the `oba` brand's tests. Pass `-PbuildAllBrands=true` to run
all four brands.

## Release builds

Release signing, versioning, and publishing to Google Play are covered in
[RELEASING.md](RELEASING.md). That includes building a signed release on your own machine.
