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

import org.onebusaway.android.UriAssert;
import org.onebusaway.android.app.Application;
import org.onebusaway.android.io.elements.ObaRegion;
import org.onebusaway.android.io.elements.ObaRouteSchedule;
import org.onebusaway.android.io.elements.ObaStop;
import org.onebusaway.android.io.request.ObaScheduleForStopRequest;
import org.onebusaway.android.io.request.ObaScheduleForStopResponse;
import org.onebusaway.android.mock.MockRegion;

import android.text.format.Time;

import java.util.HashMap;

@SuppressWarnings("serial")
public class ScheduleForStopTest extends ObaTestCase {

    // TODO - fix this test in context of regions and loading multiple URLs
    // Currently mixes Tampa URL with KCM data
    public void testKCMStopRequest() {
        // Test by setting API directly
        Application.get().setCustomApiUrl("api.pugetsound.onebusaway.org");
        callKCMStopRequest();

        // Test by setting region
        ObaRegion ps = MockRegion.getPugetSound(getContext());
        assertNotNull(ps);
        Application.get().setCurrentRegion(ps);
        callKCMStopRequest();
    }

    private void callKCMStopRequest() {
        ObaScheduleForStopRequest request =
                new ObaScheduleForStopRequest.Builder(getContext(), "1_75403")
                        .build();
        UriAssert.assertUriMatch(
                "http://api.pugetsound.onebusaway.org/api/where/schedule-for-stop/1_75403.json",
                new HashMap<String, String>() {{
                    put("key", "*");
                    put("version", "2");
                }},
                request
        );
    }

    public void testKCMStop() {
        ObaScheduleForStopResponse response =
                new ObaScheduleForStopRequest.Builder(getContext(), "1_75403")
                        .build()
                        .call();
        // This is just to ensure we can call it, but since we don't
        // know the day we can't really assume very much.
        assertOK(response);
        final ObaStop stop = response.getStop();
        assertEquals("1_75403", stop.getId());
        // TODO: This is no longer included?
        //final ObaStopSchedule.CalendarDay[] days = response.getCalendarDays();
        //assertTrue(days.length > 0);
        final ObaRouteSchedule[] schedules = response.getRouteSchedules();
        assertTrue(schedules.length > 0);
        final ObaRouteSchedule.Direction[] dirs = schedules[0].getDirectionSchedules();
        assertTrue(dirs.length > 0);
    }

    // TODO - fix this test in context of regions and loading multiple URLs
    // Currently mixes Tampa URL with KCM data
    public void testKCMStopRequestWithDate() {
        // Test by setting API directly
        Application.get().setCustomApiUrl("api.pugetsound.onebusaway.org");
        callKCMStopRequestWithDate();

        // Test by setting region
        ObaRegion ps = MockRegion.getPugetSound(getContext());
        assertNotNull(ps);
        Application.get().setCurrentRegion(ps);
        callKCMStopRequestWithDate();
    }

    private void callKCMStopRequestWithDate() {
        Time time = new Time();
        time.year = 2012;
        time.month = 6;
        time.monthDay = 30;
        ObaScheduleForStopRequest request =
                new ObaScheduleForStopRequest.Builder(getContext(), "1_75403")
                        .setDate(time)
                        .build();
        UriAssert.assertUriMatch(
                "http://api.pugetsound.onebusaway.org/api/where/schedule-for-stop/1_75403.json",
                new HashMap<String, String>() {{
                    put("date", "2012-07-30");
                    put("key", "*");
                    put("version", "2");
                }},
                request
        );
    }

    public void testKCMStopResponseWithDate() throws Exception {
        Time time = new Time();
        time.year = 2012;
        time.month = 6;
        time.monthDay = 30;
        ObaScheduleForStopRequest request =
                new ObaScheduleForStopRequest.Builder(getContext(), "1_75403")
                        .setDate(time)
                        .build();
        ObaScheduleForStopResponse response = request.call();
        assertOK(response);
        final ObaStop stop = response.getStop();
        assertEquals("1_75403", stop.getId());
        final ObaRouteSchedule[] schedules = response.getRouteSchedules();
        assertTrue(schedules.length > 0);
        assertEquals("1_25", schedules[0].getRouteId());
        final ObaRouteSchedule.Direction[] dirs = schedules[0].getDirectionSchedules();
        assertTrue(dirs.length > 0);
        assertEquals("DOWNTOWN SEATTLE UNIVERSITY DISTRICT", dirs[0].getTripHeadsign());
        final ObaRouteSchedule.Time[] times = dirs[0].getStopTimes();
        assertTrue(times.length > 0);
        assertEquals("1_20969000", times[0].getTripId());
    }
}
