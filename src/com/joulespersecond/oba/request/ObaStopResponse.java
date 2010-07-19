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

import com.google.android.maps.GeoPoint;
import com.joulespersecond.oba.elements.ObaReferences;
import com.joulespersecond.oba.elements.ObaRouteElement;
import com.joulespersecond.oba.elements.ObaStop;
import com.joulespersecond.oba.elements.ObaStopElement;

import java.util.List;

/**
 * Response object for ObaStopRequest requests.
 * @author Paul Watts (paulcwatts@gmail.com)
 */
public final class ObaStopResponse extends ObaResponse implements ObaStop {
    private static final class Data {
        private static final Data EMPTY_OBJECT = new Data();

        private final ObaReferences references = ObaReferences.EMPTY_OBJECT;
        private final ObaStopElement entry = ObaStopElement.EMPTY_OBJECT;
    }
    private final Data data;

    private ObaStopResponse() {
        data = Data.EMPTY_OBJECT;
    }

    @Override
    public String getId() {
        return data.entry.getId();
    }

    @Override
    public String getStopCode() {
        return data.entry.getStopCode();
    }

    @Override
    public String getName() {
        return data.entry.getName();
    }

    @Override
    public GeoPoint getLocation() {
        return data.entry.getLocation();
    }

    @Override
    public double getLatitude() {
        return data.entry.getLatitude();
    }

    @Override
    public double getLongitude() {
        return data.entry.getLatitude();
    }

    @Override
    public String getDirection() {
        return data.entry.getDirection();
    }

    @Override
    public int getLocationType() {
        return data.entry.getLocationType();
    }

    @Override
    public String[] getRouteIds() {
        return data.entry.getRouteIds();
    }

    /**
     * Returns the list of dereferenced routes.
     */
    public List<ObaRouteElement> getRoutes() {
        return data.references.getRoutes(data.entry.getRouteIds());
    }
}
