package com.joulespersecond.oba.test;

import com.joulespersecond.oba.ObaApi;
import com.joulespersecond.oba.ObaArray;
import com.joulespersecond.oba.ObaData;
import com.joulespersecond.oba.ObaResponse;
import com.joulespersecond.oba.ObaStop;

public class StopsForRouteTest extends ObaTestCase {
    private int mStopCount = -1;

    public void testV1() {
        ObaApi.setVersion(ObaApi.VERSION1);
        //doTest1(ObaApi.getStopsForRoute(getContext(), "1_43"));
    }

    public void testV2() {
        ObaApi.setVersion(ObaApi.VERSION2);
        doTest1(ObaApi.getStopsForRoute(getContext(), "1_43"));
    }

    private void doTest1(ObaResponse response) {
        assertOK(response);

        ObaData data = response.getData();
        assertNotNull(data);
        ObaArray<ObaStop> stops = data.getStops();
        assertNotNull(stops);
        if (mStopCount == -1) {
            mStopCount = stops.length();
        }
        else {
            assertEquals(stops.length(), mStopCount);
        }
    }
}
