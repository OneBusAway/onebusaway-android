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
package org.onebusaway.android.io.test;

import org.junit.After;
import org.junit.Before;
import org.onebusaway.android.io.ObaApi;
import org.onebusaway.android.io.ObaConnectionFactory;
import org.onebusaway.android.mock.MockConnectionFactory;

import static androidx.test.InstrumentationRegistry.getTargetContext;

/**
 * Tests loading data from Loaders for OBA
 */
public abstract class ObaLoaderTestCase extends LoaderTestCase {

    private MockConnectionFactory mMockFactory = null;

    private ObaConnectionFactory mOldFactory = null;

    @Before
    public void before() {
        enableMock();
    }

    @After
    public void after() {
        disableMock();
    }

    protected void enableMock() {
        if (mMockFactory == null) {
            mMockFactory = new MockConnectionFactory(getTargetContext());
        }
        mOldFactory = ObaApi.getDefaultContext().setConnectionFactory(mMockFactory);
    }

    protected void disableMock() {
        if (mOldFactory != null) {
            ObaApi.getDefaultContext().setConnectionFactory(mOldFactory);
        }
    }
}
