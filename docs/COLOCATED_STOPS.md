# Overlapping stops (#2286)

The Puget Sound API was checked on 2026-09-06. At 3rd Avenue and Pine Street,
`1_590` and `3_2479` share the exact coordinates `47.611137, -122.338951`.
The first serves Metro routes and the second route 597. A second pair,
`1_430` and `3_2474`, shares the southbound location.

These are separate provider IDs with separate stop codes and arrivals responses.
The [stop contract](https://developer.onebusaway.org/api/where/elements/stop)
maps stops to GTFS records. Nearby references describe geographic neighbors;
they do not establish that two IDs are the same stop.

The client handles this as a map selection problem. When several rendered stops
have the same point, tapping any of them offers a chooser with each stop's name,
code and available route names. Choosing a row forwards that original marker to
the existing stop callback. Canceling leaves the current focus alone. This lives
in the shared map surface, so both map implementations and the report-location
map use the same chooser.

There is no stop reconciliation, distance tolerance, combined arrival request,
or persisted group membership. Stops with different rendered points retain their
ordinary tap behavior. A route presentation can hide map labels while keeping
route names available to the chooser. If route metadata has not loaded, the
chooser still identifies each stop by its code (or original ID if code is absent).
