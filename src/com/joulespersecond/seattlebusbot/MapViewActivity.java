package com.joulespersecond.seattlebusbot;

import java.util.List;

import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.Window;

import com.google.android.maps.MapActivity;
import com.google.android.maps.MapView;
import com.google.android.maps.MyLocationOverlay;
import com.google.android.maps.Overlay;

public class MapViewActivity extends MapActivity {
	private static final String TAG = "MapViewActivity";
	
	private MyLocationOverlay mLocationOverlay;
	private StopOverlay mStopOverlay;
	
	private class GetRouteTask extends AsyncTask<String,Void,ObaResponse> {
		@Override
		protected void onPreExecute() {
	        setProgressBarIndeterminateVisibility(true);
		}
		@Override
		protected ObaResponse doInBackground(String... params) {
			return ObaApi.getStopsForRoute(params[0]);
		}
		@Override
		protected void onPostExecute(ObaResponse result) {

	    	if (result.getCode() != ObaApi.OBA_OK) {
	    		Log.v(TAG, "Request failed: " + result.getText());
	    		return;
	    	}
            MapView mapView = (MapView)findViewById(R.id.mapview);
        	List<Overlay> mapOverlays = mapView.getOverlays();
			// If there is an existing StopOverlay, remove it.
	    	if (mStopOverlay != null) {
	        	mapOverlays.remove(mStopOverlay);
	    	}
	        	
	        mStopOverlay = new StopOverlay(result.getData().getStops(),
	        			MapViewActivity.this);
	        mapOverlays.add(mStopOverlay);
	        mapView.postInvalidate();
	        setProgressBarIndeterminateVisibility(false);
		}
	}
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.main);
        MapView mapView = (MapView)findViewById(R.id.mapview);
        mapView.setBuiltInZoomControls(true);
        // Add the MyLocation Overlay
        mLocationOverlay = new MyLocationOverlay(this, mapView);
    	List<Overlay> mapOverlays = mapView.getOverlays();
    	mapOverlays.add(mLocationOverlay);
    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
    	MenuInflater inflater = getMenuInflater();
    	inflater.inflate(R.menu.main_options, menu);
    	return true;
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
    	if (item.getItemId() == R.id.my_location) {
    	}
    	else if (item.getItemId() == R.id.find_route) {
    		new GetRouteTask().execute("0001_8");
    	}
    	return false;
    }
    @Override
    public void onPause() {
    	mLocationOverlay.disableMyLocation();
    	super.onPause();
    }
    @Override
    public void onResume() {
    	mLocationOverlay.enableMyLocation();
    	super.onResume();
    }
    @Override
    protected void onSaveInstanceState(Bundle outState) {
    	// TODO: Save whether or not we have any overlays, and what the state of those are.
    	super.onSaveInstanceState(outState);
    }                   
    @Override
    protected boolean isRouteDisplayed() {
    	return false;
    }
}