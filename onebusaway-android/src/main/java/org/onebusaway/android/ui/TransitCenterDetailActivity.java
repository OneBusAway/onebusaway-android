/*
 * Copyright (C) 2026 Open Transit Software Foundation
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

import android.os.Bundle;
import android.view.MenuItem;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentManager;

import org.onebusaway.android.util.UIUtils;

public class TransitCenterDetailActivity extends AppCompatActivity {

    public static final String EXTRA_TRANSIT_CENTER_ID = "transit_center_id";
    public static final String EXTRA_TRANSIT_CENTER_NAME = "transit_center_name";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        UIUtils.setupActionBar(this);

        long transitCenterId = getIntent().getLongExtra(EXTRA_TRANSIT_CENTER_ID, -1);
        String name = getIntent().getStringExtra(EXTRA_TRANSIT_CENTER_NAME);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            if (name != null) {
                getSupportActionBar().setTitle(name);
            }
        }

        if (savedInstanceState == null) {
            TransitCenterDetailFragment fragment = TransitCenterDetailFragment.newInstance(
                    transitCenterId, name);
            FragmentManager fm = getSupportFragmentManager();
            fm.beginTransaction()
                    .add(android.R.id.content, fragment, TransitCenterDetailFragment.TAG)
                    .commit();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
