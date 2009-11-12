package com.joulespersecond.seattlebusbot;

import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnMultiChoiceClickListener;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.joulespersecond.oba.ObaApi;
import com.joulespersecond.oba.ObaArray;
import com.joulespersecond.oba.ObaArrivalInfo;
import com.joulespersecond.oba.ObaData;
import com.joulespersecond.oba.ObaResponse;
import com.joulespersecond.oba.ObaRoute;
import com.joulespersecond.oba.ObaStop;

public class StopInfoActivity extends ListActivity {
    private static final String TAG = "StopInfoActivity";
    private static final long RefreshPeriod = 60*1000;

    private static final int FILTER_ROUTES_DIALOG = 1;
    
    private static final String STOP_ID = ".StopId";
    private static final String STOP_NAME = ".StopName";
    private static final String STOP_DIRECTION = ".StopDir";
    private static final String STOP_INFO = ".StopInfo";
    
    private ObaResponse mResponse;
    private String mResponseString;
    private ObaStop mStop;
    private String mStopId;
    private Timer mTimer;
    private StopsDbAdapter mStopsDbAdapter;
    private ArrayList<String> mRoutesFilter;
    
    private AsyncTask<String,?,?> mAsyncTask;

    private TripsDbAdapter mTripsDbAdapter;    
    private TripsDbAdapter.TripsForStopSet mTripsForStop;
    
    public static final int getStopDirectionText(String direction) {
        if (direction.equals("N")) {
            return R.string.direction_n;
        } else if (direction.equals("NW")) {
            return R.string.direction_nw;                    
        } else if (direction.equals("W")) {
            return R.string.direction_w;                    
        } else if (direction.equals("SW")) {
            return R.string.direction_sw;    
        } else if (direction.equals("S")) {
            return R.string.direction_s;    
        } else if (direction.equals("SE")) {
            return R.string.direction_se;    
        } else if (direction.equals("E")) {
            return R.string.direction_e;    
        } else if (direction.equals("NE")) {
            return R.string.direction_ne;                             
        } else {
            Log.v(TAG, "Unknown direction: " + direction);
            return R.string.direction_n;
        }    
    }

    public static void start(Context context, String stopId) {
        context.startActivity(makeIntent(context, stopId));
    }
    public static void start(Context context, String stopId, String stopName) {
        context.startActivity(makeIntent(context, stopId, stopName));
    }
    public static void start(Context context, 
                        String stopId, 
                        String stopName,
                        String stopDir) {
        context.startActivity(makeIntent(context, stopId, stopName, stopDir));
    }
    public static void start(Context context, ObaStop stop) {
        context.startActivity(makeIntent(context, stop));
    }
    public static Intent makeIntent(Context context, String stopId) {
        Intent myIntent = new Intent(context, StopInfoActivity.class);
        myIntent.putExtra(STOP_ID, stopId);
        return myIntent;       
    }
    public static Intent makeIntent(Context context, String stopId, String stopName) {
        Intent myIntent = new Intent(context, StopInfoActivity.class);
        myIntent.putExtra(STOP_ID, stopId);
        myIntent.putExtra(STOP_NAME, stopName);
        return myIntent;       
    }
    public static Intent makeIntent(Context context,
                        String stopId, 
                        String stopName,
                        String stopDir) {
        Intent myIntent = new Intent(context, StopInfoActivity.class);
        myIntent.putExtra(STOP_ID, stopId);
        myIntent.putExtra(STOP_NAME, stopName);
        myIntent.putExtra(STOP_DIRECTION, stopDir);
        return myIntent;       
    }
    public static Intent makeIntent(Context context, ObaStop stop) {
        Intent myIntent = new Intent(context, StopInfoActivity.class);
        myIntent.putExtra(STOP_ID, stop.getId());
        myIntent.putExtra(STOP_NAME, stop.getName());
        myIntent.putExtra(STOP_DIRECTION, stop.getDirection());
        return myIntent;
    }
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);

        setContentView(R.layout.stop_info);
        setListAdapter(new StopInfoListAdapter());
        
        mStopsDbAdapter = new StopsDbAdapter(this);
        mStopsDbAdapter.open();
        
        mTripsDbAdapter = new TripsDbAdapter(this);
        mTripsDbAdapter.open();
        
        Bundle bundle = getIntent().getExtras();
        mStopId = bundle.getString(STOP_ID);
        setHeader(bundle);
     
        mRoutesFilter = mStopsDbAdapter.getStopRouteFilter(mStopId);

        mTripsForStop = mTripsDbAdapter.getTripsForStopId(mStopId);
        if (savedInstanceState != null) {
            String str = savedInstanceState.getString(STOP_INFO);
            if (str != null) {
                // Make a copy and keep it around.
                mResponseString = new String(str);
            }
            getStopInfo(mResponseString, false, false);
        }
        else {
            getStopInfo(null, false, true);
        }
    }
    @Override
    public void onDestroy() {
        mTripsForStop.close();
        mStopsDbAdapter.close();
        mTripsDbAdapter.close();
        if (mAsyncTask != null) {
            mAsyncTask.cancel(true);
        }
        super.onDestroy();
    }
    @Override 
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STOP_INFO, mResponseString);
    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.stop_info_options, menu);
        return true;
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        final int id = item.getItemId();
        if (id == R.id.show_on_map) {
            if (mResponse != null) {
                MapViewActivity.start(this, 
                        mStopId, 
                        mStop.getLatitude(), 
                        mStop.getLongitude());
            }
            return true;
        }
        else if (id == R.id.refresh) {
            getStopInfo(null, true, false);
            return true;
        }
        else if (id == R.id.filter) {
            if (mResponse != null) {
                showDialog(FILTER_ROUTES_DIALOG);
            }
        }
        else if (id == R.id.show_all) {
            if (mResponse != null) {
                setRoutesFilter(new ArrayList<String>());
            }
        }
        return false;
    }
    @Override
    public void onPause() {
        mTimer.cancel();
        mTimer = null;
        super.onPause();
    }
    @Override
    public void onResume() {
        if (mTimer == null) {
            mTimer = new Timer();
        }
        mTripsForStop.refresh();
        // Always refresh once on resume
        getStopInfo(null, true, false);
        
        mTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                mRefreshHandler.post(mRefresh);            
            }         
        }, RefreshPeriod, RefreshPeriod);
        super.onResume();
    }
    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        final StopInfo stop = (StopInfo)getListView().getItemAtPosition(position);
        if (stop == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.stop_info_item_options_title);
        // Check to see if the trip name is visible.
        // (we don't have any other state, so this is good enough)
        int options;
        View tripView = v.findViewById(R.id.trip_info);
        if (tripView.getVisibility() != View.GONE) {
            options = R.array.stop_item_options_edit;
        }
        else {
            options = R.array.stop_item_options;
        }
        builder.setItems(options, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                switch (which) {
                case 0:
                    goToTrip(stop);
                    break;
                case 1:
                    goToRoute(stop);
                    break;
                case 2:
                    ArrayList<String> routes = new ArrayList<String>(1);
                    routes.add(stop.getInfo().getRouteId());
                    setRoutesFilter(routes);
                    break;
                }
            }
        });
        AlertDialog dialog = builder.create();
        dialog.setOwnerActivity(this);
        dialog.show();
    }
    
    private class DialogListener 
        implements DialogInterface.OnClickListener, OnMultiChoiceClickListener {
        // It's easiest just to store this here.
        private final ArrayList<String> mRouteIds;
        private final String[] mRouteNames;
        private boolean[] mChecks;
        
        DialogListener(ObaStop stop, ArrayList<String> filter) {
            final ObaArray routes = stop.getRoutes();
            final int len = routes.length();
            
            mRouteIds = new ArrayList<String>(len);
            mRouteNames = new String[len];
            mChecks = new boolean[len];
            
            // Go through all the stops, add them to the Ids and Names
            // For each stop, if it is in the enabled list, mark it as checked.
            for (int i=0; i < len; ++i) {
                final ObaRoute route = routes.getRoute(i);
                final String id = route.getId();
                mRouteIds.add(i, id);
                mRouteNames[i] = route.getShortName();
                if (filter.contains(id)) {
                    mChecks[i] = true;
                }
            }
        }
        public String[] getItems() {
            return mRouteNames;
        }
        public boolean[] getChecks() {
            return mChecks;
        }
        
        public void onClick(DialogInterface dialog, int which) {
            if (which == DialogInterface.BUTTON_POSITIVE) {
                ArrayList<String> filter = new ArrayList<String>();
                final int len = mChecks.length;
                int checks = 0;
                for (int i=0; i < len; ++i) {
                    if (mChecks[i]) {
                        filter.add(mRouteIds.get(i));
                        ++checks;
                    }
                }
                // If they are *all* checked, pretend like none were
                if (checks == len) {
                    filter.clear();
                }
                setRoutesFilter(filter);
            }
            dialog.dismiss();            
        }
        // Multi-click
        public void onClick(DialogInterface dialog, int which, boolean checked) {
            mChecks[which] = checked;            
        }
    }
    
    @Override
    protected Dialog onCreateDialog(int id) {
        AlertDialog dialog;
        AlertDialog.Builder builder;
        switch (id) {
        case FILTER_ROUTES_DIALOG:
            builder = new AlertDialog.Builder(this);
            builder.setTitle(R.string.stop_info_filter_title);
            DialogListener listener = new DialogListener(mStop, mRoutesFilter);

            MultiChoiceHelper.setMultiChoiceItems(builder,
                        this,
                        listener.getItems(),
                        listener.getChecks(),
                        listener);

            dialog = builder
                .setPositiveButton(R.string.stop_info_save, listener)
                .setNegativeButton(R.string.stop_info_cancel, listener)
                .create();
            break;
        default:
            dialog = null;
            break;
        }
        return dialog;
    }
    
    final class StopInfoListAdapter extends BaseAdapter {
        private ArrayList<StopInfo> mInfo;
        
        public StopInfoListAdapter() {
            mInfo = new ArrayList<StopInfo>();
        }
        public int getCount() {
            return mInfo.size();
        }
        public Object getItem(int position) {
            // Replace this when we add a real stop info array
            return mInfo.get(position);
        }

        public long getItemId(int position) {
            return position;
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            ViewGroup newView;
            if (convertView == null) {
                LayoutInflater inflater = getLayoutInflater();
                newView = (ViewGroup)inflater.inflate(R.layout.stop_info_listitem, null);
            }
            else {
                newView = (ViewGroup)convertView;
            }
            setData(newView, position);
            return newView;
        }
        public boolean hasStableIds() {
            return false;
        }
        
        public void setData(ObaArray arrivals) {
            mInfo = StopInfo.convertObaArrivalInfo(StopInfoActivity.this, arrivals, mRoutesFilter);
            setFilterHeader();
            notifyDataSetChanged();
        }
        private void setData(ViewGroup view, int position) {
            TextView route = (TextView)view.findViewById(R.id.route);
            TextView destination = (TextView)view.findViewById(R.id.destination);
            TextView time = (TextView)view.findViewById(R.id.time);
            TextView status = (TextView)view.findViewById(R.id.status);
            TextView etaView = (TextView)view.findViewById(R.id.eta);

            final StopInfo stopInfo = mInfo.get(position);
            final ObaArrivalInfo arrivalInfo = stopInfo.getInfo();
            
            route.setText(arrivalInfo.getShortName());
            destination.setText(arrivalInfo.getHeadsign());
            status.setText(stopInfo.getStatusText());

            long eta = stopInfo.getEta();
            if (eta == 0) {
                etaView.setText(R.string.stop_info_eta_now);
            }
            else {
                etaView.setText(String.valueOf(eta));
            }

            int color = getResources().getColor(stopInfo.getColor());
            //status.setTextColor(color); // This just doesn't look very good.
            etaView.setTextColor(color);

            time.setText(DateUtils.formatDateTime(StopInfoActivity.this, 
                    stopInfo.getDisplayTime(), 
                    DateUtils.FORMAT_SHOW_TIME|
                    DateUtils.FORMAT_NO_NOON|
                    DateUtils.FORMAT_NO_MIDNIGHT));    

            String tripName = mTripsForStop.getTripName(arrivalInfo.getTripId());
            
            if (tripName != null) {
                View tripInfo = view.findViewById(R.id.trip_info);
                TextView tripNameView = (TextView)view.findViewById(R.id.trip_name);
                if (tripName.length() == 0) {
                    tripName = getString(R.string.trip_info_noname);
                }
                tripNameView.setText(tripName);
                tripInfo.setVisibility(View.VISIBLE);
            }
            else {
                // Explicitly set this to invisible because we might be reusing this view.
                View tripInfo = view.findViewById(R.id.trip_info);
                tripInfo.setVisibility(View.GONE);
                
            }
        }
    }
    
    private void goToTrip(StopInfo stop) {
        ObaArrivalInfo stopInfo = stop.getInfo();
        TripInfoActivity.start(this,
                stopInfo.getTripId(),
                mStopId, 
                stopInfo.getRouteId(),
                stopInfo.getShortName(),
                mStop.getName(),
                stopInfo.getScheduledDepartureTime(),
                stopInfo.getHeadsign());        
    }
    private void goToRoute(StopInfo stop) {
        RouteInfoActivity.start(this, stop.getInfo().getRouteId());
    }
    private void setRoutesFilter(ArrayList<String> routes) {
        mRoutesFilter = routes;
        mStopsDbAdapter.setStopRouteFilter(mStopId, mRoutesFilter);
        StopInfoListAdapter adapter = (StopInfoListAdapter)getListView().getAdapter();
        adapter.setData(mResponse.getData().getArrivalsAndDepartures());
    }
    
    // Similar to the annoying bit in MapViewActivity, the timer is run
    // in a separate task, so we need to post back to the main thread 
    // to run our AsyncTask. We can't do everything in the timer thread
    // because the progressBar has to be modified in the UI (main) thread.
    private final Handler mRefreshHandler = new Handler();
    private final Runnable mRefresh = new Runnable() {
        public void run() {
            getStopInfo(null, true, false);
        }
    };
    
    //
    // Async tasks for various things.
    //
    private final AsyncTasks.Progress mLoadingProgress = new AsyncTasks.Progress() {
        public void showLoading() {
            View v = findViewById(R.id.loading);
            v.setVisibility(View.VISIBLE);         
        }
        public void hideLoading() {
            View v = findViewById(R.id.loading);
            v.setVisibility(View.GONE);       
        }
    };
    private final AsyncTasks.Progress mTitleProgress 
        = new AsyncTasks.ProgressIndeterminateVisibility(this);
    
    private abstract class GetStopInfoBase extends AsyncTasks.StringToResponse {
        private final boolean mAddToDb;
        GetStopInfoBase(AsyncTasks.Progress progress, boolean addToDb) {
            super(progress);
            mAddToDb = addToDb;
        }
        @Override
        protected void doResult(ObaResponse result) {
            if (result.getCode() == ObaApi.OBA_OK) {
                setResponse(result, mAddToDb);
            }
            else {
                TextView empty = (TextView)findViewById(android.R.id.empty);
                empty.setText(R.string.generic_comm_error);
            } 
        }
    }
    private final class GetStopInfoString extends GetStopInfoBase {
        GetStopInfoString(AsyncTasks.Progress progress, boolean addToDb) {
            super(progress, addToDb);
        }
        @Override
        protected ObaResponse doInBackground(String... params) {
            return ObaResponse.createFromString(params[0]);
        }        
    }
    private final class GetStopInfoNet extends GetStopInfoBase {
        GetStopInfoNet(AsyncTasks.Progress progress, boolean addToDb) {
            super(progress, addToDb);
        }
        @Override
        protected ObaResponse doInBackground(String... params) {
            return ObaApi.getArrivalsDeparturesForStop(params[0]);
        }
    }
    
    private void getStopInfo(String response, 
                            boolean titleProgress,
                            boolean addToDb) {
        if (mStopId == null) {
            return;
        }
        if (AsyncTasks.isRunning(mAsyncTask)) {
            return;
        }
        // To determine 
        AsyncTasks.Progress progress = titleProgress ? mTitleProgress : mLoadingProgress;
        
        if (response != null) {
            // Convert this to a string.
            mAsyncTask = new GetStopInfoString(progress, addToDb);
            // For some reason we need to make a copy of this string.
            mAsyncTask.execute(response);
        }
        else {
            // Get it from the Net
            mAsyncTask = new GetStopInfoNet(progress, addToDb);
            mAsyncTask.execute(mStopId);
        }
    }
    
    void setResponse(ObaResponse response, boolean addToDb) {
        assert(response != null);
        mResponse = response;
        mResponseString = response.toString();
        ObaData data = response.getData();
        mStop = data.getStop();       
        setHeader(mStop, addToDb);
        StopInfoListAdapter adapter = (StopInfoListAdapter)getListView().getAdapter();
        adapter.setData(data.getArrivalsAndDepartures());
        
        // Ensure there is some hint text in case there is no error 
        // but the data is empty.
        TextView empty = (TextView)findViewById(android.R.id.empty);
        empty.setText(R.string.stop_info_nodata);
        
        mLoadingProgress.hideLoading();
        setProgressBarIndeterminateVisibility(false);        
    }
    private void setHeader(Bundle bundle) {
        setHeader(bundle.getString(STOP_NAME), bundle.getString(STOP_DIRECTION));
    }
    
    void setHeader(ObaStop stop, boolean addToDb) {
        String code = stop.getCode();
        String name = stop.getName();
        String direction = stop.getDirection();
        double lat = stop.getLatitude();
        double lon = stop.getLongitude();

        setHeader(name, direction);
           
        if (addToDb) {
            // Update the database
            mStopsDbAdapter.addStop(
                    stop.getId(), code, name, direction, 
                    lat, lon, true);
        }
    }
    private void setHeader(String name, String direction) {
        if (name != null) {
            TextView nameText = (TextView)findViewById(R.id.name);
            nameText.setText(name);
        }
        if (direction != null) {
            TextView directionText = (TextView)findViewById(R.id.direction);
            directionText.setText(getStopDirectionText(direction));  
        }
    }
    void setFilterHeader() {
        TextView v = (TextView)findViewById(R.id.filter);
        final int filtered = (mRoutesFilter != null) ? mRoutesFilter.size() : 0;
        if (filtered > 0) {
            // How many routes?
            final int num = mRoutesFilter.size();
            final int total = mStop.getRoutes().length();
            v.setText(getString(R.string.stop_info_filter_header, num, total));
            // Show the filter text
            v.setVisibility(View.VISIBLE);
        }
        else {
            v.setVisibility(View.GONE);
        }
    }
}
