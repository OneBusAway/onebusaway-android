package com.joulespersecond.seattlebusbot;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import android.content.Intent;
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
	private MapView mMapView;
	private MyLocationOverlay mLocationOverlay;
	private StopOverlay mStopOverlay;
	
	// There's a major hole in the MapView in that there's apparently 
	// no way of getting an event when the user pans the view.
	// Oh well, we'll just set a timer and poll.
	// When we see the map center change, we'll wait for a second or so
	// and then request new stops.
	private Timer mTimer;
	private GeoPoint mMapCenter;
	private boolean mMapCenterWaitFlag = false;
	private static final int CenterPollPeriod = 2000;
	
	/*
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
	*/
	
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
        mMapView = (MapView)findViewById(R.id.mapview);
        mMapView.setBuiltInZoomControls(true);
        mMapCenter = mMapView.getMapCenter();
        // Add the MyLocation Overlay
        mLocationOverlay = new MyLocationOverlay(this, mMapView);
    	List<Overlay> mapOverlays = mMapView.getOverlays();
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
    		Intent myIntent = new Intent(this, FindActivity.class);
    		myIntent.putExtra(FindActivity.CURRENT_TAB, FindActivity.TAB_FIND_ROUTE);
    		startActivity(myIntent);
    		return true;
    	}
    	else if (item.getItemId() == R.id.find_stop) {
    		Intent myIntent = new Intent(this, FindActivity.class);
    		myIntent.putExtra(FindActivity.CURRENT_TAB, FindActivity.TAB_FIND_STOP);
    		startActivity(myIntent);
    		return true;
    	}
    	return false;
    }
    @Override
    public void onPause() {
    	mLocationOverlay.disableMyLocation();
    	mTimer.cancel(); 	
    	super.onPause();
    }
    @Override
    public void onResume() {
    	mLocationOverlay.enableMyLocation();
    	watchMapCenter(); 
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
    
    private void watchMapCenter() {
    	mTimer = new Timer();
    	mTimer.schedule(new TimerTask() {
    		@Override
    		public void run() {
    			// If the map center has moved:
    			//		If the wait flag is set:
    			//			Set the new center
    			//			Request new stops
    			//			Clear the wait flag
    			//		Else:
    			//			Set the wait flag
    			//			
    			GeoPoint newCenter = mMapView.getMapCenter();
    			if (!newCenter.equals(mMapCenter)) {
    				if (mMapCenterWaitFlag) {
    					mMapCenter = newCenter;
    					mMapCenterWaitFlag = false;
    					// This is run in another thread, so post back to the UI thread.
    					mGetStopsHandler.post(mGetStopsFromCenter);
    				} else {
    					mMapCenterWaitFlag = true;
    				}
    			}
    		}
    	}, CenterPollPeriod, CenterPollPeriod);
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
    final Runnable mGetStopsFromCenter = new Runnable() {
    	public void run() {
    		getStopsByLocation(mMapCenter);
    	}
    };
    private void setMyLocation(GeoPoint point) {
		MapController mapCtrl = mMapView.getController();
		mapCtrl.animateTo(point);
		mapCtrl.setZoom(16);
		mMapCenter = point;
		getStopsByLocation(point);   	
    }
    private void getStopsByLocation(GeoPoint point) {
		GetStopsByLocationInfo info = new GetStopsByLocationInfo(
				mMapView.getLatitudeSpan(),
				mMapView.getLongitudeSpan());
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
    	List<Overlay> mapOverlays = mMapView.getOverlays();
		// If there is an existing StopOverlay, remove it.
    	if (mStopOverlay != null) {
        	mapOverlays.remove(mStopOverlay);
    	}

        mStopOverlay = new StopOverlay(stops, this);
        mapOverlays.add(mStopOverlay);
        mMapView.postInvalidate();
    }
}