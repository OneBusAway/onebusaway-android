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

import com.joulespersecond.oba.provider.ObaContract;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.support.v4.widget.SimpleCursorAdapter;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.ListView;
import android.widget.TextView;

abstract class MyStopListActivity extends MyBaseListActivity implements QueryUtils.StopList.Columns {
    //private static final String TAG = "MyStopListActivity";

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        StopData stopData = getStopData(l, position);

        if (mShortcutMode) {
            final Intent shortcut =
                UIHelp.makeShortcut(this, stopData.getUiName(),
                        ArrivalsListActivity.makeIntent(this, stopData.getId(), stopData.getName(), stopData.getDir()));

            if (isChild()) {
                // Is there a way to do this more generically?
                final Activity parent = getParent();
                if (parent instanceof MyStopsActivity) {
                    MyStopsActivity myStops = (MyStopsActivity)parent;
                    myStops.returnResult(shortcut);
                }
            }
            else {
                setResult(RESULT_OK, shortcut);
                finish();
            }
        }
        else {
            ArrivalsListActivity.start(this, stopData.id, stopData.name, stopData.dir);
        }
    }

    protected StopData getStopData(ListView l, int position) {
        // Get the cursor and fetch the stop ID from that.
        SimpleCursorAdapter cursorAdapter = (SimpleCursorAdapter)l.getAdapter();
        return new StopData(cursorAdapter.getCursor(), position - l.getHeaderViewsCount());
    }

    private static final int CONTEXT_MENU_DEFAULT = 1;
    private static final int CONTEXT_MENU_SHOW_ON_MAP = 2;

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v,
            ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        AdapterContextMenuInfo info = (AdapterContextMenuInfo)menuInfo;
        final TextView text = (TextView)info.targetView.findViewById(R.id.stop_name);
        menu.setHeaderTitle(text.getText());
        if (mShortcutMode) {
            menu.add(0, CONTEXT_MENU_DEFAULT, 0, R.string.my_context_create_shortcut);
        }
        else {
            menu.add(0, CONTEXT_MENU_DEFAULT, 0, R.string.my_context_get_stop_info);
        }
        menu.add(0, CONTEXT_MENU_SHOW_ON_MAP, 0, R.string.my_context_showonmap);
        addShortcutContextMenuItem(menu, v, menuInfo);
    }
    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterContextMenuInfo info = (AdapterContextMenuInfo)item.getMenuInfo();
        switch (item.getItemId()) {
        case CONTEXT_MENU_DEFAULT:
            // Fake a click
            onListItemClick(getListView(), info.targetView, info.position, info.id);
            return true;
        case CONTEXT_MENU_SHOW_ON_MAP:
            showOnMap(getListView(), info.position);
            return true;
        case CONTEXT_MENU_CREATE_SHORTCUT:
            StopData stopData = getStopData(getListView(), info.position);
            final Intent shortcutIntent =
                    UIHelp.makeShortcut(this, stopData.uiName,
                            ArrivalsListActivity.makeIntent(this, stopData.id, stopData.name, stopData.dir));
            shortcutIntent.setAction(INSTALL_SHORTCUT);
            sendBroadcast(shortcutIntent);
            return true;
        default:
            return super.onContextItemSelected(item);
        }
    }
    private void showOnMap(ListView l, int position) {
        // Get the cursor and fetch the stop ID from that.
        SimpleCursorAdapter cursorAdapter = (SimpleCursorAdapter)l.getAdapter();
        Cursor c = cursorAdapter.getCursor();
        c.moveToPosition(position - l.getHeaderViewsCount());
        final String stopId = c.getString(COL_ID);
        final double lat = c.getDouble(COL_LATITUDE);
        final double lon = c.getDouble(COL_LONGITUDE);

        MapViewActivity.start(this, stopId, lat, lon);
    }

    protected void initList(Cursor c) {
        startManagingCursor(c);

        setListAdapter(QueryUtils.StopList.newAdapter(this));
    }

    protected Uri getObserverUri() {
        return ObaContract.Stops.CONTENT_URI;
    }

    protected class StopData {
        private final String id;
        private final String name;
        private final String dir;
        private final String uiName;

        public StopData(Cursor c, int row){
            c.moveToPosition(row);
            id = c.getString(COL_ID);
            name = c.getString(COL_NAME);
            dir = c.getString(COL_DIRECTION);
            uiName = c.getString(COL_UI_NAME);
        }

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public String getDir() {
            return dir;
        }

        public String getUiName() {
            return uiName;
        }
    }
}
