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
            final String description = 
                JsonHelp.deserializeChild(obj, "description", String.class, context);
            final ObaAgency agency = 
                JsonHelp.deserializeChild(obj, "agency", ObaAgency.class, context);
            return new ObaRoute(id, shortName, longName, description, agency);
        } 
    }
   static String getAlternateRouteName(String id, String name) {
       if (id.equals("1_599")) {
           return "Link";
       }
       else {
           return name;
       }
    }
    
    private final String id;
    private final String shortName;
    private final String longName;
    private final String description;
    private final ObaAgency agency;
    
    /**
     * Constructor.
     */
    ObaRoute() {
        id = "";
        shortName = "";
        longName = "";
        description = "";
        agency = null;
    }
    ObaRoute(String _id, String _short, String _long, String _description, ObaAgency _agency) {
        id = _id != null ? _id : "";
        shortName = getAlternateRouteName(id, _short);
        longName = _long != null ? _long : "";
        description = _description != null ? _description : "";
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
     * Returns the description of the route.
     * 
     */
    public String getDescription() {
        return description;
    }
    
    /**
     * Returns the long name if it exists, otherwise it returns the description.
     * @return
     */
    public String getLongNameOrDescription() {
        if (longName != null && longName.length() > 0) {
            return longName;
        }
        return description;
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
