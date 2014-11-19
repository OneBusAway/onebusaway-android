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

import org.onebusaway.android.io.ObaApi;


public final class ObaStopGrouping {

    public static final ObaStopGrouping EMPTY_OBJECT = new ObaStopGrouping();

    public static final ObaStopGrouping[] EMPTY_ARRAY = new ObaStopGrouping[]{};

    public static final String TYPE_DIRECTION = "direction";

    private final boolean ordered;

    private final String type;

    private final ObaStopGroup[] stopGroups;

    /**
     * Constructor.
     */
    ObaStopGrouping() {
        ordered = false;
        type = "";
        stopGroups = ObaStopGroup.EMPTY_ARRAY;
    }

    /**
     * Returns whether or not this grouping is ordered.
     *
     * @return A boolean indicating whether this grouping is ordered.
     */
    public boolean getOrdered() {
        return ordered;
    }

    /**
     * Returns the type of ordering.
     *
     * @return The type of ordering.
     */
    public String getType() {
        return type;
    }

    /**
     * Returns the list of stop groups.
     *
     * @return The list of stop groups.
     */
    public ObaStopGroup[] getStopGroups() {
        return stopGroups;
    }

    @Override
    public String toString() {
        return ObaApi.getSerializer(ObaStopGrouping.class).serialize(this);
    }
}
