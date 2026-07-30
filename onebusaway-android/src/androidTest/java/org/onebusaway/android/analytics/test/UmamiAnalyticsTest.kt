package org.onebusaway.android.analytics.test

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.onebusaway.android.analytics.UmamiAnalytics
import org.onebusaway.android.analytics.UmamiAnalyticsReporter

@RunWith(AndroidJUnit4::class)
class UmamiAnalyticsTest {
    private fun newClient() = UmamiAnalytics("https://umami.example.com/", "wid-1", "api.example.com")

    @Test fun eventPayload() {
        val client = newClient().apply { setRegionName("Tampa Bay") }
        val root = JSONObject(client.buildPayload("Search", "/search", mapOf("query" to "bus")))
        assertEquals("event", root.getString("type"))
        val payload = root.getJSONObject("payload")
        assertEquals("wid-1", payload.getString("website"))
        assertEquals("api.example.com", payload.getString("hostname"))
        assertEquals("/search", payload.getString("url"))
        assertEquals("Search", payload.getString("name"))
        assertEquals("bus", payload.getJSONObject("data").getString("query"))
        assertEquals("Tampa Bay", payload.getJSONObject("data").getString("RegionName"))
    }

    @Test fun pageviewHasNoName() {
        val payload = JSONObject(newClient().buildPayload(null, "/stop", null)).getJSONObject("payload")
        assertFalse(payload.has("name"))
        assertEquals("/stop", payload.getString("url"))
    }

    @Test fun reducePath() {
        assertEquals("/map", UmamiAnalytics.reducePath("app://localhost/map"))
        assertEquals("/", UmamiAnalytics.reducePath("app://localhost"))
        assertEquals("/", UmamiAnalytics.reducePath(null))
        assertEquals("/search", UmamiAnalytics.reducePath("app://localhost/search?q=x"))
    }

    @Test fun successfulIngest() {
        assertFalse(UmamiAnalytics.isSuccessfulIngest(200, "{\"beep\":\"boop\"}"))
        assertTrue(UmamiAnalytics.isSuccessfulIngest(200, "some.jwt.token"))
        assertFalse(UmamiAnalytics.isSuccessfulIngest(500, "error"))
    }

    @Test fun userAgentFormat() {
        val userAgent = UmamiAnalytics.buildUserAgent()
        assertTrue(userAgent.startsWith("OneBusAway/"))
        assertTrue(userAgent.contains("Android"))
    }

    @Test fun sanitizePropsDropsNullAndStringifies() {
        val output = UmamiAnalytics.sanitizeProps(
            mapOf(
                "a" to "x",
                "b" to null,
                "c" to object {
                    override fun toString() = "obj"
                }
            )
        )
        assertEquals("x", output["a"])
        assertFalse(output.containsKey("b"))
        assertEquals("obj", output["c"])
    }

    @Test fun reporterNullEmitterIsNoOp() {
        UmamiAnalyticsReporter.reportUiEvent(null, "app://localhost/map", "id", "state")
        UmamiAnalyticsReporter.reportSearchEvent(null, "bus")
        UmamiAnalyticsReporter.reportViewStopEvent(null, "stop-1", "DISTANCE_1")
    }
}
