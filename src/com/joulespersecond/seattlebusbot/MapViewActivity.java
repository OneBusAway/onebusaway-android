package com.joulespersecond.seattlebusbot;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.TextView;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.ItemizedOverlay;
import com.google.android.maps.MapActivity;
import com.google.android.maps.MapController;
import com.google.android.maps.MapView;
import com.google.android.maps.MyLocationOverlay;
import com.google.android.maps.Overlay;
import com.google.android.maps.OverlayItem;
import com.google.android.maps.ItemizedOverlay.OnFocusChangeListener;
import com.joulespersecond.seattlebusbot.StopOverlay.StopOverlayItem;

public class MapViewActivity extends MapActivity {
	private static final String TAG = "MapViewActivity";
	
    private static final String HELP_URL = "http://www.joulespersecond.com/seattlebusbot/userguide-v1.html";	
	
	public static final String FOCUS_STOP_ID = ".FocusStopId";
	public static final String CENTER_LAT = ".CenterLat";
	public static final String CENTER_LON = ".CenterLon";
	// Switches to 'route mode' -- stops aren't updated on move
	public static final String ROUTE_ID = ".RouteId";	
	// If this is specified, it is a JSON string that corresponds to a ObaResponse
	public static final String STOP_DATA = ".StopData";
	
	public MapView mMapView;
	private MyLocationOverlay mLocationOverlay;
	public StopOverlay mStopOverlay;
	private String mRouteId;
	private String mFocusStopId;
	
	// There's a major hole in the MapView in that there's apparently 
	// no way of getting an event when the user pans the view.
	// Oh well, we'll just set a timer and poll.
	// When we see the map center change, we'll wait for a second or so
	// and then request new stops.
	private Timer mTimer;
	private GeoPoint mMapCenter;
	private boolean mMapCenterWaitFlag = false;
	private static final int CenterPollPeriod = 2000;
	
	private abstract class GetStopsForRouteTaskBase extends AsyncTask<String,Void,ObaResponse> {
		@Override
		protected void onPreExecute() {
	        setProgressBarIndeterminateVisibility(true);
		}
		@Override
		protected void onPostExecute(ObaResponse result) {
	    	if (result.getCode() == ObaApi.OBA_OK) {
	    		setStopOverlay(result.getData().getStops());
	    	}
	        setProgressBarIndeterminateVisibility(false); 
		}		
	}
	
	private class GetStopsForRouteTask extends GetStopsForRouteTaskBase {
		@Override
		protected ObaResponse doInBackground(String... params) {
			return ObaApi.getStopsForRoute(params[0]);
		}	
	}
	
	private class GetStopsForRouteTask2 extends GetStopsForRouteTaskBase {
		@Override
		protected ObaResponse doInBackground(String... params) {
			try {
				return new ObaResponse(new JSONObject(params[0]));
			} catch (JSONException e) {
				Log.e(TAG, "Expected JSON data, got something else entirely: " + params[0]);
				// I guess we don't have stop data...
				e.printStackTrace();
				return new ObaResponse("JSON error");
			}
		}	
	}
	
	private final class GetStopsByLocationInfo {
		private final int latSpan;
		private final int lonSpan;
		
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
    	
    	// Initialize the links
    	TextView arrival = (TextView)findViewById(R.id.show_arrival_info);
    	arrival.setOnClickListener(mOnShowArrivals);
    	
    	String stopData = null;
    	Bundle bundle = getIntent().getExtras();
    	double centerLat = 0.0;
    	double centerLon = 0.0;
    	boolean setZoom = true;
    	boolean goToLocation = true;
    	if (bundle != null) {
    		mRouteId = bundle.getString(ROUTE_ID);
    		stopData = bundle.getString(STOP_DATA);
    		mFocusStopId = bundle.getString(FOCUS_STOP_ID);
    		centerLat = bundle.getDouble(CENTER_LAT);
    		centerLon = bundle.getDouble(CENTER_LON);
    	}
    	// These will override anything that's in the intent.
    	if (savedInstanceState != null) {
    		mFocusStopId = savedInstanceState.getString(FOCUS_STOP_ID);
    		if (savedInstanceState.containsKey(CENTER_LAT)) {
    			centerLat = savedInstanceState.getDouble(CENTER_LAT);
    			centerLon = savedInstanceState.getDouble(CENTER_LON);
    			setZoom = false;
    		}
    	}
    	final boolean routeMode = isRouteMode();
    	
		if (centerLat != 0.0 && centerLon != 0.0) {
			GeoPoint point = ObaApi.makeGeoPoint(centerLat, centerLon);
			MapController mapCtrl = mMapView.getController();
			mapCtrl.setCenter(point);
			if (setZoom) {
				mapCtrl.setZoom(18);
			}
			mMapCenter = point; 
			if (!routeMode) {
				getStopsByLocation(mMapCenter);
			}
			goToLocation = false;
		}
		if (!routeMode && goToLocation) {
			setMyLocation();			
		}
		if (routeMode) {
			getStopsForRoute(stopData);			
		}
    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
    	MenuInflater inflater = getMenuInflater();
    	inflater.inflate(R.menu.main_options, menu);
    	return true;
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
    	final int id = item.getItemId();
    	if (id == R.id.my_location) {
    		setMyLocation();
    		return true;
    	}
    	else if (id == R.id.find_route) {
    		Intent myIntent = new Intent(this, FindRouteActivity.class);
    		startActivity(myIntent);
    		return true;
    	}
    	else if (id == R.id.find_stop) {
    		Intent myIntent = new Intent(this, FindStopActivity.class);
    		startActivity(myIntent);
    		return true;
    	}
    	else if (id == R.id.view_trips) {
    		Intent myIntent = new Intent(this, TripListActivity.class);
    		startActivity(myIntent);
    		return true;    		
    	}
        else if (id == R.id.help) {
            Intent myIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(HELP_URL));
            startActivity(myIntent);
            return true;            
        }
    	return false;
    }
    @Override
    public void onPause() {
    	mLocationOverlay.disableMyLocation();
    	if (mTimer != null) {
        	mTimer.cancel();   		
    	}
    	super.onPause();
    }
    @Override
    public void onResume() {
    	mLocationOverlay.enableMyLocation();
    	if (!isRouteMode()) {
    		watchMapCenter();
    	} 
    	super.onResume();
    }
    @Override
    protected void onSaveInstanceState(Bundle outState) {
    	// Save the center of the map so we automatically 
    	// get the stops around it when we restart.
    	GeoPoint center = mMapCenter = mMapView.getMapCenter();
    	if (center != null) {
    		outState.putDouble(CENTER_LAT, center.getLatitudeE6()/ObaApi.E6);
    		outState.putDouble(CENTER_LON, center.getLongitudeE6()/ObaApi.E6);
    	}
    	
    	StopOverlay overlay = mStopOverlay;
    	if (overlay != null) {
    		String id = overlay.getFocusedId();
    		if (id != null) {
    			outState.putString(FOCUS_STOP_ID, id); 
    		}
    	}
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
    		if (!isRouteMode()) {
    			getStopsByLocation(mMapCenter);
    		}
    	}
    };
    private void setMyLocation(GeoPoint point) {
		MapController mapCtrl = mMapView.getController();
		mMapCenter = point;
		mapCtrl.animateTo(point);
		mapCtrl.setZoom(16);
		if (!isRouteMode()) {
			getStopsByLocation(point);   	
		}
    }
    private void getStopsByLocation(GeoPoint point) {
		GetStopsByLocationInfo info = new GetStopsByLocationInfo(
				mMapView.getLatitudeSpan(),
				mMapView.getLongitudeSpan());
		new GetStopsByLocationTask().execute(point, info);       	
    }
    private void getStopsForRoute(String stopData) {
    	if (stopData != null) {
    		new GetStopsForRouteTask2().execute(stopData);
    	}
    	else {
    		assert(mRouteId != null);
    		new GetStopsForRouteTask().execute(mRouteId);
    	}
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
    
    final Handler mStopChangedHandler = new Handler();
    final OnFocusChangeListener mFocusChangeListener = new OnFocusChangeListener() {
		@SuppressWarnings("unchecked")
		public void onFocusChanged(ItemizedOverlay overlay,
				final OverlayItem newFocus) {
		 	mStopChangedHandler.post(new Runnable() {
		    	public void run() {
		    		final View popup = findViewById(R.id.map_popup);
		    		if (newFocus == null) {
		    			popup.setVisibility(View.GONE);
		    			return;
		    		}
		    		
		    		final StopOverlay.StopOverlayItem item = (StopOverlayItem)newFocus;
		    		final ObaStop stop = item.getStop();
		    		
		    		final TextView name = (TextView)popup.findViewById(R.id.stop_name);
		    		name.setText(stop.getName());
		    		
		    		final TextView direction = (TextView)popup.findViewById(R.id.direction);
		    		direction.setText(StopInfoActivity.getStopDirectionText(stop.getDirection()));

		    		// Right now the popup is always at the top of the screen.
		    		popup.setVisibility(View.VISIBLE);
		    	}    
		 	});
		}
	};
	final View.OnClickListener mOnShowArrivals = new View.OnClickListener() {
		public void onClick(View v) {
			StopOverlayItem item = (StopOverlayItem)mStopOverlay.getFocus();
			if (item != null) {
				goToStop(MapViewActivity.this, item.getStop());
			}
		}
	};
    
    private void setStopOverlay(ObaArray stops) {
    	List<Overlay> mapOverlays = mMapView.getOverlays();
		// If there is an existing StopOverlay, remove it.
		final View popup = findViewById(R.id.map_popup);
		popup.setVisibility(View.GONE);
		String focused = null;
		
    	if (mStopOverlay != null) {
    		StopOverlayItem item = (StopOverlayItem)mStopOverlay.getFocus();
    		if (item != null) {
    			focused = item.getStop().getId();
    		}
        	mapOverlays.remove(mStopOverlay);
    	}

        mStopOverlay = new StopOverlay(stops, this);
        mStopOverlay.setOnFocusChangeListener(mFocusChangeListener);
        if (focused != null) {
        	mStopOverlay.setFocusById(focused);
        }
        else if (mFocusStopId != null) {
        	mStopOverlay.setFocusById(mFocusStopId);
        	// This is a one-shot thing, and it only happens from an intent.
        	mFocusStopId = null;
        }
        mapOverlays.add(mStopOverlay);
        mMapView.postInvalidate();
    }
    
    public static void goToStop(Context context, ObaStop stop) {
    	Intent myIntent = new Intent(context, StopInfoActivity.class);
    	myIntent.putExtra(StopInfoActivity.STOP_ID, stop.getId());
    	context.startActivity(myIntent);	    	
    }

    private boolean isRouteMode() {
    	return mRouteId != null;
    }
}