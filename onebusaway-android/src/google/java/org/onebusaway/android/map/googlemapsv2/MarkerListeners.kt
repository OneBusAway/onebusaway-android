/* Copyright (C) 2017 Rodrigo Carvalho (carvalhorr@gmail.com) */
package org.onebusaway.android.map.googlemapsv2

import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker

/** Marker and map click callbacks implemented by map overlays. */
interface MarkerListeners {
    fun markerClicked(marker: Marker): Boolean

    fun removeMarkerClicked(latLng: LatLng)
}
