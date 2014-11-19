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
package org.onebusaway.android;

/*
import android.app.Instrumentation;
import android.test.ActivityInstrumentationTestCase2;

import com.joulespersecond.oba.request.ObaResponse;
import ObaTestCase;
import com.joulespersecond.seattlebusbot.MapViewActivity;
import com.joulespersecond.seattlebusbot.map.StopsController;

public class MapViewActivityTest extends
        ActivityInstrumentationTestCase2<MapViewActivity> {

    private MapViewActivity mActivity;

    public MapViewActivityTest() {
        super("com.joulespersecond.seattlebusbot", MapViewActivity.class);
    }

    // We can't use setUp() and tearDown() because we want to start
    // the app in different modes
    public interface WaitForStopsListener {
        void onStops(StopsController controller);
    }

    public void waitForStops(final WaitForStopsListener listener) throws InterruptedException {
        // This can't be run on the UI thread.
        Object wait = new Object();
        synchronized (wait) {
            mActivity.setStopWait(wait);
            wait.wait(120*1000);
            // Do we have stops?
            final StopsController controller = mActivity.getStopsController();
            // The Stops Controller isn't thread-safe.
            mActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    listener.onStops(controller);
                }
            });
        }
    }

    public void testDefault() throws InterruptedException {
        // Not sure what we want to do here.
        // Send a key? Wait for our location?
        // setActivityInitialTouchMode(false);
        Instrumentation instr = getInstrumentation();
        setActivityIntent(MapViewActivity.makeIntent(instr.getContext(),
                null,
                47.610980,
                -122.33845));
        mActivity = getActivity();
        assertNotNull(mActivity);
        waitForStops(new WaitForStopsListener() {
            @Override
            public void onStops(StopsController controller) {
                ObaResponse response = controller.getResponse();
                ObaTestCase.assertOK(response);
            }
        });
    }

    public void testRouteMode() throws InterruptedException {
        Instrumentation instr = getInstrumentation();
        setActivityIntent(MapViewActivity.makeIntent(instr.getContext(), "1_49"));
        mActivity = getActivity();
        assertNotNull(mActivity);
        waitForStops(new WaitForStopsListener() {
            @Override
            public void onStops(StopsController controller) {
                ObaResponse response = controller.getResponse();
                ObaTestCase.assertOK(response);
            }
        });
    }
}
*/
