# Travel Assistance Device (TAD) feature - Destination Alerts

**Usage**

1. View the Trip Status of a desired trip.
2. Long tap the desired destination stop.
3. Confirm that you want to start this trip.
4. TAD will be launched as a service in the background.
5. A real-time notification is shown at all times.
6. A second "Get Ready" notification is triggered when nearing the second
to last stop.
7. A final "Pull the cord" notification is triggered when nearing the
destination stop.

**How It Works**

A Service is created when the user begins a trip, this service listens
for location updates and passes the locations to its instance of 
TADServiceNavigationProvider each time. TADServiceProvider is responsible
for computing the statuses of the trips and issuing notifications/TTS
messages. Once the TADServiceNavProvider is completed, the TAD Service
will stop itself.

***GPS Logging***

When the BuildConfig "TAD_GPS_LOGGING" flag is set to true, the TAD 
Service will log all co-ordinates it receives during the trip and 30
seconds after the trip has ended. The log file is a CSV file written to
the "TADLog" folder on your external storage root directory. The filename
format is <TestID>-<Date/time of test>.csv. For example, "1-Thu, Aug 25 2016, 04:20 PM.csv".
The first line of the file includes the following information in the same order is going to be 
presented: The trip ID, the destination Stop ID, the latitude of the destination, the longitude
of the destination, stop ID of the Stop before the final Stop,the latitude of the
stop before the final stop, and the longitude of the stop before the last stop.
Starting from the second line, the first column contains the time in nanoseconds
since the application started, the second column contains the time in UTC, the 
third column contains the latitude, the fourth column contains the longitude,
the fifth column contains the altitude, the sixth column contains the speed,
the seventh column contains the Bearing, the eight column contains the accuracy,
the ninth column contains the satellites, the tenth column contains the provider.
                                  

***Testing***

Once a a test trip has been generated, drop the generated CSV file into
the resources folder for the androidTest build. Then, in the TADTest
file, a new test method can be created. In this method, a TADTrip object
can be instantiated with the CSV string passed into the constructor and
the runSimulation method called.

***(Potential) Idea Wish list***

- Favorite Trips: Allow person to bookmark a favorite TAD Trip. This 
would involve storing a combo of the starting stop, destination stop &
the trip Id. However, since Trip IDs are not persistent and are subject
to expiration, a way to uniquely identify trips is needed.

- Multi-Legged Trips: Maybe something for OTP, but have multi-bus
trips. TAD already supports it by passing in an array of segments.

- Configurable Alert Triggering Threshold: As of right now, the distance 
the person needs to be within from the second-to-last stop for the first 
notification to be fired is fixed. Allowing it be possible for user to
specify the threshold, or for the threshold to be dynamically calculated
based on certain factors such as distance between stops would be neat 
addition.

- Advanced Location Logging: Currently, the location logger is built into
TAD Service & transparently logs to a file when TAD is running. A toggle 
could be added in the Advanced Settings menu for the user to switch on and
off.