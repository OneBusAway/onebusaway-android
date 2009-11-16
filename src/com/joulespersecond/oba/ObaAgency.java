package com.joulespersecond.oba;

import java.lang.reflect.Type;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;

public final class ObaAgency {
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
    String getId() {
        return id;
    }
    String getName() {
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
