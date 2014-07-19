# OneBusAway for Android [![Build Status](https://travis-ci.org/OneBusAway/onebusaway-android.svg?branch=master)](https://travis-ci.org/OneBusAway/onebusaway-android)

This is the official Android app for OneBusAway!

OneBusAway for Android provides:

1. Real-time bus arrival information for public transit
2. A browse-able map of nearby stops
3. The ability to search for nearby stops or routes

OneBusAway for Android automatically keeps track of your most used stops and routes, and allows you to put shortcuts on your phone's Main Menu for any stop or route you choose.

It's available on the [Play Store](https://play.google.com/store/apps/details?id=com.joulespersecond.seattlebusbot).

## Build Setup

### Prerequisites for both Android Studio and Gradle

1. Set the `JAVA_HOME` environmental variables to point to your JDK folder (e.g. `C:\Program Files\Java\jdk1.6.0_27`)

### Building in Android Studio

1. Download and install the latest version of [Android Studio](http://developer.android.com/sdk/installing/studio.html).
2. Run Android Studio (Windows users may need to `Run as administator` when installing Android SDK components).
3. At the welcome screen select `Import Project`, browse to the location of this repository and double-click it.
4. Open the Android SDK Manager (Tools->Andorid->SDK Manager) and under the currently used SDK version (see `compileSdkVersion` in [`onebusaway-android/build.gradle`](onebusaway-android/build.gradle)) add a checkmark next to `Google APIs` then select `Install n packages`. `n` may be 1 or more if other updates are available.
5. Connect a [debugging enabled](https://developer.android.com/tools/device.html) Android device to your computer or setup an Android Virtual Device (Tools->Andorid->AVD Manager).
6. Click the green play button (or Alt+Shift+F10) to build and run the project!

### Building from the command line using Gradle

1. Download and install the [Android SDK](http://developer.android.com/sdk/index.html). Make sure to install the Google APIs for your API level (e.g. 17), the Android SDK Build-tools version for your `buildToolsVersion` version, the Android Support Repository and the Google Repository.
2. Set the `ANDROID_HOME` environmental variable to your Android SDK location.
3. To build and push the app to the device, run `gradlew installDebug` from the command line at the root of the project
4. To start the app, run `adb shell am start -n com.joulespersecond.seattlebusbot/.HomeActivity` (alternately, you can manually start the app)

### Release builds

To build a release build, you need to create a `gradle.properties` file that points to a `secure.properties` file, and a `secure.properties` file that points to your keystore and alias. The `gradlew assembleRelease` command will prompt for your keystore passphrase.

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

### Contributing

We welcome contributions to the project! Please see our [Contributing Guide](https://github.com/OneBusAway/onebusaway-android/blob/master/CONTRIBUTING.md) for details, including Code Style Guidelines and Template.

## Troubleshooting

### When importing to Android Studio, I get an error "You are using an old, unsupported version of Gradle..."

If you're using Android Studio v0.4.2 or lower, when importing, please be sure to select the `settings.gradle` file in the root, **NOT** the project directory.
You will get the above error if you select the project directory / name of the project.

### I get build errors for the Android Support libraries or Google APIs

Open the Android SDK Manager and make sure the following are installed:
* Under Tools
	* Android SDK Tools
	* Android SDK Platform-tools
	* Android SDK Build-tools
* Under the currently used SDK version (see `compileSdkVersion` in [`onebusaway-android/build.gradle`](onebusaway-android/build.gradle))
	* SDK Platform
	* Google APIs
* Extras
	* Android Support Repository
	* Android Support Library
	* Google Play services

### Android Studio or Gradle can't find my Android SDK, or the API Levels that I have installed

Make sure that you're consistently using the same Android SDK throughout Android Studio and your environmental variables.
Android Studio comes bundled with an Android SDK, and can get confused if you're pointing to this SDK within Android Studio
but have your environmental variables pointed elsewhere. Click "File->Project Structure", and then under "Android SDK"
make sure you "Android SDK Location" is the correct location of your Android SDK.

Also, make sure you've set the `ANDROID_HOME` environmental variable to your Android SDK location and
the `JAVA_HOME` environmental variables to point to your JDK folder.

## OneBusAway Project

Want to learn more about the OneBusAway project? [Read up on the entire Application Suite](https://github.com/OneBusAway/onebusaway-application-modules/wiki) and/or [learn more about the mobile apps](https://github.com/OneBusAway/onebusaway-application-modules/wiki/Mobile-App-Design-Considerations).