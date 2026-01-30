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

import android.app.Dialog;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Html;
import android.text.TextUtils;
import android.text.style.ClickableSpan;
import android.view.View;
import android.widget.TextView;
import android.widget.ScrollView;
import android.view.ViewGroup;
import android.content.res.Configuration;
import android.graphics.Color;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.snackbar.Snackbar;

import org.onebusaway.android.R;
import org.onebusaway.android.io.elements.ObaSituation;
import org.onebusaway.android.provider.ObaContract;
import org.onebusaway.android.util.PreferenceUtils;
import org.onebusaway.android.util.UIUtils;

/**
 * Displays service alerts (i.e., situations) is a dialog
 */
public class SituationDialogFragment extends DialogFragment {

    public static final String TAG = "SituationFragment";

    public static final String ID = ".ID";

    public static final String TITLE = ".Title";

    public static final String DESCRIPTION = ".Description";

    public static final String URL = ".Url";

    private static final float MAX_DIALOG_HEIGHT_RATIO = 0.65f;

    interface Listener {

        /**
         * Called when this dialog is dismissed
         *
         * @param isAlertHidden true if the service alert was hidden by the user, false
         *                      if it was
         *                      not
         */
        void onDismiss(boolean isAlertHidden);

        /**
         * Called when the user taps the "Undo" snackbar for hiding an alert
         */
        void onUndo();
    }

    private Listener mListener;

    /**
     * Helper method to show this dialog
     */
    public static SituationDialogFragment newInstance(ObaSituation situation) {
        Bundle args = new Bundle();
        args.putString(ID, situation.getId());
        args.putString(TITLE, situation.getSummary());
        // We don't use the stop name map here...we want the actual stop name.
        args.putString(DESCRIPTION, situation.getDescription());
        if (!TextUtils.isEmpty(situation.getUrl())) {
            args.putString(URL, situation.getUrl());
        }

        // Create the list fragment and add it as our sole content.
        SituationDialogFragment dialog = new SituationDialogFragment();
        dialog.setArguments(args);
        return dialog;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Bundle args = getArguments();
        final String situationId = args.getString(ID);

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity())
                .setView(R.layout.situation)
                .setPositiveButton(R.string.hide, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int which) {
                        Dialog dialog = (Dialog) dialogInterface;

                        // Update the database to indicate that this alert has been hidden
                        ObaContract.ServiceAlerts
                                .insertOrUpdate(situationId, new ContentValues(), false,
                                        true);

                        // Show the UNDO snackbar
                        Snackbar.make(getActivity().findViewById(R.id.fragment_arrivals_list),
                                R.string.alert_hidden_snackbar_text, Snackbar.LENGTH_SHORT)
                                .setAction(R.string.alert_hidden_snackbar_action,
                                        new View.OnClickListener() {
                                            @Override
                                            public void onClick(View v) {
                                                ObaContract.ServiceAlerts
                                                        .insertOrUpdate(situationId,
                                                                new ContentValues(), false,
                                                                false);
                                                if (mListener != null) {
                                                    mListener.onUndo();
                                                }
                                            }
                                        })
                                .show();
                        dialog.dismiss();
                        if (mListener != null) {
                            mListener.onDismiss(true);
                        }
                    }
                })
                .setNeutralButton(R.string.hide_all, (dialog, which) -> {
                    // Hide existing alerts in the database
                    ObaContract.ServiceAlerts.hideAllAlerts();
                    // Also set the user preference to hide new alerts
                    PreferenceUtils.saveBoolean(getString(R.string.preference_key_hide_alerts), true);

                    // Show the snackbar
                    Snackbar.make(getActivity().findViewById(R.id.fragment_arrivals_list),
                            R.string.all_alert_hidden_snackbar_text, Snackbar.LENGTH_SHORT)
                            .show();

                    dialog.dismiss();
                    if (mListener != null) {
                        mListener.onDismiss(true);
                    }
                })
                .setNegativeButton(R.string.close, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        if (mListener != null) {
                            mListener.onDismiss(false);
                        }
                    }
                });

        final AlertDialog dialog = builder.create();
        dialog.show();

        // Ensure the alert ScrollView has a max height so it becomes scrollable
        // when there is lots of content instead of expanding the dialog infinitely.
        ScrollView scrollView = (ScrollView) dialog.findViewById(R.id.alert_scrollview);
        if (scrollView != null) {
            // Capture screen height before posting to avoid accessing resources after
            // detachment.
            final int screenHeight = getResources().getDisplayMetrics().heightPixels;
            // Post to ensure views are measured before checking/setting height.
            scrollView.post(() -> {
                if (!isAdded())
                    return;
                // Use ~65% of the screen height as a reasonable max dialog height.
                final int maxHeightPx = (int) (MAX_DIALOG_HEIGHT_RATIO * screenHeight);
                if (scrollView.getHeight() > maxHeightPx) {
                    ViewGroup.LayoutParams lp = scrollView.getLayoutParams();
                    lp.height = maxHeightPx;
                    scrollView.setLayoutParams(lp);
                }
            });
        }

        // --- START MANUAL DARK MODE CHECK ---

        // 1. Get the current UI mode from the dialog's context
        int currentNightMode = dialog.getContext().getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;

        // 2. Define the colors based on the mode
        int textColor;
        int linkColor;

        if (currentNightMode == Configuration.UI_MODE_NIGHT_YES) {
            // Dark Mode: Use light colors
            textColor = Color.WHITE;
            linkColor = Color.CYAN; // A light blue for links
        } else {
            // Light Mode: Use dark colors
            textColor = Color.BLACK;
            linkColor = Color.BLUE; // Standard dark blue for links
        }
        // --- END MANUAL DARK MODE CHECK ---

        // 3. Apply the colors
        TextView title = (TextView) dialog.findViewById(R.id.alert_title);
        if (title != null) {
            title.setText(args.getString(TITLE));
            // The title's background (@color/theme_primary) is dark, so title text should
            // always be light.
            title.setTextColor(Color.WHITE);
        }

        TextView descTxtView = (TextView) dialog.findViewById(R.id.alert_description);
        if (descTxtView != null) {
            descTxtView.setTextColor(textColor);
            descTxtView.setLinkTextColor(linkColor);

            String desc = args.getString(DESCRIPTION);
            if (!TextUtils.isEmpty(desc)) {
                String htmlDescription = desc.replaceAll("\\r\\n|\\r|\\n", "<br>");
                descTxtView.setText(Html.fromHtml(htmlDescription));
            } else {
                descTxtView.setText(R.string.no_description_available);
            }
        }

        TextView urlView = (TextView) dialog.findViewById(R.id.alert_url);
        if (urlView != null) {
            urlView.setTextColor(linkColor);
            urlView.setLinkTextColor(linkColor);

            // Remove any previous clickable spans just to be safe
            UIUtils.removeAllClickableSpans(urlView);

            final String url = args.getString(URL);
            if (!TextUtils.isEmpty(url)) {
                urlView.setVisibility(View.VISIBLE);

                ClickableSpan urlClick = new ClickableSpan() {
                    public void onClick(View v) {
                        getActivity().startActivity(
                                new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                    }
                };
                UIUtils.setClickableSpan(urlView, urlClick);
            } else {
                urlView.setVisibility(View.GONE);
            }
        }

        // Update the database to indicate that this alert has been read
        ObaContract.ServiceAlerts
                .insertOrUpdate(args.getString(ID), new ContentValues(), true, null);

        return dialog;

    }

    public Listener getListener() {
        return mListener;
    }

    public void setListener(Listener listener) {
        mListener = listener;
    }
}
