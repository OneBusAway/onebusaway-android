/*
 * Copyright (C) 2012 Paul Watts (paulcwatts@gmail.com)
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
import org.onebusaway.android.util.ArrayAdapter;
import org.onebusaway.android.util.UIUtils;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.support.v4.content.ContextCompat;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.text.style.ClickableSpan;
import android.view.View;
import android.widget.ListView;
import android.widget.TextView;

import java.util.Collection;

class AlertList {

    interface Alert {

        public static final int TYPE_ERROR = 1;
        public static final int TYPE_WARNING = 2;
        public static final int TYPE_INFO = 3;
        public static final int TYPE_SHOW_HIDDEN_ALERTS = 4;

        // Adds an affordance to show that it's clickable
        // and you can see more..
        public static final int FLAG_HASMORE = 0x1;

        String getId();

        int getType();

        int getFlags();

        CharSequence getString();

        void onClick();
    }

    //
    // Adapter
    //
    private static class Adapter extends ArrayAdapter<Alert> {

        public Adapter(Context context) {
            super(context, R.layout.alert_item);
        }

        /**
         * Initialize the views - if the alert is a service alert that should be shown, populate
         * that text.  If its a response error (i.e., couldn't update from server), show that.
         * If it's an alert showing that some alerts are filtered, show that.
         */
        @Override
        protected void initView(View view, final Alert alert) {
            TextView alertView = (TextView) view.findViewById(android.R.id.text1);
            View filterGroupView = view.findViewById(R.id.filter_alert_group);
            TextView filterTextView = (TextView) view.findViewById(R.id.filter_alert_text);
            TextView showAllView = (TextView) view.findViewById(R.id.show_all_alerts);

            alertView.setText(alert.getString());
            boolean clickable = (alert.getFlags() & Alert.FLAG_HASMORE) == Alert.FLAG_HASMORE;
            int type = alert.getType();
            Resources r = Application.get().getResources();

            int bg = R.color.alert_text_background_info;
            int arrowColor = R.color.alert_text_color_info;
            int alertColor = R.color.alert_icon_info;
            int resourceIdAlert = 0;

            switch (type) {
                case Alert.TYPE_ERROR:
                    bg = R.color.alert_text_background_error;
                    arrowColor = R.color.alert_text_color_error;
                    resourceIdAlert = R.drawable.ic_alert_warning;
                    alertColor = R.color.alert_icon_error;
                    alertView.setVisibility(View.VISIBLE);
                    filterGroupView.setVisibility(View.GONE);
                    break;
                case Alert.TYPE_WARNING:
                    bg = R.color.alert_text_background_warning;
                    arrowColor = R.color.alert_text_color_warning;
                    resourceIdAlert = R.drawable.ic_alert_warning;
                    alertColor = R.color.alert_icon_warning;
                    alertView.setVisibility(View.VISIBLE);
                    filterGroupView.setVisibility(View.GONE);
                    break;
                case Alert.TYPE_SHOW_HIDDEN_ALERTS:
                    alertView.setVisibility(View.GONE);
                    filterGroupView.setVisibility(View.VISIBLE);
                    filterTextView.setText(alert.getString());
                    break;
                case Alert.TYPE_INFO:
                default:
                    bg = R.color.alert_text_background_info;
                    arrowColor = R.color.alert_text_color_info;
                    alertView.setVisibility(View.VISIBLE);
                    filterGroupView.setVisibility(View.GONE);
                    break;
            }
            // Set text color to same as arrow color
            alertView.setTextColor(r.getColor(arrowColor));

            Drawable dWarning;
            Drawable wdWarning = null;

            if (resourceIdAlert != 0) {
                dWarning = ContextCompat
                        .getDrawable(Application.get().getApplicationContext(), resourceIdAlert);
                wdWarning = DrawableCompat.wrap(dWarning);
                wdWarning = wdWarning.mutate();
                // Tint the icon
                DrawableCompat.setTint(wdWarning, r.getColor(alertColor));
            }

            Drawable dArrow;
            Drawable wdArrow = null;
            int resourceIdArrow = clickable ? R.drawable.ic_navigation_chevron_right : 0;

            if (resourceIdArrow != 0) {
                dArrow = ContextCompat
                        .getDrawable(Application.get().getApplicationContext(), resourceIdArrow);
                wdArrow = DrawableCompat.wrap(dArrow);
                wdArrow = wdArrow.mutate();
                // Tint the icon
                DrawableCompat.setTint(wdArrow, r.getColor(arrowColor));
            }

            alertView.setCompoundDrawablesWithIntrinsicBounds(wdWarning, null, wdArrow, null);

            // Set the background color
            view.setBackgroundResource(bg);

            // Even if we don't think it's clickable, we still need to
            // reset the onclick listener because we could be reusing this view.
            View.OnClickListener listener = new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    alert.onClick();
                }
            };
            view.setOnClickListener(listener);

            // Remove any previous clickable spans - we're recycling views between fragments for efficiency
            UIUtils.removeAllClickableSpans(showAllView);
            ClickableSpan showAllClick = new ClickableSpan() {
                public void onClick(View v) {
                    alert.onClick();
                }
            };
            UIUtils.setClickableSpan(showAllView, showAllClick);
        }
    }

    private Adapter mAdapter;

    private boolean mIsAlertHidden;

    private int mHiddenAlertCount;

    //
    // Cached views
    //
    private ListView mView;

    AlertList(Context context) {
        mAdapter = new Adapter(context);
    }

    void initView(View view) {
        mView = (ListView) view;
        mView.setAdapter(mAdapter);
    }

    //
    // Array / adapter methods
    //
    void add(Alert alert) {
        mAdapter.add(alert);
    }

    void addAll(Collection<? extends Alert> alerts) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            mAdapter.addAll(alerts);
        } else {
            for (Alert a : alerts) {
                mAdapter.add(a);
            }
        }
    }

    void insert(Alert alert, int index) {
        mAdapter.insert(alert, index);
    }

    int getPosition(Alert alert) {
        return mAdapter.getPosition(alert);
    }

    void remove(Alert alert) {
        mAdapter.remove(alert);
    }

    int getCount() {
        return mAdapter.getCount();
    }

    Alert getItem(int position) {
        return mAdapter.getItem(position);
    }

    /**
     * Returns true if some alerts have previously been hidden that would otherwise
     * appear in this list, false if all alerts are visible
     *
     * @return true if some alerts have previously been hidden that would otherwise
     * appear in this list, false if all alerts are visible
     */
    public boolean isAlertHidden() {
        return mIsAlertHidden;
    }

    /**
     * Set to true if there are some alerts that have been previously hidden that would
     * otherwise appear in this list, , false if all alerts are visible
     *
     * @param alertHidden true if there are some alerts that have been previously hidden that would
     *                    otherwise appear in this list, false if all alerts are visible
     */
    public void setAlertHidden(boolean alertHidden) {
        mIsAlertHidden = alertHidden;
    }

    /**
     * Returns the number of active alerts hidden that would otherwise appear in this list
     *
     * @return the number of active alerts hidden that would otherwise appear in this list
     */
    public int getHiddenAlertCount() {
        return mHiddenAlertCount;
    }

    /**
     * Sets the number of active alerts hidden that would otherwise appear in this list
     *
     * @param hiddenAlertCount the number of active alerts hidden that would otherwise appear in
     *                         this list
     */
    public void setHiddenAlertCount(int hiddenAlertCount) {
        mHiddenAlertCount = hiddenAlertCount;
    }
}
