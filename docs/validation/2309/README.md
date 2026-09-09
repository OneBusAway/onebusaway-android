# Search workflow continuity (#2309)

Compared on a Pixel 7 Pro on September 8, 2026, against the March 19 baseline
`e65872a72`. The historical build uses application ID
`com.joulespersecond.seattlebusbot.march2309` and a separate provider authority,
so its data is separate from the current app. Its launcher label is **OBA March 2026**.
These packaging changes are local
comparison-build changes, not part of the product patch.

## Shared task

Puget Sound / Metro route **8**, **Mount Baker Transit Center** direction,
**Denny Way & Westlake Ave**, eastbound, stop **2255** (`1_2255`).

| Interaction | March baseline | Lists and arrivals |
| --- | --- | --- |
| Search access from restored Starred stops | Toolbar Search icon | Toolbar Search icon |
| Search route | Enter `8`, submit, tap Seattle route 8 | Same |
| Open route stops | Tap “Show list of stops” in a choice dialog | Saved preference opens the list directly |
| Choose direction | Tap “Mount Baker Transit Center” to expand | Same |
| Choose stop | Scroll ordered stops and tap “Denny Way & Westlake Ave” | Same |
| Read arrivals | Mapless arrivals list | Mapless arrivals board; existing Time / Route choice remains independent |
| Direct stop search | Enter `2255`, submit, tap result, tap “Show arrivals” | Enter `2255`, submit, tap result; board opens directly |

The historical route and direct-stop paths were both walked on the device and
reached the shared stop's live arrivals. Back traversed the route list and search
results. Selecting Starred stops and restarting restored that section with Search
visible. Screenshots in this directory record the historical direction list,
expanded stops, arrivals, direct-stop choice, and restored starting section.

The saved preference removes one repeated choice dialog from each search task.
No replacement gesture, form, or map interpretation is introduced.

## Automated validation

- 2,233 Google-variant unit tests passed, including migration eligibility for
  March releases and 26.2.x, fresh-install exclusion, deferred choices, Settings
  choices, and independence from arrival ordering.
- 12 focused tests passed on the Pixel 7 Pro: real Navigation Compose back-stack
  behavior, the actual route list's expanded direction and scroll restoration
  across arrivals and saved-state recreation, map reveals, and graphical chooser
  selection/recreation with Map preselected.
- Google app and instrumentation APK builds and MapLibre Kotlin compilation passed
  with `-PwarningsAsErrors=true`.
- `spotlessCheck` passed.

## Updated-app device observations

Both graphical migration dialogs place the Previous layout first (Lists and
arrivals / Time), followed by the selected New layout (Map / Route). Each has a
shared footer with page dots centered above the Back and Continue buttons.
Back is disabled on the first page;
the second page returns to the first while preserving both selections. The page
count includes only the choices owed at startup (one or two). A filled circle
marks the current page; the other circle is outlined. Screen readers announce
“Page 1 of 2” or “Page 2 of 2”.

The 16 migration unit tests and seven focused arrival-display/search-choice
device tests passed. The phone check verified button Back and system Back,
retention of both selections, and page indicators centered above the buttons.
Both previews and the footer fit on the Pixel screen. Chose Lists and arrivals, restarted, and
confirmed the prompt did not return. Starred stops restored with toolbar Search.
From that section, entered `8`, selected the Seattle route, expanded Mount Baker
Transit Center, scrolled, and selected Denny Way & Westlake Ave. The live mapless
board showed that stop's eastbound route 8 departures.

The modern list uses larger rows and an indented stop hierarchy; its route,
direction, and stop names and stop order match the historical version. This
changes how many rows fit onscreen, but retains ordinary taps and vertical
scrolling. The arrivals board's existing Time / Route presentation is a separate
choice; choosing Lists and arrivals left Route selected on this installation.

Back from arrivals restored the exact scrolled list: the Westlake stop's text
bounds were `[84,711][614,774]` both before and after. The route list's explicit
Show on map action displayed route 8; a single Back again restored those same
bounds. Further Back presses returned through search results to Starred stops.

Direct stop search from the restored Starred stops section (`2255`) returned
Denny Way & Westlake Ave; tapping it opened the same mapless board directly,
without the March “Show arrivals” dialog. The same results also retained the
existing coach-number result type.

Changed Open search results in to Map in Settings. Searching `2255` and tapping
the stop opened the focused stop with its map arrivals drawer. Searching `8` and
tapping the Seattle route opened the route map with direction selection and live
vehicles. Both matched the existing Map-mode destinations. The setting remained
independent of the Time / Route arrival-display control on the same Settings page.

## Full-suite result

The full connected suite ran 325 cases on the Pixel. The search/navigation and
migration tests passed. Four checks reported unmet assumptions (no last-known
location for two checks; destination reminders disabled for two channel checks).
One unrelated existing trip-planner rendering assertion failed:
`TripPlanFormRenderTest.reverseSitsBetweenTheTwoEndpointFields` expected a centre
at pixel 139 and found pixel 140. Neither that test nor the trip-planner UI is
changed by this patch. The full Gradle connected-test task therefore reports a
failure; this is not an all-green full-suite claim.

## Screenshots

| Screen | March baseline | Updated app |
| --- | --- | --- |
| Restored Starred stops | [Before](oba-2309-march-restored-starred.png) | [After](oba-2309-new-restored-starred.png) |
| Named directions | [Before](oba-2309-march-directions.png) | [After](oba-2309-new-directions.png) |
| Ordered stops | [Before](oba-2309-march-stops.png) | [After, scrolled](oba-2309-new-stops.png) |
| Mapless arrivals | [Before](oba-2309-march-arrivals.png) | [After](oba-2309-new-arrivals.png) |
| Direct stop result | [Old choice dialog](oba-2309-march-stop-choice.png) | [New result](oba-2309-new-stop-search.png) |

[Search migration](oba-2309-new-migration.png) ·
[Arrival-display migration](oba-2309-arrival-migration.png) ·
[Map-mode stop search](oba-2309-map-stop-search.png) ·
[Map-mode route search](oba-2309-map-route-search.png)
