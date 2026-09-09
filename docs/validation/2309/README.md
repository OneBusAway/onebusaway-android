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
  selection/recreation with Map preselected in the earlier implementation (changed below).
- Google app and instrumentation APK builds and MapLibre Kotlin compilation passed
  with `-PwarningsAsErrors=true`.
- `spotlessCheck` passed.

## Updated-app device observations

Both graphical migration dialogs place the Previous layout first (Lists and
arrivals / Time), followed by the New layout (Map / Route). The earlier screenshots preselect the
new layout; the post-26.2.1 revision described below preselects the previous layout. Each has a
shared footer with page dots centered above the Back and Continue buttons.
Back is disabled on the first page;
the second page returns to the first while preserving both selections. The page
count includes only the choices owed at startup (one or two). A filled circle
marks the current page; the other circle is outlined. Screen readers announce
“Page 1 of 2” or “Page 2 of 2”.

The 16 migration unit tests and eight focused arrival-display/search-choice
device tests passed. The phone check verified button Back and system Back,
retention of both selections, and page indicators centered above the buttons.
Both previews and the footer fit on the Pixel screen.

Search previews render the actual direction header, stop rows, and arrival cards
at scaled phone proportions. The offline map illustration adds labeled streets,
a shoreline, route stops, and the app's bus glyph, with light/dark colors.
Preview taps select the enclosing option; sample controls never open trip actions. Chose Lists and arrivals, restarted, and
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

## Stop map zoom and route banner follow-up

The mapless stop board's Map action now centers the stop at the existing street-level
zoom (16), including when its coordinates arrive after navigation. Ordinary viewport
restoration keeps its previous behavior. A retained route selection does not frame
the whole route over this explicit stop zoom.

The map's route banner long-press menu now offers **Show stop list** alongside
**Show route schedule**. The stop list remains available without a schedule URL and
opens the route's directions regardless of the search-layout preference.

Validation: 133 focused HomeViewModel/MapReveal unit tests and 21 Pixel instrumented
FocusBanner/ArrivalsNavigation/SearchWorkflowNavigation tests passed. Google debug
app and test APK builds, MapLibre debug Kotlin compilation, and Spotless passed with
compiler warnings treated as errors.

On the Pixel 7 Pro, long-pressing route 8's banner opened the
[new menu](oba-2309-route-menu.png); Show stop list opened its directions and stops.
From the Denny Way & Westlake Ave mapless board, Map changed the saved zoom from
13.0697 to exactly 16 and [centered stop 2255](oba-2309-stop-map-zoom.png).

## Simplification review

Three review passes consolidated migration completion around the startup page list,
shared the choice-card layout, removed a forwarding-only preview function, and reused
already-resolved preferences. The final pass found no further useful reductions.
Both migration types now offer the tutorial invitation after the final page when
release notes have already been read; a regression test covers this path.

On the simplified implementation, 150 focused HelpViewModel/HomeViewModel/MapReveal
unit tests and 30 migration/banner/navigation Pixel tests passed. Google app/test
builds, MapLibre Kotlin compilation, and Spotless passed with warnings treated as
errors. A live Pixel check verified both migration layouts, centered pips, default
choices, retained selections through Back, and final Continue. Original device
preferences and migration markers were restored after the check.

## Post-26.2.1 migration revision

Both migration pages now apply to existing installations, including users who
installed 26.2.0 or 26.2.1 fresh. They share a new source marker captured before
What's New advances its version marker; the old March-only arrival migration
marker cannot exclude those users. Confirmed choices remain saved independently,
and deferred choices remain available on the next launch. Fresh installs of the
updated app receive no upgrade prompt and retain Map / Route defaults.

The migration dialogs now preselect **Lists and arrivals** and **Time**. Continuing
without changing the selections saves the previous workflows. The earlier
migration screenshots above show the superseded Map / Route preselection.

Validation of this revision: all 17 `HelpViewModelTest` unit tests and all eight
`SearchWorkflowChoiceTest` / `ArrivalDisplayModeTest` tests passed on the USB Pixel
7 Pro. The device checks confirm Continue saves Lists and arrivals / Time without
a selection change, and an explicit Map selection survives state restoration.
Google app/test APK builds and `spotlessCheck` passed with warnings treated as
errors. The full unit and connected suites were not rerun for this revision.

## Review follow-up

The preview regression test now targets the actual `PhonePreview` bounds inside
a selectable migration choice and observes separate preview, parent, and sample
callbacks. A tap must invoke only the preview callback. The full-dialog test also
starts on Map before tapping the Lists preview, so it verifies a selection change.
Migration-label assertions retain Lists as Previous layout and Map as New layout,
matching the March baseline and the intended restored workflow.

All four `SearchWorkflowChoiceTest` cases passed on the Pixel 7 Pro with the
matching PR app/test APKs. The test APK build (warnings as errors),
`spotlessCheck`, and `git diff --check` passed.
