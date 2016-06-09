/*
 * Copyright (C) 2012-2015 Paul Watts (paulcwatts@gmail.com), University of South Florida
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
import org.onebusaway.android.app.Application;
import org.onebusaway.android.io.ObaAnalytics;
import org.onebusaway.android.provider.ObaContract;
import org.onebusaway.android.util.PreferenceUtils;
import org.onebusaway.android.util.ShowcaseViewUtils;

import android.content.DialogInterface;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView.AdapterContextMenuInfo;

public class MyStarredStopsFragment extends MyStopListFragmentBase {

    public static final String TAG = "MyStarredStopsFragment";
    public static final String TAB_NAME = "starred";

    private static String sortBy;

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        // Set the sort by clause, in case its the first execution and none is set
        final int currentStopOrder = PreferenceUtils.getStopSortOrderFromPreferences();
        setSortByClause(currentStopOrder);

        return new CursorLoader(getActivity(),
                ObaContract.Stops.CONTENT_URI,
                PROJECTION,
                ObaContract.Stops.FAVORITE + "=1" +
                        (Application.get().getCurrentRegion() == null ? "" : " AND " +
                                QueryUtils.getRegionWhere(ObaContract.Stops.REGION_ID,
                                        Application.get().getCurrentRegion().getId())),
                null, sortBy);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        setHasOptionsMenu(true);
        showStarredStopsTutorials();
    }


    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden) {
            showStarredStopsTutorials();
        }
    }

    /**
     * Show the sort starred stops tutorial if we have more than 1 starred stop
     */
    private void showStarredStopsTutorials() {
        if (!isVisible()) {
            return;
        }
        if (mAdapter.getCount() > 0) {
            ShowcaseViewUtils.showTutorial(ShowcaseViewUtils.TUTORIAL_STARRED_STOPS_SHORTCUT,
                    (AppCompatActivity) getActivity(), null);
        }
        if (mAdapter.getCount() > 1) {
            ShowcaseViewUtils.showTutorial(ShowcaseViewUtils.TUTORIAL_STARRED_STOPS_SORT,
                    (AppCompatActivity) getActivity(), null);
        }
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
        menu.add(0, CONTEXT_MENU_DELETE, 0, R.string.my_context_remove_star);
    }

    @Override
    public boolean onContextItemSelected(android.view.MenuItem item) {
        AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
        switch (item.getItemId()) {
            case CONTEXT_MENU_DELETE:
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
        final int id = item.getItemId();
        if (id == R.id.clear_starred) {
            new ClearDialog()
                    .show(getActivity().getSupportFragmentManager(), "confirm_clear_starred_stops");
            return true;
        } else if (id == R.id.sort_stops) {
            ShowcaseViewUtils.doNotShowTutorial(ShowcaseViewUtils.TUTORIAL_STARRED_STOPS_SORT);
            showSortByDialog();
        }
        return false;
    }

    private void showSortByDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(R.string.menu_option_sort_by);

        final int currentStopOrder = PreferenceUtils.getStopSortOrderFromPreferences();

        builder.setSingleChoiceItems(R.array.sort_stops, currentStopOrder,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int index) {
                        // If the user picked a different option, change the sort order
                        if (currentStopOrder != index) {
                            setSortByClause(index);

                            // Restart the loader with the new sorting
                            getLoaderManager().restartLoader(0, null, MyStarredStopsFragment.this);
                        }
                        dialog.dismiss();
                    }
                });
        AlertDialog dialog = builder.create();
        dialog.setOwnerActivity(getActivity());
        dialog.show();
    }

    /**
     * Sets the "sort by" string for ordering the stops, based on the given index of
     * R.array.sort_stops.  It also saves the sort by order to preferences.
     *
     * @param index the index of R.array.sort_stops that should be set
     */
    private void setSortByClause(int index) {
        switch (index) {
            case 0:
                // Sort by name
                Log.d(TAG, "Sort by name");
                sortBy = ObaContract.Stops.UI_NAME + " asc";
                ObaAnalytics.reportEventWithCategory(ObaAnalytics.ObaEventCategory.UI_ACTION.toString(),
                        getActivity().getString(R.string.analytics_action_button_press),
                        getActivity().getString(R.string.analytics_label_sort_by_name_stops));
                break;
            case 1:
                // Sort by frequently used
                Log.d(TAG, "Sort by frequently used");
                sortBy = ObaContract.Stops.USE_COUNT + " desc";
                ObaAnalytics.reportEventWithCategory(ObaAnalytics.ObaEventCategory.UI_ACTION.toString(),
                        getActivity().getString(R.string.analytics_action_button_press),
                        getActivity().getString(R.string.analytics_label_sort_by_mfu_stops));
                break;
        }
        // Set the sort option to preferences
        final String[] sortOptions = getResources().getStringArray(R.array.sort_stops);
        PreferenceUtils.saveString(getResources()
                        .getString(R.string.preference_key_default_stop_sort),
                sortOptions[index]);
    }

    @Override
    protected int getEmptyText() {
        return R.string.my_no_starred_stops;
    }

    public static class ClearDialog extends ClearConfirmDialog {

        @Override
        protected void doClear() {
            ObaContract.Stops.markAsFavorite(getActivity(), ObaContract.Stops.CONTENT_URI, false);
            //Analytics
            ObaAnalytics.reportEventWithCategory(ObaAnalytics.ObaEventCategory.UI_ACTION.toString(),
                    getString(R.string.analytics_action_edit_field),
                    getString(R.string.analytics_label_edit_field_bookmark_delete));
        }
    }
}
