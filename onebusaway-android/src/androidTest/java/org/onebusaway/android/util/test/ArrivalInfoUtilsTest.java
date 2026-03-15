package org.onebusaway.android.util.test;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.onebusaway.android.R;
import org.onebusaway.android.app.Application;
import org.onebusaway.android.io.elements.ObaArrivalInfo;
import org.onebusaway.android.ui.ArrivalInfo;
import org.onebusaway.android.util.ArrivalInfoUtils;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests to evaluate arrival info utilities, especially related to user preferences for hiding
 * scheduled arrivals and showing both arrivals by default.
 */
@RunWith(AndroidJUnit4.class)
public class ArrivalInfoUtilsTest {

    private Context context;
    private SharedPreferences prefs;
    private String hideScheduledKey;
    private String showNegativeArrivalsKey;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        prefs = Application.getPrefs();
        hideScheduledKey = context.getString(R.string.preference_key_hide_scheduled_arrivals);
        showNegativeArrivalsKey = context.getString(R.string.preference_key_show_negative_arrivals);

        prefs.edit()
                .putBoolean(hideScheduledKey, false)
                .putBoolean(showNegativeArrivalsKey, true)
                .commit();
    }

    @After
    public void tearDown() {
        prefs.edit()
                .remove(hideScheduledKey)
                .remove(showNegativeArrivalsKey)
                .commit();
    }

    /**
     * Test that by default, both scheduled-only and predicted arrivals are shown when converting
     * OBA arrival info to our internal format, and that the predicted arrival is properly marked as such.
     * @throws Exception
     */
    @Test
    public void TestShowAllByDefault() throws Exception {
        long now = System.currentTimeMillis();

        ObaArrivalInfo scheduledOnly = createArrival(
                "1_100", "8", "Downtown", "STOP_1",
                now + 10 * 60_000L, 0L,
                now + 10 * 60_000L, 0L,
                false
        );

        ObaArrivalInfo realtime = createArrival(
                "1_200", "49", "Uptown", "STOP_1",
                now + 12 * 60_000L, now + 13 * 60_000L,
                now + 12 * 60_000L, now + 13 * 60_000L,
                true
        );

        ObaArrivalInfo[] arrivals = new ObaArrivalInfo[]{scheduledOnly, realtime};

        ArrayList<ArrivalInfo> result = ArrivalInfoUtils.convertObaArrivalInfo(
                context, arrivals, null, now, false
        );

        assertEquals(2, result.size());
    }

    /**
     * Test that when the preference to hide scheduled arrivals is enabled, even though there are
     * both scheduled and real time traffic, only predicted arrivals are shown when converting
     * OBA arrival info to our internal format, and that the predicted arrival is properly marked as such.
     * @throws Exception
     */
    @Test
    public void TestHideScheduledWithBothKinds() throws Exception {
        prefs.edit().putBoolean(hideScheduledKey, true).commit();

        long now = System.currentTimeMillis();

        ObaArrivalInfo scheduledOnly = createArrival(
                "1_100", "8", "Downtown", "STOP_1",
                now + 10 * 60_000L, 0L,
                now + 10 * 60_000L, 0L,
                false
        );

        ObaArrivalInfo realtime = createArrival(
                "1_200", "49", "Uptown", "STOP_1",
                now + 12 * 60_000L, now + 13 * 60_000L,
                now + 12 * 60_000L, now + 13 * 60_000L,
                true
        );

        ObaArrivalInfo[] arrivals = new ObaArrivalInfo[]{scheduledOnly, realtime};

        ArrayList<ArrivalInfo> result = ArrivalInfoUtils.convertObaArrivalInfo(
                context, arrivals, null, now, false
        );

        assertEquals(1, result.size());
        assertTrue(result.get(0).getPredicted());
        assertEquals("49", result.get(0).getInfo().getShortName());
    }

    /**
     * Test that when the preference to hide scheduled arrivals is enabled, and there are
     * only scheduled arrivals with no real time traffic, that no arrivals are shown when converting
     * OBA arrival info to our internal format.
     * @throws Exception
     */
    @Test
    public void TestHideScheduleWithAllSchedule() throws Exception {
        prefs.edit().putBoolean(hideScheduledKey, true).commit();

        long now = System.currentTimeMillis();

        ObaArrivalInfo scheduledOnly1 = createArrival(
                "1_100", "8", "Downtown", "STOP_1",
                now + 10 * 60_000L, 0L,
                now + 10 * 60_000L, 0L,
                false
        );

        ObaArrivalInfo scheduledOnly2 = createArrival(
                "1_101", "9", "Airport", "STOP_1",
                now + 15 * 60_000L, 0L,
                now + 15 * 60_000L, 0L,
                false
        );

        ObaArrivalInfo[] arrivals = new ObaArrivalInfo[]{scheduledOnly1, scheduledOnly2};

        ArrayList<ArrivalInfo> result = ArrivalInfoUtils.convertObaArrivalInfo(
                context, arrivals, null, now, false
        );

        assertTrue(result.isEmpty());
    }


    private ObaArrivalInfo createArrival(String routeId,
                                         String shortName,
                                         String headsign,
                                         String stopId,
                                         long scheduledArrivalTime,
                                         long predictedArrivalTime,
                                         long scheduledDepartureTime,
                                         long predictedDepartureTime,
                                         boolean predicted) throws Exception {
        Constructor<ObaArrivalInfo> constructor = ObaArrivalInfo.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        ObaArrivalInfo info = constructor.newInstance();

        setField(info, "routeId", routeId);
        setField(info, "routeShortName", shortName);
        setField(info, "routeLongName", shortName);
        setField(info, "tripId", routeId + "_trip");
        setField(info, "tripHeadsign", headsign);
        setField(info, "stopId", stopId);

        setField(info, "scheduledArrivalTime", scheduledArrivalTime);
        setField(info, "predictedArrivalTime", predictedArrivalTime);
        setField(info, "scheduledDepartureTime", scheduledDepartureTime);
        setField(info, "predictedDepartureTime", predictedDepartureTime);

        setField(info, "predicted", predicted);

        // Make it behave like a normal arrival instead of first-stop departure
        setField(info, "stopSequence", 1);

        return info;
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
