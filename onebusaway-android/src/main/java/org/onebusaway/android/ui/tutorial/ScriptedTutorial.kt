/*
 * Copyright (C) 2026 Open Transit Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.onebusaway.android.ui.tutorial

import org.onebusaway.android.R

/**
 * What the app does when a scripted tutorial step opens (#2164).
 *
 * The tour narrates a fixed path through the app, so each step drives the app to the place its caption
 * is about rather than waiting for the user to find it. That is what makes the sequence *scripted*: the
 * screen behind the spotlight is a consequence of the step, not of what the user happened to tap, so
 * the caption can never describe something that isn't there.
 *
 * **These actions navigate and present; none of them persists anything.** The tour points at the
 * favourite star and the pin affordance and explains them, rather than starring a stop or pinning a
 * trip on the user's behalf — nobody should finish a tutorial with a bus stop in a city they've never
 * visited saved to their favourites. Every one of these is undone by leaving the tour.
 */
enum class TutorialAction {
    /** Focus the demo anchor stop, opening its arrivals drawer — as tapping it on the map would. */
    FOCUS_DEMO_STOP,

    /** Drill from the focused stop into one of its routes, drawing that route and its vehicles. */
    SHOW_DEMO_ROUTE,

    /** Drill from the focused route into a single vehicle's trip, as tapping an ETA pill does. */
    SELECT_DEMO_TRIP,

    /** Open the navigation drawer, where starred stops and routes live. */
    OPEN_DRAWER,

    /** Close the drawer and unwind the map back to the plain stop map, as backing out would. */
    RESET_MAP,

    /** Switch the micromobility layer on so the rental markers are drawn. */
    SHOW_RENTALS,

    /** Bring the demo trip's destination into view, so the long-press step has somewhere to point. */
    SHOW_TRIP_DESTINATION,

    /** Enter trip planning with the demo trip's endpoints filled in, as a map long-press does. */
    PLAN_DEMO_TRIP,

    /** Select a different itinerary from the ones planned, so the map redraws with another trip. */
    SHOW_OTHER_ITINERARY,

    /** Drill into one ride of the chosen trip, drawing that leg and the vehicles running it. */
    FOCUS_ROUTE_LEG
}

/**
 * The guided tour of the app (#2164) — a fixed sequence run against the bundled demo transit system
 * ([org.onebusaway.android.demo.DemoModeController]) rather than against whatever the user's region
 * happens to be doing.
 *
 * This replaces the old welcome tutorial, which spotlighted whichever real stop was nearest the middle
 * of the screen and then narrated whatever arrivals came back. That made the tour depend on the user
 * being somewhere served, on the network being up, and on a bus actually running: the "colored pills
 * tell you if it's late" step had nothing to point at on a quiet evening, and the whole sequence fell
 * apart with no region resolved. Here every step's content is guaranteed, because the data behind it is
 * part of the app.
 *
 * The steps mirror the script in the issue. Each pairs a caption with a spotlight anchor and, where the
 * step is about somewhere else in the app, a [TutorialAction] that takes the user there.
 */
object ScriptedTutorial {

    // ---- Spotlight anchor keys, attached to their targets with [Modifier.tutorialAnchor]. Several
    // steps may share one: the tour says three separate things about the itinerary list. ----

    /** The map itself — the opening step has no single target, so this never resolves to bounds. */
    const val KEY_MAP = "tutorial_scripted_map"

    /** The demo anchor stop's marker, projected to screen coordinates by [MapStopSpotlight]. */
    const val KEY_STOP = "tutorial_scripted_stop"

    /** The route badge on an arrivals row. */
    const val KEY_ROUTE_BADGE = "tutorial_scripted_route_badge"

    /** The row's favourite star. */
    const val KEY_ROUTE_STAR = "tutorial_scripted_route_star"

    /** The drawer's starred stops / starred routes entries. */
    const val KEY_DRAWER_STARRED = "tutorial_scripted_drawer_starred"

    /** The map's micromobility (rentals) button. */
    const val KEY_RENTALS = "tutorial_scripted_rentals"

    /**
     * The trip planner's action bar — the depart/arrive toggle, the time, and the mode + advanced
     * buttons. The row itself rather than the whole form: the endpoints above it are the *trip*, and the
     * step is about narrowing how it's made.
     */
    const val KEY_TRIP_OPTIONS = "tutorial_scripted_trip_options"

    /** The point on the map the demo trip runs to — what the long-press step rings and mimes on. */
    const val KEY_TRIP_DESTINATION = "tutorial_scripted_trip_destination"

    /**
     * The long-press step itself, named because the *screen* reads it: while it is current the map
     * shows the "navigate here" bubble the mimed press would raise (#2243), so the caption's "then tap
     * Navigate here" points at something that is really there. A step id rather than an anchor key —
     * that step rings [KEY_TRIP_DESTINATION], which is where the bubble hangs.
     */
    const val STEP_PLAN_PRESS = "tutorial_scripted_plan_press"

    /** The whole trip-results drawer: the options, the caution notice and the step-by-step directions. */
    const val KEY_TRIP_DRAWER = "tutorial_scripted_trip_drawer"

    /**
     * The horizontally scrollable strip of itinerary option cards — for the step about choosing
     * *between* them. The strip, not the whole results sheet, which is mostly the selected trip's
     * step-by-step directions and so named the wrong thing.
     */
    const val KEY_ITINERARIES = "tutorial_scripted_itineraries"

    /** A ride within the chosen trip — the row that focuses that leg and its vehicles on the map. */
    const val KEY_ROUTE_LEG = "tutorial_scripted_route_leg"

    /**
     * The currently selected option card. The steps after the comparison one are about a single
     * option — opening it, and pinning it — so they ring that card rather than the whole picker.
     */
    const val KEY_ITINERARY_CARD = "tutorial_scripted_itinerary_card"

    /**
     * The tour, in order. Step numbering follows the issue's script.
     *
     * Three steps deliberately reuse an existing anchor rather than introducing one of their own —
     * [ArrivalTutorial.KEY_ETA] for the ETA pill — so the arrivals panel keeps a single set of
     * spotlight targets whichever tutorial is running. That shared anchor is also how the two
     * tutorials avoid teaching the same control twice: `RecordArrivalSpotlightsShown` records an
     * arrivals spotlight by anchor, so the tour retires the opportunistic ETA step by *showing* it.
     * The rest of that sequence stays owed, because the tour never covers it.
     */
    val steps: List<TutorialStep> = listOf(
        // 1. The map. It carries an action even though nothing has happened yet, so that stepping
        // *back* to it from the focused stop has something to return the app to — every step the rider
        // can land on names the state it wants (see `governingActionIndex`).
        TutorialStep(
            id = KEY_MAP,
            title = R.string.tutorial_scripted_map_title,
            body = R.string.tutorial_scripted_map_text,
            action = TutorialAction.RESET_MAP
        ),
        // 2. A stop, with three routes to talk about.
        TutorialStep(
            id = KEY_STOP,
            title = R.string.tutorial_scripted_stop_title,
            body = R.string.tutorial_scripted_stop_text,
            // The stop sits mid-screen, so the automatic placement would put the caption at the bottom
            // — straight over the arrivals drawer this step has just opened, which is the thing the
            // rider is meant to look at.
            captionAtTop = true,
            action = TutorialAction.FOCUS_DEMO_STOP
        ),
        // 3. Arrivals, grouped by route.
        TutorialStep(
            id = "tutorial_scripted_arrivals",
            anchorId = ArrivalTutorial.KEY_ETA,
            title = R.string.tutorial_scripted_arrivals_title,
            body = R.string.tutorial_scripted_arrivals_text
        ),
        // 4. The route badge: every vehicle on that route.
        TutorialStep(
            id = "tutorial_scripted_route",
            anchorId = KEY_ROUTE_BADGE,
            title = R.string.tutorial_scripted_route_title,
            body = R.string.tutorial_scripted_route_text,
            action = TutorialAction.SHOW_DEMO_ROUTE
        ),
        // 5. An ETA pill: the one vehicle behind that arrival.
        TutorialStep(
            id = "tutorial_scripted_vehicle",
            anchorId = ArrivalTutorial.KEY_ETA,
            title = R.string.tutorial_scripted_vehicle_title,
            body = R.string.tutorial_scripted_vehicle_text,
            action = TutorialAction.SELECT_DEMO_TRIP
        ),
        // 7. What the colors and the corner marks mean. (The issue's script has no step 6.)
        TutorialStep(
            id = "tutorial_scripted_legend",
            anchorId = ArrivalTutorial.KEY_ETA,
            title = R.string.tutorial_scripted_legend_title,
            body = R.string.tutorial_scripted_legend_text,
            // Rendered inside the caption rather than by opening the app's Legend dialog over the
            // tutorial: a modal on a modal, hiding the arrivals the legend is about.
            extra = TutorialExtra.ARRIVAL_LEGEND
        ),
        // 8. Starring — pointed at, never pressed.
        TutorialStep(
            id = "tutorial_scripted_star",
            anchorId = KEY_ROUTE_STAR,
            title = R.string.tutorial_scripted_star_title,
            body = R.string.tutorial_scripted_star_text
        ),
        // 9. Where starred things end up.
        TutorialStep(
            id = "tutorial_scripted_starred_list",
            anchorId = KEY_DRAWER_STARRED,
            title = R.string.tutorial_scripted_starred_list_title,
            body = R.string.tutorial_scripted_starred_list_text,
            action = TutorialAction.OPEN_DRAWER
        ),
        // 10. Backing out again.
        TutorialStep(
            id = "tutorial_scripted_back",
            anchorId = KEY_MAP,
            title = R.string.tutorial_scripted_back_title,
            body = R.string.tutorial_scripted_back_text,
            action = TutorialAction.RESET_MAP
        ),
        // 11. Micromobility.
        TutorialStep(
            id = "tutorial_scripted_rentals",
            anchorId = KEY_RENTALS,
            title = R.string.tutorial_scripted_rentals_title,
            body = R.string.tutorial_scripted_rentals_text,
            action = TutorialAction.SHOW_RENTALS
        ),
        // 12a. The gesture itself, on a map that hasn't been asked for anything yet. The press has
        // nothing of its own to ring, so the overlay mimes it rather than only describing it — and it
        // gets its own step so the rider watches the press, and the offer it raises, before the trip
        // itself arrives. The map draws that offer for the length of this step (see HomeScreen).
        TutorialStep(
            id = STEP_PLAN_PRESS,
            // The destination itself, not the whole map: the press has a *place*, and ringing the
            // screen would say the gesture works anywhere without showing where this one landed.
            anchorId = KEY_TRIP_DESTINATION,
            title = R.string.tutorial_scripted_plan_press_title,
            body = R.string.tutorial_scripted_plan_press_text,
            gesture = TutorialGesture.LONG_PRESS,
            action = TutorialAction.SHOW_TRIP_DESTINATION
        ),
        // 12b. What that press produced.
        TutorialStep(
            id = "tutorial_scripted_plan",
            // The whole drawer: what came back is the options *and* the directions under them.
            anchorId = KEY_TRIP_DRAWER,
            title = R.string.tutorial_scripted_plan_title,
            body = R.string.tutorial_scripted_plan_text,
            action = TutorialAction.PLAN_DEMO_TRIP
        ),
        // 13. Narrowing the search.
        TutorialStep(
            id = "tutorial_scripted_trip_options",
            anchorId = KEY_TRIP_OPTIONS,
            title = R.string.tutorial_scripted_trip_options_title,
            body = R.string.tutorial_scripted_trip_options_text
        ),
        // 14. The options themselves.
        TutorialStep(
            id = "tutorial_scripted_itineraries",
            anchorId = KEY_ITINERARIES,
            title = R.string.tutorial_scripted_itineraries_title,
            body = R.string.tutorial_scripted_itineraries_text
        ),
        // 15. Drilling into one.
        TutorialStep(
            id = "tutorial_scripted_itinerary_detail",
            anchorId = KEY_ITINERARY_CARD,
            title = R.string.tutorial_scripted_itinerary_detail_title,
            body = R.string.tutorial_scripted_itinerary_detail_text,
            // Opens a *different* option than the one the previous step left selected, so the rider
            // sees the map redraw. Saying "tap a row to see it on the map" over an unchanged map
            // demonstrates nothing.
            action = TutorialAction.SHOW_OTHER_ITINERARY
        ),
        // 15b. One ride within the chosen trip.
        TutorialStep(
            id = "tutorial_scripted_leg",
            anchorId = KEY_ROUTE_LEG,
            title = R.string.tutorial_scripted_leg_title,
            body = R.string.tutorial_scripted_leg_text,
            action = TutorialAction.FOCUS_ROUTE_LEG
        ),
        // 16. Keeping one.
        TutorialStep(
            id = "tutorial_scripted_pin",
            anchorId = KEY_ITINERARY_CARD,
            title = R.string.tutorial_scripted_pin_title,
            body = R.string.tutorial_scripted_pin_text,
            // Mimed on the card itself, since that is the thing to press.
            gesture = TutorialGesture.LONG_PRESS
        )
    )
}
