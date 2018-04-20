# OneBusAway for Android [![Build Status](https://travis-ci.org/OneBusAway/onebusaway-android.svg?branch=master)](https://travis-ci.org/OneBusAway/onebusaway-android) [![Join the OneBusAway chat](https://onebusaway.herokuapp.com/badge.svg)](https://onebusaway.herokuapp.com/)

This is the official Android / Fire Phone app for OneBusAway!

[![Google Play logo](http://www.android.com/images/brand/android_app_on_play_logo_large.png)](https://play.google.com/store/apps/details?id=com.joulespersecond.seattlebusbot)

[![Amazon App Store logo](https://images-na.ssl-images-amazon.com/images/G/01/mobile-apps/devportal2/res/images/amazon-underground-app-us-black.png)](http://www.amazon.com/gp/mas/dl/android?p=com.joulespersecond.seattlebusbot)

<image src="https://user-images.githubusercontent.com/928045/29891146-14ee4f44-8d98-11e7-999b-63f5d2ece916.gif" width="240" height="427" align=right />

OneBusAway for Android provides:

1. Real-time arrival/departure information for public transit
2. A browse-able map of nearby stops
3. A list of favorite bus stops
4. Reminders to notify you when your bus is arriving or departing
5. The ability to search for nearby stops or routes
6. Real-time multimodal trip planning, using real-time transit and bike share information (requires a regional [OpenTripPlanner](http://www.opentripplanner.org/) server)
6. Bike share map layer, which includes real-time availability information for floating bikes and bike rack capacity (requires a regional [OpenTripPlanner](http://www.opentripplanner.org/) server)
7. Issue reporting to any Open311-compliant issue management system (see [this page](ISSUE_REPORTING.md) for details)

OneBusAway for Android automatically keeps track of your most used stops and routes, and allows you to put shortcuts on your phone's home screen for any stop or route you choose.

## Alpha and Beta Testing

Get early access to new OneBusAway Android versions, and help us squash bugs! See our [Testing Guide](https://github.com/OneBusAway/onebusaway-android/blob/master/BETA_TESTING.md) for details.

### Testing Your Own OneBusAway/OpenTripPlanner servers

Did you just set up your own [OneBusAway](https://github.com/OneBusAway/onebusaway-application-modules/wiki) and/or [OpenTripPlanner](http://www.opentripplanner.org/) server?  You can test both in this app without compiling any Android code.  Just download the app from [Google Play](https://play.google.com/store/apps/details?id=com.joulespersecond.seattlebusbot), and see our [Custom Server Setup Guide](CUSTOM_SERVERS.md) for details.

## System Architecture

Curious what servers power certain features in OneBusAway Android?  Check out the [System Architecture page](SYSTEM_ARCHITECTURE.md).

## Build Setup

We use [Gradle build flavors](http://developer.android.com/tools/building/configuring-gradle.html#workBuildVariants) to enable a number of different build variants of OneBusAway Android.

We have two Gradle "platform" flavor dimensions:

* **google** = Normal Google Play release
* **amazon** = Amazon Fire Phone release

...and three Gradle "brand" flavor dimensions:

* **oba** = Original OneBusAway brand
* **agencyX** = A sample rebranded version of OneBusAway for a fictitious "Agency X"
* **agencyY** = A sample rebranded version of OneBusAway for a fictitious "Agency Y"

This results in a total of 2 * 3 = 6 core build variants.  Each of these core variants also has a debug/release build type - the end result is that you'll have 12 build variants to choose to build.

The below instructions assume you're going to be building for the **google** platform flavor and original **oba** brand by default (e.g., `obaGoogleDebug`), but
also mention how you would build/run the **amazon** flavor for the **oba** brand (e.g., `obaAmazonDebug`).  If you want more info about building the other brands, please see the [Rebranding OneBusAway Android page](https://github.com/OneBusAway/onebusaway-android/blob/master/REBRANDING.md).

### Prerequisites for both Android Studio and Gradle

1. Clone this repository
1. Install [Java Development Kit (JDK)](http://www.oracle.com/technetwork/java/javase/downloads/index.html)

### Building in Android Studio

1. Download, install, and run the latest version of [Android Studio](http://developer.android.com/sdk/installing/studio.html).
1. At the welcome screen select `Import Project`, browse to the location of this repository and select it then select Ok.
1. Open the Android SDK Manager (Tools->Android->SDK Manager) and add a checkmark for the necessary API level (see `compileSdkVersion` in [`onebusaway-android/build.gradle`](onebusaway-android/build.gradle)) then select OK.
1. Connect a [debugging enabled](https://developer.android.com/tools/device.html) Android device to your computer or setup an Android Virtual Device (Tools->Andorid->AVD Manager).
1. Open the "Build Variants" window (it appears as a vertical button on left side of workspace by default) & choose **obaGoogleDebug** to select the Google Play version, or **obaAmazonDebug** to select the Fire Phone.
1. Click the green play button (or Alt+Shift+F10) to build and run the project!

### Building from the command line using Gradle

1. Set the `JAVA_HOME` environmental variables to point to your JDK folder (e.g. `C:\Program Files\Java\jdk1.6.0_27`)
1. Download and install the [Android SDK](http://developer.android.com/sdk/index.html). Make sure to install the Google APIs for your API level (e.g. 17), the Android SDK Build-tools version for your `buildToolsVersion` version, the Android Support Repository and the Google Repository.
1. Set the `ANDROID_HOME` environmental variable to your Android SDK location.
1. To build and push the app to the device, run `gradlew installObaGoogleDebug` from the command line at the root of the project (or `gradlew installObaAmazonDebug` for Amazon build flavor)
1. To start the app, run `adb shell am start -n com.joulespersecond.seattlebusbot/org.onebusaway.android.ui.HomeActivity` (alternately, you can manually start the app)

### Release builds

To build a release build, you need to create a `gradle.properties` file that points to a `secure.properties` file, and a `secure.properties` file that points to your keystore and alias.

The `gradle.properties` file is located in the onebusaway-android directory and has the contents:
```
secure.properties=<full_path_to_secure_properties_file>
```

The `secure.properties` file (in the location specified in gradle.properties) has the contents:
```
key.store=<full_path_to_keystore_file>
key.alias=<key_alias_name>
```

Note that the paths in these files always use the Unix path separator `/`, even on Windows. If you use the Windows path separator `\` you will get the error `No value has been specified for property 'signingConfig.keyAlias'.`

Then, to build all flavors run:

`gradlew assembleRelease`

(If you want to assemble just the Google variant, use `gradlew assembleObaGoogleRelease`, and for Amazon Fire Phone-only use `gradlew assembleObaAmazonRelease`)

If Gradle is running as a daemon, you'll be prompted for the keystore/key password via a popup dialog box. If you're not running Gradle as a daemon, command will prompt for your passwords (See https://github.com/OneBusAway/onebusaway-android/issues/770).

If you want to force Gradle to not run as a daemon, use `gradlew assembleRelease -D org.gradle.daemon=false`.

### Updating the Amazon Maps API library

Occasionally Amazon will likely release updates to their `amazon-maps-api-v2` library.  These artifacts aren't currently hosted on Maven Central or Jcenter.  As a result, when they release an update, we need to update our bundled Maven repo with the new artifact.  The steps to do this are:

1. Download updated Amazon Maps API `aar` and `pom` files
1. Download [Apache Maven](https://maven.apache.org/download.cgi) & unzip Apache Maven (installation not required)
1. Run following command, replacing appropriate paths:
  `path-to-bin-folder-of-maven/mvn install:install-file -Dfile=path-to-amazon-files/amazon-maps-api-v2.aar -DpomFile=path-to-amazon-files/amazon-maps-api-v2.pom -DlocalRepositoryPath=path-to-git-repo/.m2/repository`

## Contributing

We welcome contributions to the project! Please see our [Contributing Guide](https://github.com/OneBusAway/onebusaway-android/blob/master/.github/CONTRIBUTING.md) for details, including Code Style Guidelines and Template.

## Deploying OneBusAway Android in Your City

There are two ways to deploy OneBusAway Android in your city:

1. **Join the OneBusAway [multi-region project](https://github.com/OneBusAway/onebusaway/wiki/Multi-Region)** - The easiest way to get started - simply set up your own OneBusAway server with your own transit data, and get added to the OneBusAway apps!  See [this page](https://github.com/OneBusAway/onebusaway/wiki/Multi-Region) for details.
2. **Deploy a rebranded version of OneBusAway Android as your own app on Google Play** - Requires a bit more maintenance, but it allows you to set up your own app on Google Play / Amazon App Store based on the OneBusAway Android source code. See [rebranding page](https://github.com/OneBusAway/onebusaway-android/blob/master/REBRANDING.md) for details.

## Permissions

In order to support certain features in OneBusAway, we need to request various permissions to access information on your device.  See an explanation of why each permission is needed [here](PERMISSIONS.md).

## Troubleshooting

Things not going well building the project?  See our [Troubleshooting](TROUBLESHOOTING.md) section.  If you're a user of the app, check out our [FAQ](FAQ.md).

## OneBusAway Project

Want to learn more about the OneBusAway project? [Read up on the entire Application Suite](https://github.com/OneBusAway/onebusaway-application-modules/wiki) and/or [learn more about the mobile apps](https://github.com/OneBusAway/onebusaway-application-modules/wiki/Mobile-App-Design-Considerations).
