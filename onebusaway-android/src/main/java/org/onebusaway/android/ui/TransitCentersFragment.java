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

import android.content.ContentResolver;
import android.content.Intent;
import android.database.ContentObserver;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.cursoradapter.widget.SimpleCursorAdapter;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.CursorLoader;
import androidx.loader.content.Loader;

import org.onebusaway.android.R;
import org.onebusaway.android.app.Application;
import org.onebusaway.android.provider.ObaContract;

import java.util.ArrayList;

public class TransitCentersFragment extends ListFragment
        implements LoaderManager.LoaderCallbacks<Cursor> {

    public static final String TAG = "TransitCentersFragment";

    private static final int CONTEXT_MENU_RENAME = 1;
    private static final int CONTEXT_MENU_DELETE = 2;

    private SimpleCursorAdapter mAdapter;
    private ContentObserver mObserver;
    private static final Handler mHandler = new Handler();

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        setHasOptionsMenu(true);
        setEmptyText(getString(R.string.transit_center_empty));
        registerForContextMenu(getListView());

        String[] from = {ObaContract.TransitCenters.NAME};
        int[] to = {R.id.transit_center_name};
        mAdapter = new SimpleCursorAdapter(getActivity(),
                R.layout.transit_center_list_item, null, from, to, 0);
        setListAdapter(mAdapter);

        ContentResolver cr = getActivity().getContentResolver();
        mObserver = new ContentObserver(mHandler) {
            @Override
            public void onChange(boolean selfChange) {
                if (isAdded()) {
                    getLoaderManager().restartLoader(0, null, TransitCentersFragment.this);
                }
            }
        };
        cr.registerContentObserver(ObaContract.TransitCenters.CONTENT_URI, true, mObserver);

        getLoaderManager().initLoader(0, null, this);
    }

    @Override
    public void onDestroy() {
        if (mObserver != null && getActivity() != null) {
            ContentResolver cr = getActivity().getContentResolver();
            cr.unregisterContentObserver(mObserver);
            mObserver = null;
        }
        super.onDestroy();
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        String selection = null;
        if (Application.get().getCurrentRegion() != null) {
            long regionId = Application.get().getCurrentRegion().getId();
            selection = "(" + ObaContract.TransitCenters.REGION_ID + "=" + regionId +
                    " OR " + ObaContract.TransitCenters.REGION_ID + " IS NULL)";
        }
        return new CursorLoader(getActivity(),
                ObaContract.TransitCenters.CONTENT_URI,
                new String[]{
                        ObaContract.TransitCenters._ID,
                        ObaContract.TransitCenters.NAME
                },
                selection, null,
                ObaContract.TransitCenters.NAME + " ASC");
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        mAdapter.swapCursor(data);
        updateStopCounts();
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        mAdapter.swapCursor(null);
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        Cursor c = (Cursor) mAdapter.getItem(position);
        if (c == null) {
            return;
        }
        long transitCenterId = c.getLong(0);
        String name = c.getString(1);

        Intent intent = new Intent(getActivity(), TransitCenterDetailActivity.class);
        intent.putExtra(TransitCenterDetailActivity.EXTRA_TRANSIT_CENTER_ID, transitCenterId);
        intent.putExtra(TransitCenterDetailActivity.EXTRA_TRANSIT_CENTER_NAME, name);
        startActivity(intent);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.transit_centers_options, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.add_transit_center) {
            showAddDialog();
            return true;
        }
        return false;
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        AdapterContextMenuInfo info = (AdapterContextMenuInfo) menuInfo;
        Cursor c = (Cursor) mAdapter.getItem(info.position);
        if (c != null) {
            menu.setHeaderTitle(c.getString(1));
        }
        menu.add(0, CONTEXT_MENU_RENAME, 0, R.string.transit_center_rename);
        menu.add(0, CONTEXT_MENU_DELETE, 0, R.string.transit_center_delete);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
        Cursor c = (Cursor) mAdapter.getItem(info.position);
        if (c == null) {
            return super.onContextItemSelected(item);
        }
        final long transitCenterId = c.getLong(0);
        final String name = c.getString(1);

        switch (item.getItemId()) {
            case CONTEXT_MENU_RENAME:
                showRenameDialog(transitCenterId, name);
                return true;
            case CONTEXT_MENU_DELETE:
                showDeleteConfirmation(transitCenterId);
                return true;
            default:
                return super.onContextItemSelected(item);
        }
    }

    private void showAddDialog() {
        final EditText input = new EditText(getActivity());
        input.setHint(R.string.transit_center_name_hint);
        input.setSingleLine();

        new AlertDialog.Builder(getActivity())
                .setTitle(R.string.transit_center_add)
                .setView(input)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (!TextUtils.isEmpty(name)) {
                        long regionId = 0;
                        if (Application.get().getCurrentRegion() != null) {
                            regionId = Application.get().getCurrentRegion().getId();
                        }
                        ObaContract.TransitCenters.insert(getActivity(), name, regionId);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showRenameDialog(final long transitCenterId, String currentName) {
        final EditText input = new EditText(getActivity());
        input.setText(currentName);
        input.setSingleLine();
        input.setSelection(currentName.length());

        new AlertDialog.Builder(getActivity())
                .setTitle(R.string.transit_center_rename)
                .setView(input)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    String newName = input.getText().toString().trim();
                    if (!TextUtils.isEmpty(newName)) {
                        ObaContract.TransitCenters.rename(getActivity(), transitCenterId, newName);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showDeleteConfirmation(final long transitCenterId) {
        new AlertDialog.Builder(getActivity())
                .setMessage(R.string.transit_center_delete_confirm)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    ObaContract.TransitCenters.delete(getActivity(), transitCenterId);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void updateStopCounts() {
        ListView listView = getListView();
        if (listView == null) {
            return;
        }
        // Post to update views after adapter has bound data
        listView.post(() -> {
            if (!isAdded() || mAdapter.getCursor() == null) {
                return;
            }
            Cursor cursor = mAdapter.getCursor();
            for (int i = 0; i < listView.getChildCount(); i++) {
                View child = listView.getChildAt(i);
                int position = listView.getFirstVisiblePosition() + i;
                if (position >= cursor.getCount()) {
                    break;
                }
                cursor.moveToPosition(position);
                long tcId = cursor.getLong(0);
                ArrayList<String> stopIds =
                        ObaContract.TransitCenterStops.getStopIds(getActivity(), tcId);
                TextView countView = child.findViewById(R.id.transit_center_stop_count);
                if (countView != null) {
                    countView.setText(getResources().getQuantityString(
                            R.plurals.transit_center_stop_count,
                            stopIds.size(), stopIds.size()));
                }
            }
        });
    }

}
