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
package com.joulespersecond.oba.request.test;

import com.joulespersecond.oba.ObaApi;
import com.joulespersecond.oba.ObaConnectionFactory;
import com.joulespersecond.oba.mock.MockConnectionFactory;
import com.joulespersecond.oba.request.ObaResponse;

import android.test.AndroidTestCase;

public class ObaTestCase extends AndroidTestCase {
    private MockConnectionFactory mMockFactory = null;
    private ObaConnectionFactory mOldFactory = null;

    @Override
    protected void setUp() {
        enableMock();
    }

    @Override
    protected void tearDown() {
        disableMock();
    }

    public static void assertOK(ObaResponse response) {
        assertNotNull(response);
        assertEquals(ObaApi.OBA_OK, response.getCode());
    }

    protected void enableMock() {
        if (mMockFactory == null) {
            mMockFactory = new MockConnectionFactory(getContext());
        }
        mOldFactory = ObaApi.setConnectionFactory(mMockFactory);
    }

    protected void disableMock() {
        if (mOldFactory != null) {
            ObaApi.setConnectionFactory(mOldFactory);
        }
    }
}
