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

package org.onebusaway.android.util.test;

import org.onebusaway.android.io.elements.ObaRegion;
import org.onebusaway.android.mock.MockRegion;
import org.onebusaway.android.util.LocationUtils;
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

    Location mLondonLoc;

    Location mOriginLoc;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mPsRegion = MockRegion.getPugetSound(getContext());
        mTampaRegion = MockRegion.getTampa(getContext());
        mAtlantaRegion = MockRegion.getAtlanta(getContext());

        // Region locations
        mSeattleLoc = LocationUtils.makeLocation(47.6097, -122.3331);
        mTampaLoc = LocationUtils.makeLocation(27.9681, -82.4764);
        mAtlantaLoc = LocationUtils.makeLocation(33.7550, -84.3900);

        // Far locations
        mLondonLoc = LocationUtils.makeLocation(51.5072, -0.1275);
        mOriginLoc = LocationUtils.makeLocation(0, 0);
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

        boolean useLimiter = false;

        /**
         * Without distance limiter - this should always return a region, no matter how far away
         * it is
         */
        // Close to region
        ObaRegion closestRegion = RegionUtils.getClosestRegion(list, mSeattleLoc, useLimiter);
        assertEquals(closestRegion.getId(), RegionUtils.PUGET_SOUND_REGION_ID);

        closestRegion = RegionUtils.getClosestRegion(list, mTampaLoc, useLimiter);
        assertEquals(closestRegion.getId(), RegionUtils.TAMPA_REGION_ID);

        closestRegion = RegionUtils.getClosestRegion(list, mAtlantaLoc, useLimiter);
        assertEquals(closestRegion.getId(), RegionUtils.ATLANTA_REGION_ID);

        // Far from region
        closestRegion = RegionUtils.getClosestRegion(list, mLondonLoc, useLimiter);
        assertEquals(closestRegion.getId(), RegionUtils.ATLANTA_REGION_ID);

        closestRegion = RegionUtils.getClosestRegion(list, mOriginLoc, useLimiter);
        assertEquals(closestRegion.getId(), RegionUtils.TAMPA_REGION_ID);

        /**
         * With distance limiter - this should only return a region if its within
         * the RegionUtils.DISTANCE_LIMITER threshold, otherwise null should be returned
         */
        useLimiter = true;

        // Close to region
        closestRegion = RegionUtils.getClosestRegion(list, mSeattleLoc, useLimiter);
        assertEquals(closestRegion.getId(), RegionUtils.PUGET_SOUND_REGION_ID);

        closestRegion = RegionUtils.getClosestRegion(list, mTampaLoc, useLimiter);
        assertEquals(closestRegion.getId(), RegionUtils.TAMPA_REGION_ID);

        closestRegion = RegionUtils.getClosestRegion(list, mAtlantaLoc, useLimiter);
        assertEquals(closestRegion.getId(), RegionUtils.ATLANTA_REGION_ID);

        // Far from region
        closestRegion = RegionUtils.getClosestRegion(list, mLondonLoc, useLimiter);
        assertNull(closestRegion);

        closestRegion = RegionUtils.getClosestRegion(list, mOriginLoc, useLimiter);
        assertNull(closestRegion);
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
