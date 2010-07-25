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
package com.joulespersecond.oba.request;

import com.joulespersecond.oba.elements.ObaReferences;
import com.joulespersecond.oba.elements.ObaReferencesElement;
import com.joulespersecond.oba.elements.ObaShape;
import com.joulespersecond.oba.elements.ObaShapeElement;
import com.joulespersecond.oba.elements.ObaStop;
import com.joulespersecond.oba.elements.ObaStopGrouping;

import java.util.List;

/**
 * Response object for ObaStopForRouteRequest requests.
 * @author Paul Watts (paulcwatts@gmail.com)
 */
public final class ObaStopsForRouteResponse extends ObaResponseWithRefs {
    private static final class Entry {
        private static final Entry EMPTY_OBJECT = new Entry();

        private final String[] stopIds;
        private final ObaStopGrouping[] stopGroupings;
        private final ObaShapeElement[] polylines;

        Entry() {
            stopIds = new String[] {};
            stopGroupings = ObaStopGrouping.EMPTY_ARRAY;
            polylines = ObaShapeElement.EMPTY_ARRAY;
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

    @Override
    protected ObaReferences getRefs() {
        return data.references;
    }
}
