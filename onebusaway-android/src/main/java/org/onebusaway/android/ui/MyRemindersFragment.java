/*
 * Copyright (C) 2014 Paul Watts (paulcwatts@gmail.com),
 * University of South Florida (sjbarbeau@gmail.com)
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
package org.onebusaway.android.ui;

import org.onebusaway.android.R;
import org.onebusaway.android.io.ObaAnalytics;
import org.onebusaway.android.provider.ObaContract;
import org.onebusaway.android.tripservice.TripService;
import org.onebusaway.android.util.UIUtils;

import android.content.ContentResolver;
import android.database.ContentObserver;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.widget.SimpleCursorAdapter;
import android.view.ContextMenu;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;

public final class MyRemindersFragment extends ListFragment
        implements LoaderManager.LoaderCallbacks<Cursor> {

    public static final String TAG = "MyRemindersFragment";

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

    private static final int COL_HEADSIGN = 2;

    private static final int COL_DEPARTURE = 3;

    private static final int COL_ROUTE_ID = 4;

    private static final int COL_STOP_ID = 5;

    private static final Handler mHandler = new Handler();

    private class Observer extends ContentObserver {

        Observer() {
            super(mHandler);
        }

        @Override
        public boolean deliverSelfNotifications() {
            return false;
        }

        public void onChange(boolean selfChange) {
            if (isAdded()) {
                getLoaderManager().restartLoader(0, null, MyRemindersFragment.this);
            }
        }
    }

    private SimpleCursorAdapter mAdapter;

    private Observer mObserver;

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        // Set empty text
        setEmptyText(getString(R.string.trip_list_notrips));
        registerForContextMenu(getListView());

        // Create our generic adapter
        mAdapter = newAdapter();
        setListAdapter(mAdapter);
        ContentResolver cr = getActivity().getContentResolver();
        mObserver = new Observer();
        cr.registerContentObserver(ObaContract.Trips.CONTENT_URI, true, mObserver);

        // Prepare the loader
        getLoaderManager().initLoader(0, null, this);
    }

    @Override
    public void onStart() {
        ObaAnalytics.reportFragmentStart(this);
        super.onStart();
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ListView listView = getListView();
        listView.setBackgroundColor(getResources().getColor(R.color.listview_background));
    }

    @Override
    public void onDestroy() {
        if (mObserver != null) {
            ContentResolver cr = getActivity().getContentResolver();
            cr.unregisterContentObserver(mObserver);
            mObserver = null;
        }
        super.onDestroy();
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        return new CursorLoader(getActivity(),
                ObaContract.Trips.CONTENT_URI,
                PROJECTION,
                null,
                null,
                ObaContract.Trips.NAME + " asc");
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        mAdapter.swapCursor(data);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        mAdapter.swapCursor(null);

    }

    private SimpleCursorAdapter newAdapter() {
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
                new SimpleCursorAdapter(getActivity(), R.layout.trip_list_listitem, null, from,
                        to, 0);

        simpleAdapter.setViewBinder(new SimpleCursorAdapter.ViewBinder() {
            public boolean setViewValue(View view,
                    Cursor cursor,
                    int columnIndex) {
                if (columnIndex == COL_NAME) {
                    TextView text = (TextView) view;
                    String name = cursor.getString(columnIndex);
                    if (name.length() == 0) {
                        name = getString(R.string.trip_info_noname);
                    }
                    text.setText(name);
                    return true;
                } else if (columnIndex == COL_HEADSIGN) {
                    String headSign = cursor.getString(columnIndex);
                    TextView text = (TextView) view;
                    text.setText(UIUtils.formatDisplayText(headSign));
                    return true;
                } else if (columnIndex == COL_DEPARTURE) {
                    TextView text = (TextView) view;
                    text.setText(TripInfoActivity.getDepartureTime(
                            getActivity(),
                            ObaContract.Trips.convertDBToTime(cursor
                                    .getInt(columnIndex))
                    ));
                    return true;
                } else if (columnIndex == COL_ROUTE_ID) {
                    //
                    // Translate the Route ID into the Route Name by looking
                    // it up in the Routes table.
                    //
                    TextView text = (TextView) view;
                    final String routeId = cursor.getString(columnIndex);
                    final String routeName = TripService.getRouteShortName(
                            getActivity(), routeId);
                    if (routeName != null) {
                        text.setText(getString(R.string.trip_info_route,
                                routeName));
                    }
                    return true;
                }
                return false;
            }
        });
        return simpleAdapter;
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        String[] ids = getIds(l, position);

        TripInfoActivity.start(getActivity(), ids[0], ids[1]);
    }

    private static final int CONTEXT_MENU_DEFAULT = 1;

    private static final int CONTEXT_MENU_DELETE = 2;

    private static final int CONTEXT_MENU_SHOWSTOP = 3;

    private static final int CONTEXT_MENU_SHOWROUTE = 4;

    @Override
    public void onCreateContextMenu(ContextMenu menu,
            View v,
            ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;
        final TextView text = (TextView) info.targetView.findViewById(R.id.name);
        menu.setHeaderTitle(text.getText());
        menu.add(0, CONTEXT_MENU_DEFAULT, 0, R.string.trip_list_context_edit);
        menu.add(0, CONTEXT_MENU_DELETE, 0, R.string.trip_list_context_delete);
        menu.add(0, CONTEXT_MENU_SHOWSTOP, 0,
                R.string.trip_list_context_showstop);
        menu.add(0, CONTEXT_MENU_SHOWROUTE, 0,
                R.string.trip_list_context_showroute);
    }

    @Override
    public boolean onContextItemSelected(android.view.MenuItem item) {
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item
                .getMenuInfo();
        switch (item.getItemId()) {
            case CONTEXT_MENU_DEFAULT:
                // Fake a click
                onListItemClick(getListView(), info.targetView, info.position,
                        info.id);
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
        ContentResolver cr = getActivity().getContentResolver();
        cr.delete(ObaContract.Trips.buildUri(ids[0], ids[1]), null, null);
        TripService.scheduleAll(getActivity());

        getLoaderManager().getLoader(0).onContentChanged();
    }

    private void goToStop(ListView l, int position) {
        String[] ids = getIds(l, position);
        ArrivalsListActivity.start(getActivity(), ids[1]);
    }

    private void goToRoute(ListView l, int position) {
        String[] ids = getIds(l, position);
        RouteInfoActivity.start(getActivity(), ids[2]);
    }

    private String[] getIds(ListView l, int position) {
        // Get the cursor and fetch the stop ID from that.
        SimpleCursorAdapter cursorAdapter = (SimpleCursorAdapter) l.getAdapter();
        final Cursor c = cursorAdapter.getCursor();
        c.moveToPosition(position - l.getHeaderViewsCount());
        final String[] result = new String[]{
                c.getString(COL_ID),
                c.getString(COL_STOP_ID),
                c.getString(COL_ROUTE_ID)
        };
        return result;
    }
}
