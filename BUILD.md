# Building the project

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

### Configuration Pelias API key for geocoding

If trip planning is active, you'll need to provide an API key for [geocode.earth](https://geocode.earth/).

Add the following to `onebusaway-android/gradle.properties`:

`Pelias_oba=XXXXXX`

...where `XXXXXX` is your API key. Note that the suffix of `_oba` can be changed to configure API keys for other build flavors.

### Release builds

To build a release build, you need to create a `gradle.properties` file that points to a `secure.properties` file, and a `secure.properties` file that points to your keystore and alias.

The `gradle.properties` file is located in the `onebusaway-android` directory and has the contents:
```
secure.properties=<full_path_to_secure_properties_file>
```

The `secure.properties` file (in the location specified in gradle.properties) has the contents:
```
key.store=<full_path_to_keystore_file>
key.alias=<key_alias_name>
key.storepassword=<your_keystore_password>
key.keypassword=<your_key_password>
```

Note that the paths in these files always use the Unix path separator `/`, even on Windows. If you use the Windows path separator `\` you will get the error `No value has been specified for property 'signingConfig.keyAlias'.`

Then, to build all flavors run:

`gradlew assembleRelease`

(If you want to assemble just the Google variant, use `gradlew assembleObaGoogleRelease`, and for Amazon Fire Phone-only use `gradlew assembleObaAmazonRelease`)

### Updating the Amazon Maps API library

Occasionally Amazon will likely release updates to their `amazon-maps-api-v2` library.  These artifacts aren't currently hosted on Maven Central or Jcenter.  As a result, when they release an update, we need to update our bundled Maven repo with the new artifact.  The steps to do this are:

1. Download updated Amazon Maps API `aar` and `pom` files
1. Download [Apache Maven](https://maven.apache.org/download.cgi) & unzip Apache Maven (installation not required)
1. Run following command, replacing appropriate paths:
  `path-to-bin-folder-of-maven/mvn install:install-file -Dfile=path-to-amazon-files/amazon-maps-api-v2.aar -DpomFile=path-to-amazon-files/amazon-maps-api-v2.pom -DlocalRepositoryPath=path-to-git-repo/.m2/repository`