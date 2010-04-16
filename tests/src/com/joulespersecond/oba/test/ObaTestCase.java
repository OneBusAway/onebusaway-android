package com.joulespersecond.oba.test;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.test.AndroidTestCase;

import com.joulespersecond.oba.ObaAgency;
import com.joulespersecond.oba.ObaApi;
import com.joulespersecond.oba.ObaResponse;
import com.joulespersecond.oba.ObaRoute;

class ObaTestCase extends AndroidTestCase {
    static final String PROD = "api.onebusaway.org";
    static final String SOAK = "soak-api.onebusaway.org";

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        // We need to explicitly set our server to soak to test V2 APIs.
        SharedPreferences.Editor edit =
            PreferenceManager.getDefaultSharedPreferences(getContext()).edit();
        edit.putString("preferences_oba_api_servername", SOAK);
        edit.commit();
    }

    static void assertOK(ObaResponse response) {
        assertNotNull(response);
        assertEquals(response.getCode(), ObaApi.OBA_OK);
    }

    static void checkRoute(ObaRoute route,
            String id,
            String name,
            String agencyId,
            String agencyName) {
        assertEquals(route.getId(), id);
        assertEquals(route.getShortName(), name);
        ObaAgency agency = route.getAgency();
        assertNotNull(agency);
        assertEquals(agency.getId(), agencyId);
        assertEquals(agency.getName(), agencyName);
        assertEquals(route.getAgencyId(), agencyId);
        assertEquals(route.getAgencyName(), agencyName);
    }
}
