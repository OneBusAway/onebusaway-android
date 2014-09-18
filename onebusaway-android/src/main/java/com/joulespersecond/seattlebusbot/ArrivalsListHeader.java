/*
 * Copyright (C) 2011-2012 Paul Watts (paulcwatts@gmail.com)
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

import com.joulespersecond.oba.elements.ObaRegion;
import com.joulespersecond.seattlebusbot.util.LocationHelper;
import com.joulespersecond.seattlebusbot.util.OrientationHelper;
import com.joulespersecond.seattlebusbot.util.UIHelp;
import com.joulespersecond.view.ArrowView;
import com.joulespersecond.view.DistanceToStopView;

import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.net.Uri;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.text.style.ClickableSpan;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

//
// A helper class that gets most of the header interaction
// out of the Fragment itself.
//
class ArrivalsListHeader {

    interface Controller {

        Location getStopLocation();

        String getStopName();

        String getStopDirection();

        String getUserStopName();

        String getStopId();

        void setUserStopName(String userName);

        long getLastGoodResponseTime();

        // Returns a sorted list (by ETA) of arrival times for the current stop
        ArrayList<ArrivalInfo> getArrivalInfo();

        ArrayList<String> getRoutesFilter();

        void setRoutesFilter(ArrayList<String> filter);

        int getNumRoutes();

        boolean isFavorite();

        boolean setFavorite(boolean favorite);

        AlertList getAlertList();

        List<String> getRouteDisplayNames();

        /**
         * Gets the range of arrival info (i.e., arrival info for the next "minutesAfter" minutes),
         * or -1 if this information isn't available
         *
         * @return minutesAfter the range of arrival info (i.e., arrival info for the next
         * "minutesAfter" minutes), or -1 if this information isn't available
         */
        int getMinutesAfter();
    }

    private static final String TAG = "ArrivalsListHeader";

    private Controller mController;

    private Context mContext;

    private ArrayList<ArrivalInfo> mArrivalInfo;

    //
    // Cached views
    //
    private View mView;

    private View mNameContainerView;

    private View mEditNameContainerView;

    private TextView mNameView;

    private EditText mEditNameView;

    private ImageButton mFavoriteView;

    private TextView mRouteIdView;

    private View mRouteDirectionView;

    private View mFilterGroup;

    private boolean mInNameEdit = false;

    private ArrowView mArrowToStopView;

    private DistanceToStopView mDistanceToStopView;

    private boolean mIsSlidingPanelCollapsed = false;

    private int mShortAnimationDuration;

    private ImageView mBusStopIconView;

    private TextView mArrivalInfoView;

    private ProgressBar mProgressBar;

    private ImageButton mStopInfo;

    // Utility classes to help with managing location and orientation for the arrow/distance views
    OrientationHelper mOrientationHelper;

    LocationHelper mLocationHelper;

    ArrivalsListHeader(Context context, Controller controller) {
        mController = controller;
        mContext = context;

        // Start helpers to monitor location and orientation
        mOrientationHelper = new OrientationHelper(mContext);
        mLocationHelper = new LocationHelper(mContext);

        // Retrieve and cache the system's default "short" animation time.
        mShortAnimationDuration = mContext.getResources().getInteger(
                android.R.integer.config_shortAnimTime);
    }

    void initView(View view) {
        mView = view;
        mNameContainerView = mView.findViewById(R.id.name_container);
        mEditNameContainerView = mView.findViewById(R.id.edit_name_container);
        mNameView = (TextView) mView.findViewById(R.id.stop_name);
        mEditNameView = (EditText) mView.findViewById(R.id.edit_name);
        mFavoriteView = (ImageButton) mView.findViewById(R.id.stop_favorite);
        mRouteIdView = (TextView) mView.findViewById(R.id.routeIds);
        mRouteDirectionView = mView.findViewById(R.id.direction);
        mFilterGroup = mView.findViewById(R.id.filter_group);
        mArrowToStopView = (ArrowView) mView.findViewById(R.id.arrow);
        mBusStopIconView = (ImageView) mView.findViewById(R.id.header_bus_icon);
        mArrivalInfoView = (TextView) mView.findViewById(R.id.header_arrival_info);
        mOrientationHelper.registerListener(mArrowToStopView);
        mLocationHelper.registerListener(mArrowToStopView);
        mDistanceToStopView = (DistanceToStopView) mView.findViewById(R.id.dist_to_stop);
        mLocationHelper.registerListener(mDistanceToStopView);
        mProgressBar = (ProgressBar) mView.findViewById(R.id.header_loading_spinner);
        mStopInfo = (ImageButton) mView.findViewById(R.id.stop_info);

        // Initialize right margin view visibilities
        UIHelp.showViewWithAnimation(mProgressBar, mShortAnimationDuration);
        UIHelp.hideViewWithAnimation(mArrowToStopView, mShortAnimationDuration);
        UIHelp.hideViewWithAnimation(mDistanceToStopView, mShortAnimationDuration);
        UIHelp.hideViewWithAnimation(mArrivalInfoView, mShortAnimationDuration);
        UIHelp.hideViewWithAnimation(mBusStopIconView, mShortAnimationDuration);

        // Initialize stop info view
        final ObaRegion obaRegion = Application.get().getCurrentRegion();

        if (obaRegion == null || TextUtils.isEmpty(obaRegion.getStopInfoUrl())) {
            // This region doesn't support StopInfo - hide the info icon
            mStopInfo.setVisibility(View.GONE);
        } else {
            mStopInfo.setVisibility(View.VISIBLE);

            mStopInfo.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    // Assemble StopInfo URL for the current stop
                    Uri stopInfoUri = Uri.parse(obaRegion.getStopInfoUrl());
                    Uri.Builder stopInfoBuilder = stopInfoUri.buildUpon();
                    stopInfoBuilder.appendPath(mContext.getString(R.string.stop_info_url_path));
                    stopInfoBuilder.appendPath(mController.getStopId());

                    Log.d(TAG, "StopInfoUrl - " + stopInfoBuilder.build());

                    Intent i = new Intent(Intent.ACTION_VIEW);
                    i.setData(stopInfoBuilder.build());
                    mContext.startActivity(i);
                }
            });
        }

        mFavoriteView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mController.setFavorite(!mController.isFavorite());
                refreshFavorite();
            }
        });

        mNameView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                beginNameEdit(null);
            }
        });

        // Implement the "Save" and "Clear" buttons
        View save = mView.findViewById(R.id.edit_name_save);
        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mController.setUserStopName(mEditNameView.getText().toString());
                endNameEdit();
            }
        });

        mEditNameView.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    mController.setUserStopName(mEditNameView.getText().toString());
                    endNameEdit();
                    return true;
                }
                return false;
            }
        });

        // "Cancel"
        View cancel = mView.findViewById(R.id.edit_name_cancel);
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                endNameEdit();
            }
        });

        View clear = mView.findViewById(R.id.edit_name_revert);
        clear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mController.setUserStopName(null);
                endNameEdit();
            }
        });
        UIHelp.setChildClickable(mView, R.id.show_all, mShowAllClick);
    }

    /**
     * Should be called from onResume() of context hosting this header
     */
    public void onResume() {
        // Resume monitoring of sensors and location
        mOrientationHelper.onResume();
        mLocationHelper.onResume();
    }

    /**
     * Should be called from onPause() of context hosting this header
     */
    public void onPause() {
        // Pause monitoring of sensors and location
        mOrientationHelper.onPause();
        mLocationHelper.onPause();
    }

    public void setSlidingPanelCollapsed(boolean collapsed) {
        mIsSlidingPanelCollapsed = collapsed;
    }

    private final ClickableSpan mShowAllClick = new ClickableSpan() {
        public void onClick(View v) {
            mController.setRoutesFilter(new ArrayList<String>());
            refreshFilter();
        }
    };

    void refresh() {
        refreshName();
        refreshArrivalInfo();  // Needs to occur before refreshRouteDisplayNames(), so route name for next arrival is highlighted
        refreshRouteDisplayNames();
        refreshDirection();
        refreshLocation();
        refreshFavorite();
        refreshFilter();
        refreshError();
        if (mArrivalInfo != null) {
            // If we have arrival info, we can show the right margin contents
            showRightMarginContainer();
        }
    }

    private void refreshName() {
        String name = mController.getStopName();
        String userName = mController.getUserStopName();

        if (!TextUtils.isEmpty(userName)) {
            mNameView.setText(userName);
        } else if (name != null) {
            mNameView.setText(name);
        }
    }

    /**
     * Retrieves a sorted list of arrival times for the current stop
     */
    private void refreshArrivalInfo() {
        mArrivalInfo = mController.getArrivalInfo();

        StringBuilder arrivalInfo = new StringBuilder();

        if (mController.getRouteDisplayNames().size() > 1) {
            // More than one route will be displayed, so add highlight
            arrivalInfo.append("* ");
        }

        if (mArrivalInfo != null) {
            if (mArrivalInfo.size() > 0) {
                // We have arrival info for at least one bus
                long eta = mArrivalInfo.get(0).getEta();
                if (eta == 0) {
                    arrivalInfo.append(mContext.getString(R.string.stop_info_eta_now));
                    mArrivalInfoView.setText(arrivalInfo);
                } else if (eta > 0) {
                    mArrivalInfoView
                            .setText(mContext.getString(R.string.stop_info_header_arrival_info,
                                    eta));
                } else if (eta < 0) {
                    arrivalInfo.append(mContext.getString(R.string.stop_info_header_just_left));
                    mArrivalInfoView.setText(arrivalInfo);
                }
            } else if (mArrivalInfo.size() == 0) {
                // Show abbreviated "no upcoming arrivals" message (e.g., "35+ min")
                int minAfter = mController.getMinutesAfter();
                if (minAfter != -1) {
                    mArrivalInfoView
                            .setText(UIHelp.getNoArrivalsMessage(mContext, minAfter, false, true));
                } else {
                    // If we don't have the precise minAfter value, show a generic message
                    mArrivalInfoView.setText(mContext.getString(R.string.stop_info_header_later));
                }
            }
        }
    }

    private void refreshRouteDisplayNames() {
        List<String> routeDisplayNames = mController.getRouteDisplayNames();
        ArrayList<String> nextArrivalRouteShortNames = new ArrayList<String>();

        if (mArrivalInfo != null && mArrivalInfo.size() > 0 && routeDisplayNames.size() > 1
                && mIsSlidingPanelCollapsed) {
            // Only highlight routes if there is more than one route and the sliding panel is collapsed.
            // Always highlight the route for the next ETA
            String firstArrivalShortName = mArrivalInfo.get(0).getInfo().getShortName();
            long firstEta = mArrivalInfo.get(0).getEta();
            nextArrivalRouteShortNames.add(firstArrivalShortName);

            // Add the routes for the next X sequential arrivals that have the same eta status
            // (e.g., "just left", "NOW", "1 min") so we highlight more than one route in the header
            for (int i = 1; i < mArrivalInfo.size(); i++) {
                if (mArrivalInfo.get(i).getEta() < 0 && firstEta < 0) {
                    // All arrival times less than 0 are grouped into the same "Just left" message,
                    // so add any sequential routes that also have negative ETAs
                    nextArrivalRouteShortNames.add(mArrivalInfo.get(i).getInfo().getShortName());
                } else if (mArrivalInfo.get(i).getEta() == firstEta) {
                    // All ETA == first ETA can also be highlighted
                    nextArrivalRouteShortNames.add(mArrivalInfo.get(i).getInfo().getShortName());
                } else {
                    // No match, so break entirely, since no
                    break;
                }
            }
        }

        if (routeDisplayNames != null) {
            mRouteIdView.setText(
                    mContext.getString(R.string.stop_info_route_ids_label) + " " + UIHelp
                            .formatRouteDisplayNames(routeDisplayNames,
                                    nextArrivalRouteShortNames));
            mRouteIdView.setVisibility(View.VISIBLE);
        } else {
            mRouteIdView.setVisibility(View.GONE);
        }
    }

    private void refreshDirection() {
        String direction = mController.getStopDirection();
        if (direction != null) {
            final int directionText = UIHelp.getStopDirectionText(direction);
            ((TextView) mRouteDirectionView).setText(directionText);
            if (directionText != R.string.direction_none && !mInNameEdit) {
                mRouteDirectionView.setVisibility(View.VISIBLE);
            } else {
                mRouteDirectionView.setVisibility(View.INVISIBLE);
            }
        }
    }

    private void refreshLocation() {
        Location location = mController.getStopLocation();
        if (location != null) {
            mArrowToStopView.setStopLocation(location);
            mDistanceToStopView.setStopLocation(location);
        }
    }

    private void refreshFavorite() {
        mFavoriteView.setImageResource(mController.isFavorite() ?
                R.drawable.focus_star_on :
                R.drawable.focus_star_off);
    }

    private void refreshFilter() {
        TextView v = (TextView) mView.findViewById(R.id.filter);
        ArrayList<String> routesFilter = mController.getRoutesFilter();
        final int num = (routesFilter != null) ? routesFilter.size() : 0;
        if (num > 0) {
            final int total = mController.getNumRoutes();
            v.setText(mContext.getString(R.string.stop_info_filter_header, num, total));
            // Show the filter text (except when in name edit mode)
            mFilterGroup.setVisibility(mInNameEdit ? View.GONE : View.VISIBLE);
        } else {
            mFilterGroup.setVisibility(View.GONE);
        }
    }

    /**
     * Hiding the loading component, and replaces it with the correct content depending on sliding
     * panel state
     */
    private void showRightMarginContainer() {
        if (mIsSlidingPanelCollapsed) {
            // Cross-fade in bus icon and arrival info, and hide direction arrow and distance to stop
            UIHelp.hideViewWithAnimation(mArrowToStopView, mShortAnimationDuration);
            UIHelp.hideViewWithAnimation(mDistanceToStopView, mShortAnimationDuration);
            UIHelp.showViewWithAnimation(mArrivalInfoView, mShortAnimationDuration);
            UIHelp.showViewWithAnimation(mBusStopIconView, mShortAnimationDuration);
        } else {
            // Cross-fade in direction arrow and distance to stop, and hide bus icon and arrival info
            UIHelp.hideViewWithAnimation(mArrivalInfoView, mShortAnimationDuration);
            UIHelp.hideViewWithAnimation(mBusStopIconView, mShortAnimationDuration);
            UIHelp.showViewWithAnimation(mArrowToStopView, mShortAnimationDuration);
            UIHelp.showViewWithAnimation(mDistanceToStopView, mShortAnimationDuration);
        }

        // Hide progress bar
        UIHelp.hideViewWithAnimation(mProgressBar, mShortAnimationDuration);
    }

    private static class ResponseError implements AlertList.Alert {

        private final CharSequence mString;

        ResponseError(CharSequence seq) {
            mString = seq;
        }

        @Override
        public String getId() {
            return "STATIC: RESPONSE ERROR";
        }

        @Override
        public int getType() {
            return AlertList.Alert.TYPE_ERROR;
        }

        @Override
        public int getFlags() {
            return 0;
        }

        @Override
        public CharSequence getString() {
            return mString;
        }

        @Override
        public void onClick() {
        }

        @Override
        public int hashCode() {
            return getId().hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            ResponseError other = (ResponseError) obj;
            if (!getId().equals(other.getId())) {
                return false;
            }
            return true;
        }
    }

    private ResponseError mResponseError = null;

    private void refreshError() {
        final long now = System.currentTimeMillis();
        final long responseTime = mController.getLastGoodResponseTime();
        AlertList alerts = mController.getAlertList();

        if (mResponseError != null) {
            alerts.remove(mResponseError);
        }

        if ((responseTime) != 0 &&
                ((now - responseTime) >= 2 * DateUtils.MINUTE_IN_MILLIS)) {
            CharSequence relativeTime =
                    DateUtils.getRelativeTimeSpanString(responseTime,
                            now,
                            DateUtils.MINUTE_IN_MILLIS,
                            0);
            CharSequence s = mContext.getString(R.string.stop_info_old_data,
                    relativeTime);
            mResponseError = new ResponseError(s);
            alerts.insert(mResponseError, 0);
        }
    }

    void beginNameEdit(String initial) {
        // If we can click on this, then we're definitely not
        // editable, so we should go into edit mode.
        mEditNameView.setText((initial != null) ? initial : mNameView.getText());
        mNameContainerView.setVisibility(View.GONE);
        mRouteDirectionView.setVisibility(View.GONE);
        mFilterGroup.setVisibility(View.GONE);
        mEditNameContainerView.setVisibility(View.VISIBLE);
        mFavoriteView.setVisibility(View.GONE);
        // TODO - Re-size the header layout to show the edit buttons
//        mView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
//                ViewGroup.LayoutParams.WRAP_CONTENT));
        mEditNameView.requestFocus();
        mInNameEdit = true;
        // TODO: Ensure the soft keyboard is up
    }

    void endNameEdit() {
        mInNameEdit = false;
        mNameContainerView.setVisibility(View.VISIBLE);
        mEditNameContainerView.setVisibility(View.GONE);
        mRouteDirectionView.setVisibility(View.VISIBLE);
        mFavoriteView.setVisibility(View.VISIBLE);
        // TODO - Re-size the header layout back to 68dp
//        mView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
//                ViewGroup.LayoutParams.WRAP_CONTENT));
        //setFilterHeader();
        InputMethodManager imm =
                (InputMethodManager) mContext.getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(mEditNameView.getWindowToken(), 0);
        refreshName();
    }
}
