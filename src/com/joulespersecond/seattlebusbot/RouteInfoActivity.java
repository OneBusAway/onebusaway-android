package com.joulespersecond.seattlebusbot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.app.ExpandableListActivity;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ExpandableListView;
import android.widget.SimpleExpandableListAdapter;
import android.widget.TextView;

import com.joulespersecond.oba.ObaApi;
import com.joulespersecond.oba.ObaArray;
import com.joulespersecond.oba.ObaData;
import com.joulespersecond.oba.ObaResponse;
import com.joulespersecond.oba.ObaRoute;
import com.joulespersecond.oba.ObaStop;
import com.joulespersecond.oba.ObaStopGroup;
import com.joulespersecond.oba.ObaStopGrouping;

public class RouteInfoActivity extends ExpandableListActivity {
    private static final String ROUTE_ID = ".RouteId";
    
    private String mRouteId;
    private AsyncTask<String,?,?> mRouteInfoTask;
    private AsyncTask<String,?,?> mStopsForRouteTask;
    
    private ObaResponse mRouteInfo;
    private StopsForRouteInfo mStopsForRoute;
    
    public static void start(Context context, String routeId) {
        context.startActivity(makeIntent(context, routeId));
    }
    public static Intent makeIntent(Context context, String routeId) {
        Intent myIntent = new Intent(context, RouteInfoActivity.class);
        myIntent.putExtra(ROUTE_ID, routeId);
        return myIntent;        
    }
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
 
        setContentView(R.layout.route_info);
      
        Bundle bundle = getIntent().getExtras();
        mRouteId = bundle.getString(ROUTE_ID);
                
        setListAdapter(new SimpleExpandableListAdapter(
                    this,
                    new ArrayList<HashMap<String,String>>(),
                    android.R.layout.simple_expandable_list_item_1,
                    new String[] { "name "},
                    new int[] { android.R.id.text1 },
                    new ArrayList<ArrayList<HashMap<String,String>>>(),
                    0,
                    null,
                    new int[] {}
                ));
        
        Object config = getLastNonConfigurationInstance();
        if (config != null) {
            Object[] results = (Object[])config;
            setHeader((ObaResponse)results[0], false);
            setStopsForRoute((StopsForRouteInfo)results[1]);   
        }
        else {
            mRouteInfoTask = new GetRouteInfo().execute(mRouteId);        
        }
    }
    @Override
    public Object onRetainNonConfigurationInstance() {
        if (mRouteInfo != null && mStopsForRoute != null) {
            return new Object[] { mRouteInfo, mStopsForRoute };
        }
        else {
            return null;
        }
    }  
    @Override
    public void onDestroy() {
        if (mRouteInfoTask != null) {
            mRouteInfoTask.cancel(true);
        }
        if (mStopsForRouteTask != null) {
            mStopsForRouteTask.cancel(true);
        }
        super.onDestroy();
    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.route_info_options, menu);
        return true;
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.show_on_map) {
            MapViewActivity.start(this, mRouteId);
            return true;
        }
        return false;
    }
    
    @Override
    public boolean onChildClick(ExpandableListView parent, View v, 
            int groupPosition, int childPosition, long id) {
        final TextView text = (TextView)v.findViewById(R.id.stop_id);
        final String stopId = (String)text.getText(); 
        StopInfoActivity.start(this, stopId);
        return true;
    }  
    
    // This is the return value for GetStopsForRouteTask.
    //
    private final static class StopsForRouteInfo {
        private final ObaResponse mResponse;
        private final ArrayList<HashMap<String,String>> mStopGroups;
        private final ArrayList<ArrayList<HashMap<String,String>>> mStops;
        
        public StopsForRouteInfo(Context cxt, ObaResponse response) {
            mResponse = response;
            mStopGroups = new ArrayList<HashMap<String,String>>();
            mStops = new ArrayList<ArrayList<HashMap<String,String>>>();
            initMaps(cxt);
        }
            
        private void initMaps(Context cxt) {
            // Convert to weird array type. From the documentation of
            // SimpleExpandableListAdapter:
            // StopGroupings: A List of Maps. Each entry in the List corresponds 
            // to one group in the list. The Maps contain the data for each group, 
            // and should include all the entries specified in "groupFrom".
            //
            // StopGroupings: A List of Maps. Each entry in the List corresponds 
            // to one group in the list. The Maps contain the data for each group, 
            // and should include all the entries specified in "groupFrom".
            //
            // Stops: 
            // A List of List of Maps. Each entry in the outer List corresponds 
            // to a group (index by group position), each entry in the inner List 
            // corresponds to a child within the group (index by child position), 
            // and the Map corresponds to the data for a child (index by values 
            // in the childFrom array). The Map contains the data for each child, 
            // and should include all the entries specified in "childFrom"
            if (mResponse.getCode() == ObaApi.OBA_OK) {
                final ObaData data = mResponse.getData();
                final ObaArray stops = data.getStops();
                final Map<String,ObaStop> stopMap = stops.getStopMap();
                final ObaArray groupings = data.getStopGroupings();
                final int groupingsLen = groupings.length();
                
                for (int groupingIndex = 0; groupingIndex < groupingsLen; ++groupingIndex) {
                    final ObaStopGrouping grouping = groupings.getStopGrouping(groupingIndex);
                    final ObaArray groups = grouping.getStopGroups();
                    final int groupsLen = groups.length();
                    
                    for (int i=0; i < groupsLen; ++i) {
                        final HashMap<String,String> groupMap = new HashMap<String,String>(1);
                        final ObaStopGroup group = groups.getStopGroup(i);
                        // We can initialize the stop grouping values.
                        groupMap.put("name", group.getName());
                        // Add this to the groupings map

                        // Create the sub list (the list of stops in the group)
                        final List<String> stopIds = group.getStopIds();
                        final int stopIdLen = stopIds.size();
                        
                        final ArrayList<HashMap<String,String>> childList =
                                new ArrayList<HashMap<String,String>>(stopIdLen);
                        
                        for (int j=0; j < stopIdLen; ++j) {
                            final String stopId = stopIds.get(j);
                            final ObaStop stop = stopMap.get(stopId);
                            HashMap<String,String> groupStopMap = 
                                    new HashMap<String,String>(2);
                            if (stop != null) {
                                groupStopMap.put("name", stop.getName());
                                String dir = cxt.getString(
                                        StopInfoActivity.getStopDirectionText(
                                                stop.getDirection()));
                                groupStopMap.put("direction", dir);
                                groupStopMap.put("id", stopId);
                            }
                            else {
                                groupStopMap.put("name", "");
                                groupStopMap.put("direction", ""); 
                                groupStopMap.put("id", stopId);
                            }
                            childList.add(groupStopMap);
                        }    
                        
                        mStopGroups.add(groupMap);
                        mStops.add(childList);
                    }
                }
            }
        }
        public ArrayList<HashMap<String,String>> getStopGroups() {
            return mStopGroups;
        }
        public ArrayList<ArrayList<HashMap<String,String>>> getStops() {
            return mStops;
        }
    }
    
    //
    // Asynchronicity
    //
    private final AsyncTasks.Progress mProgress = new AsyncTasks.Progress() {
        public void showLoading() {
            View v = findViewById(R.id.loading);
            v.setVisibility(View.VISIBLE);         
        }
        public void hideLoading() {  
        }
    };
    private final AsyncTasks.Progress mProgress2 = new AsyncTasks.Progress() {
        public void showLoading() {   
        }
        public void hideLoading() {
            View v = findViewById(R.id.loading);
            v.setVisibility(View.GONE);       
        }
    };
    private final class GetRouteInfo extends AsyncTasks.StringToResponse {
        public GetRouteInfo() {
            super(RouteInfoActivity.this.mProgress);
        }
        @Override
        protected ObaResponse doInBackground(String... params) {
            return ObaApi.getRouteById(params[0]);
        }
        @Override
        protected void doResult(ObaResponse result) {
            setHeader(result, true); 
            mStopsForRouteTask = new GetStopsForRoute().execute(mRouteId);
        }        
    }
    private final class GetStopsForRoute extends AsyncTasks.Base<String,StopsForRouteInfo> {
        GetStopsForRoute() {
            super(RouteInfoActivity.this.mProgress2);
        }
        @Override
        protected StopsForRouteInfo doInBackground(String... params) {
            return new StopsForRouteInfo(
                    RouteInfoActivity.this, ObaApi.getStopsForRoute(params[0]));
        } 
        @Override
        protected void doResult(StopsForRouteInfo result) {
            setStopsForRoute(result);
        }
    }
    
    //
    // Helper functions (should be private, but they are mostly accessed
    // by our child classes.)
    //
    private void setHeader(ObaResponse routeInfo, boolean addToDb) {  
        mRouteInfo = routeInfo;
        
        TextView empty = (TextView)findViewById(android.R.id.empty);
        
        if (routeInfo.getCode() == ObaApi.OBA_OK) {
            ObaRoute route = routeInfo.getData().getThisRoute();
            TextView shortNameText = (TextView)findViewById(R.id.short_name);
            TextView longNameText = (TextView)findViewById(R.id.long_name);
            TextView agencyText = (TextView)findViewById(R.id.agency);
        
            String shortName = route.getShortName();
            String longName = route.getLongName();
            
            shortNameText.setText(shortName);
            longNameText.setText(longName);
            agencyText.setText(route.getAgencyName());
            
            if (addToDb) {
                RoutesDbAdapter.addRoute(this, route.getId(), shortName, longName, true);     
            }
        }
        else {
            empty.setText(R.string.generic_comm_error);
        }
    }    
    private void setStopsForRoute(StopsForRouteInfo result) {
        mStopsForRoute = result;
        setListAdapter(new SimpleExpandableListAdapter(
                this,
                result.getStopGroups(),
                android.R.layout.simple_expandable_list_item_1,
                new String[] { "name" },
                new int[] { android.R.id.text1 },
                result.getStops(),
                R.layout.route_info_listitem,
                new String[] { "name", "direction", "id" },
                new int[] { R.id.name, R.id.direction, R.id.stop_id }
            ));
    }
}
