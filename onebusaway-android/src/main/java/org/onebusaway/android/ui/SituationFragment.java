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
import org.onebusaway.android.io.ObaAnalytics;
import org.onebusaway.android.io.elements.ObaSituation;
import org.onebusaway.android.provider.ObaContract;
import org.onebusaway.android.util.UIUtils;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.text.style.ClickableSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

public class SituationFragment extends Fragment {

    public static final String ID = ".ID";

    public static final String TITLE = ".Title";

    public static final String DESCRIPTION = ".Description";

    public static final String URL = ".Url";

    static void show(AppCompatActivity activity, ObaSituation situation) {
        FragmentManager fm = activity.getSupportFragmentManager();

        Bundle args = new Bundle();
        args.putString(ID, situation.getId());
        args.putString(TITLE, situation.getSummary());
        // We don't use the stop name map here...we want the actual stop name.
        args.putString(DESCRIPTION, situation.getDescription());
        if (!TextUtils.isEmpty(situation.getUrl())) {
            args.putString(URL, situation.getUrl());
        }

        // Create the list fragment and add it as our sole content.
        SituationFragment content = new SituationFragment();
        content.setArguments(args);

        FragmentTransaction ft = fm.beginTransaction();
        ft.replace(android.R.id.content, content);
        ft.addToBackStack(null);
        ft.commit();
    }

    @Override
    public View onCreateView(LayoutInflater inflater,
            ViewGroup root, Bundle savedInstanceState) {
        if (root == null) {
            // Currently in a layout without a container, so no
            // reason to create our view.
            return null;
        }
        return inflater.inflate(R.layout.situation, null);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        // Set the stop name.
        Bundle args = getArguments();
        String id = args.getString(ID);

        TextView title = (TextView) view.findViewById(R.id.alert_title);
        title.setText(args.getString(TITLE));

        TextView desc = (TextView) view.findViewById(R.id.alert_description);
        desc.setText(args.getString(DESCRIPTION));

        TextView urlView = (TextView) view.findViewById(R.id.alert_url);

        // Remove any previous clickable spans just to be safe
        UIUtils.removeAllClickableSpans(urlView);

        final String url = args.getString(URL);
        if (url != null) {
            urlView.setVisibility(View.VISIBLE);

            ClickableSpan urlClick = new ClickableSpan() {
                public void onClick(View v) {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                }
            };
            UIUtils.setClickableSpan(urlView, urlClick);
        } else {
            urlView.setVisibility(View.GONE);
        }

        Button btn = (Button) view.findViewById(R.id.alert_dismiss);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Update the database to indicate that this alert has been dismissed
                ObaContract.ServiceAlerts.insertOrUpdate(ID, new ContentValues(), false, true);

                // Close the activity
                Activity a = getActivity();
                if (a != null) {
                    a.finish();
                }
            }
        });

        // Update the database to indicate that this alert has been read
        ObaContract.ServiceAlerts.insertOrUpdate(ID, new ContentValues(), true, null);
    }

    @Override
    public void onStart() {
        super.onStart();
        ObaAnalytics.reportFragmentStart(this);
    }
}
