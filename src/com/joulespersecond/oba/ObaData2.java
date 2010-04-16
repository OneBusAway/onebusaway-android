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

public final class ObaData2 implements ObaData {
    static class Deserializer implements JsonDeserializer<ObaData2> {
        @Override
        public ObaData2 deserialize(JsonElement element,
                Type type,
                JsonDeserializationContext context) throws JsonParseException {
            final JsonObject obj = element.getAsJsonObject();

            // First, the references
            final ObaReferences refs =
                JsonHelp.deserializeChild(obj, "references", ObaReferences.class, context);
            if (refs != null) {
                ObaApi.mRefMap.put(context, refs);
            }
            final ObaEntry entry =
                JsonHelp.deserializeChild(obj, "entry", ObaEntry.class, context);
            ObaApi.mRefMap.remove(context);
            return new ObaData2(refs, entry);
        }
    }

    private final ObaReferences references;
    private final ObaEntry entry;

    ObaData2() {
        references = ObaReferences.EMPTY_OBJECT;
        entry = ObaEntry.EMPTY_OBJECT;
    }
    private ObaData2(ObaReferences refs, ObaEntry e) {
        references = refs != null ? refs : ObaReferences.EMPTY_OBJECT;
        entry = e != null ? e : ObaEntry.EMPTY_OBJECT;
    }

    public ObaReferences getReferences() {
        return references;
    }

    @Override
    public ObaArray<ObaStop> getStops() {
        return entry.getStops();
    }

    @Override
    public ObaArray<ObaRoute> getRoutes() {
        return entry.getRoutes();
    }

    @Override
    public ObaStop getStop() {
        return entry.getStop();
    }

    @Override
    public ObaRoute getRoute() {
        return entry.getRoute();
    }

    @Override
    public ObaArray<ObaStop> getNearbyStops() {
        return entry.getNearbyStops();
    }

    @Override
    public ObaRoute getAsRoute() {
        return entry.getAsRoute();
    }

    @Override
    public ObaStop getAsStop() {
        return entry.getAsStop();
    }

    @Override
    public ObaArray<ObaArrivalInfo> getArrivalsAndDepartures() {
        return entry.getArrivalsAndDepartures();
    }

    @Override
    public ObaArray<ObaStopGrouping> getStopGroupings() {
        return entry.getStopGroupings();
    }

    @Override
    public ObaArray<ObaPolyline> getPolylines() {
        return entry.getPolylines();
    }

    @Override
    public boolean getLimitExceeded() {
        return entry.getLimitExceeded();
    }

    @Override
    public String toString() {
        return ObaApi.getGson().toJson(this);
    }

}
