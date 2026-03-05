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
 *
 * Modifications copyright (C) 2023 Millan Philipose, University of Washington.
 * This file is adapted from MyStarredStopsFragment, to display starred routes rather than stops.
 */
package org.onebusaway.android.ui;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.analytics.FirebaseAnalytics;

import org.onebusaway.android.R;
import org.onebusaway.android.app.Application;
import org.onebusaway.android.io.ObaAnalytics;
import org.onebusaway.android.io.PlausibleAnalytics;
import org.onebusaway.android.provider.ObaContract;
import org.onebusaway.android.util.PreferenceUtils;
import org.onebusaway.android.util.ShowcaseViewUtils;

import android.content.DialogInterface;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView.AdapterContextMenuInfo;

import androidx.appcompat.app.AlertDialog;
import androidx.loader.content.CursorLoader;
import androidx.loader.content.Loader;

public class MyStarredRoutesFragment extends MyRouteListFragmentBase {

    public static final String TAG = "MyStarredRoutesFragment";
    public static final String TAB_NAME = "starred_rts";

    private static String sortBy;

    private FirebaseAnalytics mFirebaseAnalytics;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        mFirebaseAnalytics = FirebaseAnalytics.getInstance(getContext());
        return super.onCreateView(inflater, container, savedInstanceState);
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        // Set the sort by clause, in case its the first execution and none is set.
        // We use the same sorting preference (name vs most recent) as is used for sorting routes.
        final int currentRouteOrder = PreferenceUtils.getStopSortOrderFromPreferences();
        setSortByClause(currentRouteOrder);

        return new CursorLoader(getActivity(),
                ObaContract.Routes.CONTENT_URI,
                PROJECTION,
                ObaContract.Routes.FAVORITE + "=1" +
                        (Application.get().getCurrentRegion() == null ? "" : " AND " +
                                QueryUtils.getRegionWhere(ObaContract.Routes.REGION_ID,
                                        Application.get().getCurrentRegion().getId())),
                null, sortBy);
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
        menu.add(0, CONTEXT_MENU_DELETE, 0, R.string.my_context_remove_star);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
        switch (item.getItemId()) {
            case CONTEXT_MENU_DELETE:
                final String id = QueryUtils.RouteList.getId(getListView(), info.position);
                final Uri uri = Uri.withAppendedPath(ObaContract.Routes.CONTENT_URI, id);
                ObaContract.Routes.markAsFavorite(getActivity(), uri, false);
                ObaContract.RouteHeadsignFavorites.markAsFavorite(getActivity(), id, null, null, false);
                return true;
            default:
                return super.onContextItemSelected(item);
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.my_starred_route_options, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        final int id = item.getItemId();
        if (id == R.id.clear_starred) {
            new ClearDialog()
                    .show(getActivity().getSupportFragmentManager(), "confirm_clear_starred_routes");
            return true;
        } else if (id == R.id.sort_stops) {
            ShowcaseViewUtils.doNotShowTutorial(ShowcaseViewUtils.TUTORIAL_STARRED_STOPS_SORT);
            showSortByDialog();
        }
        return false;
    }

    private void showSortByDialog() {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(getActivity());
        builder.setTitle(R.string.menu_option_sort_by);

        final int currentRouteOrder = PreferenceUtils.getStopSortOrderFromPreferences();

        builder.setSingleChoiceItems(R.array.sort_stops, currentRouteOrder,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int index) {
                        // If the user picked a different option, change the sort order
                        if (currentRouteOrder != index) {
                            setSortByClause(index);

                            // Restart the loader with the new sorting
                            getLoaderManager().restartLoader(0, null, MyStarredRoutesFragment.this);
                        }
                        dialog.dismiss();
                    }
                });
        AlertDialog dialog = builder.create();
        dialog.setOwnerActivity(getActivity());
        dialog.show();
    }

    /**
     * Sets the "sort by" string for ordering the routes, based on the given index of
     * R.array.sort_stops.  It also saves the sort by order to preferences.
     *
     * @param index the index of R.array.sort_stops that should be set
     */
    private void setSortByClause(int index) {
        switch (index) {
            case 0:
                // Sort by name
                Log.d(TAG, "Sort by name");
                sortBy = "length(" + ObaContract.Routes.SHORTNAME + "), "
                        + ObaContract.Routes.SHORTNAME + " asc";
                ObaAnalytics.reportUiEvent(mFirebaseAnalytics,
                        Application.get().getPlausibleInstance(),
                        PlausibleAnalytics.REPORT_STARRED_ROUTES_EVENT_URL,
                        getString(R.string.analytics_label_sort_by_name_stops),
                        null);
                break;
            case 1:
                // Sort by frequently used
                Log.d(TAG, "Sort by frequently used");
                sortBy = ObaContract.Routes.USE_COUNT + " desc";
                ObaAnalytics.reportUiEvent(mFirebaseAnalytics,
                        Application.get().getPlausibleInstance(),
                        PlausibleAnalytics.REPORT_STARRED_ROUTES_EVENT_URL,
                        getString(R.string.analytics_label_sort_by_mfu_stops),
                        null);
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
        return R.string.my_no_starred_routes;
    }

    public static class ClearDialog extends ClearConfirmDialog {
        
        public ClearDialog() {
            super(R.string.my_option_clear_starred_routes_confirm, R.string.my_option_clear_starred_routes_title);
        }

        @Override
        protected void doClear() {
            ObaContract.Routes.markAsFavorite(getActivity(), ObaContract.Routes.CONTENT_URI, false);
            ObaContract.RouteHeadsignFavorites.clearAllFavorites(getActivity());
            ObaAnalytics.reportUiEvent(FirebaseAnalytics.getInstance(getContext()),
                    Application.get().getPlausibleInstance(),
                    PlausibleAnalytics.REPORT_BOOKMARK_EVENT_URL,
                    getString(R.string.analytics_label_edit_field_bookmark_delete),
                    null);
        }
    }
}
