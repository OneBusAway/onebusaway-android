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
package com.joulespersecond.oba;

import java.util.ArrayList;
import java.util.List;

public final class ObaStopGroup {
    public static final ObaStopGroup EMPTY_OBJECT = new ObaStopGroup();
    public static final ObaArray<ObaStopGroup> EMPTY_ARRAY = new ObaArray<ObaStopGroup>();

    private static final class StopGroupName {
        private final String type;
        private final List<String> names;

        private StopGroupName() {
            type = "";
            names = null;
        }
        String getType() {
            return type;
        }
        List<String> getNames() {
            return (names != null) ? names : new ArrayList<String>();
        }
    }
    private final List<String> stopIds;
    //private final ObaArray<ObaPolyline> polylines;
    private final StopGroupName name;

    public static final String TYPE_DESTINATION = "destination";

    /**
     * Constructor.
     */
    ObaStopGroup() {
        stopIds = null;
        //polylines = ObaPolyline.EMPTY_ARRAY;
        name = null;
    }

    /**
     * Returns the type of grouping.
     *
     * @return One of the TYPE_* string constants.
     */
    public String getType() {
        return (name != null) ? name.getType() : "";
    }

    /**
     * Returns the name of this grouping, or the empty string.
     *
     * @return The name of this grouping, or the empty string.
     */
    public String getName() {
        if (name == null) {
            return "";
        }
        List<String> names = name.getNames();
        if (names.size() > 0) {
            return names.get(0);
        }
        return "";
    }

    /**
     * Returns a list of StopIDs for this grouping.
     *
     * @return The stop IDs for this grouping.
     */
    public List<String> getStopIds() {
        return stopIds;
    }

    /**
     * Returns the array of polylines.
     *
     * @return The array of polylines, or an empty array.
     */
    public ObaArray<ObaPolyline> getPolylines() {
        //return polylines;
        return ObaPolyline.EMPTY_ARRAY;
    }

    @Override
    public String toString() {
        return ObaApi.getGson().toJson(this);
    }
}
