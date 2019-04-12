# Real-time Navigation - Destination Alerts

**Usage**

1. View the Trip Status of a desired trip.
2. Long tap the desired destination stop.
3. Confirm that you want to start this trip.
4. Destination reminders will be launched as a service in the background.
5. A real-time notification is shown at all times.
6. A second "Get Ready" notification is triggered when nearing the second
to last stop.
7. A final "Pull the cord" notification is triggered when nearing the
destination stop.

**How It Works**

The NavigationService is started when the user begins a trip, this service listens
for location updates and passes the locations to its instance of 
NavigationServiceProvider each time. NavigationServiceProvider is responsible
for computing the statuses of the trips and issuing notifications/TTS
messages. Once the NavigationServiceProvider is completed, the NavigationService will stop itself.

***GPS Logging***

When the BuildConfig "NAV_GPS_LOGGING" flag is set to true, the NavigationService
 will log all coordinates it receives during the trip and 30
seconds after the trip has ended. The log file is a CSV file written to
the "ObaNavLog" folder on your internal storage root directory. The filename
format is <TestID>-<Date/time of test>.csv. For example, "1-Thu, Aug 25 2016, 04:20 PM.csv".

The first line of the file includes the following information in this order (delimited by commas): 

1. trip ID
1. destination Stop ID
1. latitude of the destination
1. longitude of the destination
1. stop ID of the Stop before the final Stop
1. latitude of the stop before the final stop
1. longitude of the stop before the last stop

Starting from the second line, here are the columns that contain the position data:

1. coordinateID - unique ID for each location fix in the file
1. getReadyFlag - true if the "Get Ready" alert has been announced to the user, false if it has not
1. pullTheCordNowFlag - true if the "Pull the Cord Now" alert has been announced to the user, false if it has not
1. the time in nanoseconds since the application started
1. the time in UTC
1. latitude
1. longitude
1. altitude
1. speed
1. bearing
1. horizontal accuracy
1. number of satellites used in fix
1. location provider                         

***Testing***

Once a a test trip has been generated, drop the generated CSV file into
the resources folder for the androidTest build. Then, in the NavigationTest
class, a new test method can be created. In this method, a NavigationSimulation object
can be instantiated with the CSV string passed into the constructor and
the runSimulation() method called.

***User Feedback Collection***

After completion of a trip, user will be requested for their feedback and asked if they wish to
share their trip data with OneBusAway.
Case 1: If the user agrees to share the data, the log file will be uploaded to Firebase storage and
deleted from the user's internal storage along with his feedback.
Case 2: If the user does not want to share the data, only his feedback with be recorded by Firebase
Analytics and the file will be deleted from his internal storage.
Case 3: If the user does not provide any feedback within 24 hours, the log file will be deleted from
the device's internal memory.

A detailed flowchart of this functionality can be found at
https://docs.google.com/drawings/d/13Ea1KVC0sK6_fCJ6Bk0kyoapi-8UY61hvQmld3gJjwU/edit

***(Potential) Idea Wish list***

- Favorite Trips: Allow person to bookmark a favorite trip. This 
would involve storing a combo of the starting stop, destination stop &
the trip Id. However, since Trip IDs are not persistent and are subject
to expiration, a way to uniquely identify trips is needed.

- Multi-Legged Trips: Maybe something for OTP, but have multi-bus
trips. NavigationServiceProvider already supports it by passing in an array of segments.

- Configurable Alert Triggering Threshold: As of right now, the distance 
the person needs to be within from the second-to-last stop for the first 
notification to be fired is fixed. Allowing it be possible for user to
specify the threshold, or for the threshold to be dynamically calculated
based on certain factors such as distance between stops would be neat 
addition.

- Advanced Location Logging: Currently, the location logger is built into
NavigationService & transparently logs to a file when it's is running. A toggle 
could be added in the Advanced Settings menu for the user to switch on and
off.