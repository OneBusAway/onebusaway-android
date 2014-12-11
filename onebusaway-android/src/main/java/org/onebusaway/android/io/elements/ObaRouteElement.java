/*
 * Copyright (C) 2010-2014 Paul Watts (paulcwatts@gmail.com),
 * University of South Florida (sjbarbeau@gmail.com)
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

import android.graphics.Color;
import android.text.TextUtils;

/**
 * Object defining a Route element.
 * {@link http://code.google.com/p/onebusaway/wiki/OneBusAwayRestApi_RouteElementV2}
 *
 * @author Paul Watts (paulcwatts@gmail.com)
 */
public final class ObaRouteElement implements ObaRoute {

    public static final ObaRouteElement EMPTY_OBJECT = new ObaRouteElement();

    public static final ObaRouteElement[] EMPTY_ARRAY = new ObaRouteElement[]{};

    public static final String COLOR_PREFIX = "#";

    private final String id;

    private final String shortName;

    private final String longName;

    private final String description;

    private final int type;

    private final String url;

    private final String color;

    private final String textColor;

    private final String agencyId;

    public ObaRouteElement() {

/**
 * Arno Hart (ahart@skuzme.com) removed the restriction to just bus servives. Allow all modes.
 *      id = "";
 *      shortName = "";
 *      longName = "";
 *      description = "";
 *      type = TYPE_BUS;
 *      url = "";
 *      color = "";
 *      textColor = "";
 *      agencyId = "";
 */

        id = "";
        shortName = "";
        longName = "";
        description = "";
        type = 0;
        url = "";
        color = "";
        textColor = "";
        agencyId = "";

    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getShortName() {
        return shortName;
    }

    @Override
    public String getLongName() {
        return longName;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public int getType() {
        return type;
    }

    @Override
    public String getUrl() {
        return url;
    }

    /**
     * Returns the integer representation of the Android color for the route line, or null if this
     * value is not included in the API response
     *
     * @return the integer representation of the Android color for the route line, or null if this
     * value is not included in the API response
     */
    @Override
    public Integer getColor() {
        if (!TextUtils.isEmpty(color)) {
            return Color.parseColor(COLOR_PREFIX + color.trim());
        }
        return null;
    }

    /**
     * Returns the integer representation of the Android color for the route text, or null if this
     * value is not included in the API response
     *
     * @return the integer representation of the Android color for the route text, or null if this
     * value is not included in the API response
     */
    @Override
    public Integer getTextColor() {
        if (!TextUtils.isEmpty(textColor)) {
            return Color.parseColor(COLOR_PREFIX + textColor.trim());
        }
        return null;
    }

    @Override
    public String getAgencyId() {
        return agencyId;
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
        if (!(obj instanceof ObaRouteElement)) {
            return false;
        }
        ObaRouteElement other = (ObaRouteElement) obj;
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
        return "ObaRouteElement [id=" + id + "]";
    }
}
