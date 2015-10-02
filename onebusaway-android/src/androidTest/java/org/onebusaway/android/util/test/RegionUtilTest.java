package org.onebusaway.android.util.test;

import org.onebusaway.android.io.elements.ObaRegion;
import org.onebusaway.android.mock.MockRegion;
import org.onebusaway.android.util.LocationUtil;
import org.onebusaway.android.util.RegionUtils;

import android.location.Location;
import android.test.AndroidTestCase;

import java.util.ArrayList;

/**
 * Tests to evaluate region utilities
 */
public class RegionUtilTest extends AndroidTestCase {

    public static final float APPROXIMATE_DISTANCE_EQUALS_THRESHOLD = 2;  // meters

    // Mock regions to use in tests
    ObaRegion mPsRegion;

    ObaRegion mTampaRegion;

    ObaRegion mAtlantaRegion;

    // Locations to use in tests
    Location mSeattleLoc;

    Location mTampaLoc;

    Location mAtlantaLoc;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mPsRegion = MockRegion.getPugetSound(getContext());
        mTampaRegion = MockRegion.getTampa(getContext());
        mAtlantaRegion = MockRegion.getAtlanta(getContext());

        mSeattleLoc = LocationUtil.makeLocation(47.6097, -122.3331);
        mTampaLoc = LocationUtil.makeLocation(27.9681, -82.4764);
        mAtlantaLoc = LocationUtil.makeLocation(33.7550, -84.3900);
    }

    public void testGetDistanceAway() {
        float distance = RegionUtils.getDistanceAway(mPsRegion, mSeattleLoc);
        assertApproximateEquals(1210, distance);

        distance = RegionUtils.getDistanceAway(mTampaRegion, mTampaLoc);
        assertApproximateEquals(3160, distance);

        distance = RegionUtils.getDistanceAway(mAtlantaRegion, mAtlantaLoc);
        assertApproximateEquals(3927, distance);
    }

    public void testGetClosestRegion() {
        ArrayList<ObaRegion> list = new ArrayList<>();
        list.add(mPsRegion);
        list.add(mTampaRegion);
        list.add(mAtlantaRegion);

        ObaRegion closestRegion = RegionUtils.getClosestRegion(list, mSeattleLoc);
        assertEquals(closestRegion.getId(), MockRegion.PUGET_SOUND_REGION_ID);

        closestRegion = RegionUtils.getClosestRegion(list, mTampaLoc);
        assertEquals(closestRegion.getId(), MockRegion.TAMPA_REGION_ID);

        closestRegion = RegionUtils.getClosestRegion(list, mAtlantaLoc);
        assertEquals(closestRegion.getId(), MockRegion.ATLANTA_REGION_ID);
    }

    public void testGetRegionSpan() {
        double[] results = new double[4];
        RegionUtils.getRegionSpan(mTampaRegion, results);

        assertApproximateEquals(0.542461f, (float) results[0]);
        assertApproximateEquals(0.576357f, (float) results[1]);
        assertApproximateEquals(27.9769105f, (float) results[2]);
        assertApproximateEquals(-82.445851f, (float) results[3]);
    }

    public void testIsLocationWithinRegion() {
        assertTrue(RegionUtils.isLocationWithinRegion(mSeattleLoc, mPsRegion));
        assertFalse(RegionUtils.isLocationWithinRegion(mTampaLoc, mPsRegion));
        assertFalse(RegionUtils.isLocationWithinRegion(mAtlantaLoc, mPsRegion));

        assertTrue(RegionUtils.isLocationWithinRegion(mTampaLoc, mTampaRegion));
        assertFalse(RegionUtils.isLocationWithinRegion(mSeattleLoc, mTampaRegion));
        assertFalse(RegionUtils.isLocationWithinRegion(mAtlantaLoc, mTampaRegion));

        assertTrue(RegionUtils.isLocationWithinRegion(mAtlantaLoc, mAtlantaRegion));
        assertFalse(RegionUtils.isLocationWithinRegion(mSeattleLoc, mAtlantaRegion));
        assertFalse(RegionUtils.isLocationWithinRegion(mTampaLoc, mAtlantaRegion));
    }

    public void testIsRegionUsable() {
        assertTrue(RegionUtils.isRegionUsable(mPsRegion));
        assertTrue(RegionUtils.isRegionUsable(mTampaRegion));
        assertTrue(RegionUtils.isRegionUsable(mAtlantaRegion));

        assertFalse(RegionUtils.isRegionUsable(MockRegion.getRegionWithoutObaApis(getContext())));
        assertFalse(RegionUtils.isRegionUsable(MockRegion.getInactiveRegion(getContext())));
    }

    /**
     * Asserts that the expectedDistance is approximately equal to the actual distance, within
     * APPROXIMATE_DISTANCE_EQUALS_THRESHOLD
     */
    private void assertApproximateEquals(float expectedDistance, float actualDistance) {
        assertTrue(expectedDistance - APPROXIMATE_DISTANCE_EQUALS_THRESHOLD <= actualDistance &&
                actualDistance <= expectedDistance + APPROXIMATE_DISTANCE_EQUALS_THRESHOLD);
    }
}
