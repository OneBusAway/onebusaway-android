/*
 * Copyright (C) 2018 Sean J. Barbeau (sjbarbeau@gmail.com)
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
package org.onebusaway.android.io.elements;

/**
 * The occupancy of the vehicle, based on the OccupancyStatus element from GTFS-realtime
 * (https://github.com/google/transit/blob/master/gtfs-realtime/spec/en/reference.md#enum-occupancystatus)
 * <p>
 * Also supports WMATA's extensions for occupancy of "MANY_SEATS_AVAILABLE", "FEW_SEATS_AVAILABLE",
 * "MANY_SEATS_AVAILABLE" - #1059
 */
public enum Occupancy {
    EMPTY("EMPTY"),
    MANY_SEATS_AVAILABLE("MANY_SEATS_AVAILABLE"),
    FEW_SEATS_AVAILABLE("FEW_SEATS_AVAILABLE"),
    STANDING_ROOM_ONLY("STANDING_ROOM_ONLY"),
    CRUSHED_STANDING_ROOM_ONLY("CRUSHED_STANDING_ROOM_ONLY"),
    FULL("FULL"),
    NOT_ACCEPTING_PASSENGERS("NOT_ACCEPTING_PASSENGERS");

    private final String mOccupancy;

    Occupancy(String occupancy) {
        mOccupancy = occupancy;
    }

    public String toString() {
        return mOccupancy;
    }

    /**
     * Converts from the string representation of occupancy to the enumeration, or null if occupancy isn't provided
     *
     * @param occupancy the string representation of occupancy
     * @return the occupancy enumeration, or null if occupancy isn't provided
     */
    public static Occupancy fromString(String occupancy) {
        switch (occupancy) {
            case "EMPTY":
                return EMPTY;
            case "MANY_SEATS_AVAILABLE":
                return MANY_SEATS_AVAILABLE;
            case "FEW_SEATS_AVAILABLE":
                return FEW_SEATS_AVAILABLE;
            case "STANDING_ROOM_ONLY":
                return STANDING_ROOM_ONLY;
            case "CRUSHED_STANDING_ROOM_ONLY":
                return CRUSHED_STANDING_ROOM_ONLY;
            case "FULL":
                return FULL;
            case "NOT_ACCEPTING_PASSENGERS":
                return NOT_ACCEPTING_PASSENGERS;
            default:
                return null;
        }
    }
}
