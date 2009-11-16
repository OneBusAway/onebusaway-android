package com.joulespersecond.oba;


public final class ObaStopGrouping {
    public final String TYPE_DIRECTION = "direction";
    
    private final boolean ordered;
    private final String type;
    private final ObaArray<ObaStopGroup> stopGroups;
    
    /**
     * Constructor.
     */
    ObaStopGrouping() {
        ordered = false;
        type = "";
        stopGroups = null;
    }
    
    /**
     * Returns whether or not this grouping is ordered.
     * @return A boolean indicating whether this grouping is ordered.
     */
    public boolean getOrdered() {
        return ordered;
    }
    
    /**
     * Returns the type of ordering.
     * @return The type of ordering.
     */
    public String getType() {
        return type;
    }
    
    /**
     * Returns the list of stop groups.
     * 
     * @return The list of stop groups.
     */
    public ObaArray<ObaStopGroup> getStopGroups() {
        return (stopGroups != null) ? stopGroups : new ObaArray<ObaStopGroup>();
    }
    
    @Override
    public String toString() {
        return ObaApi.getGson().toJson(this);
    }
}
