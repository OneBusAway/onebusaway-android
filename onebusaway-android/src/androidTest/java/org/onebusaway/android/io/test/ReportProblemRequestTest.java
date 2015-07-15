package org.onebusaway.android.io.test;


import org.onebusaway.android.UriAssert;
import org.onebusaway.android.io.request.ObaReportProblemWithStopRequest;
import org.onebusaway.android.io.request.ObaReportProblemWithStopResponse;
import org.onebusaway.android.io.request.ObaReportProblemWithTripRequest;
import org.onebusaway.android.io.request.ObaReportProblemWithTripResponse;

import java.util.HashMap;

/**
 * Tests Report Problems API requests and responses
 */
public class ReportProblemRequestTest extends ObaTestCase {

    public void testProblemWithStopRequest() {
        ObaReportProblemWithStopRequest.Builder builder =
                new ObaReportProblemWithStopRequest.Builder(getContext(), "1_75403");
        ObaReportProblemWithStopRequest request = builder.build();
        ObaReportProblemWithStopResponse response = request.call();
        assertOK(response);
    }

    public void testProblemWithStopBuilder() {
        ObaReportProblemWithStopRequest.Builder builder =
                new ObaReportProblemWithStopRequest.Builder(getContext(), "1_75403");
        ObaReportProblemWithStopRequest request = builder.build();
        assertNotNull(request);
        UriAssert.assertUriMatch(
                "http://api.pugetsound.onebusaway.org/api/where/report-problem-with-stop/1_75403.json",
                new HashMap<String, String>() {{
                    put("key", "*");
                    put("version", "2");
                }},
                request
        );
    }

    public void testProblemWithTripRequest() {
        ObaReportProblemWithTripRequest.Builder builder =
                new ObaReportProblemWithTripRequest.Builder(getContext(), "1_28322560");
        ObaReportProblemWithTripRequest request = builder.build();
        ObaReportProblemWithTripResponse response = request.call();
        assertOK(response);
    }

    public void testProblemWithTripBuilder() {
        ObaReportProblemWithTripRequest.Builder builder =
                new ObaReportProblemWithTripRequest.Builder(getContext(), "1_28322560");
        ObaReportProblemWithTripRequest request = builder.build();
        assertNotNull(request);
        UriAssert.assertUriMatch(
                "http://api.pugetsound.onebusaway.org/api/where/report-problem-with-trip/1_28322560.json",
                new HashMap<String, String>() {{
                    put("key", "*");
                    put("version", "2");
                }},
                request
        );
    }
}
