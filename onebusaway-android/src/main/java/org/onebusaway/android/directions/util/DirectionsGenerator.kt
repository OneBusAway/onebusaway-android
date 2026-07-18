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
import org.onebusaway.android.BuildConfig
import org.onebusaway.android.R
import org.onebusaway.android.directions.model.Direction
import org.onebusaway.android.directions.model.TripAbsoluteDirection
import org.onebusaway.android.directions.model.TripLeg
import org.onebusaway.android.directions.model.TripMode
import org.onebusaway.android.directions.model.TripPlace
import org.onebusaway.android.directions.model.TripRelativeDirection
import org.onebusaway.android.time.ServerTime

/**
 * Generates a set of step-by-step directions that can be shown to the user from a list of trip
 * legs
 *
 * @author Khoa Tran
 */
class DirectionsGenerator(
    private val legs: List<TripLeg>,
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

            if (leg.mode?.isOnStreetNonTransit == true) {
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

    private fun generateNonTransitDirections(leg: TripLeg): Direction {
        val direction = Direction()

        // Get appropriate action and icon
        var action =
            applicationContext.getString(R.string.step_by_step_non_transit_mode_walk_action)
        val mode = leg.mode
        val icon = getModeIcon(mode)
        if (mode == TripMode.BICYCLE) {
            action = applicationContext.getString(R.string.step_by_step_non_transit_mode_bicycle_action)
        } else if (mode == TripMode.CAR) {
            action = applicationContext.getString(R.string.step_by_step_non_transit_mode_car_action)
        }

        direction.icon = icon

        // Focus the non-transit step on where it starts (the leg's origin).
        direction.focusLat = leg.from.lat
        direction.focusLon = leg.from.lon

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
        val legDuration = leg.duration.inWholeSeconds
        if (!TextUtils.isEmpty(extraStopInformation)) {
            mainDirectionText += " ($extraStopInformation)"
        }
        mainDirectionText += "\n[" + ConversionUtils
            .getFormattedDistance(leg.distance, applicationContext) + " - " +
            ConversionUtils.getFormattedDurationTextNoSeconds(legDuration, false, applicationContext) +
            " ]"
        direction.directionText = mainDirectionText

        // Sub-direction
        val walkSteps = leg.steps

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
                subdirectionIcon = getRelativeDirectionIcon(relativeDir, applicationContext.resources)

                // Do not need TURN Continue
                if (relativeDir == TripRelativeDirection.RIGHT || relativeDir == TripRelativeDirection.LEFT) {
                    subDirectionText += applicationContext
                        .getString(R.string.step_by_step_non_transit_turn) + " "
                }

                subDirectionText += "$relativeDirString "

                if (relativeDir == TripRelativeDirection.CIRCLE_CLOCKWISE ||
                    relativeDir == TripRelativeDirection.CIRCLE_COUNTERCLOCKWISE
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

            // Each turn-by-turn step carries its own point, so a tapped sub-step focuses the map on it.
            dir.focusLat = step.lat
            dir.focusLon = step.lon

            // Add new sub-direction
            subDirections.add(dir)
        }

        direction.subDirections = subDirections

        return direction
    }

    private fun generateTransitDirections(leg: TripLeg): ArrayList<Direction> {
        val directions = ArrayList<Direction>(2)
        directions.add(generateTransitSubdirection(leg, true))
        directions.add(generateTransitSubdirection(leg, false))
        return directions
    }

    fun generateTransitSubdirection(leg: TripLeg, isOnDirection: Boolean): Direction {
        val direction = Direction()
        direction.isRealTimeInfo = leg.realTime

        // Set icon
        val mode = getLocalizedMode(leg.mode, applicationContext.resources)
        val modeIcon: Int
        val agencyName = leg.agencyName
        val from = leg.from
        val to = leg.to
        var newTimeMillis = ServerTime(0L)
        var oldTimeMillis = ServerTime(0L)

        // As a work-around for #662, we always use routeShortName and not tripShortName
        val shortName = leg.routeShortName

        val route = ConversionUtils.getRouteLongNameSafe(leg.routeLongName, shortName, true)

        direction.isTransit = true

        val action: String
        // Nullable: Place.name may be null, and the legacy Java concatenated it through
        // (rendering "null") rather than crashing — String?.plus preserves that.
        var placeAndHeadsign: String?
        var extra = ""

        // The "get on" step focuses the boarding stop, the "get off" step the alighting stop.
        direction.focusLat = if (isOnDirection) from.lat else to.lat
        direction.focusLon = if (isOnDirection) from.lon else to.lon

        if (isOnDirection) {
            action = applicationContext.getString(R.string.step_by_step_transit_get_on)
            placeAndHeadsign = from.name
            modeIcon = getModeIcon(leg.mode)
            newTimeMillis = leg.startTime
            oldTimeMillis = newTimeMillis - leg.departureDelay

            // Only onDirection has subdirection (list of stops in between)
            val stopsInBetween = ArrayList<TripPlace>()
            if (leg.intermediateStops != null && !leg.intermediateStops.isEmpty()) {
                stopsInBetween.addAll(leg.intermediateStops)
            } else if (leg.stop != null && !leg.stop.isEmpty()) {
                stopsInBetween.addAll(leg.stop)
            }
            // sub-direction
            val stopIcon = getStopIcon(leg.mode)
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
                subDirection.focusLat = stop.lat
                subDirection.focusLon = stop.lon

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
            newTimeMillis = leg.endTime
            oldTimeMillis = newTimeMillis - leg.arrivalDelay
        }

        direction.icon = modeIcon
        direction.placeAndHeadsign = applicationContext
            .getString(R.string.step_by_step_transit_connector_stop_name) + " " + placeAndHeadsign
        direction.service = "$action $mode $route"
        direction.agency = agencyName
        direction.extra = extra

        if (leg.realTime) {
            val newTimeString = ConversionUtils.getTimeUpdated(
                applicationContext, leg.agencyTimeZoneOffset, oldTimeMillis.epochMs, newTimeMillis.epochMs
            )
            direction.newTime = newTimeString
        }

        val oldTimeString = SpannableString(
            ConversionUtils.getTimeWithContext(
                applicationContext, leg.agencyTimeZoneOffset, oldTimeMillis.epochMs, true
            )
        )
        direction.oldTime = oldTimeString

        return direction
    }

    /* Added for Trip Plan titles */

    private fun getTransitTitle(leg: TripLeg): String? {
        // As a work-around for #662, we don't use leg.tripShortName
        return arrayOf(leg.routeShortName, leg.route, leg.routeId).firstOrNull { !it.isNullOrEmpty() }
    }

    val itineraryTitle: String
        get() {
            if (legs.size == 1) {
                val mode = legs[0].mode
                if (mode == null || !mode.isTransit) {
                    // getLocalizedMode only names transit modes and WALK; for a lone bicycle/car leg
                    // it returns null, so fall through to the general labeling below (which tags a
                    // bicycle leg with the bikeshare label) rather than crashing.
                    getLocalizedMode(mode, applicationContext.resources)?.let { return it }
                }
            }

            val tokens = ArrayList<String?>()

            for (leg in legs) {
                val mode = leg.mode
                if (mode?.isTransit == true) {
                    tokens.add(getTransitTitle(leg))
                } else {
                    if (mode == TripMode.BICYCLE) {
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
        fun getLocalizedRelativeDir(relDir: TripRelativeDirection?, resources: Resources): String? {
            if (relDir != null) {
                return when (relDir) {
                    TripRelativeDirection.CIRCLE_CLOCKWISE ->
                        resources.getString(R.string.step_by_step_non_transit_dir_relative_circle_clockwise)
                    TripRelativeDirection.CIRCLE_COUNTERCLOCKWISE ->
                        resources.getString(R.string.step_by_step_non_transit_dir_relative_circle_counterclockwise)
                    TripRelativeDirection.CONTINUE ->
                        resources.getString(R.string.step_by_step_non_transit_dir_relative_continue)
                    TripRelativeDirection.DEPART ->
                        resources.getString(R.string.step_by_step_non_transit_dir_relative_depart)
                    TripRelativeDirection.ELEVATOR ->
                        resources.getString(R.string.step_by_step_non_transit_dir_relative_elevator)
                    TripRelativeDirection.HARD_LEFT ->
                        resources.getString(R.string.step_by_step_non_transit_dir_relative_hard_left)
                    TripRelativeDirection.HARD_RIGHT ->
                        resources.getString(R.string.step_by_step_non_transit_dir_relative_hard_right)
                    TripRelativeDirection.LEFT ->
                        resources.getString(R.string.step_by_step_non_transit_dir_relative_left)
                    TripRelativeDirection.RIGHT ->
                        resources.getString(R.string.step_by_step_non_transit_dir_relative_right)
                    TripRelativeDirection.SLIGHTLY_LEFT ->
                        resources.getString(R.string.step_by_step_non_transit_dir_relative_slightly_left)
                    TripRelativeDirection.SLIGHTLY_RIGHT ->
                        resources.getString(R.string.step_by_step_non_transit_dir_relative_slightly_right)
                    TripRelativeDirection.UTURN_LEFT ->
                        resources.getString(R.string.step_by_step_non_transit_dir_relative_uturn_left)
                    TripRelativeDirection.UTURN_RIGHT ->
                        resources.getString(R.string.step_by_step_non_transit_dir_relative_uturn_right)
                }
            }
            return null
        }

        @JvmStatic
        fun getLocalizedAbsoluteDir(absDir: TripAbsoluteDirection?, resources: Resources): String? {
            if (absDir != null) {
                return when (absDir) {
                    TripAbsoluteDirection.EAST ->
                        resources.getString(R.string.step_by_step_non_transit_dir_absolute_east)
                    TripAbsoluteDirection.NORTH ->
                        resources.getString(R.string.step_by_step_non_transit_dir_absolute_north)
                    TripAbsoluteDirection.NORTHEAST ->
                        resources.getString(R.string.step_by_step_non_transit_dir_absolute_northeast)
                    TripAbsoluteDirection.NORTHWEST ->
                        resources.getString(R.string.step_by_step_non_transit_dir_absolute_northwest)
                    TripAbsoluteDirection.SOUTH ->
                        resources.getString(R.string.step_by_step_non_transit_dir_absolute_south)
                    TripAbsoluteDirection.SOUTHEAST ->
                        resources.getString(R.string.step_by_step_non_transit_dir_absolute_southeast)
                    TripAbsoluteDirection.SOUTHWEST ->
                        resources.getString(R.string.step_by_step_non_transit_dir_absolute_southwest)
                    TripAbsoluteDirection.WEST ->
                        resources.getString(R.string.step_by_step_non_transit_dir_absolute_west)
                }
            }
            return null
        }

        @JvmStatic
        fun getLocalizedMode(mode: TripMode?, resources: Resources): String? {
            if (mode != null) {
                return when (mode) {
                    TripMode.TRAM -> resources.getString(R.string.step_by_step_transit_mode_tram)
                    TripMode.SUBWAY -> resources.getString(R.string.step_by_step_transit_mode_subway)
                    TripMode.RAIL -> resources.getString(R.string.step_by_step_transit_mode_rail)
                    TripMode.BUS -> resources.getString(R.string.step_by_step_transit_mode_bus)
                    TripMode.FERRY -> resources.getString(R.string.step_by_step_transit_mode_ferry)
                    TripMode.CABLE_CAR ->
                        resources.getString(R.string.step_by_step_transit_mode_cable_car)
                    TripMode.GONDOLA ->
                        resources.getString(R.string.step_by_step_transit_mode_gondola)
                    TripMode.FUNICULAR ->
                        resources.getString(R.string.step_by_step_transit_mode_funicular)
                    TripMode.WALK ->
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
        fun getModeIcon(mode: TripMode?): Int {
            // Order matters: the first matching mode wins, matching the legacy TraverseModeSet
            // priority (e.g. a SUBWAY+TRAM set resolved to subway, not railway).
            return when (mode) {
                TripMode.BUS -> R.drawable.ic_bus
                TripMode.RAIL -> R.drawable.ic_directions_railway
                TripMode.FERRY, TripMode.GONDOLA -> R.drawable.ic_directions_boat
                TripMode.SUBWAY -> R.drawable.ic_directions_subway
                TripMode.TRAM -> R.drawable.ic_directions_railway
                TripMode.WALK -> R.drawable.ic_directions_walk
                TripMode.BICYCLE -> R.drawable.ic_directions_bike
                else -> {
                    if (BuildConfig.DEBUG) Log.d(TAG, "No icon for mode: $mode")
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
        fun getStopIcon(mode: TripMode?): Int {
            if (mode == TripMode.BUS || mode == TripMode.RAIL) {
                return R.drawable.stop_flag
            }
            // Just use the mode icon
            return getModeIcon(mode)
        }

        @JvmStatic
        fun getRelativeDirectionIcon(relDir: TripRelativeDirection, resources: Resources): Int {
            return when (relDir) {
                TripRelativeDirection.CIRCLE_CLOCKWISE -> R.drawable.ic_rotary_clockwise
                TripRelativeDirection.CIRCLE_COUNTERCLOCKWISE -> R.drawable.ic_rotary_counterclockwise
                TripRelativeDirection.CONTINUE -> R.drawable.ic_continue
                TripRelativeDirection.DEPART -> R.drawable.ic_depart
                TripRelativeDirection.ELEVATOR -> R.drawable.ic_elevator
                TripRelativeDirection.HARD_LEFT -> R.drawable.ic_turn_sharp_left
                TripRelativeDirection.HARD_RIGHT -> R.drawable.ic_turn_sharp_right
                TripRelativeDirection.LEFT -> R.drawable.ic_turn_left
                TripRelativeDirection.RIGHT -> R.drawable.ic_turn_right
                TripRelativeDirection.SLIGHTLY_LEFT -> R.drawable.ic_turn_slight_left
                TripRelativeDirection.SLIGHTLY_RIGHT -> R.drawable.ic_turn_slight_right
                TripRelativeDirection.UTURN_LEFT -> R.drawable.ic_uturn_left
                TripRelativeDirection.UTURN_RIGHT -> R.drawable.ic_uturn_right
                // No else: the when is exhaustive over TripRelativeDirection, so a future OTP enum
                // addition surfaces as a compile error here rather than a silent missing icon.
                // (The caller's -1 initializer still covers the "icon not set" case.)
            }
        }
    }
}
