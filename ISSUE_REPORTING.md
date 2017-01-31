# Issue Reporting

In OneBusAway Android, we support issue reporting via the Open311 protocol to any server that is Open311 compliant.  The following sections provide more information about this, including the data agencies can expect to see in issue reports.

## User experience

Users can report problems for:
* Bus stops, and any other issue categories (i.e., "services" in the Open311 spec language) defined by the agency
* Prediction arrival/departure times

See slideshare

## Example post

https://seeclickfix.com/issues/3177416-arrival-times-schedules

### Issue metadata

We capture the below information directly from the OneBusAway server and include it in at the bottom of all arrival/departure time prediction problem reports:

~~~
service_date=01-29-2017;                               // The GTFS date of service when this issue was reported
agency_name=Hillsborough Area Regional Transit;        // The name of the agency that this report is for
gtfs_stop_id=Hillsborough Area Regional Transit_5542;  // The stop_id of the stop from OneBusaway - - consists of the GTFS agency_id (or agency_name if agency_id isn't provided), followed by an underscore (_), followed by the GTFS stop_id
stop_name=Busch Blvd @ Twin Lakes Blvd;                // The name of the stop
route_id=Hillsborough Area Regional Transit_39;        // The route_id of the stop from OneBusAway - consists of the GTFS agency_id (or agency_name if agency_id isn't provided), followed by an underscore (_), followed by the GTFS route_id
route_display_name=39;                                 // The name of the route that was shown to the rider - this is the GTFS route_short name if it exists, and if not the GTFS route route_long_name
trip_id=Hillsborough Area Regional Transit_238563;     // The trip_id of the trip of this arrival time from OneBusaway - - consists of the GTFS agency_id (or agency_name if agency_id isn't provided), followed by an underscore (_), followed by the GTFS trip_id
trip_headsign=East to Yukon Transfer Center;      // The GTFS trip_headsign shown to the rider
predicted=true;     // True if real-time information existed for this prediction, false if the schedule time was shown to the rider
vehicle_id=Hillsborough Area Regional Transit_1305;
vehicle_location=28.033536911010742 -82.48483276367188;
schedule_deviation=3.000 min early;
stop_arrival_time=03:54 PM;
stop_departure_time=03:54 PM;
~~~

### OpenTripPlanner API Server

You can enter a server URL in a few different formats, including:

* `example.opentripplanner.org/otp`
* `http://example.opentripplanner.org/otp`

Note that if your server is using SSL/HTTPS, you currently need to enter the entire URL:

* `https://example.opentripplanner.org/otp`

For older OpenTripPlanner servers (circa pre-v0.19.0) that don't include "/otp/routers/default" in the path, you can 
include the path up until the "plan" endpoint.

For example, if your server "plan" endpoint is at `http://example.opentripplanner.org/tripplanner/plan?...`, then you can enter the following as the custom OTP URL:

* `example.opentripplanner.org/tripplanner/`