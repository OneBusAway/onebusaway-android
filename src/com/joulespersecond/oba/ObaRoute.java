package com.joulespersecond.oba;

import java.lang.reflect.Type;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;



public final class ObaRoute {
    static class Deserialize implements JsonHelp.Deserialize<ObaRoute> {
        public ObaRoute doDeserialize(JsonObject obj, 
                                String id,
                                Type type,
                                JsonDeserializationContext context) {  
            final String shortName = 
                JsonHelp.deserializeChild(obj, "shortName", String.class, context);
            final String longName = 
                JsonHelp.deserializeChild(obj, "longName", String.class, context);
            final ObaAgency agency = 
                JsonHelp.deserializeChild(obj, "agency", ObaAgency.class, context);
            return new ObaRoute(id, shortName, longName, agency);
        } 
    }
    
    private final String id;
    private final String shortName;
    private final String longName;
    private final ObaAgency agency;
    
    /**
     * Constructor.
     */
    ObaRoute() {
        id = "";
        shortName = "";
        longName = "";
        agency = null;
    }
    ObaRoute(String _id, String _short, String _long, ObaAgency _agency) {
        id = _id;
        shortName = _short;
        longName = _long;
        agency = _agency;
    }
    /**
     * Returns the route ID.
     * 
     * @return The route ID.
     */
    public String getId() {
        return id;
    }
    /**
     * Returns the short name of the route (ex. "10", "30").
     * 
     * @return The short name of the route.
     */
    public String getShortName() {
        return shortName;
    }
    /**
     * Returns the long name of the route (ex. "Sandpoint/QueenAnne")
     * 
     * @return The long name of the route.
     */
    public String getLongName() {
        return longName;
    }
    
    /**
     * Returns the name of the agency running this route.
     * 
     * @return The name of the agency running this route.
     */
    public String getAgencyName() {
        return (agency != null) ? agency.getName() : "";
    }
    /**
     * Returns the ID of the agency running this route.
     * 
     * @return The ID of the agency running this route.
     */
    public String getAgencyId() {
        return (agency != null) ? agency.getId() : "";      
    }
    
    /**
     * Returns the agency object running this route.
     * @return The agency object running this route, or an empty object.
     */
    public ObaAgency getAgency() {
        return (agency != null) ? agency : new ObaAgency();
    }
    @Override
    public String toString() {
        return ObaApi.getGson().toJson(this);
    }
}
