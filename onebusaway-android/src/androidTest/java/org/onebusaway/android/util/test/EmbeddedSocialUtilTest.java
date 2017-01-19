package org.onebusaway.android.util.test;

import org.onebusaway.android.util.EmbeddedSocialUtils;

import android.test.AndroidTestCase;

/**
 * Tests embedded social utilities
 */
public class EmbeddedSocialUtilTest extends AndroidTestCase {

    public void testDiscussionTitles() {
        long regionId;
        String stopId;
        String actual;
        String expected;

        regionId = 0;
        stopId = "Hello world \\ # % + / ? \u0082";
        actual = EmbeddedSocialUtils.createStopDiscussionTitle(regionId, stopId);
        expected = "stop_0_Hello world XA== Iw== JQ== Kw== Lw== Pw== woI=";
        assertEquals(expected, actual);
    }
}
