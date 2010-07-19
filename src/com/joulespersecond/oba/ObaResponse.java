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

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;
import java.net.URL;

public final class ObaResponse {
    //private static final String TAG = "ObaResponse";

    static class Deserializer implements JsonDeserializer<ObaResponse> {
        @Override
        public ObaResponse deserialize(JsonElement element,
                Type type,
                JsonDeserializationContext context) throws JsonParseException {
            final JsonObject obj = element.getAsJsonObject();

            final String version =
                JsonHelp.deserializeChild(obj, "version", String.class, context);
            final int code =
                JsonHelp.deserializeChild(obj, "code", int.class, context);
            final String text =
                JsonHelp.deserializeChild(obj, "text", String.class, context);
            ObaData data;

            if (ObaApi.VERSION2.equals(version)) {
                data = JsonHelp.deserializeChild(obj, "data", ObaData2.class, context);
            }
            else {
                data = JsonHelp.deserializeChild(obj, "data", ObaData1.class, context);
            }
            return new ObaResponse(version, code, text, data);
        }
    }

    private final String version;
    private final int code;
    private final String text;
    private final ObaData data;

    /**
     * Constructor from Deserializer
     */
    ObaResponse() {
        version = ObaApi.VERSION1;
        code = 0;
        text = "Uninit";
        data = ObaData1.EMPTY_OBJECT;
    }

    /**
     * Constructor for ObaResponse
     */
    private ObaResponse(String v, int c, String t, ObaData d) {
        version = v;
        code = c;
        text = t;
        data = d != null ? d : ObaData1.EMPTY_OBJECT;
    }
    static public ObaResponse createFromString(String str)  {
        try {
            ObaResponse r = ObaApi.getGson().fromJson(str, ObaResponse.class);
            if (r != null) {
                return r;
            }
            // We must never ever return null, always an error object.
            return createFromError("null gson response");
        }
        catch (JsonParseException e) {
            return createFromError(e.toString());
        }
    }
    static public ObaResponse createFromError(String error) {
        return new ObaResponse(ObaApi.VERSION1, 0, error, null);
    }
    static public ObaResponse createFromError(String error, int code) {
        return new ObaResponse(ObaApi.VERSION1, code, error, null);
    }
    static public ObaResponse createFromURL(URL url) throws IOException {
        Reader reader = ObaHelp.getUri(url);
        try {
            ObaResponse r = ObaApi.getGson().fromJson(reader, ObaResponse.class);
            if (r != null) {
                return r;
            }
            // Gson may return null, but we must never.
            return createFromError("null gson response");
        }
        catch (JsonParseException e) {
            return createFromError(e.toString());
        }
    }

    public String getVersion() {
        return version;
    }
    public int getCode() {
        return code;
    }
    public String getText() {
        return text;
    }
    public ObaData getData() {
        return data;
    }
    @Override
    public String toString() {
        return ObaApi.getGson().toJson(this);
    }
}
