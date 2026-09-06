# Overlapping stops (#2286)

The Puget Sound API was checked on 2026-09-06. At 3rd Avenue and Pine Street,
`1_590` and `3_2479` share the exact coordinates `47.611137, -122.338951`.
The first serves Metro routes and the second route 597. A second pair,
`1_430` and `3_2474`, shares the southbound location.

These are separate provider IDs with separate stop codes and arrivals responses.
The [stop contract](https://developer.onebusaway.org/api/where/elements/stop)
maps stops to GTFS records. Nearby references describe geographic neighbors;
they do not establish that two IDs are the same stop.

The client handles this as a map selection problem. Stop taps use the platform's
minimum touch target (normally 48 dp wide and tall), centered on the actual tap
in screen coordinates. Stops within that target are choices even when their GTFS
coordinates differ. The live map projection makes this zoom-dependent: zooming in
can separate two stops into independently selectable targets. A tap on empty map
within a stop's target can select it too. Vehicle, rental, and route-badge taps
retain the SDK's normal dispatch.

The SDK's original marker hit is always included, even when the tap lands on its
route label outside the target around its anchor. Exact overlapping anchors keep
their existing chooser in that case. If projection is unavailable, selection falls
back to that original hit and exact overlapping anchors. The touch observer does
not consume gestures; the SDK still decides whether a gesture is a click, pan,
zoom, or long press.

The chooser shows each stop's name, code, direction, and available routes in the
map's badge grid, filled horizontally. Choosing a card forwards that original
marker to the existing stop callback. Canceling leaves the current focus alone.
This lives in the shared map surface, with live projections supplied by both map
implementations, so the report-location map uses the same chooser too.

There is no geographic distance tolerance, stop reconciliation, combined arrival
request, or persisted group membership. A route presentation can hide map labels
while keeping route names available to the chooser. If route metadata has not
loaded, the chooser still identifies each stop by its code (or original ID if code
is absent).
