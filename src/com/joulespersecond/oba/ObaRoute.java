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

import java.lang.reflect.Type;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;



public final class ObaRoute {
    static class Deserialize implements JsonHelp.Deserialize<ObaRoute> {
        public ObaRoute doDeserialize(JsonObject obj,
                                String id,
                                Type type,
                                JsonDeserializationContext context) {
            final String shortName =
                JsonHelp.deserializeChild(obj, "shortName", String.class, context);
            final String longName =
                JsonHelp.deserializeChild(obj, "longName", String.class, context);
            final String description =
                JsonHelp.deserializeChild(obj, "description", String.class, context);
            final String url =
                JsonHelp.deserializeChild(obj, "url", String.class, context);
            final ObaAgency agency =
                JsonHelp.deserializeChild(obj, "agency", ObaAgency.class, context);
            return new ObaRoute(id, shortName, longName, description, url, agency);
        }
    }
   static String getAlternateRouteName(String id, String name) {
       if (id.equals("1_599")) {
           return "Link";
       }
       else {
           return name;
       }
    }

    private final String id;
    private final String shortName;
    private final String longName;
    private final String description;
    private final String url;
    private final ObaAgency agency;

    /**
     * Constructor.
     */
    ObaRoute() {
        id = "";
        shortName = "";
        longName = "";
        description = "";
        url = "";
        agency = null;
    }
    ObaRoute(String _id,
            String _short,
            String _long,
            String _description,
            String _url,
            ObaAgency _agency) {
        id = _id != null ? _id : "";
        shortName = getAlternateRouteName(id, _short);
        longName = _long != null ? _long : "";
        description = _description != null ? _description : "";
        url = _url != null ? _url : "";
        agency = _agency;
    }
    /**
     * Returns the route ID.
     *
     * @return The route ID.
     */
    public String getId() {
        return id;
    }
    /**
     * Returns the short name of the route (ex. "10", "30").
     *
     * @return The short name of the route.
     */
    public String getShortName() {
        return shortName;
    }
    /**
     * Returns the long name of the route (ex. "Sandpoint/QueenAnne")
     *
     * @return The long name of the route.
     */
    public String getLongName() {
        return longName;
    }
    /**
     * Returns the description of the route.
     *
     */
    public String getDescription() {
        return description;
    }

    /**
     * Returns the Url to the route schedule.
     * @return The url to the route schedule.
     */
    public String getUrl() {
        return url;
    }

    /**
     * Returns the long name if it exists, otherwise it returns the description.
     * @return
     */
    public String getLongNameOrDescription() {
        if (longName != null && longName.length() > 0) {
            return longName;
        }
        return description;
    }

    /**
     * Returns the name of the agency running this route.
     *
     * @return The name of the agency running this route.
     */
    public String getAgencyName() {
        return (agency != null) ? agency.getName() : "";
    }
    /**
     * Returns the ID of the agency running this route.
     *
     * @return The ID of the agency running this route.
     */
    public String getAgencyId() {
        return (agency != null) ? agency.getId() : "";
    }

    /**
     * Returns the agency object running this route.
     * @return The agency object running this route, or an empty object.
     */
    public ObaAgency getAgency() {
        return (agency != null) ? agency : new ObaAgency();
    }
    @Override
    public String toString() {
        return ObaApi.getGson().toJson(this);
    }
}
