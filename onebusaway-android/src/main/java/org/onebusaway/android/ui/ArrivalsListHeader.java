/*
 * Copyright (C) 2011-2014 Paul Watts (paulcwatts@gmail.com),
 * University of South Florida (sjbarbeau@gmail.com)
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
import org.onebusaway.android.io.elements.ObaArrivalInfo;
import org.onebusaway.android.io.elements.ObaRegion;
import org.onebusaway.android.provider.ObaContract;
import org.onebusaway.android.util.UIHelp;

import android.annotation.TargetApi;
import android.content.ContentQueryMap;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.net.Uri;
import android.os.Build;
import android.support.v4.app.FragmentManager;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.text.style.ClickableSpan;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.RotateAnimation;
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

        boolean isFavoriteStop();

        boolean setFavoriteStop(boolean favorite);

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

        /**
         * Shows the list item menu for the given view and arrival info
         *
         * @param v    view for the container of the arrival info
         * @param stop arrival info to show a list for
         */
        void showListItemMenu(View v, final ArrivalInfo stop);

        /**
         * Triggers a local refresh of the controller content (i.e., does not trigger another
         * call to the server)
         */
        void refreshLocal();
    }

    private static final String TAG = "ArrivalsListHeader";

    private Controller mController;

    private Context mContext;

    private FragmentManager mFragmentManager;

    //
    // Cached views
    //
    private View mView;  // Entire header parent view

    private View mMainContainerView; // Holds everything except for the mFilterGroup

    private View mNameContainerView;

    private View mEditNameContainerView;

    private TextView mNameView;

    private EditText mEditNameView;

    private ImageButton mStopFavorite;

    private View mFilterGroup;

    private TextView mShowAllView;

    private ClickableSpan mShowAllClick;

    private boolean mInNameEdit = false;

    // Default state for the header is inside a sliding fragment
    private boolean mIsSlidingPanelCollapsed = true;

    private int mShortAnimationDuration;

    private ProgressBar mProgressBar;

    private ImageButton mStopInfo;

    private ImageView mExpandCollapse;

    // All arrival info returned by the adapter
    private ArrayList<ArrivalInfo> mArrivalInfo;

    // Arrival info for the two rows of arrival info in the header
    private ArrayList<ArrivalInfo> mHeaderArrivalInfo = new ArrayList<>(2);

    // Trip (e.g., reminder) content for this stop
    protected ContentQueryMap mTripsForStop;

    /**
     * Views to show ETA information in header
     */
    // Number of arrival current shown in the header - needed for various header view sizing.
    int mNumHeaderArrivals = -1;  // -1 if mArrivalInfo hasn't been refreshed

    private TextView mNoArrivals;

    private final float ETA_TEXT_SIZE_SP = 30;

    private final float ETA_TEXT_NOW_SIZE_SP = 28;

    // Row 1
    private View mEtaContainer1;

    private ImageButton mEtaRouteFavorite1;

    private ImageButton mEtaReminder1;

    private TextView mEtaRouteName1;

    private TextView mEtaRouteDirection1;

    private TextView mEtaArrivalInfo1;

    private TextView mEtaMin1;

    private View mEtaRealtime1;

    private ImageButton mEtaMoreVert1;

    private View mEtaSeparator;

    // Row 2
    private View mEtaContainer2;

    private ImageButton mEtaRouteFavorite2;

    private ImageButton mEtaReminder2;

    private TextView mEtaRouteName2;

    private TextView mEtaRouteDirection2;

    private TextView mEtaArrivalInfo2;

    private TextView mEtaMin2;

    private View mEtaRealtime2;

    private ImageButton mEtaMoreVert2;

    // Animations
    private static final float ANIM_PIVOT_VALUE = 0.5f;  // 50%

    private static final float ANIM_STATE_NORMAL = 0.0f;  // 0 degrees (no rotation)

    private static final float ANIM_STATE_INVERTED = 180.0f;  // 180 degrees

    private static final long ANIM_DURATION = 300;  // milliseconds

    // Manages header size in "stop name edit mode"
    private int cachedExpandCollapseViewVisibility;

    private static float HEADER_HEIGHT_ONE_ARRIVAL_DP;

    private static float HEADER_HEIGHT_TWO_ARRIVALS_DP;

    private static float HEADER_HEIGHT_EDIT_NAME_DP;

    private static float HEADER_OFFSET_FILTER_ROUTES_DP;

    // Controller to change parent sliding panel
    HomeActivity.SlidingPanelController mSlidingPanelController;

    ArrivalsListHeader(Context context, Controller controller, FragmentManager fm) {
        mController = controller;
        mContext = context;
        mFragmentManager = fm;

        // Retrieve and cache the system's default "short" animation time.
        mShortAnimationDuration = mContext.getResources().getInteger(
                android.R.integer.config_shortAnimTime);
    }

    void initView(View view) {
        // Clear any existing arrival info
        mArrivalInfo = null;
        mHeaderArrivalInfo.clear();
        mNumHeaderArrivals = -1;

        // Cache the ArrivalsListHeader height values
        HEADER_HEIGHT_ONE_ARRIVAL_DP =
                view.getResources().getDimension(R.dimen.arrival_header_height_one_arrival)
                / view.getResources().getDisplayMetrics().density;
        HEADER_HEIGHT_TWO_ARRIVALS_DP =
                view.getResources().getDimension(R.dimen.arrival_header_height_two_arrivals)
                        / view.getResources().getDisplayMetrics().density;
        HEADER_OFFSET_FILTER_ROUTES_DP = view.getResources()
                .getDimension(R.dimen.arrival_header_height_offset_filter_routes)
                / view.getResources().getDisplayMetrics().density;
        HEADER_HEIGHT_EDIT_NAME_DP =
                view.getResources().getDimension(R.dimen.arrival_header_height_edit_name)
                        / view.getResources().getDisplayMetrics().density;

        // Init views
        mView = view;
        mMainContainerView = mView.findViewById(R.id.main_header_content);
        mNameContainerView = mView.findViewById(R.id.stop_name_and_info_container);
        mEditNameContainerView = mView.findViewById(R.id.edit_name_container);
        mNameView = (TextView) mView.findViewById(R.id.stop_name);
        mEditNameView = (EditText) mView.findViewById(R.id.edit_name);
        mStopFavorite = (ImageButton) mView.findViewById(R.id.stop_favorite);
        mStopFavorite.setColorFilter(mView.getResources().getColor(R.color.header_text_color));
        mFilterGroup = mView.findViewById(R.id.filter_group);

        mShowAllView = (TextView) mView.findViewById(R.id.show_all);
        // Remove any previous clickable spans - we're recycling views between fragments for efficiency
        UIHelp.removeAllClickableSpans(mShowAllView);
        mShowAllClick = new ClickableSpan() {
            public void onClick(View v) {
                mController.setRoutesFilter(new ArrayList<String>());
            }
        };
        UIHelp.setClickableSpan(mShowAllView, mShowAllClick);

        mNoArrivals = (TextView) mView.findViewById(R.id.no_arrivals);

        // First ETA row
        mEtaContainer1 = mView.findViewById(R.id.eta_container1);
        mEtaRouteFavorite1 = (ImageButton) mEtaContainer1.findViewById(R.id.eta_route_favorite);
        mEtaRouteFavorite1.setColorFilter(mView.getResources().getColor(R.color.header_text_color));
        mEtaReminder1 = (ImageButton) mEtaContainer1.findViewById(R.id.reminder);
        mEtaReminder1.setColorFilter(mView.getResources().getColor(R.color.header_text_color));
        mEtaRouteName1 = (TextView) mEtaContainer1.findViewById(R.id.eta_route_name);
        mEtaRouteDirection1 = (TextView) mEtaContainer1.findViewById(R.id.eta_route_direction);
        mEtaArrivalInfo1 = (TextView) mEtaContainer1.findViewById(R.id.eta);
        mEtaMin1 = (TextView) mEtaContainer1.findViewById(R.id.eta_min);
        mEtaRealtime1 = mEtaContainer1.findViewById(R.id.eta_realtime_indicator);
        mEtaMoreVert1 = (ImageButton) mEtaContainer1.findViewById(R.id.eta_more_vert);
        mEtaMoreVert1.setColorFilter(mView.getResources().getColor(R.color.header_text_color));

        mEtaSeparator = mView.findViewById(R.id.eta_separator);

        // Second ETA row
        mEtaContainer2 = mView.findViewById(R.id.eta_container2);
        mEtaRouteFavorite2 = (ImageButton) mEtaContainer2.findViewById(R.id.eta_route_favorite);
        mEtaRouteFavorite2.setColorFilter(mView.getResources().getColor(R.color.header_text_color));
        mEtaReminder2 = (ImageButton) mEtaContainer2.findViewById(R.id.reminder);
        mEtaReminder2.setColorFilter(mView.getResources().getColor(R.color.header_text_color));
        mEtaRouteName2 = (TextView) mEtaContainer2.findViewById(R.id.eta_route_name);
        mEtaRouteDirection2 = (TextView) mEtaContainer2.findViewById(R.id.eta_route_direction);
        mEtaArrivalInfo2 = (TextView) mEtaContainer2.findViewById(R.id.eta);
        mEtaMin2 = (TextView) mEtaContainer2.findViewById(R.id.eta_min);
        mEtaRealtime2 = mEtaContainer2.findViewById(R.id.eta_realtime_indicator);
        mEtaMoreVert2 = (ImageButton) mEtaContainer2.findViewById(R.id.eta_more_vert);
        mEtaMoreVert2.setColorFilter(mView.getResources().getColor(R.color.header_text_color));

        mProgressBar = (ProgressBar) mView.findViewById(R.id.header_loading_spinner);
        mStopInfo = (ImageButton) mView.findViewById(R.id.stop_info_button);
        mExpandCollapse = (ImageView) mView.findViewById(R.id.expand_collapse);
        resetExpandCollapseAnimation();

        // Initialize right margin view visibilities
        UIHelp.showViewWithAnimation(mProgressBar, mShortAnimationDuration);

        UIHelp.hideViewWithAnimation(mEtaContainer1, mShortAnimationDuration);
        UIHelp.hideViewWithAnimation(mEtaSeparator, mShortAnimationDuration);
        UIHelp.hideViewWithAnimation(mEtaContainer2, mShortAnimationDuration);

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
                    //Analytics
                    if (obaRegion != null && obaRegion.getName() != null)
                        ObaAnalytics.reportEventWithCategory(ObaAnalytics.ObaEventCategory.UI_ACTION.toString(),
                                mContext.getString(R.string.analytics_action_button_press),
                                mContext.getString(R.string.analytics_label_button_press_stopinfo) + obaRegion.getName());
                }
            });
        }

        mStopFavorite.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mController.setFavoriteStop(!mController.isFavoriteStop());
                refreshStopFavorite();
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
    }

    /**
     * Used to give the header control over the containing sliding panel, primarily to change the
     * panel header height
     */
    public void setSlidingPanelController(HomeActivity.SlidingPanelController controller) {
        mSlidingPanelController = controller;
    }

    /**
     * Should be called from onPause() of context hosting this header
     */
    public void onPause() {
        // If we're editing a stop name, close out the editing session
        if (mInNameEdit) {
            mController.setUserStopName(null);
            endNameEdit();
        }
    }

    synchronized public void setSlidingPanelCollapsed(boolean collapsed) {
        // If the state has changed and image is initialized, then rotate the expand/collapse image
        if (mExpandCollapse != null && collapsed != mIsSlidingPanelCollapsed) {
            // Update value
            mIsSlidingPanelCollapsed = collapsed;

            doExpandCollapseRotation(collapsed);
        }
    }

    /**
     * Sets the trip details (e.g., reminders) for this stop
     */
    public void setTripsForStop(ContentQueryMap tripsForStop) {
        mTripsForStop = tripsForStop;
        refreshArrivalInfoVisibilityAndListeners();
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB_MR1)
    private void doExpandCollapseRotation(boolean collapsed) {
        if (!UIHelp.canAnimateViewModern()) {
            rotateExpandCollapseImageViewLegacy(collapsed);
            return;
        }
        if (!collapsed) {
            // Rotate clockwise
            mExpandCollapse.animate()
                    .setDuration(ANIM_DURATION)
                    .rotationBy(ANIM_STATE_INVERTED);
        } else {
            // Rotate counter-clockwise
            mExpandCollapse.animate()
                    .setDuration(ANIM_DURATION)
                    .rotationBy(-ANIM_STATE_INVERTED);
        }
    }

    private void rotateExpandCollapseImageViewLegacy(boolean isSlidingPanelCollapsed) {
        RotateAnimation rotate;

        if (!isSlidingPanelCollapsed) {
            // Rotate clockwise
            rotate = getRotation(ANIM_STATE_NORMAL, ANIM_STATE_INVERTED);
        } else {
            // Rotate counter-clockwise
            rotate = getRotation(ANIM_STATE_INVERTED, ANIM_STATE_NORMAL);
        }

        mExpandCollapse.setAnimation(rotate);
    }

    /**
     * Resets the animation that has been applied to the expand/collapse indicator image
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB_MR1)
    private synchronized void resetExpandCollapseAnimation() {
        if (mExpandCollapse == null) {
            return;
        }
        if (UIHelp.canAnimateViewModern()) {
            if (mExpandCollapse.getRotation() != 0) {
                mExpandCollapse.setRotation(0);
            }
        } else {
            mExpandCollapse.clearAnimation();
        }
    }

    /**
     * Allows external classes to set whether or not the expand/collapse indicator should be
     * shown (e.g., if the header is not in a sliding window, we don't want to show it)
     *
     * @param value true if the expand/collapse indicator should be shown, false if it should not
     */
    public void showExpandCollapseIndicator(boolean value) {
        if (mExpandCollapse != null) {
            if (value) {
                mExpandCollapse.setVisibility(View.VISIBLE);
            } else {
                mExpandCollapse.setVisibility(View.GONE);
            }
        }
    }

    /**
     * Creates and returns a new rotation animation for the expand/collapse image based on the
     * provided startState and endState.
     *
     * @param startState beginning state of the image, either ANIM_STATE_NORMAL or
     *                   ANIM_STATE_INVERTED
     * @param endState   end state of the image, either ANIM_STATE_NORMAL or ANIM_STATE_INVERTED
     * @return a new rotation animation for the expand/collapse image
     */
    private static RotateAnimation getRotation(float startState, float endState) {
        RotateAnimation r = new RotateAnimation(startState, endState,
                Animation.RELATIVE_TO_SELF, ANIM_PIVOT_VALUE,
                Animation.RELATIVE_TO_SELF, ANIM_PIVOT_VALUE);
        r.setDuration(ANIM_DURATION);
        r.setFillAfter(true);
        return r;
    }

    synchronized void refresh() {
        refreshName();
        refreshArrivalInfoData();
        refreshStopFavorite();
        refreshFilter();
        refreshError();
        refreshArrivalInfoVisibilityAndListeners();
        refreshHeaderSize();
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
     * Refreshes the arrival info data to be displayed in the header based on the most recent
     * arrival info, and sets the number of arrival rows that should be displayed in the header
     */
    private void refreshArrivalInfoData() {
        mArrivalInfo = mController.getArrivalInfo();
        mHeaderArrivalInfo.clear();

        if (mArrivalInfo != null && !mInNameEdit) {
            // Get the indexes for arrival times that should be featured in the header
            ArrayList<Integer> etaIndexes = ArrivalInfo.findPreferredArrivalIndexes(mArrivalInfo);
            if (etaIndexes != null) {
                // We have a non-negative ETA for at least one bus - fill the first arrival row
                int indexFirstEta = etaIndexes.get(0);
                boolean isFavorite = ObaContract.RouteHeadsignFavorites.isFavorite(mContext,
                        mArrivalInfo.get(indexFirstEta).getInfo().getRouteId(),
                        mArrivalInfo.get(indexFirstEta).getInfo().getHeadsign(),
                        mArrivalInfo.get(indexFirstEta).getInfo().getStopId());
                mEtaRouteFavorite1.setImageResource(isFavorite ?
                        R.drawable.focus_star_on :
                        R.drawable.focus_star_off);

                mEtaRouteName1.setText(mArrivalInfo.get(indexFirstEta).getInfo().getShortName());
                mEtaRouteDirection1
                        .setText(mArrivalInfo.get(indexFirstEta).getInfo().getHeadsign());
                long eta = mArrivalInfo.get(indexFirstEta).getEta();
                if (eta == 0) {
                    mEtaArrivalInfo1.setText(mContext.getString(R.string.stop_info_eta_now));
                    mEtaArrivalInfo1.setTextSize(ETA_TEXT_NOW_SIZE_SP);
                    UIHelp.hideViewWithAnimation(mEtaMin1, mShortAnimationDuration);
                } else if (eta > 0) {
                    mEtaArrivalInfo1.setText(Long.toString(eta));
                    mEtaArrivalInfo1.setTextSize(ETA_TEXT_SIZE_SP);
                    UIHelp.showViewWithAnimation(mEtaMin1, mShortAnimationDuration);
                }

                if (mArrivalInfo.get(indexFirstEta).getPredicted()) {
                    // We have real-time data - show the indicator
                    UIHelp.showViewWithAnimation(mEtaRealtime1, mShortAnimationDuration);
                } else {
                    // We only have schedule data - hide the indicator
                    UIHelp.hideViewWithAnimation(mEtaRealtime1, mShortAnimationDuration);
                }
                // Save the arrival info for the options menu later
                mHeaderArrivalInfo.add(mArrivalInfo.get(indexFirstEta));

                // If there is another arrival, fill the second row with it
                if (etaIndexes.size() >= 2) {
                    int indexSecondEta = etaIndexes.get(1);
                    boolean isFavorite2 = ObaContract.RouteHeadsignFavorites.isFavorite(mContext,
                            mArrivalInfo.get(indexSecondEta).getInfo().getRouteId(),
                            mArrivalInfo.get(indexSecondEta).getInfo().getHeadsign(),
                            mArrivalInfo.get(indexSecondEta).getInfo().getStopId());
                    mEtaRouteFavorite2.setImageResource(isFavorite2 ?
                            R.drawable.focus_star_on :
                            R.drawable.focus_star_off);
                    mEtaRouteName2
                            .setText(mArrivalInfo.get(indexSecondEta).getInfo().getShortName());
                    mEtaRouteDirection2
                            .setText(mArrivalInfo.get(indexSecondEta).getInfo().getHeadsign());
                    eta = mArrivalInfo.get(indexSecondEta).getEta();

                    if (eta == 0) {
                        mEtaArrivalInfo2.setText(mContext.getString(R.string.stop_info_eta_now));
                        mEtaArrivalInfo2.setTextSize(ETA_TEXT_NOW_SIZE_SP);
                        UIHelp.hideViewWithAnimation(mEtaMin2, mShortAnimationDuration);
                    } else if (eta > 0) {
                        mEtaArrivalInfo2.setText(Long.toString(eta));
                        mEtaArrivalInfo2.setTextSize(ETA_TEXT_SIZE_SP);
                        UIHelp.showViewWithAnimation(mEtaMin2, mShortAnimationDuration);
                    }

                    if (mArrivalInfo.get(indexSecondEta).getPredicted()) {
                        // We have real-time data - show the indicator
                        UIHelp.showViewWithAnimation(mEtaRealtime2, mShortAnimationDuration);
                    } else {
                        // We only have schedule data - hide the indicator
                        UIHelp.hideViewWithAnimation(mEtaRealtime2, mShortAnimationDuration);
                    }

                    mNumHeaderArrivals = 2;

                    // Save the arrival info for the options menu later
                    mHeaderArrivalInfo.add(mArrivalInfo.get(indexSecondEta));
                } else {
                    mNumHeaderArrivals = 1;
                }
            } else {
                // Show abbreviated "no upcoming arrivals" message (e.g., "35+ min")
                int minAfter = mController.getMinutesAfter();
                if (minAfter != -1) {
                    mNoArrivals
                            .setText(UIHelp.getNoArrivalsMessage(mContext, minAfter, false, false));
                } else {
                    minAfter = 35;  // Assume 35 minutes, because that's the API default
                    mNoArrivals
                            .setText(UIHelp.getNoArrivalsMessage(mContext, minAfter, false, false));
                }
                mNumHeaderArrivals = 0;
            }
        }
    }

    private void refreshStopFavorite() {
        mStopFavorite.setImageResource(mController.isFavoriteStop() ?
                R.drawable.focus_star_on :
                R.drawable.focus_star_off);
    }

    /**
     * Refreshes the routes filter, and displays/hides it if necessary
     */
    private void refreshFilter() {
        TextView v = (TextView) mView.findViewById(R.id.filter_text);
        ArrayList<String> routesFilter = mController.getRoutesFilter();
        final int num = (routesFilter != null) ? routesFilter.size() : 0;
        if (num > 0) {
            final int total = mController.getNumRoutes();
            v.setText(mContext.getString(R.string.stop_info_filter_header, num, total));
            // Show the filter text (except when in name edit mode)
            if (mInNameEdit) {
                mFilterGroup.setVisibility(View.GONE);
            } else {
                mFilterGroup.setVisibility(View.VISIBLE);
            }
        } else {
            mFilterGroup.setVisibility(View.GONE);
        }
    }

    /**
     * Returns true if we're filtering routes from the user's view, or false if we're not
     *
     * @return true if we're filtering routes from the user's view, or false if we're not
     */
    private boolean isFilteringRoutes() {
        ArrayList<String> routesFilter = mController.getRoutesFilter();
        final int num = (routesFilter != null) ? routesFilter.size() : 0;
        if (num > 0) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * Hides the progress bar, and replaces it with the correct content depending on sliding
     * panel state and arrival info
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private void refreshArrivalInfoVisibilityAndListeners() {
        if (mInNameEdit) {
            // If the user is editing a stop name, we shouldn't show any of these views
            return;
        }
        if (mArrivalInfo == null) {
            // We don't have any arrival info yet, so make sure the progress bar is running
            UIHelp.showViewWithAnimation(mProgressBar, mShortAnimationDuration);
            // Hide all the arrival views
            UIHelp.hideViewWithAnimation(mNoArrivals, mShortAnimationDuration);
            UIHelp.hideViewWithAnimation(mEtaContainer1, mShortAnimationDuration);
            UIHelp.hideViewWithAnimation(mEtaSeparator, mShortAnimationDuration);
            UIHelp.hideViewWithAnimation(mEtaContainer2, mShortAnimationDuration);
            return;
        }

        if (mNumHeaderArrivals == 0) {
            // "No routes" message should be shown
            UIHelp.showViewWithAnimation(mNoArrivals, mShortAnimationDuration);
            // Hide all others
            UIHelp.hideViewWithAnimation(mEtaContainer1, mShortAnimationDuration);
            UIHelp.hideViewWithAnimation(mEtaSeparator, mShortAnimationDuration);
            UIHelp.hideViewWithAnimation(mEtaContainer2, mShortAnimationDuration);
        }

        // Show at least the first row of arrival info, if we have one or more records
        if (mNumHeaderArrivals >= 1) {
            // Show the first row of arrival info
            UIHelp.showViewWithAnimation(mEtaContainer1, mShortAnimationDuration);
            // Hide no arrivals
            UIHelp.hideViewWithAnimation(mNoArrivals, mShortAnimationDuration);

            // Setup tapping on star for first row
            final ObaArrivalInfo info1 = mHeaderArrivalInfo.get(0).getInfo();
            final Uri routeUri = Uri.withAppendedPath(ObaContract.Routes.CONTENT_URI,
                    info1.getRouteId());
            final boolean isRouteFavorite = ObaContract.RouteHeadsignFavorites.isFavorite(mContext,
                    info1.getRouteId(), info1.getHeadsign(), info1.getStopId());
            mEtaRouteFavorite1.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    // Show dialog for setting route favorite
                    RouteFavoriteDialogFragment dialog = new RouteFavoriteDialogFragment.Builder(
                            info1.getRouteId(), info1.getHeadsign())
                            .setRouteShortName(info1.getShortName())
                            .setRouteLongName(info1.getRouteLongName())
                            .setStopId(info1.getStopId())
                            .setFavorite(!isRouteFavorite)
                            .build();

                    dialog.setCallback(new RouteFavoriteDialogFragment.Callback() {
                        @Override
                        public void onSelectionComplete(boolean savedFavorite) {
                            if (savedFavorite) {
                                mController.refreshLocal();
                            }
                        }
                    });
                    dialog.show(mFragmentManager, RouteFavoriteDialogFragment.TAG);

                }
            });

            // Setup "more" button click for first row
            mEtaMoreVert1.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mController.showListItemMenu(mEtaContainer1, mHeaderArrivalInfo.get(0));
                }
            });

            // Show or hide reminder for first row
            View r = mEtaContainer1.findViewById(R.id.reminder);
            refreshReminder(mHeaderArrivalInfo.get(0).getInfo().getTripId(), r);
        }

        if (mNumHeaderArrivals >= 2) {
            // Also show the 2nd row of arrival info
            UIHelp.showViewWithAnimation(mEtaSeparator, mShortAnimationDuration);
            UIHelp.showViewWithAnimation(mEtaContainer2, mShortAnimationDuration);

            // Setup tapping on star for second row
            final ObaArrivalInfo info2 = mHeaderArrivalInfo.get(1).getInfo();

            final Uri routeUri2 = Uri.withAppendedPath(ObaContract.Routes.CONTENT_URI,
                    info2.getRouteId());
            final boolean isRouteFavorite2 = ObaContract.RouteHeadsignFavorites.isFavorite(mContext,
                    info2.getRouteId(), info2.getHeadsign(), info2.getStopId());
            mEtaRouteFavorite2.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    // Show dialog for setting route favorite
                    RouteFavoriteDialogFragment dialog = new RouteFavoriteDialogFragment.Builder(
                            info2.getRouteId(), info2.getHeadsign())
                            .setRouteShortName(info2.getShortName())
                            .setRouteLongName(info2.getRouteLongName())
                            .setStopId(info2.getStopId())
                            .setFavorite(!isRouteFavorite2)
                            .build();

                    dialog.setCallback(new RouteFavoriteDialogFragment.Callback() {
                        @Override
                        public void onSelectionComplete(boolean savedFavorite) {
                            if (savedFavorite) {
                                mController.refreshLocal();
                            }
                        }
                    });
                    dialog.show(mFragmentManager, RouteFavoriteDialogFragment.TAG);
                }
            });

            // Setup "more" button click for second row
            mEtaMoreVert2.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mController.showListItemMenu(mEtaContainer2, mHeaderArrivalInfo.get(1));
                }
            });

            // Show or hide reminder for second row
            View r = mEtaContainer2.findViewById(R.id.reminder);
            refreshReminder(mHeaderArrivalInfo.get(1).getInfo().getTripId(), r);
        } else {
            // Hide the 2nd row of arrival info and related views - we only had one arrival info
            UIHelp.hideViewWithAnimation(mEtaSeparator, mShortAnimationDuration);
            UIHelp.hideViewWithAnimation(mEtaContainer2, mShortAnimationDuration);
        }

        // Hide progress bar
        UIHelp.hideViewWithAnimation(mProgressBar, mShortAnimationDuration);
    }

    /**
     * Shows or hides a reminder in the header, based on whether there is a saved reminder for
     * the given tripId and the current stop
     * @param tripId tripId to search for reminders for in the database
     * @param v reminder view to show or hide, based on whether the there is or isn't a reminder for
     *          this tripId and stop
     */
    void refreshReminder(String tripId, View v) {
        ContentValues values = null;
        if (mTripsForStop != null) {
            values = mTripsForStop.getValues(tripId);
        }
        if (values != null) {
            // A reminder exists for this trip
            v.setVisibility(View.VISIBLE);
        } else {
            v.setVisibility(View.GONE);
        }
    }

    /**
     * Sets the header to the correct size based on the number of arrivals currently
     * shown in the header, and whether the panel is collapsed, anchored, or expanded.
     */
    void refreshHeaderSize() {
        if (mInNameEdit) {
            // When editing the stop name, the header size is always the same
            setHeaderSize(HEADER_HEIGHT_EDIT_NAME_DP);
            return;
        }

        float newSize = 0;

        // Change the header size based on arrival info and if a route filter is used
        if (mNumHeaderArrivals == 0 || mNumHeaderArrivals == 1) {
            newSize = HEADER_HEIGHT_ONE_ARRIVAL_DP;
        } else if (mNumHeaderArrivals == 2) {
            newSize = HEADER_HEIGHT_TWO_ARRIVALS_DP;
        }

        // If we're showing the route filter, than add the offset so this filter view has room
        if (isFilteringRoutes()) {
            newSize += HEADER_OFFSET_FILTER_ROUTES_DP;
        }

        if (newSize != 0) {
            setHeaderSize(newSize);
        }
    }


    /**
     * Re-size the header layout to the provided size, in dp
     *
     * @param newHeightDp the new header layout, in dp
     */
    void setHeaderSize(float newHeightDp) {
        int newHeightPixels = UIHelp.dpToPixels(mContext,
                newHeightDp);
        // Assume that the sliding panel isn't the same if it's not initialized
        boolean isSlidingPanelSame = false;
        if (mSlidingPanelController != null) {
            isSlidingPanelSame = mSlidingPanelController.getPanelHeightPixels() == newHeightPixels;
        }
        if (mMainContainerView.getLayoutParams().height == newHeightPixels
                && mView.getLayoutParams().height == newHeightPixels
                && isSlidingPanelSame) {
            // If the header is already this size, do nothing
            return;
        }

        mView.getLayoutParams().height = newHeightPixels;
        if (mSlidingPanelController != null) {
            mSlidingPanelController.setPanelHeightPixels(newHeightPixels);
        }
        // Set the main container view size to be the same
        mMainContainerView.getLayoutParams().height = newHeightPixels;
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
            return TYPE_ERROR;
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

    synchronized void beginNameEdit(String initial) {
        // If we can click on this, then we're definitely not
        // editable, so we should go into edit mode.
        mEditNameView.setText((initial != null) ? initial : mNameView.getText());
        mNameContainerView.setVisibility(View.GONE);
        mFilterGroup.setVisibility(View.GONE);
        mStopFavorite.setVisibility(View.GONE);
        mEtaContainer1.setVisibility(View.GONE);
        mEtaSeparator.setVisibility(View.GONE);
        mEtaContainer2.setVisibility(View.GONE);

        // Save mExpandCollapse visibility state
        cachedExpandCollapseViewVisibility = mExpandCollapse.getVisibility();
        if (!UIHelp.canAnimateViewModern()) {
            // View won't disappear without clearing the legacy rotation animation
            mExpandCollapse.clearAnimation();
        }
        mExpandCollapse.setVisibility(View.GONE);
        mEditNameContainerView.setVisibility(View.VISIBLE);

        // Set the entire header size
        setHeaderSize(HEADER_HEIGHT_EDIT_NAME_DP);

        mEditNameView.requestFocus();
        mEditNameView.setSelection(mEditNameView.getText().length());
        mInNameEdit = true;

        // Open soft keyboard if no physical keyboard is open
        InputMethodManager inputMethodManager = (InputMethodManager) mContext.getSystemService(
                Context.INPUT_METHOD_SERVICE);
        inputMethodManager.showSoftInput(mEditNameView, InputMethodManager.SHOW_IMPLICIT);
    }

    synchronized void endNameEdit() {
        mInNameEdit = false;
        mNameContainerView.setVisibility(View.VISIBLE);
        mEditNameContainerView.setVisibility(View.GONE);
        mStopFavorite.setVisibility(View.VISIBLE);
        mExpandCollapse.setVisibility(cachedExpandCollapseViewVisibility);
        // Reset the header size
        refreshHeaderSize();
        // Hide soft keyboard
        InputMethodManager imm =
                (InputMethodManager) mContext.getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(mEditNameView.getWindowToken(), 0);
        refresh();
    }
}
