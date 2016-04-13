/*
* Copyright (C) 2014-2015 University of South Florida (sjbarbeau@gmail.com)
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
package org.onebusaway.android.report.ui.util;

import org.onebusaway.android.io.elements.ObaStop;

import android.location.Location;

/**
 * IssueLocationHelper is responsible to handle multiple markers on the map
 * BaseMapFragment only adds markers when a bus stop clicked. However, we need to add
 * markers if a user clicks on the map
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
        void onClearMarker(int markerId);
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

    public Callback getCallback() {
        return callback;
    }

    public void setCallback(Callback callback) {
        this.callback = callback;
    }
}