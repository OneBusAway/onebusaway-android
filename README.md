# OneBusAway for Android

This is the official Android app for OneBusAway!

OneBusAway for Android provides:

1. Real-time bus arrival information for public transit
2. A browse-able map of nearby stops
3. The ability to search for nearby stops or routes

OneBusAway for Android automatically keeps track of your most used stops and routes, and allows you to put shortcuts on your phone's Main Menu for any stop or route you choose.

It's available on the Play Store: https://play.google.com/store/apps/details?id=com.joulespersecond.seattlebusbot

## Build Setup

### Building in Eclipse

OneBusAway requires ActionBarSherlock: http://actionbarsherlock.com/

Luckily, the version that we use is already included for you in this repository. You just need to
import the SeattleBusBot/ActionBarSherlock project as an existing project. If you have trouble 
building, make sure you have the latest version of the Android SDK and Android ADT. It never hurts to
restart Eclipse and clean the project. If you still have trouble building, 
let me know at paulcwatts@gmail.com.

### Building in Ant

This should just be as simple as using <code>ant debug</code> in the project root path.

## Troubleshooting

### I don't get any map tiles!

The Google Maps API requires an API key that's tied to your keystore. If the API key doesn't match,
there won't be any map files. If you're using the develop branch, you'll want to download this 
and replace your existing debug keystore:

https://github.com/downloads/paulcwatts/onebusaway-android/seattlebusbot3.debug.keystore

This will work for develop. If you are using the master branch, that uses the production key generated
from my private keystore. You'll want to use a different key. Luckily you can easily modify 
one file to add your own API key (or use the debug key): 

https://github.com/paulcwatts/onebusaway-android/blob/master/res/values/maps_api_key.xml

## OneBusAway Project

Want to learn more about the OneBusAway project? Read up on the entire Application Suite here:

https://github.com/OneBusAway/onebusaway-application-modules/wiki

You can also learn more about the mobile apps here:

https://github.com/OneBusAway/onebusaway-application-modules/wiki/Mobile-App-Design-Considerations

