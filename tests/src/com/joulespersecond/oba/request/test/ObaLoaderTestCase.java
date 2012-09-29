package com.joulespersecond.oba.request.test;

import com.joulespersecond.oba.ObaApi;
import com.joulespersecond.oba.ObaConnectionFactory;
import com.joulespersecond.oba.mock.MockConnectionFactory;

public class ObaLoaderTestCase extends LoaderTestCase {
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

    protected void enableMock() {
        if (mMockFactory == null) {
            mMockFactory = new MockConnectionFactory(getContext());
        }
        mOldFactory = ObaApi.getDefaultContext().setConnectionFactory(mMockFactory);
    }

    protected void disableMock() {
        if (mOldFactory != null) {
            ObaApi.getDefaultContext().setConnectionFactory(mOldFactory);
        }
    }
}
