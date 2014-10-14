/*
 * Copyright (C) 2010 Paul Watts (paulcwatts@gmail.com)
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

import android.location.Location;

/**
 * Interface defining a Stop element.
 * {@link http://code.google.com/p/onebusaway/wiki/OneBusAwayRestApi_StopElementV2}
 *
 * @author Paul Watts (paulcwatts@gmail.com)
 */
public interface ObaStop extends ObaElement {

    public static final int LOCATION_STOP = 0;
    public static final int LOCATION_STATION = 1;

    /**
     * Returns the passenger-facing stop identifier.
     *
     * @return The passenger-facing stop ID.
     */
    public String getStopCode();

    /**
     * Returns the passenger-facing name for the stop.
     *
     * @return The passenger-facing name for the stop.
     */
    public String getName();

    /**
     * Returns the location of the stop.
     *
     * @return The location of the stop, or null if it can't be converted to a Location.
     */
    public Location getLocation();

    /**
     * Returns the latitude of the stop as a double.
     *
     * @return The latitude of the stop, or 0 if it doesn't exist.
     */
    public double getLatitude();

    /**
     * Returns the longitude of the stop as a double.
     *
     * @return The longitude of the stop, or 0 if it doesn't exist.
     */
    public double getLongitude();

    /**
     * Returns the direction of the stop (ex "NW", "E").
     *
     * @return The direction of the stop.
     */
    public String getDirection();

    /**
     * @return The location type.
     */
    public int getLocationType();

    /**
     * @return The list of route IDs serving this stop.
     */
    public String[] getRouteIds();
}
