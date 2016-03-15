/*
 *
 */
package org.onebusaway.android.tad;


import android.location.Location;
import android.location.LocationManager;

import org.onebusaway.android.io.elements.ObaStop;
import org.onebusaway.android.util.LocationHelper;

/**
 *
 */
public class Segment {

    private int idSegment;

    public void setIdSegment(int idSegment)  {
        this.idSegment = idSegment;
    }

    public int getIdSegment() {
        return idSegment;
    }

    private int agencyFeedIDTAD;

    public void setAgencyFeedIDTAD(int agencyFeedIDTAD)  {
        this.agencyFeedIDTAD = agencyFeedIDTAD;
    }

    public int getAgencyFeedIDTAD() {
        return agencyFeedIDTAD;
    }

    private String route_IDGTFS;

    public void setRoute_IDGTFS(String route_IDGTFS)  {
        this.route_IDGTFS = route_IDGTFS;
    }

    public String getRoute_IDGTFS() {
        return route_IDGTFS;
    }

    private String trip_headsignGTFS;

    public void setTrip_headsignGTFS(String trip_headsignGTFS)  {
        this.trip_headsignGTFS = trip_headsignGTFS;
    }

    public String getTrip_headsignGTFS() {
        return trip_headsignGTFS;
    }

    private int direction_IDGTFS;

    public void setDirection_IDGTFS(int direction_IDGTFS)  {
        this.direction_IDGTFS = direction_IDGTFS;
    }

    public int getDirection_IDGTFS() {
        return direction_IDGTFS;
    }

    private int idStopFrom;

    public void setIdStopFrom(int idStopFrom)  {
        this.idStopFrom = idStopFrom;
    }

    public int getIdStopFrom() {
        return idStopFrom;
    }

    private String idStopFromTransitAgencyGTFS;

    public void setIdStopFromTransitAgencyGTFS(String idStopFromTransitAgencyGTFS)  {
        this.idStopFromTransitAgencyGTFS = idStopFromTransitAgencyGTFS;
    }

    public String getIdStopFromTransitAgencyGTFS() {
        return idStopFromTransitAgencyGTFS;
    }


    private Location fromLocation;
    public Location getFromLocation() { return fromLocation; }
    public void setFromLocation(Location l) {fromLocation = l; }

    private int idStopTo;

    public void setIdStopTo(int idStopTo)  {
        this.idStopTo = idStopTo;
    }

    public int getIdStopTo() {
        return idStopTo;
    }

    private String idStopToTransitAgencyGTFS;

    public void setIdStopToTransitAgencyGTFS(String idStopToTransitAgencyGTFS)  {
        this.idStopToTransitAgencyGTFS = idStopToTransitAgencyGTFS;
    }

    public String getIdStopToTransitAgencyGTFS() {
        return idStopToTransitAgencyGTFS;
    }

    private Location toLocation;
    public Location getToLocation() { return toLocation; }
    public void setToLocation(Location l) {toLocation = l; }

    private Location beforeLocation;
    public Location getBeforeLocation() { return beforeLocation; }
    public void setBeforeLocation(Location l) { beforeLocation = l; }

    private float AlertDistance;

    public void setAlertDistance(float AlertDistance)  {
        this.AlertDistance = AlertDistance;
    }

    public float getAlertDistance() {
        return AlertDistance;
    }

    private String tripId;
    public void setTripId(String trip) { tripId = trip; }
    public String getTripId() { return tripId; }

    /**
        Construct a segment from two stops. From Location remains null.
        @param destination Destination Stop.
        @param before Second-to-last stop.
     */
    public Segment(ObaStop destination, ObaStop before)
    {
        toLocation = new Location(LocationManager.GPS_PROVIDER);
        toLocation.setLatitude(destination.getLatitude());
        toLocation.setLongitude(destination.getLongitude());
        beforeLocation = new Location(LocationManager.GPS_PROVIDER);
        beforeLocation.setLatitude(before.getLatitude());
        beforeLocation.setLongitude(before.getLongitude());
    }

    /**
     * Construct a segment from locations.
     * @param beforeLoc Second to last location.
     * @param toLoc Destination location.
     * @param fromLoc User's from location.
     */
    public Segment(Location beforeLoc, Location toLoc, Location fromLoc)
    {
        fromLocation = fromLoc;
        toLocation = toLoc;
        beforeLocation = beforeLoc;
    }


}
