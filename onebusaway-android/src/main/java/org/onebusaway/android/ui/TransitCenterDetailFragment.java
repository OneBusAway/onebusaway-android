/*
 * Copyright (C) 2026 Open Transit Software Foundation
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

import android.database.Cursor;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.ListView;
import android.widget.TextView;

import androidx.cursoradapter.widget.SimpleCursorAdapter;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.CursorLoader;
import androidx.loader.content.Loader;

import org.onebusaway.android.R;
import org.onebusaway.android.provider.ObaContract;
import org.onebusaway.android.util.UIUtils;

import java.util.ArrayList;

public class TransitCenterDetailFragment extends ListFragment
        implements LoaderManager.LoaderCallbacks<Cursor>, QueryUtils.StopList.Columns {

    public static final String TAG = "TransitCenterDetailFragment";

    private static final String ARG_TRANSIT_CENTER_ID = "transit_center_id";
    private static final String ARG_TRANSIT_CENTER_NAME = "transit_center_name";

    private static final int CONTEXT_MENU_REMOVE_STOP = 20;

    private long mTransitCenterId;
    private boolean mStarredOnly = false;
    private SimpleCursorAdapter mAdapter;

    public static TransitCenterDetailFragment newInstance(long transitCenterId, String name) {
        TransitCenterDetailFragment fragment = new TransitCenterDetailFragment();
        Bundle args = new Bundle();
        args.putLong(ARG_TRANSIT_CENTER_ID, transitCenterId);
        args.putString(ARG_TRANSIT_CENTER_NAME, name);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        setHasOptionsMenu(true);

        Bundle args = getArguments();
        if (args != null) {
            mTransitCenterId = args.getLong(ARG_TRANSIT_CENTER_ID, -1);
        }

        setEmptyText(getString(R.string.transit_center_empty));
        registerForContextMenu(getListView());

        mAdapter = QueryUtils.StopList.newAdapter(getActivity());
        setListAdapter(mAdapter);

        getLoaderManager().initLoader(0, null, this);
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        ArrayList<String> stopIds =
                ObaContract.TransitCenterStops.getStopIds(getActivity(), mTransitCenterId);
        if (stopIds.isEmpty()) {
            // Return a loader that will produce an empty cursor
            return new CursorLoader(getActivity(),
                    ObaContract.Stops.CONTENT_URI,
                    PROJECTION,
                    ObaContract.Stops._ID + "='__NONE__'",
                    null, null);
        }

        StringBuilder sb = new StringBuilder();
        sb.append(ObaContract.Stops._ID);
        sb.append(" IN (");
        for (int i = 0; i < stopIds.size(); i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append("'");
            sb.append(stopIds.get(i).replace("'", "''"));
            sb.append("'");
        }
        sb.append(")");

        if (mStarredOnly) {
            sb.append(" AND ");
            sb.append(ObaContract.Stops.FAVORITE);
            sb.append("=1");
        }

        return new CursorLoader(getActivity(),
                ObaContract.Stops.CONTENT_URI,
                PROJECTION,
                sb.toString(),
                null,
                ObaContract.Stops.UI_NAME + " ASC");
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        mAdapter.swapCursor(data);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        mAdapter.swapCursor(null);
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        SimpleCursorAdapter cursorAdapter = (SimpleCursorAdapter) l.getAdapter();
        Cursor c = cursorAdapter.getCursor();
        c.moveToPosition(position - l.getHeaderViewsCount());
        String stopId = c.getString(COL_ID);
        String stopName = c.getString(COL_NAME);
        String stopDir = c.getString(COL_DIRECTION);

        ArrivalsListActivity.Builder b = new ArrivalsListActivity.Builder(getActivity(), stopId);
        b.setStopName(stopName);
        b.setStopDirection(stopDir);
        b.setUpMode(NavHelp.UP_MODE_BACK);
        b.start();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.transit_center_detail_options, menu);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        MenuItem toggleItem = menu.findItem(R.id.toggle_starred);
        if (toggleItem != null) {
            toggleItem.setTitle(mStarredOnly ?
                    R.string.transit_center_all_stops :
                    R.string.transit_center_starred_only);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.add_stops) {
            AddStopToTransitCenterDialog dialog =
                    AddStopToTransitCenterDialog.newInstance(mTransitCenterId);
            dialog.setOnStopsAddedListener(() -> {
                getLoaderManager().restartLoader(0, null, TransitCenterDetailFragment.this);
            });
            dialog.show(getActivity().getSupportFragmentManager(), "add_stops");
            return true;
        } else if (itemId == R.id.toggle_starred) {
            mStarredOnly = !mStarredOnly;
            getActivity().invalidateOptionsMenu();
            getLoaderManager().restartLoader(0, null, this);
            return true;
        }
        return false;
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        AdapterContextMenuInfo info = (AdapterContextMenuInfo) menuInfo;
        final TextView text = (TextView) info.targetView.findViewById(R.id.stop_name);
        if (text != null) {
            menu.setHeaderTitle(UIUtils.formatDisplayText(text.getText().toString()));
        }
        menu.add(0, CONTEXT_MENU_REMOVE_STOP, 0, R.string.transit_center_remove_stop);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        if (item.getItemId() == CONTEXT_MENU_REMOVE_STOP) {
            AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
            String stopId = QueryUtils.StopList.getId(getListView(), info.position);
            ObaContract.TransitCenterStops.removeStop(getActivity(), mTransitCenterId, stopId);
            getLoaderManager().restartLoader(0, null, this);
            return true;
        }
        return super.onContextItemSelected(item);
    }

}
