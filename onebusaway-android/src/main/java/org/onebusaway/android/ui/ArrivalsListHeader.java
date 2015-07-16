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
import org.onebusaway.android.io.elements.ObaRegion;
import org.onebusaway.android.util.LocationHelper;
import org.onebusaway.android.util.OrientationHelper;
import org.onebusaway.android.util.UIHelp;
import org.onebusaway.android.view.ArrowView;
import org.onebusaway.android.view.DistanceToStopView;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.net.Uri;
import android.os.Build;
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
    private View mView;  // Entire header parent view

    private View mMainContainerView; // Holds everything except for the mFilterGroup

    private View mNameContainerView;

    private View mEditNameContainerView;

    private TextView mNameView;

    private EditText mEditNameView;

    private ImageButton mFavoriteView;

    private View mFilterGroup;

    private TextView mShowAllView;

    private ClickableSpan mShowAllClick;

    private boolean mInNameEdit = false;

    private ArrowView mArrowToStopView;

    private DistanceToStopView mDistanceToStopView;

    // Default state for the header is inside a sliding fragment
    private boolean mIsSlidingPanelCollapsed = true;

    private int mShortAnimationDuration;

    private TextView mArrivalInfoView;

    private ProgressBar mProgressBar;

    private ImageButton mStopInfo;

    private ImageView mExpandCollapse;

    private View mRightMarginSeparatorView;

    // Utility classes to help with managing location and orientation for the arrow/distance views
    OrientationHelper mOrientationHelper;

    LocationHelper mLocationHelper;

    // Animations
    private static final float ANIM_PIVOT_VALUE = 0.5f;  // 50%

    private static final float ANIM_STATE_NORMAL = 0.0f;  // 0 degrees (no rotation)

    private static final float ANIM_STATE_INVERTED = 180.0f;  // 180 degrees

    private static final long ANIM_DURATION = 300;  // milliseconds

    // Manages header size in "stop name edit mode"
    private int cachedExpandCollapseViewVisibility;

    private static float DEFAULT_HEADER_HEIGHT_DP;

    private static float EXPANDED_HEADER_HEIGHT_EDIT_NAME_DP;

    private static float EXPANDED_HEADER_HEIGHT_FILTER_STOPS_DP;

    // Controller to change parent sliding panel
    HomeActivity.SlidingPanelController mSlidingPanelController;

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
        // Clear any existing arrival info
        mArrivalInfo = null;

        // Cache the ArrivalsListHeader height values
        DEFAULT_HEADER_HEIGHT_DP =
                view.getResources().getDimension(R.dimen.arrival_header_height_two_arrivals)
                / view.getResources().getDisplayMetrics().density;
        EXPANDED_HEADER_HEIGHT_FILTER_STOPS_DP = view.getResources()
                .getDimension(R.dimen.arrival_header_height_expanded_filter_routes)
                / view.getResources().getDisplayMetrics().density;
        EXPANDED_HEADER_HEIGHT_EDIT_NAME_DP =
                view.getResources().getDimension(R.dimen.arrival_header_height_expanded_edit_name)
                        / view.getResources().getDisplayMetrics().density;

        // Init views
        mView = view;
        mMainContainerView = mView.findViewById(R.id.main_header_content);
        mNameContainerView = mView.findViewById(R.id.stop_name_and_info_container);
        mEditNameContainerView = mView.findViewById(R.id.edit_name_container);
        mNameView = (TextView) mView.findViewById(R.id.stop_name);
        mEditNameView = (EditText) mView.findViewById(R.id.edit_name);
        mFavoriteView = (ImageButton) mView.findViewById(R.id.stop_favorite);
        mFilterGroup = mView.findViewById(R.id.filter_group);

        mShowAllView = (TextView) mView.findViewById(R.id.show_all);
        // Remove any previous clickable spans - we're recycling views between fragments for efficiency
        UIHelp.removeAllClickableSpans(mShowAllView);
        mShowAllClick = new ClickableSpan() {
            public void onClick(View v) {
                mController.setRoutesFilter(new ArrayList<String>());
                refreshFilter();
            }
        };
        UIHelp.setClickableSpan(mShowAllView, mShowAllClick);

        mArrivalInfoView = (TextView) mView.findViewById(R.id.header_arrival_info);
        mProgressBar = (ProgressBar) mView.findViewById(R.id.header_loading_spinner);
        mStopInfo = (ImageButton) mView.findViewById(R.id.stop_info_button);
        mExpandCollapse = (ImageView) mView.findViewById(R.id.expand_collapse);
        resetExpandCollapseAnimation();
        mRightMarginSeparatorView = mView.findViewById(R.id.right_margin_separator);

        mDistanceToStopView = (DistanceToStopView) mView.findViewById(R.id.dist_to_stop);
        // Register to be notified when the ArrowView and DistanceToStopView are completely initialized
        mDistanceToStopView.registerListener(new DistanceToStopView.Listener() {
            @Override
            public void onInitializationComplete() {
                refresh();
            }
        });
        mArrowToStopView = (ArrowView) mView.findViewById(R.id.arrow);
        mArrowToStopView.registerListener(new ArrowView.Listener() {
            @Override
            public void onInitializationComplete() {
                refresh();
            }
        });

        // Register views for location and orientation updates
        mOrientationHelper.registerListener(mArrowToStopView);
        mLocationHelper.registerListener(mArrowToStopView);
        mLocationHelper.registerListener(mDistanceToStopView);

        // Initialize right margin view visibilities
        UIHelp.showViewWithAnimation(mProgressBar, mShortAnimationDuration);

        UIHelp.hideViewWithAnimation(mArrowToStopView, mShortAnimationDuration);
        UIHelp.hideViewWithAnimation(mDistanceToStopView, mShortAnimationDuration);
        UIHelp.hideViewWithAnimation(mArrivalInfoView, mShortAnimationDuration);

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
    }

    /**
     * Used to give the header control over the containing sliding panel, primarily to change the
     * panel header height
     */
    public void setSlidingPanelController(HomeActivity.SlidingPanelController controller) {
        mSlidingPanelController = controller;
    }

    /**
     * Should be called from onResume() of context hosting this header
     */
    public void onResume() {
        // Resume monitoring of sensors and location
        mOrientationHelper.onResume();
        mLocationHelper.onResume();

        // Refresh preference for units
        mDistanceToStopView.refreshUnitsPreference();
    }

    /**
     * Should be called from onPause() of context hosting this header
     */
    public void onPause() {
        // Pause monitoring of sensors and location
        mOrientationHelper.onPause();
        mLocationHelper.onPause();

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

    void refresh() {
        refreshName();
        refreshArrivalInfo();
        refreshLocation();
        refreshFavorite();
        refreshFilter();
        refreshError();
        refreshRightMarginContainer();
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

        if (mController != null & mController.getRouteDisplayNames() != null &&
                mController.getRouteDisplayNames().size() > 1) {
            // More than one route will be displayed, so add highlight
            arrivalInfo.append("* ");
        }

        if (mArrivalInfo != null && !mInNameEdit) {
            if (mArrivalInfo.size() > 0) {
                // We have arrival info for at least one bus
                long eta = mArrivalInfo.get(0).getEta();
                if (eta == 0) {
                    arrivalInfo.append(mContext.getString(R.string.stop_info_eta_now));
                    mArrivalInfoView.setText(arrivalInfo);
                } else if (eta > 0) {
                    arrivalInfo.append(mContext.getString(R.string.stop_info_header_arrival_info,
                            eta));
                    mArrivalInfoView.setText(arrivalInfo);
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
        TextView v = (TextView) mView.findViewById(R.id.filter_text);
        ArrayList<String> routesFilter = mController.getRoutesFilter();
        final int num = (routesFilter != null) ? routesFilter.size() : 0;
        if (num > 0) {
            final int total = mController.getNumRoutes();
            v.setText(mContext.getString(R.string.stop_info_filter_header, num, total));
            // Show the filter text (except when in name edit mode)
            if (mInNameEdit) {
                mFilterGroup.setVisibility(View.GONE);
                setHeaderSize(EXPANDED_HEADER_HEIGHT_EDIT_NAME_DP);
            } else {
                setHeaderSize(EXPANDED_HEADER_HEIGHT_FILTER_STOPS_DP);
                mFilterGroup.setVisibility(View.VISIBLE);
            }
        } else {
            setHeaderSize(DEFAULT_HEADER_HEIGHT_DP);
            mFilterGroup.setVisibility(View.GONE);
        }
    }

    /**
     * Hides the progress bar, and replaces it with the correct content depending on sliding
     * panel state
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private void refreshRightMarginContainer() {
        if (mInNameEdit) {
            // If the user is editing a stop name, we shouldn't show any of these views
            return;
        }
        if (mIsSlidingPanelCollapsed) {
            // Cross-fade in bus icon and arrival info, and hide direction arrow and distance to stop
            UIHelp.hideViewWithAnimation(mArrowToStopView, mShortAnimationDuration);
            UIHelp.hideViewWithAnimation(mDistanceToStopView, mShortAnimationDuration);
            if (mArrivalInfo == null) {
                // We don't have any arrival info yet, so make sure the progress bar is running
                UIHelp.showViewWithAnimation(mProgressBar, mShortAnimationDuration);
                return;
            }
            UIHelp.showViewWithAnimation(mArrivalInfoView, mShortAnimationDuration);

            // Hide progress bar
            UIHelp.hideViewWithAnimation(mProgressBar, mShortAnimationDuration);
        } else {
            // Cross-fade in direction arrow and distance to stop, and hide bus icon and arrival info
            UIHelp.hideViewWithAnimation(mArrivalInfoView, mShortAnimationDuration);

            if (!mArrowToStopView.isInitialized() || !mDistanceToStopView.isInitialized()) {
                // At least one of the views isn't ready yet, so make sure the progress bar is running
                UIHelp.showViewWithAnimation(mProgressBar, mShortAnimationDuration);
                return;
            }

            UIHelp.showViewWithAnimation(mArrowToStopView, mShortAnimationDuration);
            UIHelp.showViewWithAnimation(mDistanceToStopView, mShortAnimationDuration);

            // Hide progress bar
            UIHelp.hideViewWithAnimation(mProgressBar, mShortAnimationDuration);
        }
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
        mFavoriteView.setVisibility(View.GONE);
        mRightMarginSeparatorView.setVisibility(View.GONE);
        mDistanceToStopView.setVisibility(View.GONE);
        mArrowToStopView.setVisibility(View.GONE);
        mArrivalInfoView.setVisibility(View.GONE);
        // Save mExpandCollapse visibility state
        cachedExpandCollapseViewVisibility = mExpandCollapse.getVisibility();
        if (!UIHelp.canAnimateViewModern()) {
            // View won't disappear without clearing the legacy rotation animation
            mExpandCollapse.clearAnimation();
        }
        mExpandCollapse.setVisibility(View.GONE);
        mEditNameContainerView.setVisibility(View.VISIBLE);

        // Set the entire header size
        setHeaderSize(EXPANDED_HEADER_HEIGHT_EDIT_NAME_DP);
        // Set the main container view size to be the same
        mMainContainerView.getLayoutParams().height = UIHelp.dpToPixels(mContext,
                EXPANDED_HEADER_HEIGHT_EDIT_NAME_DP);

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
        mFavoriteView.setVisibility(View.VISIBLE);
        mRightMarginSeparatorView.setVisibility(View.VISIBLE);
        mExpandCollapse.setVisibility(cachedExpandCollapseViewVisibility);
        // Set the entire header size
        setHeaderSize(DEFAULT_HEADER_HEIGHT_DP);
        // Set the main container view size to be the same
        mMainContainerView.getLayoutParams().height = UIHelp.dpToPixels(mContext,
                DEFAULT_HEADER_HEIGHT_DP);
        // Hide soft keyboard
        InputMethodManager imm =
                (InputMethodManager) mContext.getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(mEditNameView.getWindowToken(), 0);
        refresh();
    }

    /**
     * Re-size the header layout to the provided size, in dp
     *
     * @param newHeightDp the new header layout, in dp
     */
    void setHeaderSize(float newHeightDp) {
        mView.getLayoutParams().height = UIHelp.dpToPixels(mContext,
                newHeightDp);
        if (mSlidingPanelController != null) {
            mSlidingPanelController
                    .setPanelHeightPixels(UIHelp.dpToPixels(mContext,
                            newHeightDp));
        }
    }
}
