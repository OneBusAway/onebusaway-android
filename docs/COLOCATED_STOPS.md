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

The arrivals loader resolves matching nearby references and fetches each sibling
once in the same time window. This also works when entering through a favorite or
deep link without nearby map data. Arrival stop IDs, trip references and alerts
are preserved. Failure of any member fails the refresh, allowing the existing
repository to show a complete stale snapshot. Ordinary stops require no extra
request. Deployments omitting nearby references retain their single-stop behavior.

Regression coverage: `ColocatedStopMarkersTest` and `ColocatedStopArrivalsTest`.
