# Troubleshooting

Things not going well building the project?  See the known issues below and some solutions.  If you're a user of the app, check out our [FAQ](FAQ.md).

### Building the project takes forever (like, 4 minutes) - what's up?

In your `gradle.properties` file in the root of the project, add:

`org.gradle.jvmargs=-Xmx2048m`

This must be at least 2 Gb to take advantage of Dex In Process.  See [this post](https://medium.com/google-developers/faster-android-studio-builds-with-dex-in-process-5988ed8aa37e) for more info.

### When running the project, I get a NullPointerException in `BaseMapFragment`, related to `mMap`

You're most likely trying to run the `obaAmazon` build variant on an Google Android device, or the `obaGoogle` build flavor on an Amazon device.

To build the version of OneBusAway on Google Play, you'll want to select the `obaGoogleDebug` build variant.

Steps to set the build variant:

* In Android Studio, you'll see a "Build Variant" button on the very left side of the screen, collapsed in the dock.  Click on this, and select either `obaGoogleDebug` for Google devices, or `obaAmazonDebug` for Amazon devices:
* From the command line, run `gradlew installObaGoogleDebug` for Google devices, or `gradlew installObaAmazonDebug` for Amazon devices.

![Android Studio build variants](https://02977090730444177945.googlegroups.com/attach/16c2dec220d69a/OBAAndroidGradleBuildFlavors.png?part=0.1&view=1&vt=ANaJVrEAdC5btUPS80q3eA4EO9z9asAZW7oRvJZMs5K8bZg0CCgIR8OwqvBR_21S58M2rbK7UVMbUc6QplQKuUyr5OwxLTu10NcK6R5XH3aKjktiU0cBONY)

See the documentation at the top of the readme for more information on building via Android Studio or the command line.

### When running the project I get prompted to install Amazon Maps.  I already have Google Maps installed.  What's going on?

This is likely due to running the `obaAmazon` build variant on an Google Android device.  See the [top troubleshooting question](https://github.com/OneBusAway/onebusaway-android#when-running-the-project-i-get-a-nullpointerexception-in-basemapfragment-related-to-mmap), and make sure you select the `obaGoogleDebug` build variant.

### When running the project I get prompted to install Google Play Services.  I have an Amazon Fire Phone that doesn't have Google Play Services.  What's going on?

This is likely due to running the `obaGoogle` build variant on an Amazon Fire Phone.  See the [top troubleshooting question](https://github.com/OneBusAway/onebusaway-android#when-running-the-project-i-get-a-nullpointerexception-in-basemapfragment-related-to-mmap), and make sure you select the `obaAmazonDebug` build variant.

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
	* Google Repository

### Android Studio or Gradle can't find my Android SDK, or the API Levels that I have installed

Make sure that you're consistently using the same Android SDK throughout Android Studio and your environmental variables.
Android Studio comes bundled with an Android SDK, and can get confused if you're pointing to this SDK within Android Studio
but have your environmental variables pointed elsewhere. Click "File->Project Structure", and then under "Android SDK"
make sure you "Android SDK Location" is the correct location of your Android SDK.

Also, make sure you've set the `ANDROID_HOME` environmental variable to your Android SDK location and
the `JAVA_HOME` environmental variables to point to your JDK folder.