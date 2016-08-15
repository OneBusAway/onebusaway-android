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

/**
 * Interface defining a Route element.
 * {@link http://code.google.com/p/onebusaway/wiki/OneBusAwayRestApi_RouteElementV2}
 *
 * @author Paul Watts (paulcwatts@gmail.com)
 */
public interface ObaRoute extends ObaElement {

    public static final int TYPE_TRAM = 0;
    public static final int TYPE_SUBWAY = 1;
    public static final int TYPE_RAIL = 2;
    public static final int TYPE_BUS = 3;
    public static final int TYPE_FERRY = 4;
    public static final int TYPE_CABLECAR = 5;
    public static final int TYPE_GONDOLA = 6;
    public static final int TYPE_FUNICULAR = 7; // You can't spell "funicular" without "fun"!

    public static final int NUM_TYPES = 8;  // 8 types of transit supported by GTFS

    /**
     * Returns the short name of the route (ex. "10", "30").
     *
     * @return The short name of the route.
     */
    public String getShortName();

    /**
     * Returns the long name of the route (ex. "Sandpoint/QueenAnne")
     *
     * @return The long name of the route.
     */
    public String getLongName();

    /**
     * Returns the description of the route.
     */
    public String getDescription();

    /**
     * @return The type of route
     */
    public int getType();

    /**
     * Returns the Url to the route schedule.
     *
     * @return The url to the route schedule.
     */
    public String getUrl();

    /**
     * @return the integer representation of the Android color for the route line, or null if this
     * value is not included in the API response.
     */
    public Integer getColor();

    /**
     * @return the integer representation of the Android color for the route text, or null if this
     * value is not included in the API response
     */
    public Integer getTextColor();

    /**
     * @return The ID of the agency operating this route.
     */
    public String getAgencyId();
}
