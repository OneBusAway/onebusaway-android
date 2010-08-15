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
import java.util.concurrent.ConcurrentHashMap;

final class JsonHelp {
    //private static final String TAG = "JsonHelp";

    // Random Json helpers
    static <T> T deserializeChild(JsonObject obj, String name,
                     Type typeOfT, JsonDeserializationContext context) {
        JsonElement child = obj.get(name);
        if (child == null) {
            return null;
        }
        return context.deserialize(child, typeOfT);
    }

    interface Deserialize<E> {
        public E doDeserialize(JsonObject obj,
                        String id,
                        Type type,
                        JsonDeserializationContext context);
    }

    static class CachingDeserializer<E> implements JsonDeserializer<E> {
        private ConcurrentHashMap<String,E> mCache;
        private final String mId;
        private final Deserialize<E> mDeserialize;

        CachingDeserializer(Deserialize<E> d, String id) {
            assert(d != null);
            assert(id != null);
            mDeserialize = d;
            mId = id;
            mCache = new ConcurrentHashMap<String,E>();
        }

        public E deserialize(JsonElement elem, Type type,
                JsonDeserializationContext context) throws JsonParseException {

            try {
                JsonObject obj = elem.getAsJsonObject();
                String id = JsonHelp.deserializeChild(obj, mId, String.class, context);
                E e = mCache.get(id);
                if (e != null) {
                    //Log.d(TAG, "cache hit: " + id);
                    return e;
                }
                //Log.d(TAG, "cache miss: " + id);
                E e2 = mDeserialize.doDeserialize(obj, id, type, context);
                mCache.put(id, e2);
                return e2;
            }
            catch (ClassCastException e) {
                throw new JsonParseException("Error while deserializing", e);
            }
            catch (IllegalStateException e) {
                throw new JsonParseException("Error while deserializing", e);
            }
        }
        public void clear() {
            mCache.clear();
        }
    }

    // This will look up the "reference" child.
    // If it exists, it will deference that.
    // Otherwise, it looks up the "non-reference" child.
    static <E> E derefObject(JsonObject obj,
            JsonDeserializationContext context,
            String refChild,
            String nonRefChild,
            ObaRefMap<E> map,
            Class<E> cls) {
        final String id =
            JsonHelp.deserializeChild(obj, refChild, String.class, context);
        if (id != null) {
            return map.get(id);
        }
        else {
            return JsonHelp.deserializeChild(obj, nonRefChild, cls, context);
        }
    }
}
