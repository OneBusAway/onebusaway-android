package org.onebusaway.android.analytics.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.test.runner.AndroidJUnit4;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.onebusaway.android.analytics.UmamiAnalytics;
import org.onebusaway.android.analytics.UmamiAnalyticsReporter;

import java.util.HashMap;
import java.util.Map;

@RunWith(AndroidJUnit4.class)
public class UmamiAnalyticsTest {

    private UmamiAnalytics newClient() {
        return new UmamiAnalytics("https://umami.example.com/", "wid-1", "api.example.com");
    }

    @Test
    public void testEventPayload() throws Exception {
        UmamiAnalytics client = newClient();
        client.setRegionName("Tampa Bay");
        Map<String, Object> props = new HashMap<>();
        props.put("query", "bus");
        String json = client.buildPayload("Search", "/search", props);

        JSONObject root = new JSONObject(json);
        assertEquals("event", root.getString("type"));
        JSONObject payload = root.getJSONObject("payload");
        assertEquals("wid-1", payload.getString("website"));
        assertEquals("api.example.com", payload.getString("hostname"));
        assertEquals("/search", payload.getString("url"));
        assertEquals("Search", payload.getString("name"));
        JSONObject data = payload.getJSONObject("data");
        assertEquals("bus", data.getString("query"));
        assertEquals("Tampa Bay", data.getString("RegionName"));
    }

    @Test
    public void testPageviewHasNoName() throws Exception {
        String json = newClient().buildPayload(null, "/stop", null);
        JSONObject payload = new JSONObject(json).getJSONObject("payload");
        assertFalse(payload.has("name"));
        assertEquals("/stop", payload.getString("url"));
    }

    @Test
    public void testReducePath() {
        assertEquals("/map", UmamiAnalytics.reducePath("app://localhost/map"));
        assertEquals("/", UmamiAnalytics.reducePath("app://localhost"));
        assertEquals("/", UmamiAnalytics.reducePath(null));
        assertEquals("/search", UmamiAnalytics.reducePath("app://localhost/search?q=x"));
    }

    @Test
    public void testIsSuccessfulIngest() {
        assertFalse(UmamiAnalytics.isSuccessfulIngest(200, "{\"beep\":\"boop\"}"));
        assertTrue(UmamiAnalytics.isSuccessfulIngest(200, "some.jwt.token"));
        assertFalse(UmamiAnalytics.isSuccessfulIngest(500, "error"));
    }

    @Test
    public void testUserAgentFormat() {
        String ua = UmamiAnalytics.buildUserAgent();
        assertTrue(ua.startsWith("OneBusAway/"));
        assertTrue(ua.contains("Android"));
    }

    @Test
    public void testSanitizePropsDropsNullAndStringifies() {
        Map<String, Object> in = new HashMap<>();
        in.put("a", "x");
        in.put("b", null);
        in.put("c", new Object() { public String toString() { return "obj"; } });
        Map<String, Object> out = UmamiAnalytics.sanitizeProps(in);
        assertEquals("x", out.get("a"));
        assertFalse(out.containsKey("b"));
        assertEquals("obj", out.get("c"));
    }

    @Test
    public void testReporterNullEmitterIsNoOp() {
        // Must not throw when Umami is unconfigured (null emitter).
        UmamiAnalyticsReporter.reportUiEvent(null, "app://localhost/map", "id", "state");
        UmamiAnalyticsReporter.reportSearchEvent(null, "bus");
        UmamiAnalyticsReporter.reportViewStopEvent(null, "stop-1", "DISTANCE_1");
    }
}
