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
import com.google.gson.reflect.TypeToken;

@Deprecated
public final class ObaAgency {
    public static final ObaAgency EMPTY_OBJECT = new ObaAgency();

    public static final ObaRefMap<ObaAgency> EMPTY_MAP = new ObaRefMap<ObaAgency>();
    public static final Type MAP_TYPE = new TypeToken<ObaRefMap<ObaAgency>>(){}.getType();

    static class Deserialize implements JsonHelp.Deserialize<ObaAgency> {
        public ObaAgency doDeserialize(JsonObject obj,
                                String id,
                                Type type,
                                JsonDeserializationContext context) {
            final String name =
                JsonHelp.deserializeChild(obj, "name", String.class, context);
            return new ObaAgency(id, name);
        }

    }

    private final String id;
    private final String name;
    //private final String url;
    //private final String timezone;

    ObaAgency() {
        id = "";
        name = "";
        //url = "";
        //timezone = "";
    }
    private ObaAgency(String _id, String _name) {
        id = _id;
        name = _name;
    }
    public String getId() {
        return id;
    }
    public String getName() {
        return name;
    }
    /*
    String getUrl() {
        return url;
    }
    String getTimezone() {
        return timezone;
    }
    */
    @Override
    public String toString() {
        return ObaApi.getGson().toJson(this);
    }
}
