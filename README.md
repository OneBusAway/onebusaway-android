# SeattleBusBot / OneBusAway for Android

SeattleBusBot is the official native Android application for OneBusAway.

SeattleBusBot was the old name of the app -- it's now called "OneBusAway for Android" or, when speaking about the app itself, just "OneBusAway". It's listed in the Android Market as OneBusAway.

OneBusAway for Android provides:

1. Real-time bus arrival information for public transit
2. A browse-able map of nearby stops
3. The ability to search for nearby stops or routes

OneBusAway for Android automatically keeps track of your most used stops and routes, and allows you to put shortcuts on your phone's Main Menu for any stop or route you choose.

It's available on the Android Market: http://market.android.com/details?id=com.joulespersecond.seattlebusbot

The issues are still being tracked at Google Code: http://code.google.com/p/seattle-bus-bot/

## Build Setup

OneBusAway requires ActionBarSherlock: http://actionbarsherlock.com/

Luckily, the version that we use is already included for you in this repository. You just need to
import the SeattleBusBot/ActionBarSherlock project as an existing project. If you have trouble 
building, make sure you have the latest version of the Android SDK, Android ADT. It never hurts to
restart Eclipse and clean the project. If you still have trouble building, 
let me know at paulcwatts@gmail.com.

## Troubleshooting

### I don't get any map tiles!

The Google Maps API requires an API key that's tied to your keystore. If the API key doesn't match,
there won't be any map files. If you're using the develop branch, you'll want to download this 
and replace your existing debug keystore:

https://github.com/downloads/paulcwatts/onebusaway-android/seattlebusbot3.debug.keystore

This will work for develop. If you are using the master branch, that uses the production key generated
from my private keystore. You'll want to use a different key. Luckily you can easily modify 
one file to add your own API key (or use the debug key): 

https://github.com/paulcwatts/onebusaway-android/blob/master/res/values/apiKey.xml


