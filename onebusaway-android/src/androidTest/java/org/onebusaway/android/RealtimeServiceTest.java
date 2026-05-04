package org.onebusaway.android;

import android.content.Intent;
import android.os.Bundle;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.onebusaway.android.directions.realtime.RealtimeService;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class RealtimeServiceTest {

    @Test
    public void test_realtime_service_handles_start_checks() {

        // Arrange
        Bundle bundle = new Bundle();

        bundle.putInt("selected_itinerary", 0);

        Intent intent = new Intent(
                InstrumentationRegistry.getInstrumentation().getTargetContext(),
                RealtimeService.class
        );

        intent.setAction("org.onebusaway.android.INTENT_START_CHECKS");
        intent.putExtras(bundle);

        // Act
        InstrumentationRegistry
                .getInstrumentation()
                .getTargetContext()
                .startService(intent);

        // Assert
        assertNotNull(intent.getExtras());
        assertEquals("org.onebusaway.android.INTENT_START_CHECKS", intent.getAction());
    }
}