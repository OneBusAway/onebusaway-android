package org.onebusaway.android.map.googlemapsv2;

import android.location.Location;
import com.google.android.gms.maps.model.Marker;

/**
 *
 * Created by carvalhorr on 6/14/17.
 */

public interface MarkerListeners {
    boolean  markerClicked(Marker marker);
    void removeMarkerClicked(Location latLng);
}
