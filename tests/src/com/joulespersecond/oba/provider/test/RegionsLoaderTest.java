package com.joulespersecond.oba.provider.test;

import com.joulespersecond.oba.region.ObaRegion;
import com.joulespersecond.oba.region.ObaRegionsLoader;
import com.joulespersecond.oba.request.test.ObaLoaderTestCase;

import java.util.ArrayList;

public class RegionsLoaderTest extends ObaLoaderTestCase {

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
        // Sorted by Name
        _assertTampa(regions.get(2));
        _assertPugetSound(regions.get(1));

        // Now do this again so we can test getting it from the DB.
        loader = new ObaRegionsLoader(getContext());
        regions = getLoaderResultSynchronously(loader);
        assertNotNull(regions);
        assertEquals(3, regions.size());
        _assertTampa(regions.get(2));
        _assertPugetSound(regions.get(1));
    }
}
