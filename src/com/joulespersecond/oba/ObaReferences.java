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

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import java.lang.reflect.Type;

public final class ObaReferences {
    public static final ObaReferences EMPTY_OBJECT = new ObaReferences();

    //
    // We need to have a custom deserializer because we are responsible for
    // adding the ObaReferences object to the context map (ObaApi.mRefMap)
    // because our children also need access to the references.
    //
    static class Deserializer implements JsonDeserializer<ObaReferences> {
        @Override
        public ObaReferences deserialize(JsonElement element,
                Type type,
                JsonDeserializationContext context) throws JsonParseException {
            final JsonObject obj = element.getAsJsonObject();

            ObaReferences refs = new ObaReferences();
            ObaApi.mRefMap.put(context, refs);
            // We need to deserialize directly into the member maps
            // because lower references might depend on earlier references
            final ObaRefMap<ObaAgency> a =
                JsonHelp.deserializeChild(obj, "agencies", ObaAgency.MAP_TYPE, context);
            refs.agencies = a != null ? a : ObaAgency.EMPTY_MAP;

            final ObaRefMap<ObaRoute> r =
                JsonHelp.deserializeChild(obj, "routes", ObaRoute.MAP_TYPE, context);
            refs.routes = r != null ? r : ObaRoute.EMPTY_MAP;

            final ObaRefMap<ObaStop> s =
                JsonHelp.deserializeChild(obj, "stops", ObaStop.MAP_TYPE, context);
            refs.stops = s != null ? s : ObaStop.EMPTY_MAP;

            return refs;
        }
    }

    private ObaRefMap<ObaAgency> agencies;
    private ObaRefMap<ObaRoute> routes;
    private ObaRefMap<ObaStop> stops;

    ObaReferences() {
        agencies = null;
        routes = null;
        stops = null;
    }

    ObaRefMap<ObaAgency> getAgencyMap() {
        return agencies;
    }
    ObaRefMap<ObaRoute> getRouteMap() {
        return routes;
    }
    ObaRefMap<ObaStop> getStopMap() {
        return stops;
    }
}
