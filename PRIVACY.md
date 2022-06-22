# Permissions explained

In order to support certain features in OneBusAway, we need to request various permissions to access information on your device.  Below is an explanation of why each of these is needed:

* **Location** - Used to access the GPS or network (Wi-Fi, cell tower) location of your device so we can show your location on the map and show bus stops near you. If you enroll in research studies (opt-in - see example study [here](https://digitalcommons.usf.edu/cutr_nctr/13/)), we may ask you for access to background location information and physical activity information. Example prompts in the app with the location use disclosures are shown in this video - https://www.youtube.com/watch?v=QOZt1iHK11g.
* **Phone status and identify** - Used to create an anonymous ID for analytics purposes - we send a hashed version of the phone's unique ID (IMEI/MEID) to the server to determine unique users
* **Storage** - Used to check for the presence of an SD card (or internal storage) so you can save or restore your favorite starred stops
* **Run at startup** & **Start the trip service** - Used to schedule your reminders to alert you when the bus is approaching
* **Prevent phone from sleeping** - Used to make sure that your phone doesn't go to sleep while your reminder alarms are being scheduled with the Android operating system (otherwise your reminders wouldn't work!)
* **View network connections** - Used to create more descriptive error messages if there is a problem with loading stops or arrivals (e.g., do you have airplane mode on?)
* **Install shortcuts** - Used to create icons on your home screen for a specific stop, so you can tap on that stop icon from your home screen and immediately see arrivals 
* **Control vibration** - Used to vibrate your phone when a reminder is triggered

# Privacy policy

See the [OneBusAway website Privacy Policy](https://onebusaway.org/privacy/) for details on OneBusAway as a whole.

OneBusAway uses the Google Maps Android SDK and Firebase SDK. Here are the relevant data disclosures for those app components:

* Google Maps Android SDK - https://developers.google.com/maps/documentation/android-sdk/play-data-disclosure
* Google Firebase SDK - https://firebase.google.com/docs/android/play-data-disclosure
