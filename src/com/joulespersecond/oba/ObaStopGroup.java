package com.joulespersecond.oba;

import java.util.ArrayList;
import java.util.List;

public final class ObaStopGroup {
    private static final class StopGroupName {
        private final String type;
        private final List<String> names;
        
        private StopGroupName() {
            type = "";
            names = null;
        }
        String getType() {
            return type;
        }
        List<String> getNames() {
            return (names != null) ? names : new ArrayList<String>();
        }
    }
    private final List<String> stopIds;
    private final ObaArray<ObaPolyline> polylines;
    private final StopGroupName name;
   
    public static final String TYPE_DESTINATION = "destination";
    
    /**
     * Constructor.
     */
    ObaStopGroup() {
        stopIds = null;
        polylines = null;
        name = null;
    }
    
    /**
     * Returns the type of grouping. 
     * 
     * @return One of the TYPE_* string constants.
     */
    public String getType() {
        return (name != null) ? name.getType() : "";
    }
    
    /**
     * Returns the name of this grouping, or the empty string.
     * 
     * @return The name of this grouping, or the empty string.
     */
    public String getName() {
        if (name == null) {
            return "";
        }
        List<String> names = name.getNames();
        if (names.size() > 0) {
            return names.get(0);
        }
        return "";
    }
    
    /**
     * Returns a list of StopIDs for this grouping.
     * 
     * @return The stop IDs for this grouping.
     */
    public List<String> getStopIds() {
        return stopIds;
    }
    
    /**
     * Returns the array of polylines.
     * 
     * @return The array of polylines, or an empty array.
     */
    public ObaArray<ObaPolyline> getPolylines() {
        return polylines;
    }
    
    @Override
    public String toString() {
        return ObaApi.getGson().toJson(this);
    }
}
