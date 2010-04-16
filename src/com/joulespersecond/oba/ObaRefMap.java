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

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashMap;

public final class ObaRefMap<E> {
    static class Deserializer<E> implements JsonDeserializer<ObaRefMap<E>> {
        public ObaRefMap<E> deserialize(JsonElement elem, Type type,
                JsonDeserializationContext context) throws JsonParseException {
            try {
                Type[] typeParameters = ((ParameterizedType)type).getActualTypeArguments();
                final Type subtype = typeParameters[0];
                JsonArray array = elem.getAsJsonArray();
                final int size = array.size();
                HashMap<String,E> result = new HashMap<String,E>(size);
                for (int i=0; i < size; ++i) {
                    JsonElement child = array.get(i);
                    E e = context.deserialize(child, subtype);
                    String id = JsonHelp.deserializeChild(child.getAsJsonObject(),
                            "id", String.class, context);
                    result.put(id, e);
                }
                return new ObaRefMap<E>(result);
            }
            catch (ClassCastException e) {
                return new ObaRefMap<E>();
            }
            catch (IllegalStateException e) {
                return new ObaRefMap<E>();
            }
        }
    }

    private final HashMap<String,E> mRefs;

    ObaRefMap() {
        mRefs = new HashMap<String,E>();
    }
    ObaRefMap(HashMap<String,E> refs) {
        mRefs = refs;
    }
    /**
     * Returns the length of the array.
     *
     * @return The length of the array.
     */
    public int length() {
        return mRefs.size();
    }
    /**
     * Returns the object for the specified ID.
     *
     * @param index The string ID.
     * @return The child object.
     *
     */
    public E get(String id) {
        return mRefs.get(id);
    }

    @Override
    public String toString() {
        return mRefs.toString();
    }
}
