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
package org.onebusaway.android.io.test;

import org.junit.After;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.onebusaway.android.R;
import org.onebusaway.android.app.Application;
import org.onebusaway.android.io.ObaApi;
import org.onebusaway.android.io.request.ObaResponse;
import org.onebusaway.android.mock.ObaMock;

import androidx.test.runner.AndroidJUnit4;

import static androidx.test.InstrumentationRegistry.getTargetContext;
import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;

/**
 * Base test class extended for most OBA unit tests
 */
@RunWith(AndroidJUnit4.class)
public abstract class ObaTestCase {

    private ObaMock mMock;

    public static void assertOK(ObaResponse response) {
        assertNotNull(response);
        assertEquals(ObaApi.OBA_OK, response.getCode());
    }

    @Before
    public void before() {
        // The theme needs to be set when using "attr/?" elements - see #279
        getTargetContext().setTheme(R.style.Theme_OneBusAway);

        mMock = new ObaMock(getTargetContext());

        /*
         * Assume Puget Sound API, mainly for backwards compatibility with older tests
         * that were written before multi-region functionality. This is overwritten in some
         * subclasses so multiple regions / APIs can be tested.
         */
        Application.get().setCustomApiUrl("api.pugetsound.onebusaway.org");
    }

    @After
    public void after() {
        mMock.finish();
    }
}
