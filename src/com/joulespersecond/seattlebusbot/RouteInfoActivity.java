/*
 * Copyright (C) 2010 Paul Watts (paulcwatts@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.joulespersecond.seattlebusbot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.app.ExpandableListActivity;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
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
import com.joulespersecond.oba.provider.ObaContract;

public class RouteInfoActivity extends ExpandableListActivity {
    private static final String TAG = "RouteInfoActivity";
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
        myIntent.setData(Uri.withAppendedPath(ObaContract.Routes.CONTENT_URI, routeId));
        return myIntent;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.route_info);
        registerForContextMenu(getExpandableListView());

        final Intent intent = getIntent();
        final Bundle bundle = intent.getExtras();
        final Uri data = intent.getData();
        if (data != null) {
            mRouteId = data.getLastPathSegment();
        }
        else if (bundle != null) {
            // This is for backward compatibility
            mRouteId = bundle.getString(ROUTE_ID);
        }
        else {
            Log.e(TAG, "No route ID!");
            finish();
            return;
        }

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
        ObaStop stop = mStopsForRoute.getStopMap().get(stopId);
        if (stop != null) {
            StopInfoActivity.start(this, stopId, stop.getName(), stop.getDirection());
        }
        else {
            StopInfoActivity.start(this, stopId);
        }
        return true;
    }
    private static final int CONTEXT_MENU_DEFAULT = 1;
    private static final int CONTEXT_MENU_SHOWONMAP = 2;

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v,
            ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        ExpandableListView.ExpandableListContextMenuInfo info =
            (ExpandableListView.ExpandableListContextMenuInfo)menuInfo;
        if (ExpandableListView.getPackedPositionType(info.packedPosition)
                != ExpandableListView.PACKED_POSITION_TYPE_CHILD) {
            return;
        }
        final TextView text = (TextView)info.targetView.findViewById(R.id.name);
        menu.setHeaderTitle(text.getText());
        menu.add(0, CONTEXT_MENU_DEFAULT, 0, R.string.route_info_context_get_stop_info);
        menu.add(0, CONTEXT_MENU_SHOWONMAP, 0, R.string.route_info_context_showonmap);
    }
    @Override
    public boolean onContextItemSelected(MenuItem item) {
        ExpandableListView.ExpandableListContextMenuInfo info =
            (ExpandableListView.ExpandableListContextMenuInfo)item.getMenuInfo();
        final long packed = info.packedPosition;
        switch (item.getItemId()) {
        case CONTEXT_MENU_DEFAULT:
            return onChildClick(getExpandableListView(), info.targetView,
                        ExpandableListView.getPackedPositionGroup(packed),
                        ExpandableListView.getPackedPositionChild(packed),
                        info.id);
        case CONTEXT_MENU_SHOWONMAP:
            showOnMap(info.targetView);
            return true;
        default:
            return super.onContextItemSelected(item);
        }
    }
    @Override
    public void onLowMemory() {
        //Log.d(TAG, "******** LOW MEMORY ******** ");
        super.onLowMemory();
        mRouteInfo = null;
        mStopsForRoute = null;
    }

    private void showOnMap(View v) {
        final TextView text = (TextView)v.findViewById(R.id.stop_id);
        final String stopId = (String)text.getText();
        // we need to find this route in the response because
        // we need to know it's lat/lon
        ObaStop stop = mStopsForRoute.getStopMap().get(stopId);
        if (stop == null) {
            return;
        }
        MapViewActivity.start(this, stopId, stop.getLatitude(), stop.getLongitude());
    }


    // This is the return value for GetStopsForRouteTask.
    //
    private final static class StopsForRouteInfo {
        private final ArrayList<HashMap<String,String>> mStopGroups;
        private final ArrayList<ArrayList<HashMap<String,String>>> mStops;
        private final HashMap<String,ObaStop> mStopMap;

        public StopsForRouteInfo(Context cxt, ObaResponse response) {
            mStopGroups = new ArrayList<HashMap<String,String>>();
            mStops = new ArrayList<ArrayList<HashMap<String,String>>>();
            mStopMap = new HashMap<String,ObaStop>();
            initMaps(cxt, response);
        }

        private static Map<String,ObaStop> getStopMap(ObaArray<ObaStop> stops) {
            final int len = stops.length();
            HashMap<String,ObaStop> result = new HashMap<String,ObaStop>(len);
            for (int i=0; i < len; ++i) {
                ObaStop stop = stops.get(i);
                result.put(stop.getId(), stop);
            }
            return result;
        }

        private void initMaps(Context cxt, ObaResponse response) {
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
            if (response.getCode() == ObaApi.OBA_OK) {
                final ObaData data = response.getData();
                final ObaArray<ObaStop> stops = data.getStops();
                final Map<String,ObaStop> stopMap = getStopMap(stops);
                final ObaArray<ObaStopGrouping> groupings = data.getStopGroupings();
                final int groupingsLen = groupings.length();

                for (int groupingIndex = 0; groupingIndex < groupingsLen; ++groupingIndex) {
                    final ObaStopGrouping grouping = groupings.get(groupingIndex);
                    final ObaArray<ObaStopGroup> groups = grouping.getStopGroups();
                    final int groupsLen = groups.length();

                    for (int i=0; i < groupsLen; ++i) {
                        final HashMap<String,String> groupMap = new HashMap<String,String>(1);
                        final ObaStopGroup group = groups.get(i);
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
                                        UIHelp.getStopDirectionText(
                                                stop.getDirection()));
                                groupStopMap.put("direction", dir);
                                groupStopMap.put("id", stopId);
                                mStopMap.put(stopId, stop);
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
        public HashMap<String,ObaStop> getStopMap() {
            return mStopMap;
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
            return ObaApi.getRouteById(RouteInfoActivity.this, params[0]);
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
                    RouteInfoActivity.this,
                    ObaApi.getStopsForRoute(RouteInfoActivity.this, params[0]));
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
            ObaRoute route = routeInfo.getData().getAsRoute();
            TextView shortNameText = (TextView)findViewById(R.id.short_name);
            TextView longNameText = (TextView)findViewById(R.id.long_name);
            TextView agencyText = (TextView)findViewById(R.id.agency);

            String shortName = route.getShortName();
            String longName = route.getLongNameOrDescription();

            shortNameText.setText(shortName);
            longNameText.setText(longName);
            agencyText.setText(route.getAgencyName());

            if (addToDb) {
                ContentValues values = new ContentValues();
                values.put(ObaContract.Routes.SHORTNAME, shortName);
                values.put(ObaContract.Routes.LONGNAME, longName);
                ObaContract.Routes.insertOrUpdate(this, route.getId(), values, true);
            }
        }
        else {
            empty.setText(UIHelp.getRouteErrorString(routeInfo.getCode()));
        }
        mRouteInfoTask = null;
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
        mStopsForRouteTask = null;
    }
}
