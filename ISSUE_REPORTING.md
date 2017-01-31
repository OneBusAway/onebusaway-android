# Issue Reporting

In OneBusAway Android, we support issue reporting via the Open311 protocol to any server that is Open311 compliant.  The following sections provide more information about this, including the data agencies can expect to see in issue reports.

## User experience

From within the OneBusAway app, users can report problems for:
* Bus stops, and any other issue categories (i.e., "services" in the Open311 spec language) defined by the agency
* Predicted arrival/departure times

If a "Bus Stop ID" field is provided by the agency, we automatically populate that with the ID of the stop that the user chose.

For screenshots and more information, see [this presentation](http://www.slideshare.net/sjbarbeau/onebusaway-new-issue-reporting-flow-in-onebusaway-android).

### Issue metadata

We capture the below information directly from the OneBusAway server and include it in at the bottom of all issue reports to make it easier to quickly diagnose a problem.

For bus stop and other non-arrival time issue categories, include the following info:

~~~
gtfs_stop_id=Hillsborough Area Regional Transit_420;    // The ID of the stop from OneBusaway - - consists of the GTFS agency_id (or agency_name if agency_id isn't provided), followed by an underscore (_), followed by the GTFS stop_id 
stop_name=Nebraska Av @ Lake Av;                        // The name of the stop, from GTFS data
~~~

You can see an example of this type of report [here](https://seeclickfix.com/issues/3165497-safety-concern).

Arrival/departure time prediction problem reports include more information related to the vehicle and prediction:

~~~
service_date=01-29-2017;                                // The GTFS date of service when this issue was reported
agency_name=Hillsborough Area Regional Transit;         // The name of the agency that this report is for
gtfs_stop_id=Hillsborough Area Regional Transit_5542;   // The ID of the stop from OneBusaway - - consists of the GTFS agency_id (or agency_name if agency_id isn't provided), followed by an underscore (_), followed by the GTFS stop_id
stop_name=Busch Blvd @ Twin Lakes Blvd;                 // The name of the stop, from GTFS data
route_id=Hillsborough Area Regional Transit_39;         // The ID of the route from OneBusAway - consists of the GTFS agency_id (or agency_name if agency_id isn't provided), followed by an underscore (_), followed by the GTFS route_id
route_display_name=39;                                  // The name of the route that was shown to the rider - this is the GTFS route_short name if it exists, and if not the GTFS route route_long_name
trip_id=Hillsborough Area Regional Transit_238563;      // The ID of the trip for this arrival time from OneBusaway - - consists of the GTFS agency_id (or agency_name if agency_id isn't provided), followed by an underscore (_), followed by the GTFS trip_id
trip_headsign=East to Yukon Transfer Center;            // The headsign shown to the rider for this trip, from GTFS data
predicted=true;                                         // True if real-time information existed for this prediction, False if the schedule time was shown to the rider
vehicle_id=Hillsborough Area Regional Transit_1305;     // The ID of this vehicle - consists of the GTFS agency_id (or agency_name if agency_id isn't provided), followed by an underscore (_), followed by the vehicle ID from the GTFS-realtime data
vehicle_location=28.033536911010742 -82.48483276367188; // The location of this vehicle when the user reported the problem
schedule_deviation=3.000 min early;                     // The real-time prediction from the AVL system, shown as the deviation from the schedule (assuming predicted=true).  This is how early/late a bus is running.  If it's "0", then the bus is runnign on time.
stop_arrival_time=03:54 PM;                             // The arrival time that was shown to the user when they reported the problem.  If predicted=true then this is the real-time predicted arrival time, if predicted=false then this is the scheduled time.
stop_departure_time=03:54 PM;                           // The departure time that was shown to the user when they reported the problem.  If predicted=true then this is the real-time predicted departure time, if predicted=false then this is the scheduled time.
~~~

You can see an example of this type of report [here](https://seeclickfix.com/issues/3177416-arrival-times-schedules).

### Arrival/departure time issue reports

Users are prompted with several categories for arrival time problems, including:

* It came earlier than **predicted**
* It came later than **predicted**

The goal of this category was to identify errors in the predictions.  However, we're seeing users choose these categories to report that the bus came early or late (i.e., it was earlier/later than the *schedule*, not the *prediction*).
 
 Two example issues:
 * Report that the bus was [earlier than scheduled](https://seeclickfix.com/issues/3177416-arrival-times-schedules)
 * Report that the bus was [earlier than predicted](https://seeclickfix.com/issues/3173596-arrival-times-schedules)