/*
 * Copyright (C) 2016 University of South Florida (sjbarbeau@gmail.com)
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
package org.onebusaway.android.util.test;

import org.onebusaway.android.report.ui.util.ServiceUtils;

import android.test.AndroidTestCase;

/**
 * Tests to evaluate issue reporting utilities
 */
public class ReportUtilTest extends AndroidTestCase {

    /**
     * Test our heuristic text matching that's used to identify pure transit Open311 deployments
     * that do not support the Open311 group or keyword elements for explicit matching.
     */
    public void testServiceKeywordMatching() {
        String[] stopServiceNamesMatch = {
                "Incorrect/Missing Stop ID",
                "Trash at Bus Stop",
                "WiFi on bus isn't working",
                "Positive comments (complement bus driver, etc.)",
                "Route/trip is missing"};

        String[] tripServiceNamesMatch = {"Arrival times/schedules",
                "PSTA - Arrival times/schedules"};

        // Match sure all stop-related service names match
        for (String serviceName : stopServiceNamesMatch) {
            assertTrue(ServiceUtils.isTransitStopServiceByText(serviceName));
        }

        // Match sure all trip/arrival time-related service names match
        for (String serviceName : tripServiceNamesMatch) {
            assertTrue(ServiceUtils.isTransitTripServiceByText(serviceName));
        }

        String[] serviceNamesNoMatch = {
                "Business",
                "Monkey Business",
                "Somethingbus With More Words After It"};

        // Match sure we don't get false positives for stop matching
        for (String serviceName : serviceNamesNoMatch) {
            assertFalse(ServiceUtils.isTransitStopServiceByText(serviceName));
        }

        // Match sure we don't get false positives for trip matching
        for (String serviceName : serviceNamesNoMatch) {
            assertFalse(ServiceUtils.isTransitStopServiceByText(serviceName));
        }
    }
}
