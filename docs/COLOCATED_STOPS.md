# Stops sharing a boarding point (#2286)

The Puget Sound API was checked on 2026-09-06 using `stops-for-location` around
3rd Avenue and Pine Street and `arrivals-and-departures-for-stop` for each ID.

| Direction | Metro stop | Route 597 stop | Coordinates |
|---|---|---|---|
| NW | `1_590` | `3_2479` | `47.611137, -122.338951` |
| SE | `1_430` | `3_2474` | `47.610897, -122.338966` |

Each pair has exactly the same name (`3rd Ave & Pine St`), direction, coordinates,
and `locationType=0`. The API gives each member its own stop code and route IDs;
the `parent` field is empty. Each northbound arrivals response lists the other ID
in `nearbyStopIds`, but its arrivals belong only to the requested stop ID.

The [stop contract](https://developer.onebusaway.org/api/where/elements/stop)
maps stops to GTFS records; it does not provide a canonical ID for this pair.
The server's
[StopWithArrivalsAndDeparturesBeanServiceImpl](https://github.com/OneBusAway/onebusaway-application-modules/blob/master/onebusaway-transit-data-federation/src/main/java/org/onebusaway/transit_data_federation/impl/beans/StopWithArrivalsAndDeparturesBeanServiceImpl.java)
asks for nearby stops within a 100-meter bounding box.
[NearbyStopsBeanServiceImpl](https://github.com/OneBusAway/onebusaway-application-modules/blob/master/onebusaway-transit-data-federation/src/main/java/org/onebusaway/transit_data_federation/impl/beans/NearbyStopsBeanServiceImpl.java)
excludes the requested ID from that lookup. These are geographic neighbors, so
`nearbyStopIds` alone must not be treated as a list of equivalent stops.

The client combines stops only when their published coordinates, trimmed name,
and known compass direction match exactly, and both are boarding stops rather
than stations. There is no distance tolerance. Unknown directions and unnamed
stops remain separate. This deliberately leaves differently named bays and
stations alone; the API metadata cannot establish their equivalence.

The map combines route labels, favorites and route memberships into one marker,
while retaining the original stops in its cache. A focused ID stays selected;
otherwise a saved stop is preferred, followed by the stop with the most routes
and then ID order. Route projections combine only if their drawn points match too.

A marker carries all of its member IDs through map selection, saved focus and the
arrivals session. Each refresh (including window widening) requests those members
even if the deployment omits nearby IDs or stop references. Retapping the same
representative can add newly discovered members without losing previously selected
ones or adding a navigation rung.

The arrivals loader also resolves matching nearby references for entry points that
have only one ID, such as favorites or deep links. It fetches the union of explicit
members and discovered siblings once in the same time window. Arrival stop IDs,
trip references and alerts are preserved. Failure of any member fails the refresh,
allowing the existing repository to show a complete stale snapshot. Ordinary stops
require no extra request. ID-only entry points on deployments omitting nearby
references can show only the requested stop until a map selection supplies members.

Each snapshot carries the primary response's original server timestamp and its
monotonic receipt time. Merging retains that pair. The drawer, tracked-route
notifications and favorites project the clock from that anchor, including time
spent loading siblings, rather than inventing a later receipt for an older server
timestamp. The arrivals window still ends relative to the original response time.

Regression coverage: `ColocatedStopMarkersTest` and `ColocatedStopArrivalsTest`.
