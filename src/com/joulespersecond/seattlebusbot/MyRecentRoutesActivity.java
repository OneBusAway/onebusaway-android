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

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.AdapterView.AdapterContextMenuInfo;

import com.joulespersecond.oba.provider.ObaContract;

public class MyRecentRoutesActivity extends MyRouteListActivity {
    public static final String TAB_NAME = "recent";

    @Override
    protected Cursor getCursor() {
        return recentQuery(ObaContract.Routes.CONTENT_URI,
                PROJECTION,
                ObaContract.Routes.ACCESS_TIME,
                ObaContract.Routes.USE_COUNT);
    }
    @Override
    protected int getLayoutId() {
        return R.layout.my_recent_route_list;
    }

    private static final int CONTEXT_MENU_DELETE = 10;

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v,
            ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        menu.add(0, CONTEXT_MENU_DELETE, 0, R.string.my_context_remove_recent);
    }
    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterContextMenuInfo info = (AdapterContextMenuInfo)item.getMenuInfo();
        switch (item.getItemId()) {
        case CONTEXT_MENU_DELETE:
            ObaContract.Routes.markAsUnused(this,
                    Uri.withAppendedPath(ObaContract.Routes.CONTENT_URI,
                            getId(getListView(), info.position)));
            return true;
        default:
            return super.onContextItemSelected(item);
        }
    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.my_recent_route_options, menu);
        return true;
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.clear_recent) {
            ObaContract.Routes.markAsUnused(this, ObaContract.Routes.CONTENT_URI);
            return true;
        }
        return false;
    }
    @Override
    protected Intent getShortcutIntent() {
        return UIHelp.makeShortcut(this,
                    getString(R.string.recent_routes_shortcut),
                    new Intent(this, MyRoutesActivity.class)
                        .setData(MyTabActivityBase.getDefaultTabUri(TAB_NAME)));
    }
}
