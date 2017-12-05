/*
 * Copyright (C) 2015-2017 University of South Florida (sjbarbeau@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.onebusaway.android.ui;

import org.onebusaway.android.R;
import org.onebusaway.android.provider.ObaContract;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

/**
 * Asks the user if they was to save a route/headsign favorite for all stops, or just this stop
 */
public class RouteFavoriteDialogFragment extends android.support.v4.app.DialogFragment {

    public static final String TAG = "RouteFavDialogFragment";

    private static final String KEY_ROUTE_ID = "route_id";

    private static final String KEY_ROUTE_SHORT_NAME = "route_short_name";

    private static final String KEY_ROUTE_LONG_NAME = "route_long_name";

    private static final String KEY_HEADSIGN = "headsign";

    private static final String KEY_STOP_ID = "stop_id";

    private static final String KEY_FAVORITE = "favorite";

    // Selections need to match strings.xml "route_favorite_options"
    private static final int SELECTION_THIS_STOP = 0;

    private static final int SELECTION_ALL_STOPS = 1;

    private Callback mCallback;

    private int mSelectedItem;

    /**
     * Interface used to receive callbacks from the dialog after the user picks an option
     * and the database is updated with their choice
     */
    public interface Callback {

        /**
         * Called after the user picks an option and the database is updated with their choice
         *
         * @param savedFavorite true if the user saved a new route/headsign/stop favorite, false
         *                      if they did not
         */
        void onSelectionComplete(boolean savedFavorite);
    }

    /**
     * Builder used to create a new RouteFavoriteDialogFragment
     */
    public static class Builder {

        String mRouteId;

        String mRouteShortName;

        String mRouteLongName;

        String mHeadsign;

        String mStopId;

        String mRouteUrl;

        boolean mFavorite;

        public Builder(String routeId, String headsign) {
            mRouteId = routeId;
            mHeadsign = headsign;
        }

        public Builder setRouteId(String routeId) {
            mRouteId = routeId;
            return this;
        }

        public Builder setRouteShortName(String routeShortName) {
            mRouteShortName = routeShortName;
            return this;
        }

        public Builder setRouteLongName(String routeLongName) {
            mRouteLongName = routeLongName;
            return this;
        }

        public Builder setRouteUrl(String routeUrl) {
            mRouteUrl = routeUrl;
            return this;
        }

        public Builder setHeadsign(String headsign) {
            mHeadsign = headsign;
            return this;
        }

        public Builder setStopId(String stopId) {
            mStopId = stopId;
            return this;
        }

        public Builder setFavorite(boolean favorite) {
            mFavorite = favorite;
            return this;
        }

        public RouteFavoriteDialogFragment build() {
            RouteFavoriteDialogFragment f = new RouteFavoriteDialogFragment();

            // Provide arguments
            Bundle args = new Bundle();
            args.putString(KEY_ROUTE_ID, mRouteId);
            args.putString(KEY_ROUTE_SHORT_NAME, mRouteShortName);
            args.putString(KEY_ROUTE_LONG_NAME, mRouteLongName);
            args.putString(KEY_HEADSIGN, mHeadsign);
            args.putString(KEY_STOP_ID, mStopId);
            args.putBoolean(KEY_FAVORITE, mFavorite);
            f.setArguments(args);
            f.setCancelable(false);

            return f;
        }
    }

    /**
     * Sets the receiver of the callback after the user has picked their choice and the database
     * has been updated with the selection
     *
     * @param callback the receiver of the callback after the user has picked their choice and the
     *                 database
     *                 has been updated with the selection
     */
    public void setCallback(Callback callback) {
        mCallback = callback;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        // Initialize values from Bundle
        final String routeId = getArguments().getString(KEY_ROUTE_ID);
        final String routeShortName = getArguments().getString(KEY_ROUTE_SHORT_NAME);
        final String routeLongName = getArguments().getString(KEY_ROUTE_LONG_NAME);
        final String headsign = getArguments().getString(KEY_HEADSIGN);
        final String stopId = getArguments().getString(KEY_STOP_ID);
        final Boolean favorite = getArguments().getBoolean(KEY_FAVORITE);

        final Uri routeUri = Uri.withAppendedPath(ObaContract.Routes.CONTENT_URI, routeId);
        final ContentValues values = new ContentValues();
        values.put(ObaContract.Routes.SHORTNAME, routeShortName);
        values.put(ObaContract.Routes.LONGNAME, routeLongName);

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

        // Default to the first element in the list, which is "This stop"
        mSelectedItem = SELECTION_THIS_STOP;

        // Show the route name in title
        String routeTitle = buildRouteTitle(routeShortName, headsign);

        String title;
        if (favorite) {
            title = getString(R.string.route_favorite_options_title_star, routeTitle);
        } else {
            title = getString(R.string.route_favorite_options_title_unstar, routeTitle);
        }
        builder.setTitle(title)
                .setSingleChoiceItems(R.array.route_favorite_options, mSelectedItem,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                mSelectedItem = which;
                            }
                        })
                .setPositiveButton(R.string.stop_info_save,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int id) {
                                if (mSelectedItem == SELECTION_THIS_STOP) {
                                    Log.d(TAG, "This stop");
                                    // Saved the favorite for just this stop
                                    QueryUtils.setFavoriteRouteAndHeadsign(getActivity(),
                                            routeUri, headsign, stopId, values, favorite);
                                    mCallback.onSelectionComplete(true);
                                } else {
                                    Log.d(TAG, "All stops");
                                    // Saved the favorite for all stops by passing null as stopId
                                    QueryUtils.setFavoriteRouteAndHeadsign(getActivity(),
                                            routeUri, headsign, null, values, favorite);
                                    mCallback.onSelectionComplete(true);
                                }
                            }
                        })
                .setNegativeButton(R.string.stop_info_cancel,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int id) {
                                // Callback with false value, because nothing changed
                                mCallback.onSelectionComplete(false);
                            }
                        });
        return builder.create();
    }

    /**
     * Returns the route shortname (possibly truncated) concatenated with headsign (possibly
     * truncated) plus an ellipse
     *
     * @return the route shortname (possibly truncated) concatenated with headsign (possibly
     * truncated) plus an ellipse
     */
    private String buildRouteTitle(String routeShortName, String headsign) {
        StringBuilder routeTitle = new StringBuilder();
        if (routeShortName != null && !routeShortName.isEmpty()) {
            if (routeShortName.length() > 3) {
                routeTitle.append(routeShortName.substring(0, 3));
                routeTitle.append("...");
            } else {
                routeTitle.append(routeShortName);
            }

            routeTitle.append(" - ");
        }
        if (headsign != null && !headsign.isEmpty()) {
            if (headsign.length() > 8) {
                routeTitle.append(headsign.substring(0, 8));
                routeTitle.append("...");
            } else {
                routeTitle.append(headsign);
            }

        }
        return routeTitle.toString();
    }
}
