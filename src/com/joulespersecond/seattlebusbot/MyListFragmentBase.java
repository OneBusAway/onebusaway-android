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

import android.app.Activity;
import android.content.ContentResolver;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v4.widget.SimpleCursorAdapter;

/**
 * Base class for the stop/route list fragments.
 * Immediate base class for MyStopListFragmentBase/MyRouteListFragmentBase
 * Ancestor of:
 *      MyRecentRoutesFragment
 *      MyRecentStopsFragment
 *      MyStarredStopsFragment
 *
 * @author paulw
 *
 */
abstract class MyListFragmentBase extends ListFragment
        implements LoaderManager.LoaderCallbacks<Cursor>, MyListConstants {
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
                getLoaderManager().restartLoader(0, null, MyListFragmentBase.this);
            }
        }
    }

    private SimpleCursorAdapter mAdapter;
    private Observer mObserver;

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        // Set empty text
        setEmptyText(getString(getEmptyText()));
        registerForContextMenu(getListView());

        // Create our generic adapter
        mAdapter = newAdapter();
        setListAdapter(mAdapter);
        ContentResolver cr = getActivity().getContentResolver();
        mObserver = new Observer();
        cr.registerContentObserver(getContentUri(), true, mObserver);

        // Prepare the loader
        getLoaderManager().initLoader(0, null, this);
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
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        mAdapter.swapCursor(data);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        mAdapter.swapCursor(null);
    }

    protected boolean isShortcutMode() {
        Activity act = getActivity();
        if (act instanceof MyTabActivityBase) {
            MyTabActivityBase base = (MyTabActivityBase)act;
            return base.isShortcutMode();
        }
        return false;
    }

    protected boolean isSearchMode() {
        Activity act = getActivity();
        if (act instanceof MyTabActivityBase) {
            MyTabActivityBase base = (MyTabActivityBase)act;
            return base.isSearchMode();
        }
        return false;
    }

    //
    // Creates a new adapter
    //
    abstract protected SimpleCursorAdapter newAdapter();

    //
    // Returns the content URI to observe for changes
    //
    abstract protected Uri getContentUri();

    //
    // Returns the empty text to display
    //
    abstract protected int getEmptyText();
}
