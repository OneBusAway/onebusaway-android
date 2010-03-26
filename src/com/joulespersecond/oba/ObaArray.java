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

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;

public final class ObaArray<E> {
    static class Deserializer<E> implements JsonDeserializer<ObaArray<E>> {
        public ObaArray<E> deserialize(JsonElement elem, Type type,
                JsonDeserializationContext context) throws JsonParseException {
            try {
                Type[] typeParameters = ((ParameterizedType)type).getActualTypeArguments();
                final Type subtype = typeParameters[0];
                JsonArray array = elem.getAsJsonArray();
                final int size = array.size();
                ArrayList<E> result = new ArrayList<E>(size);
                for (int i=0; i < size; ++i) {
                    JsonElement child = array.get(i);
                    E e = context.deserialize(child, subtype);
                    result.add(e);
                }
                return new ObaArray<E>(result);
            }
            catch (ClassCastException e) {
                return new ObaArray<E>();
            }
            catch (IllegalStateException e) {
                return new ObaArray<E>();
            }
        }
    }

    private final ArrayList<E> mArray;

    /**
     * Constructor.
     *
     * @param The encapsulated array.
     */
    ObaArray() {
        mArray = new ArrayList<E>();
    }
    ObaArray(ArrayList<E> array) {
        mArray = array;
    }
    /**
     * Returns the length of the array.
     *
     * @return The length of the array.
     */
    public int length() {
        return mArray.size();
    }
    /**
     * Returns the object for the specified index.
     *
     * @param index The child index.
     * @return The child object.
     *
     */
    public E get(int index) {
        return mArray.get(index);
    }

    @Override
    public String toString() {
        return mArray.toString();
    }
}
