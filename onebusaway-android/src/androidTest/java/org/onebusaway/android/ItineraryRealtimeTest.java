package org.onebusaway.android;

import android.content.Intent;

import android.os.Bundle;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class ItineraryRealtimeTest {

    @Test
    public void realtime_updates_start_when_itinerary_screen_opens() {

        Bundle params = new Bundle();
        params.putString("test_key", "test_value");

        Intent intent = new Intent();

        //
        intent.setClassName(
                "org.onebusaway.android",
                "org.onebusaway.android.ui.RealtimeService.java"
        );

        intent.putExtras(params);

        try (ActivityScenario<?> scenario =
                     ActivityScenario.launch(intent)) {

            scenario.onActivity(activity -> {

                boolean realtimeEnabled = false;

                try {
                    java.lang.reflect.Field field =
                            activity.getClass().getDeclaredField("isRealtimeEnabled");

                    field.setAccessible(true);
                    realtimeEnabled = field.getBoolean(activity);

                } catch (Exception ignored) {}

                assertTrue(realtimeEnabled);
            });
        }
    }
}