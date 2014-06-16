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
package com.joulespersecond.seattlebusbot;

import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.joulespersecond.oba.provider.ObaContract;

import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.View;
import android.widget.AdapterView.AdapterContextMenuInfo;

public class MyStarredStopsFragment extends MyStopListFragmentBase {

    public static final String TAB_NAME = "starred";

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        return new CursorLoader(getActivity(),
                ObaContract.Stops.CONTENT_URI,
                QueryUtils.StopList.Columns.PROJECTION,
                ObaContract.Stops.FAVORITE + "=1" +
                        (Application.get().getCurrentRegion() == null ? "" : " AND " +
                                QueryUtils.getRegionWhere(ObaContract.Stops.REGION_ID,
                                        Application.get().getCurrentRegion().getId())),
                null,
                ObaContract.Stops.USE_COUNT + " desc");
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v,
            ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        menu.add(0, MyListConstants.CONTEXT_MENU_DELETE, 0, R.string.my_context_remove_star);
    }

    @Override
    public boolean onContextItemSelected(android.view.MenuItem item) {
        AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
        switch (item.getItemId()) {
            case MyListConstants.CONTEXT_MENU_DELETE:
                final String id = QueryUtils.StopList.getId(getListView(), info.position);
                final Uri uri = Uri.withAppendedPath(ObaContract.Stops.CONTENT_URI, id);
                ObaContract.Stops.markAsFavorite(getActivity(), uri, false);
                return true;
            default:
                return super.onContextItemSelected(item);
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.my_starred_stop_options, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.clear_starred) {
            Application.getAnalytics().reportEvent("ui_action", "button_press",
                    "Remove all starred stops");

            new ClearDialog()
                    .show(getActivity().getSupportFragmentManager(), "confirm_clear_starred_stops");
            return true;
        }
        return false;
    }

    @Override
    protected int getEmptyText() {
        return R.string.my_no_starred_stops;
    }

    private static class ClearDialog extends ClearConfirmDialog {

        @Override
        protected void doClear() {
            ObaContract.Stops.markAsFavorite(getActivity(), ObaContract.Stops.CONTENT_URI, false);
        }
    }
}
