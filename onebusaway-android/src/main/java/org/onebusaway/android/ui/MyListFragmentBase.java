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

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.ListView;

import androidx.annotation.NonNull;
import androidx.cursoradapter.widget.SimpleCursorAdapter;
import androidx.fragment.app.DialogFragment;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.Loader;

/**
 * Base class for the stop/route list fragments.
 * Immediate base class for MyStopListFragmentBase/MyRouteListFragmentBase
 * Ancestor of:
 * MyRecentRoutesFragment
 * MyRecentStopsFragment
 * MyStarredStopsFragment
 *
 * @author paulw
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

    protected SimpleCursorAdapter mAdapter;

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
            MyTabActivityBase base = (MyTabActivityBase) act;
            return base.isShortcutMode();
        }
        return false;
    }

    protected static abstract class ClearConfirmDialog extends DialogFragment {
        private final int dialogTitleResId;
        private final int dialogMessageResId;

        public ClearConfirmDialog() {
            this(R.string.my_option_clear_confirm, R.string.my_option_clear_confirm_title);
        }

        public ClearConfirmDialog(int dialogMessageResId, int dialogTitleResId) {
            this.dialogMessageResId = dialogMessageResId;
            this.dialogTitleResId = dialogTitleResId;
        }

        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            return new AlertDialog.Builder(requireActivity())
                    .setMessage(dialogMessageResId)
                    .setTitle(dialogTitleResId)
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setPositiveButton(android.R.string.yes, (dialog, which) -> doClear())
                    .setNegativeButton(android.R.string.no, (dialog, which) -> dialog.dismiss())
                    .create();
        }

        protected abstract void doClear();
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
