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
package org.onebusaway.android.io.request;

import org.onebusaway.android.io.elements.ObaReferences;
import org.onebusaway.android.io.elements.ObaReferencesElement;
import org.onebusaway.android.io.elements.ObaShape;
import org.onebusaway.android.io.elements.ObaShapeElement;
import org.onebusaway.android.io.elements.ObaStop;
import org.onebusaway.android.io.elements.ObaStopGroup;
import org.onebusaway.android.io.elements.ObaStopGrouping;

import java.util.Arrays;
import java.util.List;

/**
 * Response object for ObaStopForRouteRequest requests.
 *
 * @author Paul Watts (paulcwatts@gmail.com)
 */
public final class ObaStopsForRouteResponse extends ObaResponseWithRefs {

    private static final class Entry {

        private static final Entry EMPTY_OBJECT = new Entry();

        private final String routeId;

        private final String[] stopIds;

        private final ObaStopGrouping[] stopGroupings;

        private final ObaShapeElement[] polylines;

        Entry() {
            stopIds = new String[]{};
            stopGroupings = ObaStopGrouping.EMPTY_ARRAY;
            polylines = ObaShapeElement.EMPTY_ARRAY;
            routeId = null;
        }
    }

    private static final class Data {

        private static final Data EMPTY_OBJECT = new Data();

        private final ObaReferencesElement references = ObaReferencesElement.EMPTY_OBJECT;

        private final Entry entry = Entry.EMPTY_OBJECT;
    }

    private final Data data;

    private ObaStopsForRouteResponse() {
        data = Data.EMPTY_OBJECT;
    }

    /**
     * Returns the route this response describes
     */
    public String getRouteId() {
        return data.entry.routeId;
    }

    /**
     * Returns the list of dereferenced stops.
     */
    public List<ObaStop> getStops() {
        return data.references.getStops(data.entry.stopIds);
    }

    /**
     * @return The list of shapes, if they exist; otherwise returns an empty list.
     */
    public ObaShape[] getShapes() {
        return data.entry.polylines;
    }

    /**
     * @return Returns a collection of stops grouped into useful collections.
     */
    public ObaStopGrouping[] getStopGroupings() {
        return data.entry.stopGroupings;
    }

    /**
     * Search for a stop within the stop groups
     *
     * @return Returns the first ObaStopGroup that contains the specified stop
     */
    public ObaStopGroup getGroupForStop(String stopId) {
        ObaStopGrouping[] stopGroupings = getStopGroupings();
        if (stopGroupings == null) {
            return null;
        }

        for (ObaStopGrouping grouping : stopGroupings) {
            ObaStopGroup[] stopGroups = grouping.getStopGroups();
            if (stopGroups == null) {
                continue;
            }

            for (ObaStopGroup stopGroup : stopGroups) {
                if (Arrays.asList(stopGroup.getStopIds()).contains(stopId)) {
                    return stopGroup;
                }
            }
        }

        return null;
    }

    @Override
    protected ObaReferences getRefs() {
        return data.references;
    }
}
