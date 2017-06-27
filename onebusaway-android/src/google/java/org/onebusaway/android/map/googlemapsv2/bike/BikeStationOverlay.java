package org.onebusaway.android.map.googlemapsv2.bike;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import org.onebusaway.android.R;
import org.onebusaway.android.app.Application;
import org.onebusaway.android.map.googlemapsv2.BaseMapFragment;
import org.onebusaway.android.map.googlemapsv2.MapHelpV2;
import org.onebusaway.android.map.googlemapsv2.MarkerListeners;
import org.opentripplanner.routing.bike_rental.BikeRentalStation;

import java.util.HashMap;
import java.util.List;

/**
 * Class to hold bike stations and control their display on the map.
 *
 * @author carvalhorr
 */
public class BikeStationOverlay implements MarkerListeners, BikeInfoWindow.BikeStationsInfo {

    private GoogleMap mMap;

    private static final int FUZZY_MAX_MARKER_COUNT = 200;

    private HashMap<Marker, BikeRentalStation> mStations;

    private BaseMapFragment.OnFocusChangedListener mOnFocusChangedListener;

    private BitmapDescriptor mSmallBikeStationIcon;
    private BitmapDescriptor mBigBikeStationIcon;
    private BitmapDescriptor mBigFloatingBikeIcon;

    public BikeStationOverlay(Activity activity, GoogleMap map) {
        mMap = map;
        mStations = new HashMap<>();
        mMap.setInfoWindowAdapter(new BikeInfoWindow(activity, this));

        mSmallBikeStationIcon = BitmapDescriptorFactory.fromBitmap(createBitmapFromShape());
        mBigBikeStationIcon = BitmapDescriptorFactory.fromResource(R.drawable.bike_station_marker_big);
        mBigFloatingBikeIcon = BitmapDescriptorFactory.fromResource(R.drawable.bike_floating_marker_big);
    }

    private synchronized void addMarker(BikeRentalStation station) {
        MarkerOptions options = new MarkerOptions().position(MapHelpV2.makeLatLng(station.y, station.x));
        if (mMap.getCameraPosition().zoom > 13) {
            if (station.isFloatingBike) {
                options.icon(mBigFloatingBikeIcon);
            } else {
                options.icon(mBigBikeStationIcon);
            }
        } else {
            options.icon(mSmallBikeStationIcon);
        }
        Marker m = mMap.addMarker(options);
        mStations.put(m, station);
    }

    public void setOnFocusChangeListener(BaseMapFragment.OnFocusChangedListener onFocusChangedListener) {
        mOnFocusChangedListener = onFocusChangedListener;
    }


    public void addBikeStations(List<BikeRentalStation> bikeStations) {
        /*if (mStations.size() >= FUZZY_MAX_MARKER_COUNT) {
            clearBikeStations();
        }*/
        clearBikeStations();
        for (BikeRentalStation bikeStation : bikeStations) {
            addMarker(bikeStation);
        }
    }

    public void clearBikeStations() {
        for (Marker marker : mStations.keySet()) {
            marker.remove();
        }
        mStations.clear();
    }

    @Override
    public boolean markerClicked(Marker marker) {

        if (mStations.containsKey(marker)) {
            if (mOnFocusChangedListener != null) {
                BikeRentalStation bikeRentalStation = mStations.get(marker);
                marker.showInfoWindow();
                mOnFocusChangedListener.onFocusChanged(bikeRentalStation);
            }
            return true;
        }
        return false;
    }

    @Override
    public void removeMarkerClicked(LatLng latLng) {
        Log.d("", "remove selected bike station");
        mOnFocusChangedListener.onFocusChanged(null);
    }

    private Bitmap createBitmapFromShape() {
        int px = Application.get().getResources().getDimensionPixelSize(R.dimen.map_stop_shadow_size_6);

        Bitmap bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888);

        Canvas c = new Canvas(bitmap);
        Drawable shape = ContextCompat.getDrawable(Application.get(), R.drawable.bike_marker_small);
        shape.setBounds(0, 0, bitmap.getWidth(), bitmap.getHeight());

        shape.draw(c);

        return bitmap;
    }

    @Override
    public BikeRentalStation getBikeStationOnMarker(Marker marker) {
        return mStations.get(marker);
    }

}
