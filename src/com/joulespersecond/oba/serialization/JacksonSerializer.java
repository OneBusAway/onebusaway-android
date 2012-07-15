/*
 * Copyright (C) 2010-2012 Paul Watts (paulcwatts@gmail.com)
 *                and individual contributors.
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
package com.joulespersecond.oba.serialization;

import com.joulespersecond.oba.ObaApi;

import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.JsonProcessingException;
import org.codehaus.jackson.annotate.JsonAutoDetect;
import org.codehaus.jackson.map.DeserializationConfig;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.MappingJsonFactory;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.introspect.VisibilityChecker;
import org.codehaus.jackson.node.JsonNodeFactory;
import org.codehaus.jackson.node.TextNode;
import org.codehaus.jackson.node.TreeTraversingParser;

import android.util.Log;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;

public class JacksonSerializer implements ObaApi.SerializationHandler {
    private static final String TAG = "JacksonSerializer";

    private static class SingletonHolder {
        public static final JacksonSerializer INSTANCE = new JacksonSerializer();
    }

    private static final ObjectMapper mMapper = new ObjectMapper();

    static {
        mMapper.configure(DeserializationConfig.Feature.FAIL_ON_UNKNOWN_PROPERTIES,false);
        mMapper.setVisibilityChecker(
                VisibilityChecker.Std.defaultInstance()
                .withFieldVisibility(JsonAutoDetect.Visibility.ANY));
    }

    private JacksonSerializer() { /* singleton */ }

    /**
     * Make the singleton instance available
     */
    public static ObaApi.SerializationHandler getInstance() {
        return SingletonHolder.INSTANCE;
    }

    private static JsonParser getJsonParser(Reader reader)
            throws IOException, JsonProcessingException {
        TreeTraversingParser parser = new TreeTraversingParser(mMapper.readTree(reader));
        parser.setCodec(mMapper);
        return parser;
    }

    public String toJson(String input) {
        TextNode node = JsonNodeFactory.instance.textNode(input);
        return node.toString();
    }

    @Override
    public <T> T createFromError(Class<T> cls, int code, String error) {
        // This is not very efficient, but it's an error case and it's easier
        // than instantiating one ourselves.
        final String jsonErr = toJson(error);
        final String json = getErrorJson(code, jsonErr);

        try {
            // Hopefully this never returns null or throws.
            return mMapper.readValue(json, cls);
        } catch (JsonParseException e) {
            Log.d(TAG, e.toString());
        } catch (JsonMappingException e) {
            Log.d(TAG, e.toString());
        } catch (IOException e) {
            Log.d(TAG, e.toString());
        }
        return null;
    }

    private String getErrorJson(int code, final String jsonErr) {
        return String.format("{\"code\": %d,\"version\":\"2\",\"text\":%s}", code, jsonErr);
    }

    public <T> T deserialize(Reader reader, Class<T> cls) {
        try {
            T t = getJsonParser(reader).readValueAs(cls);
            if (t == null) {
                // TODO: test switching from Gson for errors
                t = createFromError(cls, ObaApi.OBA_INTERNAL_ERROR, "Json error");
            }
            return t;
        } catch (FileNotFoundException e) {
            return createFromError(cls, ObaApi.OBA_NOT_FOUND, e.toString());
        } catch (JsonProcessingException e) {
            return createFromError(cls, ObaApi.OBA_INTERNAL_ERROR, e.toString());
        } catch (IOException e) {
            return createFromError(cls, ObaApi.OBA_IO_EXCEPTION, e.toString());
        }
    }

    public String serialize(Object obj) {
        StringWriter writer = new StringWriter();
        JsonGenerator jsonGenerator;

        try {
            jsonGenerator = new MappingJsonFactory().createJsonGenerator(writer);
            mMapper.writeValue(jsonGenerator, obj);

            return writer.toString();

        } catch (JsonGenerationException e) {
            Log.d(TAG, e.toString());
            return getErrorJson(ObaApi.OBA_INTERNAL_ERROR, e.toString());
        } catch (JsonMappingException e) {
            Log.d(TAG, e.toString());
            return getErrorJson(ObaApi.OBA_INTERNAL_ERROR, e.toString());
        } catch (IOException e) {
            Log.d(TAG, e.toString());
            return getErrorJson(ObaApi.OBA_IO_EXCEPTION, e.toString());
        }
    }
}
