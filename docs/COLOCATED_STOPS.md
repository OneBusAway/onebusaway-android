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
map's badge grid, filled horizontally. The currently selected stop has a small
"Currently selected" label directly above its title. Choosing a card forwards that original
marker to the existing stop callback. Canceling leaves the current focus alone.
Stop selection is opt-in at the shared map surface. The home and report-location
maps enable it; coordinate pickers retain their original callbacks and never
open the chooser. Both map implementations supply root-space projections. The
motion-event observer already receives root-space positions, so it does not add
the map's inset again when the map sits below a toolbar.

There is no geographic distance tolerance, stop reconciliation, combined arrival
request, or persisted group membership. A route presentation can hide map labels
while keeping route names available to the chooser. If route metadata has not
loaded, the chooser still identifies each stop by its code (or original ID if code
is absent).

## Nearby alternatives during stop focus (#2293, #2295)

Selecting a stop retains the loaded nearby stops alongside the displayed trips'
stops. Nearby alternatives keep their geographic coordinates and remain tappable,
including through the chooser; only stops on the displayed trips receive route
membership. All stops stay at their GTFS boarding coordinates when a stop, route,
or vehicle is selected. Background stops use the compact
directional icon (MapLibre already uses glyph-free directional icons), scaled to
85% of its usual size, and route labels stay hidden during focus. Favorite stars
and far-zoom dots receive the same size reduction. Tap targets retain their size.

The nearby context survives selecting a route or vehicle from the focused stop,
and stays available when that stop has no departures. Clearing focus restores the
ordinary nearby marker and label presentation. Standalone route and directions
views keep their existing stop selection scope.

Route stops use the displayed line's color as an outline around an opaque,
theme-aware center. The selected stop uses the regular orange stop icon, with the
whole icon (including its direction arrow and transit symbol) enlarged to 125%.
Other route stops retain their direction arrows in both map providers. A stop shared by differently colored displayed routes uses a
neutral outline; selecting one route gives it that route's color. Stop colors
follow the map palette, including adjacency colors and directions palettes.

At full detail, unselected route-stop circles are 22.5 dp across with a 4.125 dp rim. Before a route is selected, the other route stops
recede to 80% size so they remain readable beside nearby alternatives. These visual sizes do not reduce tap targets.
