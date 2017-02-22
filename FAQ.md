# Frequently Asked Questions (FAQ)

###I did a backup via "Settings->Save to storage" - where is it located on my SD card?

 You might need to do a little hunting to find the backup using a File Manager app, or connect the device to your computer and browse the folder structure.  It should be under the folder `OBA Backups`, with the file name `db.backup`.  This could be on the SD card on your device, or on internal storage on your device, depending on your Android version and device manufacturer (it's complicated).

 Note that you'll need to put it in the same folder on a new device to be able to "Restore backup" in the app.

###The blue dot on the map for my location isn't accurate - it's a few blocks away from my real position

Location information comes directly from your mobile device.  The best way to try and fix this is to go to your Android system settings and then "Location,", and make sure that "Mode" is set to "High accuracy (GPS and networks)".  After checking/changing this, you might also want to turn your device off and back on again.

###I'm tapping on the star next to the route but I don't see anything in the Starred Stops list

Please be sure to tap on the star next to the stop name, not the route name (see below screenshot).  Right now tapping on the star next to the route name (or via the menu after tapping on the arrival time) stars the route, and pins it to the top of the sliding panel.  This allows you to quickly tap on stops on the map and see the arrival time for routes you care about.  I plan to add a separate “Starred routes” list, although I don’t have a specific timeline for this yet.  You can follow progress at https://github.com/OneBusAway/onebusaway-android/issues/354.

![image](https://cloud.githubusercontent.com/assets/928045/23220577/73101752-f8f0-11e6-828a-38da63996e01.png)

###Why does OneBusAway Android need photo and other permissions?

You can read all about the various permissions required by OneBusAway Android on the [Permissions explained](PERMISSIONS.md) page.
