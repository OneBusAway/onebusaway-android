/*
 * Copyright (C) 2015-2017 University of South Florida (sjbarbeau@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.onebusaway.android.io.test;

import org.junit.Test;
import org.onebusaway.android.UriAssert;
import org.onebusaway.android.app.Application;
import org.onebusaway.android.io.elements.ObaRegion;
import org.onebusaway.android.io.request.ObaArrivalInfoRequest;
import org.onebusaway.android.mock.MockRegion;
import org.onebusaway.android.util.RegionUtils;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;

import static androidx.test.InstrumentationRegistry.getTargetContext;
import static junit.framework.Assert.fail;

/**
 * Tests various URL formats to ensure URI building/encoding is correct
 */
public class UrlFormatTest extends ObaTestCase {

    @Test
    public void testBasicUrlUsingCustomUrl() {
        /*
        Puget Sound fits this profile (i.e., http://api.pugetsound.onebusaway.org),
        so use Puget Sound base URL
        */
        ObaRegion region = MockRegion.getPugetSound(getTargetContext());

        // Test by setting API directly
        Application.get().setCustomApiUrl(region.getObaBaseUrl());
        _assertBasicUrl();
    }

    @Test
    public void testBasicUrlUsingRegion() {
        /*
        Puget Sound fits this profile (i.e., http://api.pugetsound.onebusaway.org),
        so use Puget Sound base URL
        */
        ObaRegion region = MockRegion.getPugetSound(getTargetContext());

        // Test by setting region
        Application.get().setCurrentRegion(region);
        _assertBasicUrl();
    }

    private void _assertBasicUrl() {
        ObaArrivalInfoRequest.Builder builder =
                new ObaArrivalInfoRequest.Builder(getTargetContext(), "1_29261");
        ObaArrivalInfoRequest request = builder.build();
        UriAssert.assertUriMatch(
                "https://api.pugetsound.onebusaway.org/api/where/arrivals-and-departures-for-stop/1_29261.json",
                new HashMap<String, String>() {{
                    put("key", "*");
                    put("version", "2");
                }},
                request
        );
    }

    @Test
    public void testUrlWithSpacesUsingCustomUrl() {
        // Tampa fits this profile, so use Tampa URL
        ObaRegion region = MockRegion.getTampa(getTargetContext());

        // Test by setting API directly
        Application.get().setCustomApiUrl(region.getObaBaseUrl());
        _assertUrlWithSpaces();
    }

    @Test
    public void testUrlWithSpacesUsingRegion() {
        // Tampa fits this profile, so use Tampa URL
        ObaRegion region = MockRegion.getTampa(getTargetContext());

        // Test by setting region
        Application.get().setCurrentRegion(region);
        _assertUrlWithSpaces();
    }

    private void _assertUrlWithSpaces() {
        // Spaces are included in agency name
        ObaArrivalInfoRequest.Builder builder =
                new ObaArrivalInfoRequest.Builder(getTargetContext(),
                        "Hillsborough Area Regional Transit_3105");
        ObaArrivalInfoRequest request = builder.build();
        UriAssert.assertUriMatch(
                "https://api.tampa.onebusaway.org/api/api/where/arrivals-and-departures-for-stop/Hillsborough%20Area%20Regional%20Transit_3105.json",
                new HashMap<String, String>() {{
                    put("key", "*");
                    put("version", "2");
                }},
                request
        );
    }

    @Test
    public void testUrlWithPathAndSeparatorUsingCustomUrl() {
        // Tampa fits this profile, so use Tampa URL
        ObaRegion region = MockRegion.getTampa(getTargetContext());

        // Test by setting API directly
        Application.get().setCustomApiUrl(region.getObaBaseUrl());
        _assertUrlWithPathAndSeparator();
    }

    @Test
    public void testUrlWithPathAndSeparatorUsingRegion() {
        // Tampa fits this profile, so use Tampa URL
        ObaRegion region = MockRegion.getTampa(getTargetContext());

        // Test by setting region
        Application.get().setCurrentRegion(region);
        _assertUrlWithPathAndSeparator();
    }

    private void _assertUrlWithPathAndSeparator() {
        ObaArrivalInfoRequest.Builder builder =
                new ObaArrivalInfoRequest.Builder(getTargetContext(),
                        "Hillsborough Area Regional Transit_3105");
        ObaArrivalInfoRequest request = builder.build();
        UriAssert.assertUriMatch(
                "https://api.tampa.onebusaway.org/api/api/where/arrivals-and-departures-for-stop/Hillsborough%20Area%20Regional%20Transit_3105.json",
                new HashMap<String, String>() {{
                    put("key", "*");
                    put("version", "2");
                }},
                request
        );
    }

    @Test
    public void testUrlWithPathNoSeparatorUsingCustomUrl() {
        ObaRegion region = MockRegion.getRegionWithPathNoSeparator(getTargetContext());

        // Test by setting API directly
        Application.get().setCustomApiUrl(region.getObaBaseUrl());
        _assertUrlWithPathNoSeparator();
    }

    @Test
    public void testUrlWithPathNoSeparatorUsingRegion() {
        ObaRegion region = MockRegion.getRegionWithPathNoSeparator(getTargetContext());

        // Test by setting region
        Application.get().setCurrentRegion(region);
        _assertUrlWithPathNoSeparator();
    }

    private void _assertUrlWithPathNoSeparator() {
        ObaArrivalInfoRequest.Builder builder =
                new ObaArrivalInfoRequest.Builder(getTargetContext(),
                        "Hillsborough Area Regional Transit_3105");
        ObaArrivalInfoRequest request = builder.build();
        UriAssert.assertUriMatch(
                "https://api.tampa.onebusaway.org/api/api/where/arrivals-and-departures-for-stop/Hillsborough%20Area%20Regional%20Transit_3105.json",
                new HashMap<String, String>() {{
                    put("key", "*");
                    put("version", "2");
                }},
                request
        );
    }

    @Test
    public void testUrlNoSeparatorUsingCustomUrl() {
        ObaRegion region = MockRegion.getRegionNoSeparator(getTargetContext());

        // Test by setting API directly
        Application.get().setCustomApiUrl(region.getObaBaseUrl());
        _assertUrlNoSeparator();
    }

    @Test
    public void testUrlNoSeparatorUsingRegion() {
        ObaRegion region = MockRegion.getRegionNoSeparator(getTargetContext());

        // Test by setting region
        Application.get().setCurrentRegion(region);
        _assertUrlNoSeparator();
    }

    private void _assertUrlNoSeparator() {
        ObaArrivalInfoRequest.Builder builder =
                new ObaArrivalInfoRequest.Builder(getTargetContext(),
                        "1_29261");
        ObaArrivalInfoRequest request = builder.build();
        UriAssert.assertUriMatch(
                "https://api.pugetsound.onebusaway.org/api/where/arrivals-and-departures-for-stop/1_29261.json",
                new HashMap<String, String>() {{
                    put("key", "*");
                    put("version", "2");
                }},
                request
        );
    }

    @Test
    public void testUrlWithPortUsingCustomUrl() {
        ObaRegion region = MockRegion.getRegionWithPort(getTargetContext());

        // Test by setting API directly
        Application.get().setCustomApiUrl(region.getObaBaseUrl());
        _assertUrlWithPort();
    }

    @Test
    public void testUrlWithPortUsingRegion() {
        ObaRegion region = MockRegion.getRegionWithPort(getTargetContext());

        // Test by setting region
        Application.get().setCurrentRegion(region);
        _assertUrlWithPort();
    }

    private void _assertUrlWithPort() {
        ObaArrivalInfoRequest.Builder builder =
                new ObaArrivalInfoRequest.Builder(getTargetContext(),
                        "Hillsborough Area Regional Transit_3105");
        ObaArrivalInfoRequest request = builder.build();
        UriAssert.assertUriMatch(
                "https://api.tampa.onebusaway.org:8088/api/api/where/arrivals-and-departures-for-stop/Hillsborough%20Area%20Regional%20Transit_3105.json",
                new HashMap<String, String>() {{
                    put("key", "*");
                    put("version", "2");
                }},
                request
        );
    }

    @Test
    public void testUrlNoSchemeUsingCustomUrl() {
        ObaRegion region = MockRegion.getRegionNoScheme(getTargetContext());

        // Test by setting API directly
        Application.get().setCustomApiUrl(region.getObaBaseUrl());
        _assertUrlNoScheme();

        /*
        We don't have a similar test for regions, since all regions in OBA Server Directory should
        be a full URL with scheme (which is tested in "testRegionBaseUrls()")
        */
    }

    private void _assertUrlNoScheme() {
        ObaArrivalInfoRequest.Builder builder =
                new ObaArrivalInfoRequest.Builder(getTargetContext(),
                        "Hillsborough Area Regional Transit_3105");
        ObaArrivalInfoRequest request = builder.build();
        UriAssert.assertUriMatch(
                "https://api.tampa.onebusaway.org/api/api/where/arrivals-and-departures-for-stop/Hillsborough%20Area%20Regional%20Transit_3105.json",
                new HashMap<String, String>() {{
                    put("key", "*");
                    put("version", "2");
                }},
                request
        );
    }

    @Test
    public void testHttps() {
        ObaRegion region = MockRegion.getRegionWithHttps();

        // Test by setting API directly
        Application.get().setCustomApiUrl(region.getObaBaseUrl());
        _assertUrlHttps();
    }

    private void _assertUrlHttps() {
        ObaArrivalInfoRequest.Builder builder =
                new ObaArrivalInfoRequest.Builder(getTargetContext(),
                        "Hillsborough Area Regional Transit_3105");
        ObaArrivalInfoRequest request = builder.build();
        UriAssert.assertUriMatch(
                "https://api.tampa.onebusaway.org/api/api/where/arrivals-and-departures-for-stop/Hillsborough%20Area%20Regional%20Transit_3105.json",
                new HashMap<String, String>() {{
                    put("key", "*");
                    put("version", "2");
                }},
                request
        );
    }

    @Test
    public void testHttpsAndPort() {
        ObaRegion region = MockRegion.getRegionWithHttpsAndPort();

        // Test by setting API directly
        Application.get().setCustomApiUrl(region.getObaBaseUrl());
        _assertUrlHttpsWithPort();
    }

    private void _assertUrlHttpsWithPort() {
        ObaArrivalInfoRequest.Builder builder =
                new ObaArrivalInfoRequest.Builder(getTargetContext(),
                        "Hillsborough Area Regional Transit_3105");
        ObaArrivalInfoRequest request = builder.build();
        UriAssert.assertUriMatch(
                "https://api.tampa.onebusaway.org:8443/api/api/where/arrivals-and-departures-for-stop/Hillsborough%20Area%20Regional%20Transit_3105.json",
                new HashMap<String, String>() {{
                    put("key", "*");
                    put("version", "2");
                }},
                request
        );
    }

    @Test
    public void testRegionBaseUrls() {
        // Checks all bundled region base URLs to make sure they are real URLs
        ArrayList<ObaRegion> regions = RegionUtils.getRegionsFromResources(getTargetContext());
        for (ObaRegion r : regions) {
            try {
                URL url = new URL(r.getObaBaseUrl());
            } catch (MalformedURLException e) {
                fail("Region '" + r.getName() + "' has an invalid base URL: " + e.getMessage());
            }
        }
    }

    @Test
    public void testRegionTwitterUrls() {
        // Checks all bundled region Twitter URLs to make sure they are real URLs
        ArrayList<ObaRegion> regions = RegionUtils.getRegionsFromResources(getTargetContext());
        for (ObaRegion r : regions) {
            try {
                if (r.getTwitterUrl() != null && !r.getTwitterUrl().isEmpty()) {
                    URL url = new URL(r.getTwitterUrl());
                }
            } catch (MalformedURLException e) {
                fail("Region '" + r.getName() + "' has an invalid Twitter URL: " + e.getMessage());
            }
        }
    }
}
