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
 * Interface for a Trip element
 * {@link http://code.google.com/p/onebusaway/wiki/OneBusAwayRestApi_TripElementV2}
 *
 * @author Paul Watts (paulcwatts@gmail.com)
 */
public interface ObaTrip extends ObaElement {

    public static final int DIRECTION_OUTBOUND = 0;
    public static final int DIRECTION_INBOUND = 1;

    /**
     * @return The short name for the trip.
     */
    public String getShortName();

    /**
     * @return The ID that defines the shape of the trip.
     */
    public String getShapeId();

    /**
     * @return A binary value that indicates the direction of travel.
     */
    public int getDirectionId();

    /**
     * @return An ID that uniquely identifies a set of dates for
     * which the service is available.
     */
    public String getServiceId();

    /**
     * @return The headsign for the trip.
     */
    public String getHeadsign();

    /**
     * @return The timezone for the trip.
     */
    public String getTimezone();

    /**
     * @return The route ID for the trip.
     */
    public String getRouteId();

    /**
     * @return The block ID for the trip
     */
    public String getBlockId();
}
