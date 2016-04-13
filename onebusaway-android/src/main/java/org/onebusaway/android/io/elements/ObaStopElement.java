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

import org.onebusaway.android.util.LocationUtils;

import android.location.Location;

/**
 * Object defining a Stop element.
 * {@link http://code.google.com/p/onebusaway/wiki/OneBusAwayRestApi_StopElementV2}
 *
 * @author Paul Watts (paulcwatts@gmail.com)
 */
public final class ObaStopElement implements ObaStop {

    public static final ObaStopElement EMPTY_OBJECT = new ObaStopElement();

    public static final ObaStopElement[] EMPTY_ARRAY = new ObaStopElement[]{};

    public static final String[] EMPTY_ROUTES = new String[]{};

    private final String id;

    private final double lat;

    private final double lon;

    private final String direction;

    private final int locationType;

    private final String name;

    private final String code;

    private final String[] routeIds;

    public ObaStopElement() {
        id = "";
        lat = 0;
        lon = 0;
        direction = "";
        locationType = LOCATION_STOP;
        name = "";
        code = "";
        routeIds = EMPTY_ROUTES;
    }

    public ObaStopElement(String id, double lat, double lon, String name, String code) {
        this.id = id;
        this.lat = lat;
        this.lon = lon;
        direction = "";
        locationType = LOCATION_STOP;
        this.name = name;
        this.code = code;
        routeIds = EMPTY_ROUTES;
    }

    public String getId() {
        return id;
    }

    public String getStopCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public Location getLocation() {
        return LocationUtils.makeLocation(lat, lon);
    }

    public double getLatitude() {
        return lat;
    }

    public double getLongitude() {
        return lon;
    }

    public String getDirection() {
        return direction;
    }

    public int getLocationType() {
        return locationType;
    }

    public String[] getRouteIds() {
        return routeIds;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((id == null) ? 0 : id.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (!(obj instanceof ObaStopElement)) {
            return false;
        }
        ObaStopElement other = (ObaStopElement) obj;
        if (id == null) {
            if (other.id != null) {
                return false;
            }
        } else if (!id.equals(other.id)) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "ObaStopElement [direction=" + direction + ", id=" + id
                + ", name=" + name + "]";
    }
}
