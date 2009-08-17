package com.joulespersecond.seattlebusbot;

import java.util.List;

import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.Window;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapActivity;
import com.google.android.maps.MapController;
import com.google.android.maps.MapView;
import com.google.android.maps.MyLocationOverlay;
import com.google.android.maps.Overlay;

public class MapViewActivity extends MapActivity {
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
	    	if (result.getCode() == ObaApi.OBA_OK) {
	    		setStopOverlay(result.getData().getStops());
	    	}
	        setProgressBarIndeterminateVisibility(false); 
		}		
	}
	
	private class GetStopsByLocationInfo {
		int latSpan;
		int lonSpan;
		
		public GetStopsByLocationInfo(int lat, int lon) {
			latSpan = lat;
			lonSpan = lon;
		}
	}
	
	private class GetStopsByLocationTask extends AsyncTask<Object,Void,ObaResponse> {
		@Override
		protected void onPreExecute() {
	        setProgressBarIndeterminateVisibility(true);
		}
		@Override
		protected ObaResponse doInBackground(Object... params) {
			GeoPoint point = (GeoPoint)params[0];
			GetStopsByLocationInfo info = (GetStopsByLocationInfo)params[1];
			return ObaApi.getStopsByLocation(point, 0, info.latSpan, info.lonSpan, null, 0);
		}
		@Override
		protected void onPostExecute(ObaResponse result) {
	    	if (result.getCode() == ObaApi.OBA_OK) {
	    		setStopOverlay(result.getData().getStops());
	    	}
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
    	// TODO: Only go to MyLocation if this is due to launching.
    	setMyLocation();
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
    		setMyLocation();
    		return true;
    	}
    	else if (item.getItemId() == R.id.find_route) {
    		new GetRouteTask().execute("0001_8");
    		return true;
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
    	// TODO: Reset overlays -- add the MyLocation overlay, 
    	// add the route overlay
    	super.onResume();
    }
    @Override
    protected void onSaveInstanceState(Bundle outState) {
    	// TODO: Remember what we were doing. The MapView automatically remembers the position
    	// of the map, so we just need to remember the state of our overlays.
    	super.onSaveInstanceState(outState);
    }
    @Override
    protected boolean isRouteDisplayed() {
    	return false;
    }
    
    // This is a bit annoying: runOnFirstFix() calls its runnable either
    // immediately or on another thread (AsyncTask). Since we don't know
    // what thread the runnable will be run on , and since AsyncTasks have
    // to be created from the UI thread, we need to post a message back to the
    // UI thread just to create another AsyncTask.
    final Handler mGetStopsHandler = new Handler();
    final Runnable mGetStops = new Runnable() {
    	public void run() {
    		setMyLocation(mLocationOverlay.getMyLocation());
    	}
    };
    private void setMyLocation(GeoPoint point) {
		MapView mapView = (MapView)findViewById(R.id.mapview);
		MapController mapCtrl = mapView.getController();
		mapCtrl.animateTo(point);
		mapCtrl.setZoom(16);
		GetStopsByLocationInfo info = new GetStopsByLocationInfo(
				mapView.getLatitudeSpan(),
				mapView.getLongitudeSpan());
		new GetStopsByLocationTask().execute(point, info);     	
    }
    
    private void setMyLocation() {
		GeoPoint point = mLocationOverlay.getMyLocation();
		if (point == null) {
			mLocationOverlay.runOnFirstFix(new Runnable() {
				public void run() {
					mGetStopsHandler.post(mGetStops);
				} 		
			});
		}
		else {
			setMyLocation(point);
		}
    }
    private void setStopOverlay(ObaArray stops) {
        MapView mapView = (MapView)findViewById(R.id.mapview);
    	List<Overlay> mapOverlays = mapView.getOverlays();
		// If there is an existing StopOverlay, remove it.
    	if (mStopOverlay != null) {
        	mapOverlays.remove(mStopOverlay);
    	}

        mStopOverlay = new StopOverlay(stops, this);
        mapOverlays.add(mStopOverlay);
        mapView.postInvalidate();
    }
}