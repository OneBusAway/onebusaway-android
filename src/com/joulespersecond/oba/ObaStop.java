package com.joulespersecond.oba;

import java.lang.reflect.Type;

import com.google.android.maps.GeoPoint;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

public final class ObaStop {
    static class Deserialize implements JsonHelp.Deserialize<ObaStop> {
        public ObaStop doDeserialize(JsonObject obj, 
                                String id,
                                Type type,
                                JsonDeserializationContext context) {  
            final Double lat = 
                JsonHelp.deserializeChild(obj, "lat", Double.class, context);
            final Double lon = 
                JsonHelp.deserializeChild(obj, "lon", Double.class, context);
            final String dir = 
                JsonHelp.deserializeChild(obj, "direction", String.class, context);                
            final String name = 
                JsonHelp.deserializeChild(obj, "name", String.class, context);
            final String code = 
                JsonHelp.deserializeChild(obj, "code", String.class, context);
            
            // The deserializer needs the parameterized type, not the raw type.
            Type paramType = new TypeToken<ObaArray<ObaRoute>>(){}.getType();
            final ObaArray<ObaRoute> routes = 
                JsonHelp.deserializeChild(obj, "routes", paramType, context);
            
            final double lat2 = (lat != null) ? lat : 0;
            final double lon2 = (lon != null) ? lon : 0;            
            return new ObaStop(id, lat2, lon2, dir, name, code, routes);
        } 
    }
    
    private final String id;
    private final double lat;
    private final double lon;
    private final String direction;
    private final String name;
    private final String code;
    private final ObaArray<ObaRoute> routes;
    
    /**
     * Constructor.
     */
    ObaStop() {
        id = "";
        lat = 0;
        lon = 0;
        direction = "";
        name = "";
        code = "";
        routes = null;
    }
    ObaStop(String _id, 
                double _lat, double _lon, 
                String _dir, String _name,
                String _code, ObaArray<ObaRoute> _routes) {
        id = _id != null ? _id : "";
        lat = _lat;
        lon = _lon;
        direction = _dir != null ? _dir : "";
        name = _name != null ? _name : "";
        code = _code != null ? _code : "";
        routes = _routes;
    }
    /**
     * Returns the stop ID.
     * 
     * @return The stop ID.
     */
    public String getId() {
        return id;
    }
    /**
     * Returns the passenger-facing stop identifier.
     * 
     * @return The passenger-facing stop ID.
     */
    public String getCode() {
        return code;
    }
    /**
     * Returns the passenger-facing name for the stop.
     * 
     * @return The passenger-facing name for the stop.
     */
    public String getName() {
        return name;
    }
    /**
     * Returns the location of the stop.
     * 
     * @return The location of the stop, or null if it can't be converted to a GeoPoint.
     */
    public GeoPoint getLocation() {
        return ObaApi.makeGeoPoint(lat, lon);
    }
    /**
     * Returns the latitude of the stop as a double.
     * 
     * @return The latitude of the stop, or 0 if it doesn't exist.
     */
    public double getLatitude() {
        return lat;
    }
    /**
     * Returns the longitude of the stop as a double.
     * 
     * @return The longitude of the stop, or 0 if it doesn't exist.
     */
    public double getLongitude() {
        return lon;
    }
    
    /**
     * Returns the direction of the stop (ex "NW", "E").
     * 
     * @return The direction of the stop.
     */
    public String getDirection() {
        return direction;
    }
    /**
     * Returns an array of routes serving this stop.
     * 
     * @return The routes serving this stop.
     */
    public ObaArray<ObaRoute> getRoutes() {
        return (routes != null) ? routes : new ObaArray<ObaRoute>();
    }
    
    @Override
    public String toString() {
        return ObaApi.getGson().toJson(this);
    }
}
