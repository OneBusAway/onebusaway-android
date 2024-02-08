# Troubleshooting

Things not going well building the project?  See the known issues below and some solutions.  If you're a user of the app, check out our [FAQ](FAQ.md).

### Building the project takes forever (like, 4 minutes) - what's up?

In your `gradle.properties` file in the root of the project, add:

`org.gradle.jvmargs=-Xmx2048m`

This must be at least 2 Gb to take advantage of Dex In Process.  See [this post](https://medium.com/google-developers/faster-android-studio-builds-with-dex-in-process-5988ed8aa37e) for more info.

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