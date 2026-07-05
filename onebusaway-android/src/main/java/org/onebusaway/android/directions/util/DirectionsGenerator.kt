/*
 * Copyright 2012 University of South Florida
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.onebusaway.android.directions.util

import android.content.Context
import android.content.res.Resources
import android.text.SpannableString
import android.text.TextUtils
import android.util.Log
import org.onebusaway.android.R
import org.onebusaway.android.directions.model.Direction
import org.onebusaway.android.util.PreferenceUtils
import org.opentripplanner.api.model.AbsoluteDirection
import org.opentripplanner.api.model.Leg
import org.opentripplanner.api.model.Place
import org.opentripplanner.api.model.RelativeDirection
import org.opentripplanner.routing.core.TraverseMode
import org.opentripplanner.routing.core.TraverseModeSet

/**
 * Generates a set of step-by-step directions that can be shown to the user from a list of trip
 * legs
 *
 * @author Khoa Tran
 */
class DirectionsGenerator(
    private val legs: List<Leg>,
    private val applicationContext: Context,
) {

    /**
     * @return the directions
     */
    var directions = ArrayList<Direction>()

    /**
     * @return the totalDistance
     */
    var totalDistance = 0.0

    private var totalTimeTraveled = 0.0

    init {
        convertToDirectionList()
    }

    fun addDirection(dir: Direction) {
        directions.add(dir)
    }

    private fun convertToDirectionList() {
        var index = 0
        for (leg in legs) {
            index++
            totalDistance += leg.distance

            val traverseMode = TraverseMode.valueOf(leg.mode)
            if (traverseMode.isOnStreetNonTransit) {
                val dir = generateNonTransitDirections(leg)
                dir.directionIndex = index
                addDirection(dir)
            } else {
                val transitDirections = generateTransitDirections(leg)
                transitDirections[0].directionIndex = index
                addDirection(transitDirections[0])
                index++
                transitDirections[1].directionIndex = index
                addDirection(transitDirections[1])
            }
        }
    }

    private fun generateNonTransitDirections(leg: Leg): Direction {
        val direction = Direction()

        // Get appropriate action and icon
        var action =
            applicationContext.getString(R.string.step_by_step_non_transit_mode_walk_action)
        val mode = TraverseMode.valueOf(leg.mode)
        val icon = getModeIcon(TraverseModeSet(mode))
        if (mode == TraverseMode.BICYCLE) {
            action = applicationContext.getString(R.string.step_by_step_non_transit_mode_bicycle_action)
        } else if (mode == TraverseMode.CAR) {
            action = applicationContext.getString(R.string.step_by_step_non_transit_mode_car_action)
        }

        direction.icon = icon

        // Main direction
        val fromPlace = leg.from
        val toPlace = leg.to
        var mainDirectionText = action
        mainDirectionText += if (fromPlace.name == null) {
            ""
        } else {
            " " + applicationContext.getString(R.string.step_by_step_non_transit_from) +
                " " + getLocalizedStreetName(fromPlace.name, applicationContext.resources)
        }
        mainDirectionText += if (toPlace.name == null) {
            ""
        } else {
            " " + applicationContext.getString(R.string.step_by_step_non_transit_to) +
                " " + getLocalizedStreetName(toPlace.name, applicationContext.resources)
        }
        val extraStopInformation = toPlace.stopCode
        val legDuration = if (PreferenceUtils.getInt(
                OTPConstants.PREFERENCE_KEY_API_VERSION, OTPConstants.API_VERSION_V1
            ) == OTPConstants.API_VERSION_V1
        ) {
            leg.duration
        } else {
            leg.duration / 1000
        }
        if (!TextUtils.isEmpty(extraStopInformation)) {
            mainDirectionText += " ($extraStopInformation)"
        }
        mainDirectionText += "\n[" + ConversionUtils
            .getFormattedDistance(leg.distance, applicationContext) + " - " +
            ConversionUtils.getFormattedDurationTextNoSeconds(legDuration, false, applicationContext) +
            " ]"
        direction.directionText = mainDirectionText

        // Sub-direction
        val walkSteps = leg.steps ?: return direction

        val subDirections = ArrayList<Direction>(walkSteps.size)
        // Loop-invariant default; only the roundabout branch overrides it per step.
        val defaultStreetConnector = applicationContext
            .getString(R.string.step_by_step_non_transit_connector_street_name)

        for (step in walkSteps) {
            var subdirectionIcon = -1
            val dir = Direction()
            var subDirectionText = ""

            val relativeDir = step.relativeDirection
            val relativeDirString = getLocalizedRelativeDir(relativeDir, applicationContext.resources)
            val streetName = step.streetName
            val absoluteDir = step.absoluteDirection
            val absoluteDirString = getLocalizedAbsoluteDir(absoluteDir, applicationContext.resources)
            var streetConnector = defaultStreetConnector

            // Walk East
            if (relativeDir == null) {
                subDirectionText += action + " " + applicationContext
                    .getString(R.string.step_by_step_non_transit_heading) + " "
                subDirectionText += "$absoluteDirString "
            } else {
                // (Turn left)/(Continue)
                val rDir = RelativeDirection.valueOf(relativeDir.name)

                subdirectionIcon = getRelativeDirectionIcon(rDir, applicationContext.resources)

                // Do not need TURN Continue
                if (rDir == RelativeDirection.RIGHT || rDir == RelativeDirection.LEFT) {
                    subDirectionText += applicationContext
                        .getString(R.string.step_by_step_non_transit_turn) + " "
                }

                subDirectionText += "$relativeDirString "

                if (rDir == RelativeDirection.CIRCLE_CLOCKWISE ||
                    rDir == RelativeDirection.CIRCLE_COUNTERCLOCKWISE
                ) {
                    if (step.exit != null) {
                        try {
                            val ordinal = getOrdinal(
                                Integer.parseInt(step.exit), applicationContext.resources
                            )
                            if (ordinal != null) {
                                subDirectionText += "$ordinal "
                            } else {
                                subDirectionText += applicationContext
                                    .getString(R.string.step_by_step_non_transit_roundabout_number) +
                                    " " + ordinal + " "
                            }
                        } catch (e: NumberFormatException) {
                            // If is not a step_by_step_non_transit_roundabout_number and is not null
                            // is better to try to display it
                            subDirectionText += step.exit + " "
                        }
                        subDirectionText += applicationContext
                            .getString(R.string.step_by_step_non_transit_roundabout_exit) + " "
                        streetConnector = applicationContext
                            .getString(R.string.step_by_step_non_transit_connector_street_name_roundabout)
                    }
                }
            }

            subDirectionText += streetConnector + " " +
                getLocalizedStreetName(streetName, applicationContext.resources) + " "

            subDirectionText += "\n[" + ConversionUtils
                .getFormattedDistance(step.distance, applicationContext) + " ]"

            dir.directionText = subDirectionText

            dir.icon = subdirectionIcon

            // Add new sub-direction
            subDirections.add(dir)
        }

        direction.subDirections = subDirections

        return direction
    }

    private fun generateTransitDirections(leg: Leg): ArrayList<Direction> {
        val directions = ArrayList<Direction>(2)
        directions.add(generateTransitSubdirection(leg, true))
        directions.add(generateTransitSubdirection(leg, false))
        return directions
    }

    fun generateTransitSubdirection(leg: Leg, isOnDirection: Boolean): Direction {
        val direction = Direction()
        direction.isRealTimeInfo = leg.realTime

        // Set icon
        val mode = getLocalizedMode(TraverseMode.valueOf(leg.mode), applicationContext.resources)
        val modeIcon: Int
        val agencyName = leg.agencyName
        val from = leg.from
        val to = leg.to
        var newTimeMillis = 0L
        var oldTimeMillis = 0L

        // As a work-around for #662, we always use routeShortName and not tripShortName
        val shortName = leg.routeShortName

        val route = ConversionUtils.getRouteLongNameSafe(leg.routeLongName, shortName, true)

        direction.isTransit = true

        val action: String
        // Nullable: Place.name may be null, and the legacy Java concatenated it through
        // (rendering "null") rather than crashing — String?.plus preserves that.
        var placeAndHeadsign: String?
        var extra = ""

        if (isOnDirection) {
            action = applicationContext.getString(R.string.step_by_step_transit_get_on)
            placeAndHeadsign = from.name
            val modeSet = TraverseModeSet(leg.mode)
            modeIcon = getModeIcon(modeSet)
            newTimeMillis = leg.startTime.toLong()
            oldTimeMillis = newTimeMillis - leg.departureDelay * 1000L

            // Only onDirection has subdirection (list of stops in between)
            val stopsInBetween = ArrayList<Place>()
            if (leg.intermediateStops != null && !leg.intermediateStops.isEmpty()) {
                stopsInBetween.addAll(leg.intermediateStops)
            } else if (leg.stop != null && !leg.stop.isEmpty()) {
                stopsInBetween.addAll(leg.stop)
            }
            // sub-direction
            val stopIcon = getStopIcon(modeSet)
            val subDirections = ArrayList<Direction>()
            for (i in stopsInBetween.indices) {
                val subDirection = Direction()

                val stop = stopsInBetween[i]
                val extraStopInformation = stop.stopCode
                var subDirectionText = "${i + 1}. ${stop.name}"
                if (!TextUtils.isEmpty(extraStopInformation)) {
                    subDirectionText += " ($extraStopInformation)"
                }
                subDirection.directionText = subDirectionText
                subDirection.icon = stopIcon

                subDirections.add(subDirection)
            }
            direction.subDirections = subDirections

            if (stopsInBetween.size > 0) {
                var connector =
                    applicationContext.getString(R.string.step_by_step_transit_stops_in_between)
                if (stopsInBetween.size == 1) {
                    connector = applicationContext
                        .getString(R.string.step_by_step_transit_stops_in_between_singular)
                }
                extra = "${stopsInBetween.size} $connector"
            }

            if (!TextUtils.isEmpty(leg.headsign)) {
                placeAndHeadsign += " " + applicationContext
                    .getString(R.string.step_by_step_transit_connector_headsign) + " " + leg.headsign
            }
        } else {
            action = applicationContext.getString(R.string.step_by_step_transit_get_off)
            placeAndHeadsign = to.name
            modeIcon = -1
            newTimeMillis = leg.endTime.toLong()
            oldTimeMillis = newTimeMillis - leg.arrivalDelay * 1000L
        }

        direction.icon = modeIcon
        direction.placeAndHeadsign = applicationContext
            .getString(R.string.step_by_step_transit_connector_stop_name) + " " + placeAndHeadsign
        direction.service = "$action $mode $route"
        direction.agency = agencyName
        direction.extra = extra

        if (leg.realTime) {
            val newTimeString = ConversionUtils.getTimeUpdated(
                applicationContext, leg.agencyTimeZoneOffset, oldTimeMillis, newTimeMillis
            )
            direction.newTime = newTimeString
        }

        val oldTimeString = SpannableString(
            ConversionUtils.getTimeWithContext(
                applicationContext, leg.agencyTimeZoneOffset, oldTimeMillis, true
            )
        )
        direction.oldTime = oldTimeString

        return direction
    }

    /**
     * @return the totalTimeTraveled
     */
    fun getTotalTimeTraveled(context: Context): Double {
        if (legs.isEmpty()) {
            return 0.0
        }

        val legStart = legs[0]
        val startTimeText = legStart.startTime
        val legEnd = legs[legs.size - 1]
        val endTimeText = legEnd.endTime

        totalTimeTraveled = ConversionUtils.getDuration(startTimeText, endTimeText, context)

        return totalTimeTraveled
    }

    /* Added for Trip Plan titles */

    private fun getTransitTitle(leg: Leg): String? {
        // As a work-around for #662, we don't use leg.tripShortName
        return arrayOf(leg.routeShortName, leg.route, leg.routeId).firstOrNull { !it.isNullOrEmpty() }
    }

    val itineraryTitle: String
        get() {
            if (legs.size == 1) {
                val mode = TraverseMode.valueOf(legs[0].mode)
                if (!mode.isTransit) {
                    // getLocalizedMode only names transit modes and WALK; for a lone bicycle/car leg
                    // it returns null, so fall through to the general labeling below (which tags a
                    // bicycle leg with the bikeshare label) rather than crashing.
                    getLocalizedMode(mode, applicationContext.resources)?.let { return it }
                }
            }

            val tokens = ArrayList<String?>()

            for (leg in legs) {
                val traverseMode = TraverseMode.valueOf(leg.mode)
                if (traverseMode.isTransit) {
                    tokens.add(getTransitTitle(leg))
                } else {
                    if (traverseMode == TraverseMode.BICYCLE) {
                        tokens.add(applicationContext.getString(R.string.transit_directions_bikeshare_label))
                    }
                }
            }
            return TextUtils.join(", ", tokens)
        }

    companion object {

        private const val TAG = "DirectionsGenerator"

        private fun getOrdinal(number: Int, resources: Resources): String? {
            return when (number) {
                1 -> resources.getString(R.string.step_by_step_non_transit_roundabout_ordinal_first)
                2 -> resources.getString(R.string.step_by_step_non_transit_roundabout_ordinal_second)
                3 -> resources.getString(R.string.step_by_step_non_transit_roundabout_ordinal_third)
                4 -> resources.getString(R.string.step_by_step_non_transit_roundabout_ordinal_fourth)
                5 -> resources.getString(R.string.step_by_step_non_transit_roundabout_ordinal_fifth)
                6 -> resources.getString(R.string.step_by_step_non_transit_roundabout_ordinal_sixth)
                7 -> resources.getString(R.string.step_by_step_non_transit_roundabout_ordinal_seventh)
                8 -> resources.getString(R.string.step_by_step_non_transit_roundabout_ordinal_eighth)
                9 -> resources.getString(R.string.step_by_step_non_transit_roundabout_ordinal_ninth)
                10 -> resources.getString(R.string.step_by_step_non_transit_roundabout_ordinal_tenth)
                else -> null
            }
        }

        // Dirty fix to avoid the presence of names for unnamed streets (as road, track, etc.) for
        // other languages than English
        @JvmStatic
        fun getLocalizedStreetName(streetName: String?, resources: Resources): String {
            if (streetName == null) {
                return resources.getString(R.string.street_type_sidewalk)
            }
            return when {
                streetName == "bike path" -> resources.getString(R.string.street_type_bike_path)
                streetName == "open area" -> resources.getString(R.string.street_type_open_area)
                streetName == "path" -> resources.getString(R.string.street_type_path)
                streetName == "bridleway" -> resources.getString(R.string.street_type_bridleway)
                streetName == "footpath" -> resources.getString(R.string.street_type_footpath)
                streetName == "platform" -> resources.getString(R.string.street_type_platform)
                streetName == "footbridge" -> resources.getString(R.string.street_type_footbridge)
                streetName == "underpass" -> resources.getString(R.string.street_type_underpass)
                streetName == "road" -> resources.getString(R.string.street_type_road)
                streetName == "ramp" -> resources.getString(R.string.street_type_ramp)
                streetName == "link" -> resources.getString(R.string.street_type_link)
                streetName == "service road" -> resources.getString(R.string.street_type_service_road)
                streetName == "alley" -> resources.getString(R.string.street_type_alley)
                streetName == "parking aisle" -> resources.getString(R.string.street_type_parking_aisle)
                streetName == "byway" -> resources.getString(R.string.street_type_byway)
                streetName == "track" -> resources.getString(R.string.street_type_track)
                streetName == "sidewalk" -> resources.getString(R.string.street_type_sidewalk)
                streetName.startsWith("osm:node:") -> resources.getString(R.string.street_type_sidewalk)
                streetName == "steps" -> resources.getString(R.string.street_type_steps)
                else -> streetName
            }
        }

        @JvmStatic
        fun getLocalizedRelativeDir(relDir: RelativeDirection?, resources: Resources): String? {
            if (relDir != null) {
                return when (relDir) {
                    RelativeDirection.CIRCLE_CLOCKWISE ->
                        resources.getString(R.string.step_by_step_non_transit_dir_relative_circle_clockwise)
                    RelativeDirection.CIRCLE_COUNTERCLOCKWISE ->
                        resources.getString(R.string.step_by_step_non_transit_dir_relative_circle_counterclockwise)
                    RelativeDirection.CONTINUE ->
                        resources.getString(R.string.step_by_step_non_transit_dir_relative_continue)
                    RelativeDirection.DEPART ->
                        resources.getString(R.string.step_by_step_non_transit_dir_relative_depart)
                    RelativeDirection.ELEVATOR ->
                        resources.getString(R.string.step_by_step_non_transit_dir_relative_elevator)
                    RelativeDirection.HARD_LEFT ->
                        resources.getString(R.string.step_by_step_non_transit_dir_relative_hard_left)
                    RelativeDirection.HARD_RIGHT ->
                        resources.getString(R.string.step_by_step_non_transit_dir_relative_hard_right)
                    RelativeDirection.LEFT ->
                        resources.getString(R.string.step_by_step_non_transit_dir_relative_left)
                    RelativeDirection.RIGHT ->
                        resources.getString(R.string.step_by_step_non_transit_dir_relative_right)
                    RelativeDirection.SLIGHTLY_LEFT ->
                        resources.getString(R.string.step_by_step_non_transit_dir_relative_slightly_left)
                    RelativeDirection.SLIGHTLY_RIGHT ->
                        resources.getString(R.string.step_by_step_non_transit_dir_relative_slightly_right)
                    RelativeDirection.UTURN_LEFT ->
                        resources.getString(R.string.step_by_step_non_transit_dir_relative_uturn_left)
                    RelativeDirection.UTURN_RIGHT ->
                        resources.getString(R.string.step_by_step_non_transit_dir_relative_uturn_right)
                    else -> null
                }
            }
            return null
        }

        @JvmStatic
        fun getLocalizedAbsoluteDir(absDir: AbsoluteDirection?, resources: Resources): String? {
            if (absDir != null) {
                return when (absDir) {
                    AbsoluteDirection.EAST ->
                        resources.getString(R.string.step_by_step_non_transit_dir_absolute_east)
                    AbsoluteDirection.NORTH ->
                        resources.getString(R.string.step_by_step_non_transit_dir_absolute_north)
                    AbsoluteDirection.NORTHEAST ->
                        resources.getString(R.string.step_by_step_non_transit_dir_absolute_northeast)
                    AbsoluteDirection.NORTHWEST ->
                        resources.getString(R.string.step_by_step_non_transit_dir_absolute_northwest)
                    AbsoluteDirection.SOUTH ->
                        resources.getString(R.string.step_by_step_non_transit_dir_absolute_south)
                    AbsoluteDirection.SOUTHEAST ->
                        resources.getString(R.string.step_by_step_non_transit_dir_absolute_southeast)
                    AbsoluteDirection.SOUTHWEST ->
                        resources.getString(R.string.step_by_step_non_transit_dir_absolute_southwest)
                    AbsoluteDirection.WEST ->
                        resources.getString(R.string.step_by_step_non_transit_dir_absolute_west)
                    else -> null
                }
            }
            return null
        }

        @JvmStatic
        fun getLocalizedMode(mode: TraverseMode?, resources: Resources): String? {
            if (mode != null) {
                return when (mode) {
                    TraverseMode.TRAM -> resources.getString(R.string.step_by_step_transit_mode_tram)
                    TraverseMode.SUBWAY -> resources.getString(R.string.step_by_step_transit_mode_subway)
                    TraverseMode.RAIL -> resources.getString(R.string.step_by_step_transit_mode_rail)
                    TraverseMode.BUS -> resources.getString(R.string.step_by_step_transit_mode_bus)
                    TraverseMode.FERRY -> resources.getString(R.string.step_by_step_transit_mode_ferry)
                    TraverseMode.CABLE_CAR ->
                        resources.getString(R.string.step_by_step_transit_mode_cable_car)
                    TraverseMode.GONDOLA ->
                        resources.getString(R.string.step_by_step_transit_mode_gondola)
                    TraverseMode.FUNICULAR ->
                        resources.getString(R.string.step_by_step_transit_mode_funicular)
                    TraverseMode.WALK ->
                        resources.getString(R.string.step_by_step_non_transit_mode_walk_action)
                    else -> null
                }
            }
            return null
        }

        /**
         * Gets the mode icon for the given mode
         *
         * @return the mode icon for the given mode
         */
        @JvmStatic
        fun getModeIcon(mode: TraverseModeSet): Int {
            // Order matters: the first matching mode wins (e.g. a SUBWAY+TRAM set resolves to
            // subway, not railway), so SUBWAY must stay ahead of TRAM.
            return when {
                mode.contains(TraverseMode.BUS) -> R.drawable.ic_maps_directions_bus
                mode.contains(TraverseMode.RAIL) -> R.drawable.ic_directions_railway
                mode.contains(TraverseMode.FERRY) || mode.contains(TraverseMode.GONDOLA) ->
                    R.drawable.ic_directions_boat
                mode.contains(TraverseMode.SUBWAY) -> R.drawable.ic_directions_subway
                mode.contains(TraverseMode.TRAM) -> R.drawable.ic_directions_railway
                mode.contains(TraverseMode.WALK) -> R.drawable.ic_directions_walk
                mode.contains(TraverseMode.BICYCLE) -> R.drawable.ic_directions_bike
                else -> {
                    Log.d(TAG, "No icon for mode set: $mode")
                    -1
                }
            }
        }

        /**
         * Get the transit stop icon for the given mode
         *
         * @return the transit stop icon for the given mode
         */
        @JvmStatic
        fun getStopIcon(mode: TraverseModeSet): Int {
            if (mode.contains(TraverseMode.BUS) || mode.contains(TraverseMode.RAIL)) {
                return R.drawable.ic_stop_flag_triangle
            }
            // Just use the mode icon
            return getModeIcon(mode)
        }

        @JvmStatic
        fun getRelativeDirectionIcon(relDir: RelativeDirection, resources: Resources): Int {
            return when (relDir) {
                RelativeDirection.CIRCLE_CLOCKWISE -> R.drawable.ic_rotary_clockwise
                RelativeDirection.CIRCLE_COUNTERCLOCKWISE -> R.drawable.ic_rotary_counterclockwise
                RelativeDirection.CONTINUE -> R.drawable.ic_continue
                RelativeDirection.DEPART -> R.drawable.ic_depart
                RelativeDirection.ELEVATOR -> R.drawable.ic_elevator
                RelativeDirection.HARD_LEFT -> R.drawable.ic_turn_sharp_left
                RelativeDirection.HARD_RIGHT -> R.drawable.ic_turn_sharp_right
                RelativeDirection.LEFT -> R.drawable.ic_turn_left
                RelativeDirection.RIGHT -> R.drawable.ic_turn_right
                RelativeDirection.SLIGHTLY_LEFT -> R.drawable.ic_turn_slight_left
                RelativeDirection.SLIGHTLY_RIGHT -> R.drawable.ic_turn_slight_right
                RelativeDirection.UTURN_LEFT -> R.drawable.ic_uturn_left
                RelativeDirection.UTURN_RIGHT -> R.drawable.ic_uturn_right
                else -> {
                    Log.d(TAG, "No icon for direction: $relDir")
                    -1
                }
            }
        }
    }
}
