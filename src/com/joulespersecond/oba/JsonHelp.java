package com.joulespersecond.oba;

import java.lang.reflect.Type;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

final class JsonHelp {
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
                    return e;
                }
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
        
    }
}
