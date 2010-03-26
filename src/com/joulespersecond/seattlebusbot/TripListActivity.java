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

import android.app.ListActivity;
import android.content.ContentResolver;
import android.database.Cursor;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.AdapterView.AdapterContextMenuInfo;

import com.joulespersecond.oba.provider.ObaContract;

public class TripListActivity extends ListActivity {
    //private static final String TAG = "TripListActivity";

    private static final String[] PROJECTION = {
        ObaContract.Trips._ID,
        ObaContract.Trips.NAME,
        ObaContract.Trips.HEADSIGN,
        ObaContract.Trips.DEPARTURE,
        ObaContract.Trips.ROUTE_ID,
        ObaContract.Trips.STOP_ID
    };
    private static final int COL_ID = 0;
    private static final int COL_NAME = 1;
    //private static final int COL_HEADSIGN = 2;
    private static final int COL_DEPARTURE = 3;
    private static final int COL_ROUTE_ID = 4;
    private static final int COL_STOP_ID = 5;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.trip_list);
        registerForContextMenu(getListView());

        fillTrips();
    }

    private void fillTrips() {
        ContentResolver cr = getContentResolver();
        Cursor c = cr.query(ObaContract.Trips.CONTENT_URI,
                PROJECTION, null, null, ObaContract.Trips.NAME + " asc");
        startManagingCursor(c);

        final String[] from = {
                ObaContract.Trips.NAME,
                ObaContract.Trips.HEADSIGN,
                ObaContract.Trips.DEPARTURE,
                ObaContract.Trips.ROUTE_ID
        };
        final int[] to = {
                R.id.name,
                R.id.headsign,
                R.id.departure_time,
                R.id.route_name
        };
        SimpleCursorAdapter simpleAdapter =
            new SimpleCursorAdapter(this, R.layout.trip_list_listitem, c, from, to);

        simpleAdapter.setViewBinder(new SimpleCursorAdapter.ViewBinder() {
            public boolean setViewValue(View view, Cursor cursor, int columnIndex) {
                if (columnIndex == COL_NAME) {
                    TextView text = (TextView)view;
                    String name = cursor.getString(columnIndex);
                    if (name.length() == 0) {
                        name = getString(R.string.trip_info_noname);
                    }
                    text.setText(name);
                    return true;
                }
                else if (columnIndex == COL_DEPARTURE) {
                    TextView text = (TextView)view;
                    text.setText(TripInfoActivity.getDepartureTime(
                            TripListActivity.this,
                            ObaContract.Trips.convertDBToTime(cursor.getInt(columnIndex))));
                    return true;
                }
                else if (columnIndex == COL_ROUTE_ID) {
                    //
                    // Translate the Route ID into the Route Name by looking
                    // it up in the Routes table.
                    //
                    TextView text = (TextView)view;
                    final String routeId = cursor.getString(columnIndex);
                    final String routeName =
                        TripService.getRouteShortName(TripListActivity.this, routeId);
                    if (routeName != null) {
                        text.setText(getString(R.string.trip_info_route, routeName));
                    }
                    return true;
                }
                return false;
            }
        });
        setListAdapter(simpleAdapter);
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        String[] ids = getIds(l, position);

        TripInfoActivity.start(this, ids[0], ids[1]);
    }
    private static final int CONTEXT_MENU_DEFAULT = 1;
    private static final int CONTEXT_MENU_DELETE = 2;
    private static final int CONTEXT_MENU_SHOWSTOP = 3;
    private static final int CONTEXT_MENU_SHOWROUTE = 4;

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v,
            ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        AdapterContextMenuInfo info = (AdapterContextMenuInfo)menuInfo;
        final TextView text = (TextView)info.targetView.findViewById(R.id.name);
        menu.setHeaderTitle(text.getText());
        menu.add(0, CONTEXT_MENU_DEFAULT, 0, R.string.trip_list_context_edit);
        menu.add(0, CONTEXT_MENU_DELETE, 0, R.string.trip_list_context_delete);
        menu.add(0, CONTEXT_MENU_SHOWSTOP, 0, R.string.trip_list_context_showstop);
        menu.add(0, CONTEXT_MENU_SHOWROUTE, 0, R.string.trip_list_context_showroute);
    }
    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterContextMenuInfo info = (AdapterContextMenuInfo)item.getMenuInfo();
        switch (item.getItemId()) {
        case CONTEXT_MENU_DEFAULT:
            // Fake a click
            onListItemClick(getListView(), info.targetView, info.position, info.id);
            return true;
        case CONTEXT_MENU_DELETE:
            deleteTrip(getListView(), info.position);
            return true;
        case CONTEXT_MENU_SHOWSTOP:
            goToStop(getListView(), info.position);
            return true;
        case CONTEXT_MENU_SHOWROUTE:
            goToRoute(getListView(), info.position);
            return true;
        default:
            return super.onContextItemSelected(item);
        }
    }
    private void deleteTrip(ListView l, int position) {
        String[] ids = getIds(l, position);

        // TODO: Confirmation dialog?
        ContentResolver cr = getContentResolver();
        cr.delete(ObaContract.Trips.buildUri(ids[0], ids[1]), null, null);
        TripService.scheduleAll(this);

        SimpleCursorAdapter adapter = (SimpleCursorAdapter)getListView().getAdapter();
        adapter.getCursor().requery();
    }
    private void goToStop(ListView l, int position) {
        String[] ids = getIds(l, position);
        StopInfoActivity.start(this, ids[1]);
    }
    private void goToRoute(ListView l, int position) {
        String[] ids = getIds(l, position);
        RouteInfoActivity.start(this, ids[2]);
    }
    private String[] getIds(ListView l, int position) {
        // Get the cursor and fetch the stop ID from that.
        SimpleCursorAdapter cursorAdapter = (SimpleCursorAdapter)l.getAdapter();
        final Cursor c = cursorAdapter.getCursor();
        c.moveToPosition(position - l.getHeaderViewsCount());
        final String[] result = new String[] {
                c.getString(COL_ID),
                c.getString(COL_STOP_ID),
                c.getString(COL_ROUTE_ID)
        };
        return result;
    }
}
