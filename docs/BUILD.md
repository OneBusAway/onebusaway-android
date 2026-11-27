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

You'll almost always want **`obaGoogleDebug`**, and the instructions below assume it. To build
another brand, see [Rebranding](REBRANDING.md).

## Prerequisites

1. Clone this repository.
1. Install a **JDK 17 or newer** to run Gradle (CI uses 21). You can skip this if you only build
   from Android Studio, which bundles one.
1. Install the Android SDK platform for the `compileSdk` level in
   [`gradle/libs.versions.toml`](../gradle/libs.versions.toml). Android Studio offers to install it
   when you first sync the project.

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

## Before you open a pull request

See the [Contributing Guide](../.github/CONTRIBUTING.md#before-you-open-a-pull-request) for the
checks to run locally and what CI enforces.
