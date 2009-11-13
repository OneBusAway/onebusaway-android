package com.joulespersecond.seattlebusbot;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.text.Spannable;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.GridView;
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
import com.joulespersecond.oba.ObaApi;
import com.joulespersecond.oba.ObaArray;
import com.joulespersecond.oba.ObaResponse;
import com.joulespersecond.oba.ObaRoute;
import com.joulespersecond.oba.ObaStop;
import com.joulespersecond.seattlebusbot.StopOverlay.StopOverlayItem;

public class MapViewActivity extends MapActivity {
    private static final String TAG = "MapViewActivity";
    
    public static final String HELP_URL = "http://www.joulespersecond.com/seattlebusbot/userguide-v1.1.html";
    
    private static final String FOCUS_STOP_ID = ".FocusStopId";
    private static final String CENTER_LAT = ".CenterLat";
    private static final String CENTER_LON = ".CenterLon";
    // Switches to 'route mode' -- stops aren't updated on move
    private static final String ROUTE_ID = ".RouteId";    
    
    public MapView mMapView;
    private MyLocationOverlay mLocationOverlay;
    public StopOverlay mStopOverlay;
    private String mRouteId;
    private String mFocusStopId;
    private AsyncTask<Object,Void,ObaResponse> mGetStopsByLocationTask;
    private AsyncTask<String,Void,ObaResponse> mGetStopsForRouteTask;
    private volatile boolean mForceRestartLocationTask;
    private ObaResponse mStopsResponse;
    
    // There's a major hole in the MapView in that there's apparently 
    // no way of getting an event when the user pans the view.
    // Oh well, we'll just set a timer and poll.
    // When we see the map center change, we'll wait for a second or so
    // and then request new stops.
    private Timer mTimer;
    private static final int CENTER_POLL_PERIOD = 2000;
    
    /**
     * Starts the MapActivity with a particular stop focused with the
     * center of the map at a particular point. 
     * 
     * @param context The context of the activity.
     * @param focusId The stop to focus.
     * @param lat The latitude of the map center.
     * @param lon The longitude of the map center.
     */
    public static final void start(Context context, String focusId, double lat, double lon) {
        Intent myIntent = new Intent(context, MapViewActivity.class);
        myIntent.putExtra(FOCUS_STOP_ID, focusId);
        myIntent.putExtra(CENTER_LAT, lat);
        myIntent.putExtra(CENTER_LON, lon);
        context.startActivity(myIntent);
    }
    /**
     * Starts the MapActivity in "RouteMode", which shows stops along a route,
     * and does not get new stops when the user pans the map.
     * 
     * @param context The context of the activity.
     * @param routeId The route to show.
     * @param stopData If this is non-null, this is string representation of 
     *          the ObaResponse to use as the stops for the route.
     */
    public static final void start(Context context, String routeId) {
        Intent myIntent = new Intent(context, MapViewActivity.class);
        myIntent.putExtra(ROUTE_ID, routeId);
        context.startActivity(myIntent);        
    }
    
    private final AsyncTasks.Handler<ObaResponse> mAsyncTaskHandler 
        = new AsyncTasks.Handler<ObaResponse>() {
            public void handleResult(ObaResponse result) {
                setStopOverlay(result);
            }
    };
    private final AsyncTasks.ProgressIndeterminateVisibility mAsyncProgress 
        = new AsyncTasks.ProgressIndeterminateVisibility(this);
    
    private class GetStopsForRouteTask extends AsyncTasks.StringToResponse {
        GetStopsForRouteTask() {
            super(mAsyncProgress, mAsyncTaskHandler);
        }
        @Override
        protected ObaResponse doInBackground(String... params) {
            return ObaApi.getStopsForRoute(params[0]);
        }
        @Override
        protected void doResult(ObaResponse result) { }   
    }
    
    private class GetStopsByLocationTask extends AsyncTasks.ToResponseBase<Object> {
        GetStopsByLocationTask() {
            super(mAsyncProgress);
        }
        @Override
        protected ObaResponse doInBackground(Object... params) {
            GeoPoint point = (GeoPoint)params[0];
            Integer latSpan = (Integer)params[1];
            Integer lonSpan = (Integer)params[2];
            return ObaApi.getStopsByLocation(point, 0, latSpan, lonSpan, null, 0);
        }
        @Override
        protected void doResult(ObaResponse result) {
            setStopOverlay(result);
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
        // Add the MyLocation Overlay
        mLocationOverlay = new MyLocationOverlay(this, mMapView);
        List<Overlay> mapOverlays = mMapView.getOverlays();
        mapOverlays.add(mLocationOverlay);
        
        // Initialize the links
        setClickable(R.id.show_arrival_info, mOnShowArrivals);
        setClickable(R.id.show_routes, mOnShowRoutes);        
        
        // If you click on the popup but not on a link, nothing happens
        // (if this weren't there, the popup would be dismissed)
        View popup = findViewById(R.id.map_popup);
        popup.setOnClickListener(mPopupClick);
        
        Bundle bundle = getIntent().getExtras();
        double centerLat = 0.0;
        double centerLon = 0.0;
        if (bundle != null) {
            mRouteId = bundle.getString(ROUTE_ID);
            mFocusStopId = bundle.getString(FOCUS_STOP_ID);
            centerLat = bundle.getDouble(CENTER_LAT);
            centerLon = bundle.getDouble(CENTER_LON);
        }
        if (savedInstanceState != null) {
            String focusedId = savedInstanceState.getString(FOCUS_STOP_ID);
            if (focusedId != null) {
                mFocusStopId = focusedId;
            }
        }
        final boolean routeMode = isRouteMode();
        
        final Object config = getLastNonConfigurationInstance();
        if (config != null) {
            final Object[] result = (Object[])config;
            mFocusStopId = (String)result[1];
            assert(result[0] != null);
            setStopOverlay((ObaResponse)result[0]);
        }
        else if (routeMode) {
            // Initial instance -- route mode.
            getStopsForRoute();
        }
        else if (centerLat != 0.0 && centerLon != 0.0) {
            // Initial instance -- "show stop mode"
            // (initially moved to focused stop, but update the stops
            //  if the user pans the map)
            GeoPoint point = ObaApi.makeGeoPoint(centerLat, centerLon);
            MapController mapCtrl = mMapView.getController();
            mapCtrl.setCenter(point);
            mapCtrl.setZoom(18);
            getStopsByLocation(point);
        }
        else {
            // Regular "default" start -- leave the center
            // as it, and go to the user's location.
            setMyLocation();
        }
    }
    @Override
    public void onDestroy() {
        if (mGetStopsByLocationTask != null) {
            mGetStopsByLocationTask.cancel(true);
        }
        if (mGetStopsForRouteTask != null) {
            mGetStopsForRouteTask.cancel(true);
        }
        super.onDestroy();
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
        // The only thing we really need to save it the focused stop ID.
        String focusedStopId = getFocusedStopId();
        if (focusedStopId != null) {
            outState.putString(FOCUS_STOP_ID, focusedStopId);
        }
    }
    @Override
    public Object onRetainNonConfigurationInstance() {
        if (mStopsResponse != null) {
            return new Object[] { mStopsResponse, getFocusedStopId() };
        }
        else {
            return null;
        }
    }
    @Override
    protected boolean isRouteDisplayed() {
        return false;
    }
    
    private void watchMapCenter() {
        mTimer = new Timer();
        mTimer.schedule(new TimerTask() {
            private GeoPoint mMapCenter = mMapView.getMapCenter();
            
            @Override
            public void run() {
                final boolean restart = mForceRestartLocationTask;
                
                GeoPoint newCenter = mMapView.getMapCenter();
                if (restart || !newCenter.equals(mMapCenter)) {
                    mMapCenter = newCenter;
                    // This is run in another thread, so post back to the UI thread.
                    mGetStopsHandler.post(mGetStopsFromCenter);
                }
            }
        }, CENTER_POLL_PERIOD, CENTER_POLL_PERIOD);
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
                getStopsByLocation(mMapView.getMapCenter());
            }
        }
    };
    private void setMyLocation(GeoPoint point) {
        MapController mapCtrl = mMapView.getController();
        mapCtrl.animateTo(point);
        mapCtrl.setZoom(16);
        if (!isRouteMode()) {
            getStopsByLocation(point);       
        }
    }
    
    private void getStopsByLocation(GeoPoint point) {
        Log.d(TAG, "getStopsByLocation");
        if (AsyncTasks.isRunning(mGetStopsByLocationTask)) {
            Log.d(TAG, "Set force restart");
            mForceRestartLocationTask = true;
            return;
        }
        Log.d(TAG, "Starting async task");
        mForceRestartLocationTask = false;
        mGetStopsByLocationTask = new GetStopsByLocationTask();
        mGetStopsByLocationTask.execute(point, 
                new Integer(mMapView.getLatitudeSpan()),
                new Integer(mMapView.getLongitudeSpan()));             
    }
    private void getStopsForRoute() {
        if (AsyncTasks.isRunning(mGetStopsForRouteTask)) {
            return;
        }
        assert(mRouteId != null);
        mGetStopsForRouteTask = new GetStopsForRouteTask().execute(mRouteId);
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
    
    private class RouteArrayAdapter extends Adapters.BaseRouteArrayAdapter {       
        public RouteArrayAdapter(ObaArray routes) {
            super(MapViewActivity.this, routes, R.layout.main_popup_route_item);
        }
        @Override
        protected void setData(View view, int position) {
            TextView shortName = (TextView)view.findViewById(R.id.short_name);

            ObaRoute route = mArray.getRoute(position);
            shortName.setText(route.getShortName());
        }
    }    
    
    void populateRoutes(ObaStop stop, boolean force) {
        GridView grid = (GridView)findViewById(R.id.route_list);
        if (grid.getVisibility() != View.GONE || force) {
            grid.setAdapter(new RouteArrayAdapter(stop.getRoutes()));
        }
    }
    
    final Handler mStopChangedHandler = new Handler();
    final OnFocusChangeListener mFocusChangeListener = new OnFocusChangeListener() {
        public void onFocusChanged(@SuppressWarnings("unchecked") ItemizedOverlay overlay,
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
                     direction.setText(
                             StopInfoActivity.getStopDirectionText(stop.getDirection()));

                     populateRoutes(stop, false);
                    
                     // Right now the popup is always at the top of the screen.
                     popup.setVisibility(View.VISIBLE);
                }    
             });
        }
    };
    final ClickableSpan mOnShowArrivals = new ClickableSpan() {
        public void onClick(View v) {
            StopOverlayItem item = (StopOverlayItem)mStopOverlay.getFocus();
            if (item != null) {
                goToStop(MapViewActivity.this, item.getStop());
            }
        }
    };
    final ClickableSpan mOnShowRoutes = new ClickableSpan() {
        public void onClick(View v) {
            GridView grid = (GridView)findViewById(R.id.route_list);
            TextView text = (TextView)v;
            if (grid.getVisibility() != View.GONE) {
                // Hide this 
                grid.setVisibility(View.GONE);
                // Change link text
                text.setText(R.string.main_show_routes);
            } else {
                final StopOverlayItem item = (StopOverlayItem)mStopOverlay.getFocus();   
                // TODO: Animate at some point...
                populateRoutes(item.getStop(), true);
                grid.setVisibility(View.VISIBLE);
                text.setText(R.string.main_hide_routes);
            }
            // When the text changes, we need to reset its clickable status
            Spannable span = (Spannable)text.getText();
            span.setSpan(this, 0, span.length(), 0);
        }
    };
    final View.OnClickListener mPopupClick = new View.OnClickListener() {
        public void onClick(View v) {  
            // Eat the click so the Map doesn't get it.
        }
    };
    
    private void setStopOverlay(ObaResponse response) {
        mStopsResponse = response;
        
        if (response.getCode() != ObaApi.OBA_OK) {
            return;
        }
        final ObaArray stops = response.getData().getStops();
        
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
    private String getFocusedStopId() {
        if (mStopOverlay != null) {
            return mStopOverlay.getFocusedId();
        }
        return null;
    }
    
    private void setClickable(int id, ClickableSpan span) {
        TextView v = (TextView)findViewById(id);
        Spannable text = (Spannable)v.getText();
        text.setSpan(span, 0, text.length(), 0);
        v.setMovementMethod(LinkMovementMethod.getInstance());
    }

    static void goToStop(Context context, ObaStop stop) {
        StopInfoActivity.start(context, stop);
    }
    
    private boolean isRouteMode() {
        return mRouteId != null;
    }
}