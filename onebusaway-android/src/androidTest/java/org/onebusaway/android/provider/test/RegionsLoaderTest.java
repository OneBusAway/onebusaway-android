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
package org.onebusaway.android.provider.test;

import org.junit.Test;
import org.onebusaway.android.io.elements.ObaRegion;
import org.onebusaway.android.io.test.ObaLoaderTestCase;
import org.onebusaway.android.util.RegionUtils;

import java.util.ArrayList;

import static androidx.test.InstrumentationRegistry.getTargetContext;
import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertNull;

/**
 * Tests loading regions
 */
public class RegionsLoaderTest extends ObaLoaderTestCase {

    @Test
    public void testLoader() {
        // Load regions from resources
        ArrayList<ObaRegion> regionsFromResources = RegionUtils
                .getRegionsFromResources(getTargetContext());

        // Save to provider
        RegionUtils.saveToProvider(getTargetContext(), regionsFromResources);

        // Retrieve from provider
        ArrayList<ObaRegion> regions = RegionUtils.getRegionsFromProvider(getTargetContext());
        assertNotNull(regions);
        assertEquals(6, regions.size());  // Number of production regions

        // Production regions
        _assertTampa(regions.get(0));
        _assertPugetSound(regions.get(1));
    }

    private void assertBounds(ObaRegion.Bounds bound,
                              double lat, double lon, double latSpan, double lonSpan) {
        assertEquals(lat, bound.getLat());
        assertEquals(lon, bound.getLon());
        assertEquals(latSpan, bound.getLatSpan());
        assertEquals(lonSpan, bound.getLonSpan());
    }

    private void _assertTampa(ObaRegion tampa) {
        assertEquals(0, tampa.getId());
        assertEquals("Tampa Bay", tampa.getName());
        ObaRegion.Bounds[] bounds = tampa.getBounds();
        assertNotNull(bounds);
        assertEquals(2, bounds.length);
        // 27.976910500000002:-82.445851:0.5424609999999994:0.576357999999999
        assertBounds(bounds[0],
                27.976910500000002,
                -82.445851,
                0.5424609999999994,
                0.576357999999999);
        // 27.919249999999998:-82.652145:0.47208000000000183:0.3967700000000036
        assertBounds(bounds[1],
                27.919249999999998,
                -82.652145,
                0.47208000000000183,
                0.3967700000000036);
        assertEquals("https://otp.prod.obahart.org/otp/", tampa.getOtpBaseUrl());
        assertEquals("otp-tampa@onebusaway.org", tampa.getOtpContactEmail());
        assertEquals("co.bytemark.hart", tampa.getPaymentAndroidAppId());
        assertNull(tampa.getPaymentWarningTitle());
        assertNull(tampa.getPaymentWarningBody());
    }

    private void _assertPugetSound(ObaRegion ps) {
        assertEquals(1, ps.getId());
        assertEquals("Puget Sound", ps.getName());
        ObaRegion.Bounds[] bounds = ps.getBounds();
        assertNotNull(bounds);
        assertEquals(9, bounds.length);
        // 47.221315:-122.4051325:0.33704:0.440483
        assertBounds(bounds[0],
                47.221315,
                -122.4051325,
                0.33704,
                0.440483);
        // 47.5607395:-122.1462785:0.743251:0.720901
        assertBounds(bounds[1],
                47.5607395,
                -122.1462785,
                0.743251,
                0.720901);
        // 47.556288:-122.4013255:0.090694:0.126793
        assertBounds(bounds[2],
                47.556288,
                -122.4013255,
                0.090694,
                0.126793);
        assertEquals("http://tpng.api.soundtransit.org/tripplanner/st/", ps.getOtpBaseUrl());
        assertEquals("co.bytemark.tgt", ps.getPaymentAndroidAppId());
        assertEquals("Check before you buy!", ps.getPaymentWarningTitle());
        assertEquals("The mobile fare payment app for Puget Sound does not support all transit service shown in OneBusAway. Please check that a ticket is eligible for your agency and route before you purchase!", ps.getPaymentWarningBody());
    }
}
