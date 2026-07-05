/*
 * Copyright (C) 2010 Paul Watts (paulcwatts@gmail.com)
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
package org.onebusaway.android.api.test;

import org.junit.After;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.onebusaway.android.R;
import org.onebusaway.android.app.Application;
import org.onebusaway.android.app.di.PreferencesEntryPoint;
import org.onebusaway.android.region.Region;

import androidx.test.runner.AndroidJUnit4;

import static androidx.test.InstrumentationRegistry.getTargetContext;

/**
 * Base test class extended for most OBA unit tests. Sets the OBA theme (needed for {@code attr/?}
 * elements, see #279) and defaults the API region to Puget Sound for backwards compatibility with
 * older tests, saving and restoring the previous region / custom API URL so tests stay isolated.
 */
@RunWith(AndroidJUnit4.class)
public abstract class ObaTestCase {

    private Region mOldRegion;

    private String mOldCustomApiUrl;

    @Before
    public void before() {
        // The theme needs to be set when using "attr/?" elements - see #279
        getTargetContext().setTheme(R.style.Theme_OneBusAway);

        // Save the current region / custom API URL so the override below can be undone in after().
        mOldRegion = Application.get().getCurrentRegion();
        mOldCustomApiUrl = mOldRegion == null
                ? PreferencesEntryPoint.get(getTargetContext())
                        .getString(R.string.preference_key_oba_api_url, null)
                : null;

        /*
         * Assume Puget Sound API, mainly for backwards compatibility with older tests
         * that were written before multi-region functionality. This is overwritten in some
         * subclasses so multiple regions / APIs can be tested.
         */
        PreferencesEntryPoint.get(getTargetContext())
                .setString(R.string.preference_key_oba_api_url, "api.pugetsound.onebusaway.org");
    }

    @After
    public void after() {
        if (mOldRegion != null) {
            Application.get().setCurrentRegion(mOldRegion);
        } else if (mOldCustomApiUrl != null) {
            PreferencesEntryPoint.get(getTargetContext())
                    .setString(R.string.preference_key_oba_api_url, mOldCustomApiUrl);
        } else {
            PreferencesEntryPoint.get(getTargetContext())
                    .setString(R.string.preference_key_oba_api_url, null);
            Application.get().setCurrentRegion(null);
        }
    }
}
