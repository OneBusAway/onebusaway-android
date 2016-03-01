/*
 * Copyright (C) 2016 University of South Florida (cagricetin@mail.usf.edu)
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
import org.onebusaway.android.io.request.ObaReportProblemWithStopRequest;
import org.onebusaway.android.mock.MockRegion;

import java.util.HashMap;

public class ReportProblemWithStopRequestTest extends ObaTestCase {

    public void testPugetSoundReportProblemRequestUsingRegion() {
        // Test by setting region
        ObaRegion ps = MockRegion.getPugetSound(getContext());
        assertNotNull(ps);
        Application.get().setCurrentRegion(ps);
        _assertPugetSoundReportProblemRequest();
    }

    private void _assertPugetSoundReportProblemRequest() {
        ObaReportProblemWithStopRequest.Builder builder =
                new ObaReportProblemWithStopRequest.Builder(getContext(), "1_29261");
        builder.setCode("stop_location_wrong")
                .setUserLocation(28.0586583, -82.416445)
                .setUserLocationAccuracy(22)
                .setUserComment("test");

        ObaReportProblemWithStopRequest request = builder.build();

        UriAssert.assertUriMatch(
                "http://api.pugetsound.onebusaway.org/api/where/report-problem-with-stop.json?" +
                        "stopId=1_29261&code=stop_location_wrong&data=%7B%22code%22%3A%22stop_location_wrong%22%7D" +
                        "&userComment=test&userLat=28.0586583&userLon=-82.416445&userLocationAccuracy=22&app_ver=58&" +
                        "app_uid=fc35c268f18c0929249cdad89e8d5fcc&version=2&" +
                        "key=v1_BktoDJ2gJlu6nLM6LsT9H8IUbWc%3DcGF1bGN3YXR0c0BnbWFpbC5jb20%3D",
                new HashMap<String, String>() {{
                    put("key", "*");
                    put("version", "2");
                }},
                request
        );
    }

    public void testHARTReportProblemRequestUsingRegion() {
        // Test by setting region
        ObaRegion ps = MockRegion.getTampa(getContext());
        assertNotNull(ps);
        Application.get().setCurrentRegion(ps);
        _assertHARTReportProblemRequest();
    }

    private void _assertHARTReportProblemRequest() {
        ObaReportProblemWithStopRequest.Builder builder =
                new ObaReportProblemWithStopRequest.Builder(getContext(), "Hillsborough Area Regional Transit_4551");
        builder.setCode("stop_location_wrong")
                .setUserLocation(28.0586583, -82.416445)
                .setUserLocationAccuracy(22)
                .setUserComment("test");

        ObaReportProblemWithStopRequest request = builder.build();

        UriAssert.assertUriMatch(
                "http://api.tampa.onebusaway.org/api/api/where/report-problem-with-stop.json?" +
                        "stopId=Hillsborough%20Area%20Regional%20Transit_4551&code=stop_location_wrong&data=%7B%22code%22%3A%22stop_location_wrong%22%7D" +
                        "&userComment=test&userLat=28.0586583&userLon=-82.416445&userLocationAccuracy=22&app_ver=58&" +
                        "app_uid=fc35c268f18c0929249cdad89e8d5fcc&version=2&" +
                        "key=v1_BktoDJ2gJlu6nLM6LsT9H8IUbWc%3DcGF1bGN3YXR0c0BnbWFpbC5jb20%3D",
                new HashMap<String, String>() {{
                    put("key", "*");
                    put("version", "2");
                }},
                request
        );
    }

    public void testStagingReportProblemRequestUsingRegionCustomUrl() {
        // Test by setting custom region
        Application.get().setCustomApiUrl("app.staging.obahart.org");
        _assertStagingReportProblemRequestCustomUrl();
    }

    private void _assertStagingReportProblemRequestCustomUrl() {
        ObaReportProblemWithStopRequest.Builder builder =
                new ObaReportProblemWithStopRequest.Builder(getContext(), "Hillsborough Area Regional Transit_4551");
        builder.setCode("stop_location_wrong")
                .setUserLocation(28.0586583, -82.416445)
                .setUserLocationAccuracy(22)
                .setUserComment("test");

        ObaReportProblemWithStopRequest request = builder.build();

        UriAssert.assertUriMatch(
                "http://app.staging.obahart.org/api/where/report-problem-with-stop.json?" +
                        "stopId=Hillsborough%20Area%20Regional%20Transit_4551&code=stop_location_wrong&data=%7B%22code%22%3A%22stop_location_wrong%22%7D" +
                        "&userComment=test&userLat=28.0586583&userLon=-82.416445&userLocationAccuracy=22&app_ver=58&" +
                        "app_uid=fc35c268f18c0929249cdad89e8d5fcc&version=2&" +
                        "key=v1_BktoDJ2gJlu6nLM6LsT9H8IUbWc%3DcGF1bGN3YXR0c0BnbWFpbC5jb20%3D",
                new HashMap<String, String>() {{
                    put("key", "*");
                    put("version", "2");
                }},
                request
        );
    }
}
