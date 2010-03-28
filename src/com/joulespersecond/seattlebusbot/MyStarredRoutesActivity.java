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

import android.content.Intent;
import android.database.Cursor;

public class MyStarredRoutesActivity extends MyRouteListActivity {
    public static final String TAB_NAME = "starred";

    @Override
    protected Cursor getCursor() {
        return managedQuery(ObaContract.Routes.CONTENT_URI,
                PROJECTION,
                ObaContract.Routes.FAVORITE + "=1",
                null,
                ObaContract.Routes.USE_COUNT + " desc");
    }
    @Override
    protected int getLayoutId() {
        return R.layout.my_starred_route_list;
    }
    // TODO: Allow deleting from this list via the context menu.

    @Override
    protected Intent getShortcutIntent() {
        return UIHelp.makeShortcut(this,
                    getString(R.string.starred_routes_shortcut),
                    new Intent(this, MyRoutesActivity.class)
                        .setData(MyTabActivityBase.getDefaultTabUri(TAB_NAME)));
    }
}
