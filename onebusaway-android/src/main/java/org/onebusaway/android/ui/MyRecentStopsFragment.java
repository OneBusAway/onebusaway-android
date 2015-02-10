/*
 * Copyright (C) 2011 Paul Watts (paulcwatts@gmail.com)
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

import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.content.Loader;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView.AdapterContextMenuInfo;


public class MyRecentStopsFragment extends MyStopListFragmentBase {

    public static final String TAB_NAME = "recent";

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        return QueryUtils.newRecentQuery(getActivity(),
                ObaContract.Stops.CONTENT_URI,
                PROJECTION,
                ObaContract.Stops.ACCESS_TIME,
                ObaContract.Stops.USE_COUNT);
    }

    //
    // MyRecentStopsActivity
    //
    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public void onStart() {
        ObaAnalytics.reportFragmentStart(this);
        super.onStart();
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v,
            ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        menu.add(0, CONTEXT_MENU_DELETE, 0, R.string.my_context_remove_recent);
    }

    @Override
    public boolean onContextItemSelected(android.view.MenuItem item) {
        AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
        switch (item.getItemId()) {
            case CONTEXT_MENU_DELETE:
                ObaContract.Stops.markAsUnused(getActivity(),
                        Uri.withAppendedPath(ObaContract.Stops.CONTENT_URI,
                                QueryUtils.StopList.getId(getListView(), info.position)));
                return true;
            default:
                return super.onContextItemSelected(item);
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.my_recent_stop_options, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.clear_recent) {
            new ClearDialog()
                    .show(getActivity().getSupportFragmentManager(), "confirm_clear_recent_stops");
            return true;
        }
        return false;
    }

    @Override
    protected int getEmptyText() {
        return R.string.my_no_recent_stops;
    }

    public static class ClearDialog extends ClearConfirmDialog {

        @Override
        protected void doClear() {
            ObaContract.Stops.markAsUnused(getActivity(), ObaContract.Stops.CONTENT_URI);
        }
    }
}
