package com.joulespersecond.seattlebusbot.map.googlemapsv2;

import android.content.Context;
import android.location.Location;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.maps.model.VisibleRegion;
import com.joulespersecond.oba.elements.ObaShape;
import com.joulespersecond.seattlebusbot.map.MapModeController;

import java.util.ArrayList;

/**
 * A wrapper around MapView for Google Maps API v2, to abstract the dependency on Google Play Services
 */
public class ObaMapViewV2 extends MapView
        implements MapModeController.ObaMapView {

    private ArrayList<Polyline> mLineOverlay = new ArrayList<Polyline>();

    // We have to convert from GeoPoint to Location, so hold references to both
    private LatLng mCenter;
    private Location mCenterLocation;

    public ObaMapViewV2(Context context) {
        super(context);
    }

    @Override
    public void setZoom(float zoomLevel) {
        getMap().moveCamera(CameraUpdateFactory.zoomTo(zoomLevel));
    }

    @Override
    public Location getMapCenterAsLocation() {
        // If the center is the same as the last call to this method, pass back the same Location
        // object
        LatLng center = getMap().getCameraPosition().target;
        if (mCenter == null || mCenter != center) {
            mCenter = center;
            mCenterLocation = MapHelpV2.makeLocation(mCenter);
        }
        return mCenterLocation;
    }

    @Override
    public void setMapCenter(Location location) {
        getMap().moveCamera(CameraUpdateFactory.newCameraPosition(
                new CameraPosition.Builder().target(MapHelpV2.makeLatLng(location)).build()));
        ;
    }

    @Override
    public double getLatitudeSpanInDecDegrees() {
        VisibleRegion vr = getMap().getProjection().getVisibleRegion();
        return Math.abs(vr.latLngBounds.northeast.latitude - vr.latLngBounds.southwest.latitude);
    }

    @Override
    public double getLongitudeSpanInDecDegrees() {
        VisibleRegion vr = getMap().getProjection().getVisibleRegion();
        return Math.abs(vr.latLngBounds.northeast.longitude - vr.latLngBounds.southwest.longitude);
    }

    @Override
    public float getZoomLevelAsFloat() {
        return getMap().getCameraPosition().zoom;
    }

    @Override
    public void setRouteOverlay(int lineOverlayColor, ObaShape[] shapes) {
        mLineOverlay.clear();
        PolylineOptions lineOptions;

        for (ObaShape s : shapes) {
            lineOptions = new PolylineOptions();
            lineOptions.color(lineOverlayColor);

            for (Location l : s.getPoints()) {
                lineOptions.add(MapHelpV2.makeLatLng(l));
            }
            // Add the line to the map, and keep a reference in the ArrayList
            mLineOverlay.add(getMap().addPolyline(lineOptions));
        }
    }

    @Override
    public void zoomToRoute() {
        LatLngBounds.Builder builder = new LatLngBounds.Builder();
        for (Polyline p : mLineOverlay) {
            for (LatLng l : p.getPoints()) {
                builder.include(l);
            }
        }

        int padding = 0;
        getMap().animateCamera((CameraUpdateFactory.newLatLngBounds(builder.build(), padding)));
    }

    @Override
    public void removeRouteOverlay() {
        for (Polyline p : mLineOverlay) {
            p.remove();
        }

        mLineOverlay.clear();
    }
}
