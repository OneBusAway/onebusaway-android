package org.onebusaway.android.report.ui.util;

import android.location.Location;

import org.onebusaway.android.io.elements.ObaStop;

/**
 * Created by Cagri Cetin
 */
public class IssueLocationHelper {

    private int markerId = -1;

    private ObaStop obaStop;

    private Location markerPosition;

    private Callback callback;

    public interface Callback {
        /**
         * Called when the marker is going to be cleared from the map
         * @param markerId the created marker id from BaseMapFragment.addMarker method
         */
        public void onClearMarker(int markerId);
    }

    public IssueLocationHelper(Location markerPosition, Callback callback) {
        this.markerPosition = markerPosition;
        this.callback = callback;
    }

    public void handleMarkerUpdate(int markerId) {
        clearMarkers();
        this.setMarkerId(markerId);
    }

    public void updateMarkerPosition(Location markerPosition, ObaStop obaStop) {
        clearMarkers();
        setMarkerPosition(markerPosition);
        setObaStop(obaStop);
    }

    public Location getIssueLocation() {
        if (obaStop != null) {
            return obaStop.getLocation();
        } else {
            return markerPosition;
        }
    }

    public void clearMarkers() {
        if (markerId != -1) {
            callback.onClearMarker(markerId);
            markerId = -1;
        }
    }

    public int getMarkerId() {
        return markerId;
    }

    public void setMarkerId(int markerId) {
        this.markerId = markerId;
    }

    public ObaStop getObaStop() {
        return obaStop;
    }

    public void setObaStop(ObaStop obaStop) {
        this.obaStop = obaStop;
    }

    public void setMarkerPosition(Location markerPosition) {
        this.markerPosition = markerPosition;
    }
}