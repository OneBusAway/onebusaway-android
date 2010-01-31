package com.joulespersecond.seattlebusbot;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.text.Spannable;
import android.text.style.ClickableSpan;
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
    //private static final String TAG = "MapViewActivity";
    
    public static final String HELP_URL = "http://www.joulespersecond.com/seattlebusbot/userguide-v1.1.html";
    public static final String TWITTER_URL = "http://mobile.twitter.com/seattlebusbot";
    
    private static final String FOCUS_STOP_ID = ".FocusStopId";
    private static final String CENTER_LAT = ".CenterLat";
    private static final String CENTER_LON = ".CenterLon";
    // Switches to 'route mode' -- stops aren't updated on move
    private static final String ROUTE_ID = ".RouteId";    
    
    MapView mMapView;
    private MyLocationOverlay mLocationOverlay;
    StopOverlay mStopOverlay;
    private String mRouteId;
    private String mFocusStopId;
    private AsyncTask<Object,Void,ObaResponse> mGetStopsByLocationTask;
    private AsyncTask<String,Void,ObaResponse> mGetStopsForRouteTask;
    private volatile boolean mForceRestartLocationTask;
    private ObaResponse mStopsResponse;
    // This is the map zoom level at which we requested this response.
    private int mStopsResponseZoomLevel = 0;
    
    // There's a major hole in the MapView in that there's apparently 
    // no way of getting an event when the user pans the view.
    // Oh well, we'll just set a timer and poll.
    // When we see the map center change, we'll wait for a second or so
    // and then request new stops.
    private Timer mTimer;
    private static final int CENTER_POLL_PERIOD = 2000;
    
    private static final int HELP_DIALOG = 1;
    private static final int WHATSNEW_DIALOG = 2;
    
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
            return ObaApi.getStopsForRoute(MapViewActivity.this, params[0]);
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
            return ObaApi.getStopsByLocation(MapViewActivity.this, point, 0, latSpan, lonSpan, null, 0);
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
        mLocationOverlay = new MyFixedLocationOverlay(this, mMapView);
        List<Overlay> mapOverlays = mMapView.getOverlays();
        mapOverlays.add(mLocationOverlay);
        
        // Initialize the links
        UIHelp.setChildClickable(this, R.id.show_arrival_info, mOnShowArrivals);
        UIHelp.setChildClickable(this, R.id.show_routes, mOnShowRoutes);        
        
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
            showRoutes(null, null, (Boolean)result[2]);
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
        
        autoShowWhatsNew();
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
            showDialog(HELP_DIALOG);
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
            watchMap();
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
            return new Object[] { 
                    mStopsResponse, 
                    getFocusedStopId(),
                    areRoutesShown()
            };
        }
        else {
            return null;
        }
    }
    @Override
    protected boolean isRouteDisplayed() {
        return false;
    }
    @Override
    protected Dialog onCreateDialog(int id) {
        switch (id) {
        case HELP_DIALOG:
            return createHelpDialog();
            
        case WHATSNEW_DIALOG:
            return createWhatsNewDialog();
        }
        return null;
    }
    
    private void watchMap() {
        mTimer = new Timer();
        mTimer.schedule(new TimerTask() {
            private GeoPoint mMapCenter = mMapView.getMapCenter();
            
            @Override
            public void run() {
                boolean start = mForceRestartLocationTask;
                GeoPoint newCenter = mMapView.getMapCenter();
                int newZoom = mMapView.getZoomLevel();
                if (!newCenter.equals(mMapCenter)) {
                    //Log.d(TAG, "Center changed");
                    mMapCenter = newCenter;
                    start = true;
                }
                final ObaResponse response = mStopsResponse;
                final int responseZoom = mStopsResponseZoomLevel;
                //Log.d(TAG, "ResponseZoom: " + responseZoom);
                
                // Zoom is a bit more tricky -- we want to get new stops
                // when zooming in *only* if the last search had 
                // "limit exceeded".
                // As an additional optimization, don't get new stops
                // if our last request already had enough stops for 
                // the new zoom level.
                if (response != null) {
                    if ((newZoom > responseZoom) && 
                            response.getData().getLimitExceeded()) {
                        //Log.d(TAG, "Getting new stops from zooming in");
                        start = true;                        
                    }
                    else if (newZoom < responseZoom) {
                        //Log.d(TAG, "Zooming out past last zoom level");
                        // Zooming out -- always get stops
                        start = true;
                    }
                }
                           
                if (start) {
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
        //Log.d(TAG, "getStopsByLocation");
        if (AsyncTasks.isRunning(mGetStopsByLocationTask)) {
            //Log.d(TAG, "Set force restart");
            mForceRestartLocationTask = true;
            return;
        }
        //Log.d(TAG, "Starting async task");
        mForceRestartLocationTask = false;
        mStopsResponseZoomLevel = mMapView.getZoomLevel();
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
    
    private class RouteArrayAdapter extends Adapters.BaseArrayAdapter<ObaRoute> {       
        public RouteArrayAdapter(ObaArray<ObaRoute> routes) {
            super(MapViewActivity.this, routes, R.layout.main_popup_route_item);
        }
        @Override
        protected void setData(View view, int position) {
            TextView shortName = (TextView)view.findViewById(R.id.short_name);

            ObaRoute route = mArray.get(position);
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
                    
                     UIHelp.setStopDirection(popup.findViewById(R.id.direction), 
                             stop.getDirection(),
                             false);
                     
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
    private final ClickableSpan mOnShowRoutes = new ClickableSpan() {
        public void onClick(View v) {
            GridView grid = (GridView)findViewById(R.id.route_list);
            if (grid.getVisibility() != View.GONE) {
                showRoutes(grid, (TextView)v, false);
            }
            else {
                showRoutes(grid, (TextView)v, true);   
            }
        }
    };
    private final View.OnClickListener mPopupClick = new View.OnClickListener() {
        public void onClick(View v) {  
            // Eat the click so the Map doesn't get it.
        }
    };
    
    private boolean areRoutesShown() {
        return findViewById(R.id.route_list).getVisibility() != View.GONE;
    }
    private void showRoutes(GridView grid, TextView text, boolean show) {
        if (grid == null) {
            grid = (GridView)findViewById(R.id.route_list);
        }
        if (text == null) {
            text = (TextView)findViewById(R.id.show_routes);
        }
        if (show) {
            final StopOverlayItem item = (StopOverlayItem)mStopOverlay.getFocus();  
            if (item != null) {
                populateRoutes(item.getStop(), true);
            }
            // TODO: Animate at some point...
            grid.setVisibility(View.VISIBLE);
            text.setText(R.string.main_hide_routes);          
        }
        else {
            grid.setVisibility(View.GONE);
            text.setText(R.string.main_show_routes);              
        }
        // When the text changes, we need to reset its clickable status
        Spannable span = (Spannable)text.getText();
        span.setSpan(mOnShowRoutes, 0, span.length(), 0);
    }
    
    private void setStopOverlay(ObaResponse response) {
        mStopsResponse = response;
        
        if (response.getCode() != ObaApi.OBA_OK) {
            return;
        }
        final ObaArray<ObaStop> stops = response.getData().getStops();
        
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

    static void goToStop(Context context, ObaStop stop) {
        StopInfoActivity.start(context, stop);
    }
    
    private Dialog createHelpDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.main_help_title);
        builder.setItems(R.array.main_help_options, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                switch (which) {
                case 0:
                    Intent help = new Intent(Intent.ACTION_VIEW, Uri.parse(HELP_URL));
                    startActivity(help); 
                    break;
                case 1:
                    Intent twitter = new Intent(Intent.ACTION_VIEW, Uri.parse(TWITTER_URL));
                    startActivity(twitter); 
                    break;
                case 2:
                    showDialog(WHATSNEW_DIALOG);
                    break;
                case 3:
                    Intent preferences = new Intent(MapViewActivity.this, EditPreferencesActivity.class);
                    startActivity(preferences);
                    break;
                }
            }
        });
        return builder.create();
    }
    private Dialog createWhatsNewDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.main_help_whatsnew_title);
        builder.setIcon(R.drawable.icon);
        builder.setMessage(R.string.main_help_whatsnew);
        builder.setNeutralButton(R.string.main_help_close, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                dismissDialog(WHATSNEW_DIALOG);
            }
        });
        return builder.create();
        /*
        // If we get here, we need to show the dialog.
        final Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.whats_new);
        // OK dismisses
        Button button = (Button)dialog.findViewById(android.R.id.closeButton);
        button.setOnClickListener(new View.OnClickListener() {        
            public void onClick(View v) {
                dismissDialog(WHATSNEW_DIALOG);
            }
        });
        return dialog;
        */
    }
    
    private static final String WHATS_NEW_VER = "whatsNewVer";
    
    private void autoShowWhatsNew() {
        SharedPreferences settings = getSharedPreferences(UIHelp.PREFS_NAME, 0); 

        // Get the current app version.
        PackageManager pm = getPackageManager();
        PackageInfo appInfo = null;
        try {
            appInfo = pm.getPackageInfo(getPackageName(), PackageManager.GET_META_DATA);
        } catch (NameNotFoundException e) {
            // Do nothing, perhaps we'll get to show it again? Or never.
            return;
        }
        
        final int oldVer = settings.getInt(WHATS_NEW_VER, 0);
        final int newVer = appInfo.versionCode;
        
        if (oldVer != newVer) {
            // It's impossible to tell the difference from people updating
            // from an older version without a What's New dialog and people
            // with fresh installs just by the settings alone.
            // So we'll do a heuristic and just check to see if they have 
            // visited any stops -- in most cases that will mean they have 
            // just installed.
            if (oldVer == 0 && newVer == 7) {
                if (StopsDbAdapter.getStopCount(this) != 0) {
                    showDialog(WHATSNEW_DIALOG);
                }
            }
            else if ((oldVer > 0) && (oldVer < newVer)) {
                showDialog(WHATSNEW_DIALOG);
            }
       
            SharedPreferences.Editor edit = settings.edit();
            edit.putInt(WHATS_NEW_VER, appInfo.versionCode);
            edit.commit();
        }
    }
    
    private boolean isRouteMode() {
        return mRouteId != null;
    }
}