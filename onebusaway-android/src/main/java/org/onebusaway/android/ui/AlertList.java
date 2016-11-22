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

import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.support.v4.content.ContextCompat;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.view.View;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import java.util.Collection;

class AlertList {

    interface Alert {

        public static final int TYPE_ERROR = 1;
        public static final int TYPE_WARNING = 2;
        public static final int TYPE_INFO = 3;
        public static final int TYPE_SHOW_DISMISSED_ALERT = 4;

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

        @Override
        protected void initView(View view, final Alert alert) {
            TextView text = (TextView) view.findViewById(android.R.id.text1);
            Button button = (Button) view.findViewById(R.id.show_dismissed_alerts);

            text.setText(alert.getString());
            boolean clickable = (alert.getFlags() & Alert.FLAG_HASMORE) == Alert.FLAG_HASMORE;
            int type = alert.getType();
            Resources r = Application.get().getResources();

            int bg;
            int arrowColor = R.color.alert_text_color_info;
            int alertColor = R.color.alert_icon_info;
            int resourceIdAlert = 0;

            switch (type) {
                case Alert.TYPE_ERROR:
                    bg = R.color.alert_text_background_error;
                    arrowColor = R.color.alert_text_color_error;
                    resourceIdAlert = R.drawable.ic_alert_warning;
                    alertColor = R.color.alert_icon_error;
                    text.setVisibility(View.VISIBLE);
                    button.setVisibility(View.GONE);
                    break;
                case Alert.TYPE_WARNING:
                    bg = R.color.alert_text_background_warning;
                    arrowColor = R.color.alert_text_color_warning;
                    resourceIdAlert = R.drawable.ic_alert_warning;
                    alertColor = R.color.alert_icon_warning;
                    text.setVisibility(View.VISIBLE);
                    button.setVisibility(View.GONE);
                    break;
                case Alert.TYPE_SHOW_DISMISSED_ALERT:
                    bg = R.color.material_gray;
                    text.setVisibility(View.GONE);
                    button.setVisibility(View.VISIBLE);
                    break;
                case Alert.TYPE_INFO:
                default:
                    bg = R.color.alert_text_background_info;
                    arrowColor = R.color.alert_text_color_info;
                    text.setVisibility(View.VISIBLE);
                    button.setVisibility(View.GONE);
                    break;
            }
            // Set text color to same as arrow color
            text.setTextColor(r.getColor(arrowColor));

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

            text.setCompoundDrawablesWithIntrinsicBounds(wdWarning, null, wdArrow, null);

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
            button.setOnClickListener(listener);
        }
    }

    private Adapter mAdapter;

    private boolean mIsDismissedAlerts;

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
     * Returns true if some alerts have previously been hidden (dismissed) that would otherwise
     * appear in this list, false if all alerts are visible (not dismissed)
     *
     * @return true if some alerts have previously been hidden (dismissed) that would otherwise
     * appear in this list, false if all alerts are visible (not dismissed)
     */
    public boolean isDismissedAlerts() {
        return mIsDismissedAlerts;
    }

    /**
     * Set to true if there are some alerts that have been previoiusly hidden (dimissed) that would
     * otherwise appear in this list, , false if all alerts are visible (not dismissed)
     *
     * @param dismissedAlerts true if there are some alerts that have been previoiusly hidden (
     *                        dimissed) that would otherwise appear in this list, , false if all
     *                        alerts are visible (not dismissed)
     */
    public void setDismissedAlerts(boolean dismissedAlerts) {
        mIsDismissedAlerts = dismissedAlerts;
    }
}
