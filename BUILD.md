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

To set up a release build, you need to create a `gradle.properties` file that points to a `secure.properties` file, and a `secure.properties` file that points to your keystore and alias.

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

Before doing each release build, you'll need to:
1. Bump `onebusaway-android/build.gradle` `versionCode` by 1 and set `versionName` to the appropriate next semantic version name. 
2. Check `onebusaway-android/src/main/res/values/strings.xml` element `main_help_whatsnew` to make sure that the latest changes we want to highlight for the user are entered there. After update, users see this in a dialog.

Then, to build all flavors run:

`gradlew assembleRelease`

(If you want to assemble just the Google variant, use `gradlew assembleObaGoogleRelease`, and for Amazon Fire Phone-only use `gradlew assembleObaAmazonRelease`)

The APK files will show up in the `onebusaway-android/build/outputs/apk` folder. `obaGoogleRelease-vx.y.z.apk` is the file that's uploaded to Google Play for release.

### Release testing protocol

Prior to uploading the app to Google Play, below is the release testing protocol. This effectively mirrors what a user would experience - installing the new release as an update to the existing release.

1. Uninstall any existing version of OBA Android
2. [Opt into beta testing](BETA_TESTING.md) if you haven't already
3. Download the [latest version available on Google Play](https://play.google.com/store/apps/details?id=com.joulespersecond.seattlebusbot)
4. Launch the app, approve location permissions, ignore tutorial in those popups. Tap on a bus stop and "star" it by tapping on star next to bus stop name. Go to "Starred stops" in the main menu to ensure it saved the stop. This saves something to the database to ensure that data isn't lost in the update.
5. Install the new APK over the existing APK as an update. I usually upload the new APK to Dropbox, then download to my device using "Export" from the Dropbox app, then using a file manager app to launch it by tapping on it. It will ask you if you want to install as an update, agree.
6. Openly the newly updated app. Go to "Starred stops" in the main menu and confirm that the stop you starred in the above step is still there.

### Tagging a release on GitHub

After testing a release, I create a tag locally by using Android Studio "Git->New tag", and I enter a name in the format of `vx.y.z` and a summary of changes (see the GitHub [Releases](https://github.com/OneBusAway/onebusaway-android/releases)) for examples).

I then push this tag to GitHub using:

```
git push origin vx.y.z
```

Then I create a new release on GitHub using https://github.com/OneBusAway/onebusaway-android/releases/new, and reference this tag. I also attach the compiled APKs. I'll also mark "This is a pre-release" because release go to the [OBA beta testing group](BETA_TESTING.md) first.

### Releasing to Google Play

After you've created the release on GitHub, head to the [Google Play developer console](https://developer.android.com/distribute/console) and follow these steps to publish a beta release:
1. Go to OneBusAway main app and then "Testing->Open testing"
2. Create a new release and upload the new `obaGoogleRelease-vx.y.z.apk` APK
3. Keep all the existing APKs that are already there as "Included", except the current production release. This makes sure that everyone with older version Android devices can still download the app. So only the current production release should be under "Not Included".
4. Type in the version number `vx.y.z` in the release name, and you can typically copy the release notes from a previous release and edit accordingly for whatever you want users to see in the "What's new" section of Google Play (typically this should match the in-app string `main_help_whatsnew` mentioned above).
5. Review and publish release to beta testing group (I usually immediately push to all beta testing users)
6. Announce beta release on the [onebusaway-developers Google Group](https://groups.google.com/g/onebusaway-developers).

After the beta has soaked for at least a week or two and there have been no complaints (I typically check for crashes using Google Play Developer console for this version too), you can push the beta to production:
1. Go to OneBusAway main app and then "Testing->Open testing". You should see your release listed there
2. Click on "Promote release", and then "Production". I usually start at 5% or 10% depending on the predicted likelihood of introducing new bugs (e.g., from invasive changes or lots of new code), and roll out over a week to two weeks, again depending on predicted likelihood of new bugs. To update rollout you'll need to look at "Production" "Releases" tab to see your release there, and click on "Manage rollout" and increase percentage. At 100% is full production rollout.

Hot fixes can be released straight to production (similar process as above, but starting on "Production" tab) but are only advised in emergency scenarios where unforeseen events lead to a large number of users experiencing adverse affects that a patch would relieve.

### Releasing to Amazon App Store

I haven't done a release to the Amazon App Store in a long time because I don't want to break the app for existing users. I haven't had a device to test Amazon Maps for a while, so for now the APK is available on GitHub for users with Amazon devices that are adventurous enough to use it.

### Updating the Amazon Maps API library

Occasionally Amazon will likely release updates to their `amazon-maps-api-v2` library.  These artifacts aren't currently hosted on Maven Central or Jcenter.  As a result, when they release an update, we need to update our bundled Maven repo with the new artifact.  The steps to do this are:

1. Download updated Amazon Maps API `aar` and `pom` files
1. Download [Apache Maven](https://maven.apache.org/download.cgi) & unzip Apache Maven (installation not required)
1. Run following command, replacing appropriate paths:
  `path-to-bin-folder-of-maven/mvn install:install-file -Dfile=path-to-amazon-files/amazon-maps-api-v2.aar -DpomFile=path-to-amazon-files/amazon-maps-api-v2.pom -DlocalRepositoryPath=path-to-git-repo/.m2/repository`
