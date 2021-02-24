# Permissions explained

In order to support certain features in OneBusAway, we need to request various permissions to access information on your device.  Below is an explanation of why each of these is needed:

* **Location** - Used to access the GPS or network (Wi-Fi, cell tower) location of your device so we can show your location on the map and show bus stops near you
* **Phone status and identify** - Used to create an anonymous ID for analytics purposes - we send a hashed version of the phone's unique ID (IMEI/MEID) to the server to determine unique users
* **Storage** - Used to check for the presence of an SD card (or internal storage) so you can save or restore your favorite starred stops
* **Run at startup** & **Start the trip service** - Used to schedule your reminders to alert you when the bus is approaching
* **Prevent phone from sleeping** - Used to make sure that your phone doesn't go to sleep while your reminder alarms are being scheduled with the Android operating system (otherwise your reminders wouldn't work!)
* **View network connections** - Used to create more descriptive error messages if there is a problem with loading stops or arrivals (e.g., do you have airplane mode on?)
* **Install shortcuts** - Used to create icons on your home screen for a specific stop, so you can tap on that stop icon from your home screen and immediately see arrivals 
* **Control vibration** - Used to vibrate your phone when a reminder is triggered

