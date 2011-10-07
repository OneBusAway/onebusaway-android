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
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.text.format.DateUtils;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.View;
import android.view.Window;
import android.widget.SimpleCursorAdapter;

abstract class MyBaseListActivity extends ListActivity {
    private final Handler mHandler = new Handler();
    private class MyObserver extends ContentObserver {
        public MyObserver() {
            super(mHandler);
        }
        @Override
        public boolean deliverSelfNotifications() {
            return false;
        }
        public void onChange(boolean selfChange) {
            SimpleCursorAdapter adapter = (SimpleCursorAdapter)getListAdapter();
            adapter.getCursor().requery();
        }
    }

    protected boolean mShortcutMode;
    private MyObserver mObserver;
    // intent and permission names
    protected static final String INSTALL_SHORTCUT_PERMISSION = "com.android.launcher.permission.INSTALL_SHORTCUT";
    protected static final String INSTALL_SHORTCUT = "com.android.launcher.action.INSTALL_SHORTCUT";
    protected static boolean mHasInstallShortcutPermission;
    

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);

        Intent myIntent = getIntent();
        if (Intent.ACTION_CREATE_SHORTCUT.equals(myIntent.getAction())) {
            setResult(RESULT_OK, getShortcutIntent());
            finish();
            return;
        }

        setContentView(getLayoutId());
        registerForContextMenu(getListView());

        mShortcutMode = myIntent.getBooleanExtra(MyTabActivityBase.EXTRA_SHORTCUTMODE, false);

        initList(getCursor());
        Uri uri = getObserverUri();
        if (uri != null) {
            ContentResolver cr = getContentResolver();
            mObserver = new MyObserver();
            cr.registerContentObserver(uri, true, mObserver);
        }
        
        mHasInstallShortcutPermission = (PackageManager.PERMISSION_GRANTED ==
                getPackageManager().checkPermission(INSTALL_SHORTCUT_PERMISSION, getPackageName()));
    }
    @Override
    public void onDestroy() {
        if (mObserver != null) {
            ContentResolver cr = getContentResolver();
            cr.unregisterContentObserver(mObserver);
        }
        super.onDestroy();
    }
    
    protected static final int CONTEXT_MENU_CREATE_SHORTCUT = 15;

    public void addShortcutContextMenuItem(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        if (!mShortcutMode && mHasInstallShortcutPermission){
            menu.add(0, CONTEXT_MENU_CREATE_SHORTCUT, 0, R.string.my_context_create_shortcut);
        }
    }
    
    protected Cursor recentQuery(final Uri uri,
                final String[] projection,
                final String accessTime,
                final String useCount) {
        // "Recently" means seven days in the past
        final long last = System.currentTimeMillis() - 7*DateUtils.DAY_IN_MILLIS;
        return managedQuery(uri
                        .buildUpon()
                        .appendQueryParameter("limit", "20")
                        .build(),
                projection,
                "(" +
                    accessTime + " IS NOT NULL AND " +
                    accessTime + " > " + last +
                ") OR (" + useCount + " > 0)",
                null,
                accessTime + " desc, " +
                useCount + " desc");
    }

    /**
     * Initializes the list with a cursor
     */
    abstract protected void initList(Cursor c);
    /**
     * @return The cursor of the data to be managed.
     */
    abstract protected Cursor getCursor();
    /**
     * @return The layout ID of the activity.
     */
    abstract protected int getLayoutId();
    /**
     * @return The Uri to observe for changes
     */
    abstract protected Uri getObserverUri();
    /**
     * @return The default Tab Uri
     */
    abstract protected Intent getShortcutIntent();
}
