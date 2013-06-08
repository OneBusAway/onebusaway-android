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
package com.joulespersecond.oba.provider.test;

import com.joulespersecond.oba.request.test.ObaLoaderTestCase;

public class RegionsLoaderTest extends ObaLoaderTestCase {
    // This currently doesn't work anymore because we can't use
    // the OBA (Mock) connection to get this in production.
    /*
    private void assertBounds(ObaRegion.Bounds bound,
            double lat, double lon, double latSpan, double lonSpan) {
        assertEquals(lat, bound.getLat());
        assertEquals(lon, bound.getLon());
        assertEquals(latSpan, bound.getLatSpan());
        assertEquals(lonSpan, bound.getLonSpan());
    }

    private void _assertTampa(ObaRegion tampa) {
        assertEquals(1, tampa.getId());
        assertEquals("Tampa", tampa.getName());
        ObaRegion.Bounds[] bounds = tampa.getBounds();
        assertNotNull(bounds);
        assertEquals(1, bounds.length);
        // 27.976910500000002:82.445851:0.5424609999999994:0.576357999999999
        assertBounds(bounds[0],
                27.976910500000002,
                82.445851,
                0.5424609999999994,
                0.576357999999999);
    }

    private void _assertPugetSound(ObaRegion ps) {
        assertEquals(2, ps.getId());
        assertEquals("Puget Sound", ps.getName());
        ObaRegion.Bounds[] bounds = ps.getBounds();
        assertNotNull(bounds);
        assertEquals(3, bounds.length);
        // 47.221315000000004:-122.4051325:0.3370399999999947:0.4404830000000004
        assertBounds(bounds[0],
                47.221315000000004,
                -122.4051325,
                0.3370399999999947,
                0.4404830000000004);
        // 47.5607395:-122.1462785:0.7432510000000008:0.720901000000012
        assertBounds(bounds[1],
                47.5607395,
                -122.1462785,
                0.7432510000000008,
                0.720901000000012);
        // 47.556288:-122.4013255:0.09069399999999916:0.12679299999999216
        assertBounds(bounds[2],
                47.556288,
                -122.4013255,
                0.09069399999999916,
                0.12679299999999216);
    }

    public void testLoader() throws InterruptedException {
        ObaRegionsLoader loader = new ObaRegionsLoader(getContext(), true);
        ArrayList<ObaRegion> regions = getLoaderResultSynchronously(loader);
        assertNotNull(regions);
        assertEquals(3, regions.size());
        _assertTampa(regions.get(0));
        _assertPugetSound(regions.get(1));

        // Now do this again so we can test getting it from the DB.
        loader = new ObaRegionsLoader(getContext());
        regions = getLoaderResultSynchronously(loader);
        assertNotNull(regions);
        assertEquals(3, regions.size());
        _assertTampa(regions.get(0));
        _assertPugetSound(regions.get(1));
    }
    */
}
