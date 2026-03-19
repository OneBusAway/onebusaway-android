/*
 * Copyright (C) 2014-2017 University of South Florida (sjbarbeau@gmail.com)
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
package org.onebusaway.android.mock;

import android.content.Context;

import org.onebusaway.android.io.elements.ObaRegion;
import org.onebusaway.android.util.RegionUtils;

import java.util.ArrayList;

/**
 * Provides mock region information
 */
public class MockRegion {

    public static ObaRegion getTampa(Context context) {
        ArrayList<ObaRegion> regions = RegionUtils.getRegionsFromResources(context);
        for (ObaRegion r : regions) {
            if (r.getId() == RegionUtils.TAMPA_REGION_ID) {
                return r;
            }
        }
        return null; // This should never happen
    }

    public static ObaRegion getPugetSound(Context context) {
        ArrayList<ObaRegion> regions = RegionUtils.getRegionsFromResources(context);
        for (ObaRegion r : regions) {
            if (r.getId() == RegionUtils.PUGET_SOUND_REGION_ID) {
                return r;
            }
        }
        return null; // This should never happen
    }

    public static ObaRegion getAtlanta(Context context) {
        ArrayList<ObaRegion> regions = RegionUtils.getRegionsFromResources(context);
        for (ObaRegion r : regions) {
            if (r.getId() == RegionUtils.ATLANTA_REGION_ID) {
                return r;
            }
        }
        return null; // This should never happen
    }

    /**
     * Returns a test version of the Tampa region with a path appended, but no separator (i.e.,
     * http://api.tampa.onebusaway.org/api), as the base URL
     *
     * @return a test version of the Tampa region with a path appended, but no separator (i.e.,
     * http://api.tampa.onebusaway.org/api), as the base URL
     */
    public static ObaRegion getRegionWithPathNoSeparator(Context context) {
        ObaRegion.Bounds bound = new ObaRegion.Bounds(27.976910500000002, -82.445851,
                0.5424609999999994, 0.576357999999999);
        ObaRegion.Bounds[] bounds = new ObaRegion.Bounds[1];
        bounds[0] = bound;

        return new ObaRegion(
                0,
                "Test-RegionWithPathNoSeparator",
                true,
                "https://api.tampa.onebusaway.org/api",
                "https://onebusaway.co",
                null,
                null,
                bounds,
                new ObaRegion.Open311Server[0],
                "en_US",
                "test@test.org",
                true,
                true,
                false,
                null,
                false,
                null,
                null,
                null,
                false,
                false,
                "co.bytemark.hart",
                null,
                null,
                false,
                false);
    }

    /**
     * Returns a test version of the PugetSound region without a trailing path separator (i.e.,
     * http://api.pugetsound.onebusaway.org), as the base URL
     *
     * @return a test version of the PugetSound region without a trailing path separator (i.e.,
     * http://api.pugetsound.onebusaway.org), as the base URL
     */
    public static ObaRegion getRegionNoSeparator(Context context) {
        ObaRegion.Bounds bound = new ObaRegion.Bounds(47.221315, -122.4051325,
                0.33704, 0.440483);
        ObaRegion.Bounds[] bounds = new ObaRegion.Bounds[1];
        bounds[0] = bound;

        return new ObaRegion(
                0,
                "Test-RegionWithPathNoSeparator",
                true,
                "https://api.pugetsound.onebusaway.org",
                "https://onebusaway.co",
                null,
                null,
                bounds,
                new ObaRegion.Open311Server[0],
                "en_US",
                "test@test.org",
                true,
                true,
                false,
                null,
                false,
                "https://stopinfo.pugetsound.onebusaway.org",
                null,
                null,
                false,
                false,
                null,
                null,
                null,
                false,
                false);
    }

    /**
     * Returns a test version of the Tampa region with a custom port (i.e.,
     * http://api.tampa.onebusaway.org:8088/api/), as the base URL
     *
     * @return a test version of the Tampa region with a custom port (i.e.,
     * http://api.tampa.onebusaway.org:8088/api/), as the base URL
     */
    public static ObaRegion getRegionWithPort(Context context) {
        ObaRegion.Bounds bound = new ObaRegion.Bounds(27.976910500000002, -82.445851,
                0.5424609999999994, 0.576357999999999);
        ObaRegion.Bounds[] bounds = new ObaRegion.Bounds[1];
        bounds[0] = bound;

        return new ObaRegion(
                0,
                "Test-RegionWithPort",
                true,
                "https://api.tampa.onebusaway.org:8088/api/",
                "https://onebusaway.co",
                null,
                null,
                bounds,
                new ObaRegion.Open311Server[0],
                "en_US",
                "test@test.org",
                true,
                true,
                false,
                null,
                false,
                null,
                null,
                null,
                false,
                false,
                "co.bytemark.hart",
                null,
                null,
                false,
                false);
    }

    /**
     * Returns a test version of the Tampa region without a scheme (i.e.,
     * api.tampa.onebusaway.org:8088/api/), as the base URL
     *
     * @return a test version of the Tampa region without a scheme (i.e.,
     * api.tampa.onebusaway.org:8088/api/), as the base URL
     */
    public static ObaRegion getRegionNoScheme(Context context) {
        ObaRegion.Bounds bound = new ObaRegion.Bounds(27.976910500000002, -82.445851,
                0.5424609999999994, 0.576357999999999);
        ObaRegion.Bounds[] bounds = new ObaRegion.Bounds[1];
        bounds[0] = bound;

        return new ObaRegion(
                0,
                "Test-RegionNoScheme",
                true,
                "api.tampa.onebusaway.org/api/",
                "https://onebusaway.co",
                null,
                null,
                bounds,
                new ObaRegion.Open311Server[0],
                "en_US",
                "test@test.org",
                true,
                true,
                false,
                null,
                false,
                null,
                null,
                null,
                false,
                false,
                "co.bytemark.hart",
                null,
                null,
                false,
                false);
    }

    /**
     * Returns a test version of the Tampa region using HTTPS (i.e.,
     * https://api.tampa.onebusaway.org/api/), as the base URL
     *
     * @return a test version of the Tampa region using HTTPS (i.e.,
     * https://api.tampa.onebusaway.org/api/), as the base URL
     */
    public static ObaRegion getRegionWithHttps() {
        ObaRegion.Bounds bound = new ObaRegion.Bounds(27.976910500000002, -82.445851,
                0.5424609999999994, 0.576357999999999);
        ObaRegion.Bounds[] bounds = new ObaRegion.Bounds[1];
        bounds[0] = bound;

        return new ObaRegion(
                0,
                "Test-RegionWithHttps",
                true,
                "https://api.tampa.onebusaway.org/api/",
                "https://onebusaway.co",
                null,
                null,
                bounds,
                new ObaRegion.Open311Server[0],
                "en_US",
                "test@test.org",
                true,
                true,
                false,
                null,
                false,
                null,
                null,
                null,
                false,
                false,
                "co.bytemark.hart",
                null,
                null,
                false,
                false);
    }

    /**
     * Returns a test version of the Tampa region using HTTPS and a port (i.e.,
     * https://api.tampa.onebusaway.org:8443/api/), as the base URL
     *
     * @return a test version of the Tampa region using HTTPS and a port (i.e.,
     * https://api.tampa.onebusaway.org:8443/api), as the base URL
     */
    public static ObaRegion getRegionWithHttpsAndPort() {
        ObaRegion.Bounds bound = new ObaRegion.Bounds(27.976910500000002, -82.445851,
                0.5424609999999994, 0.576357999999999);
        ObaRegion.Bounds[] bounds = new ObaRegion.Bounds[1];
        bounds[0] = bound;

        return new ObaRegion(
                0,
                "Test-RegionWithHttpsAndPort",
                true,
                "https://api.tampa.onebusaway.org:8443/api/",
                "https://onebusaway.co",
                null,
                null,
                bounds,
                new ObaRegion.Open311Server[0],
                "en_US",
                "test@test.org",
                true,
                true,
                false,
                null,
                false,
                null,
                null,
                null,
                false,
                false,
                "co.bytemark.hart",
                null,
                null,
                false,
                false);
    }

    /**
     * Returns a test version of the Tampa region without an OBA Discovery or Real-time APIs
     *
     * @return a test version of the Tampa region without an OBA Discovery or Real-time APIs
     */
    public static ObaRegion getRegionWithoutObaApis(Context context) {
        ObaRegion.Bounds bound = new ObaRegion.Bounds(27.976910500000002, -82.445851,
                0.5424609999999994, 0.576357999999999);
        ObaRegion.Bounds[] bounds = new ObaRegion.Bounds[1];
        bounds[0] = bound;

        return new ObaRegion(
                0,
                "Test-RegionWithoutOBAApis",
                true,
                "https://api.tampa.onebusaway.org/api/",
                "https://onebusaway.co",
                null,
                null,
                bounds,
                null,
                "en_US",
                "test@test.org",
                false,
                false,
                false,
                null,
                false,
                null,
                null,
                null,
                false,
                false,
                "co.bytemark.hart",
                null,
                null,
                false,
                false);
    }

    /**
     * Returns a test version of the Tampa region that is not active
     *
     * @return a test version of the Tampa region that is not active
     */
    public static ObaRegion getInactiveRegion(Context context) {
        ObaRegion.Bounds bound = new ObaRegion.Bounds(27.976910500000002, -82.445851,
                0.5424609999999994, 0.576357999999999);
        ObaRegion.Bounds[] bounds = new ObaRegion.Bounds[1];
        bounds[0] = bound;

        return new ObaRegion(
                0,
                "Test-RegionWithoutOBAApis",
                true,
                "https://api.tampa.onebusaway.org/api/",
                "https://onebusaway.co",
                null,
                null,
                bounds,
                null,
                "en_US",
                "test@test.org",
                false,
                false,
                false,
                null,
                false,
                null,
                null,
                null,
                false,
                false,
                "co.bytemark.hart",
                null,
                null,
                false,
                false);
    }
}
