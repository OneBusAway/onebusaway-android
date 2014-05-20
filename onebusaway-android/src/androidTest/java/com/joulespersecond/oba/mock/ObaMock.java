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
package com.joulespersecond.oba.mock;

import com.joulespersecond.oba.ObaApi;
import com.joulespersecond.oba.ObaConnectionFactory;
import com.joulespersecond.oba.elements.ObaRegion;
import com.joulespersecond.seattlebusbot.Application;

import android.content.Context;

public class ObaMock {

    private final MockConnectionFactory mMockFactory;

    private final ObaConnectionFactory mOldFactory;

    private ObaRegion mOldRegion = null;

    private String mOldCustomApiUrl = null;

    public ObaMock(Context context) {
        mMockFactory = new MockConnectionFactory(context);
        mOldFactory = ObaApi.getDefaultContext().setConnectionFactory(mMockFactory);

        // Save the current region or custom API URL
        if (Application.get().getCurrentRegion() != null) {
            mOldRegion = Application.get().getCurrentRegion();
        } else {
            mOldCustomApiUrl = Application.get().getCustomApiUrl();
        }
    }

    public void finish() {
        ObaApi.getDefaultContext().setConnectionFactory(mOldFactory);

        /*
         * Restore the previous region or custom API URL
         */
        if (mOldRegion == null && mOldCustomApiUrl == null) {
            // Both were previously blank (e.g., on build server), so clear both
            Application.get().setCustomApiUrl(null);
            Application.get().setCurrentRegion(null);
            return;
        }

        // A region or a custom API was previously saved
        if (mOldRegion != null) {
            Application.get().setCurrentRegion(mOldRegion);
        } else {
            Application.get().setCustomApiUrl(mOldCustomApiUrl);
        }
    }
}
