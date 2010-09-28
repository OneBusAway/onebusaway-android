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

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.view.ContextMenu;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.AdapterView.AdapterContextMenuInfo;

import com.joulespersecond.oba.provider.ObaContract;

abstract class MyStopListActivity extends MyBaseListActivity {
    //private static final String TAG = "MyStopListActivity";

    protected static final String[] PROJECTION = {
        ObaContract.Stops._ID,
        ObaContract.Stops.UI_NAME,
        ObaContract.Stops.DIRECTION,
        ObaContract.Stops.LATITUDE,
        ObaContract.Stops.LONGITUDE,
        ObaContract.Stops.UI_NAME,
        ObaContract.Stops.FAVORITE
    };
    private static final int COL_ID = 0;
    private static final int COL_NAME = 1;
    private static final int COL_DIRECTION = 2;
    private static final int COL_LATITUDE = 3;
    private static final int COL_LONGITUDE = 4;
    private static final int COL_UI_NAME = 5;
    private static final int COL_FAVORITE = 6;

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        // Get the cursor and fetch the stop ID from that.
        SimpleCursorAdapter cursorAdapter = (SimpleCursorAdapter)l.getAdapter();
        Cursor c = cursorAdapter.getCursor();
        c.moveToPosition(position - l.getHeaderViewsCount());
        final String stopId = c.getString(COL_ID);
        final String stopName = c.getString(COL_NAME);
        final String stopDir = c.getString(COL_DIRECTION);
        final String shortcutName = c.getString(COL_UI_NAME);

        if (mShortcutMode) {
            final Intent shortcut =
                UIHelp.makeShortcut(this, shortcutName,
                        StopInfoActivity.makeIntent(this, stopId, stopName, stopDir));

            if (isChild()) {
                // Is there a way to do this more generically?
                final Activity parent = getParent();
                if (parent instanceof MyStopsActivity) {
                    MyStopsActivity myStops = (MyStopsActivity)parent;
                    myStops.returnShortcut(shortcut);
                }
            }
            else {
                setResult(RESULT_OK, shortcut);
                finish();
            }
        }
        else {
            StopInfoActivity.start(this, stopId, stopName, stopDir);
        }
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

    protected String getId(ListView l, int position) {
        // Get the cursor and fetch the stop ID from that.
        SimpleCursorAdapter cursorAdapter = (SimpleCursorAdapter)l.getAdapter();
        Cursor c = cursorAdapter.getCursor();
        c.moveToPosition(position - l.getHeaderViewsCount());
        return c.getString(COL_ID);
    }

    protected void initList(Cursor c) {
        startManagingCursor(c);

        String[] from = new String[] {
                ObaContract.Stops.UI_NAME,
                ObaContract.Stops.DIRECTION,
                ObaContract.Stops.FAVORITE
        };
        int[] to = new int[] {
                R.id.stop_name,
                R.id.direction,
                R.id.stop_name
        };
        SimpleCursorAdapter simpleAdapter =
            new SimpleCursorAdapter(this, R.layout.stop_list_item, c, from, to);

        // We need to convert the direction text (N/NW/E/etc)
        // to user level text (North/Northwest/etc..)
        simpleAdapter.setViewBinder(new SimpleCursorAdapter.ViewBinder() {
            public boolean setViewValue(View view, Cursor cursor, int columnIndex) {
                if (columnIndex == COL_FAVORITE) {
                    TextView favorite = (TextView)view.findViewById(R.id.stop_name);
                    int icon = (cursor.getInt(columnIndex) == 1) ? R.drawable.star_on : 0;
                    favorite.setCompoundDrawablesWithIntrinsicBounds(icon, 0, 0, 0);
                    return true;
                }
                else if (columnIndex == COL_DIRECTION) {
                    UIHelp.setStopDirection(view.findViewById(R.id.direction),
                            cursor.getString(columnIndex),
                            true);
                    return true;
                }
                return false;
            }
        });
        setListAdapter(simpleAdapter);
    }

    protected Uri getObserverUri() {
        return ObaContract.Stops.CONTENT_URI;
    }
}
