# OneBusAway for Android

This is the official Android app for OneBusAway!

OneBusAway for Android provides:

1. Real-time bus arrival information for public transit
2. A browse-able map of nearby stops
3. The ability to search for nearby stops or routes

OneBusAway for Android automatically keeps track of your most used stops and routes, and allows you to put shortcuts on your phone's Main Menu for any stop or route you choose.

It's available on the Play Store: https://play.google.com/store/apps/details?id=com.joulespersecond.seattlebusbot

## Build Setup

### Prerequisites for both Android Studio and Gradle

1. Download and install the [Android SDK](http://developer.android.com/sdk/index.html).  Make sure to install the Google APIs for your API level (e.g., 17), and the Android Support Repository and Google Repository.
2. Set the "ANDROID_HOME" environmental variable to your Android SDK location.
3. Set the "JAVA_HOME" environmental variables to point to your JDK folder (e.g., "C:\Program Files\Java\jdk1.6.0_27")

### Building in Android Studio

1. Download and install the latest version of [Android Studio](http://developer.android.com/sdk/installing/studio.html).
2. In Android Studio, choose "Import Project" at the welcome screen.
3. Browse to the location of the project, and double-click on the **"settings.gradle"** file in the root (**NOT** the project directory).
4. Check "Use auto-import", and select "Use default gradle wrapper (recommended)".  Click "Ok".
5. Click the green play button (or 'Shift->F10') to run the project!

### Building from the command line using Gradle

1. To build and push the app to the device, run `gradlew installDebug` from the command line at the root of the project
2. To start the app, run `adb shell am start -n com.joulespersecond.seattlebusbot/.HomeActivity` (alternately, you can manually start the app)

## Troubleshooting

### When importing to Android Studio, I get an error "You are using an old, unsupported version of Gradle..."

When importing, please be sure to select the "settings.gradle" file in the root, **NOT** the project directory.
You will get the above error if you select the project directory / name of the project.

### I get build errors for the Android Support libraries or Google APIs

Open Android SDK Manager, and under the "Extras" category make sure you've installed both the
"Android Support Repository" (in addition to the "Android Support library") as well as the
 "Google Repository".  Also, make sure you have the Google API installed for the API level that you're working with in the "/build.gradle" file.

### Android Studio or Gradle can't find my Android SDK, or the API Levels that I have installed

Make sure that you're consistently using the same Android SDK throughout Android Studio and your environmental variables.
Android Studio comes bundled with an Android SDK, and can get confused if you're pointing to this SDK within Android Studio
but have your environmental variables pointed elsewhere.  Click "File->Project Structure", and then under "Android SDK"
make sure you "Android SDK Location" is the correct location of your Android SDK.

Also, make sure you've set the "ANDROID_HOME" environmental variable to your Android SDK location and
the "JAVA_HOME" environmental variables to point to your JDK folder.

## OneBusAway Project

Want to learn more about the OneBusAway project? Read up on the entire Application Suite here:

https://github.com/OneBusAway/onebusaway-application-modules/wiki

You can also learn more about the mobile apps here:

https://github.com/OneBusAway/onebusaway-application-modules/wiki/Mobile-App-Design-Considerations

