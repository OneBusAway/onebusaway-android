# Umami Analytics Support Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add native Umami analytics as a third, region-configured analytics sink alongside the existing Firebase and Plausible integrations.

**Architecture:** Each region's feed entry may carry a `umamiAnalytics: { url, id }` object. The region model parses it (nested for JSON, two flat `TEXT` columns for the content provider). A hand-rolled `UmamiAnalytics` emitter (over `HttpURLConnection`, on a background executor) POSTs fire-and-forget events to `<url>/api/send` with a device-like `User-Agent`. `ObaAnalytics` fetches the emitter internally from `Application` (no call-site changes) and mirrors every event it already sends to Plausible.

**Tech Stack:** Java + Kotlin, Android `HttpURLConnection`, `org.json`, Jackson (region deserialization), SQLite content provider, JUnit instrumented tests (androidTest).

## Global Constraints

- **No new dependencies.** Use `HttpURLConnection` + `org.json`; do not add OkHttp/Retrofit.
- **Fail-safe telemetry.** Umami code must never throw into callers; swallow all exceptions, log at most. Network is fire-and-forget on a background executor with ~5s timeouts.
- **Disabled when unconfigured.** If `umamiAnalytics` (or either of `url`/`id`) is null/missing, Umami is disabled for that region — no requests.
- **Respect opt-out.** Umami emits only when the existing `isAnalyticsActive()` gate (`preferences_key_analytics`, default true) is on. No new preference UI.
- **Device-like User-Agent.** Exactly `OneBusAway/<versionName> (Android <release>; <model>)`, overriding `HttpURLConnection`'s default, or Umami's `isbot` filter silently drops events.
- **AOSP code style** (`AndroidStyle.xml`). Min SDK 21, Java 1.8, target SDK 36.
- **PR is a single squashed commit** (project rule); the per-step commits below are squashed at PR time.

## Constructor-caller inventory (critical)

`new ObaRegionElement(...)` has **ten** positional callers. Task 1 adds a 27th constructor parameter, so ALL ten must be updated in the same commit or the build (including androidTest) will not compile:

| Caller | File:line | New 27th arg |
|--------|-----------|--------------|
| `ObaContract.Regions.get` | `provider/ObaContract.java:1436` | `new ObaRegionElement.UmamiAnalyticsConfig(c.getString(23), c.getString(24))` |
| `RegionUtils.getRegionsFromProvider` | `util/RegionUtils.java:453` | `new ObaRegionElement.UmamiAnalyticsConfig(c.getString(23), c.getString(24))` |
| `RegionUtils.getRegionFromBuildFlavor` | `util/RegionUtils.java:649` | `null` (fixed-region build flavor — no Umami) |
| `MockRegion` ×8 | `androidTest/.../mock/MockRegion.java:74,116,158,200,242,284,324,364` | `null` (test regions — Umami disabled) |

## File Structure

| File | Responsibility | Action |
|------|----------------|--------|
| `io/elements/ObaRegion.java` | Region interface — add two Umami getters | Modify |
| `io/elements/ObaRegionElement.java` | Region impl — nested `UmamiAnalyticsConfig`, field, getters, constructors | Modify |
| `provider/ObaContract.java` | Column constants + single-region cursor reader (`Regions.get`) | Modify |
| `provider/ObaProvider.java` | DB version bump + migration | Modify |
| `util/RegionUtils.java` | `toContentValues` write, bulk cursor reader, fixed-region caller | Modify |
| `androidTest/.../mock/MockRegion.java` | 8 constructor callers (pass null) | Modify |
| `io/UmamiAnalytics.java` | The emitter: payload build, UA, path reduction, ingest check, send | Create |
| `io/UmamiAnalyticsReporter.kt` | Maps OBA events → emitter calls (mirror of `PlausibleAnalytics`) | Create |
| `app/Application.java` | Build/cache the emitter per region | Modify |
| `io/ObaAnalytics.java` | Fetch emitter internally; emit alongside Plausible | Modify |
| `androidTest/res/raw/regions_umami_test.json` | Parse-test fixture | Create |
| `androidTest/.../io/test/RegionsTest.java` | Parse + persistence round-trip tests | Modify |
| `androidTest/.../io/test/UmamiAnalyticsTest.java` | Emitter unit tests | Create |

Task order is dependency-driven: region model + persistence (1) → emitter (2) → reporter (3) → Application wiring (4) → ObaAnalytics integration (5) → build/verify (6).

---

### Task 1: Region model & persistence

Adds the 27th constructor parameter and everything that must move with it — all ten callers, the content-provider columns, the migration, and both the parse and round-trip tests — as one atomic, compiling commit.

**Files:**
- Modify: `onebusaway-android/src/main/java/org/onebusaway/android/io/elements/ObaRegion.java`
- Modify: `onebusaway-android/src/main/java/org/onebusaway/android/io/elements/ObaRegionElement.java`
- Modify: `onebusaway-android/src/main/java/org/onebusaway/android/provider/ObaContract.java`
- Modify: `onebusaway-android/src/main/java/org/onebusaway/android/provider/ObaProvider.java`
- Modify: `onebusaway-android/src/main/java/org/onebusaway/android/util/RegionUtils.java`
- Modify: `onebusaway-android/src/androidTest/java/org/onebusaway/android/mock/MockRegion.java`
- Create: `onebusaway-android/src/androidTest/res/raw/regions_umami_test.json`
- Test: `onebusaway-android/src/androidTest/java/org/onebusaway/android/io/test/RegionsTest.java`

**Interfaces:**
- Produces: `ObaRegion.getUmamiAnalyticsUrl(): String` (nullable), `ObaRegion.getUmamiAnalyticsId(): String` (nullable); nested type `ObaRegionElement.UmamiAnalyticsConfig` with `getUrl()`/`getId()`; content-provider columns `ObaContract.Regions.UMAMI_ANALYTICS_URL` / `UMAMI_ANALYTICS_ID`.

- [ ] **Step 1: Write the test fixture**

Create `onebusaway-android/src/androidTest/res/raw/regions_umami_test.json`:

```json
{
  "code": 200,
  "text": "OK",
  "version": 3,
  "data": {
    "limitExceeded": false,
    "list": [
      {
        "id": 0,
        "regionName": "Umami Region",
        "active": true,
        "obaBaseUrl": "https://api.umami.example.com/",
        "siriBaseUrl": null,
        "umamiAnalytics": { "url": "https://umami.example.com", "id": "abc-123-uuid" },
        "bounds": [ { "lat": 27.9, "lon": -82.4, "latSpan": 0.5, "lonSpan": 0.5 } ],
        "language": "en_US",
        "contactEmail": "test@example.com",
        "supportsObaDiscoveryApis": true,
        "supportsObaRealtimeApis": true,
        "supportsSiriRealtimeApis": false,
        "experimental": false
      },
      {
        "id": 1,
        "regionName": "No Umami Region",
        "active": true,
        "obaBaseUrl": "https://api.noumami.example.com/",
        "siriBaseUrl": null,
        "bounds": [ { "lat": 47.6, "lon": -122.3, "latSpan": 0.5, "lonSpan": 0.5 } ],
        "language": "en_US",
        "contactEmail": "test@example.com",
        "supportsObaDiscoveryApis": true,
        "supportsObaRealtimeApis": true,
        "supportsSiriRealtimeApis": false,
        "experimental": false
      }
    ]
  }
}
```

- [ ] **Step 2: Write the failing tests**

Add to `RegionsTest.java` (ensure imports: `android.content.ContentResolver`, `android.content.ContentValues`, `org.onebusaway.android.mock.Resources`, `org.onebusaway.android.provider.ObaContract`, and static `org.junit.Assert.assertNull`/`assertEquals`/`assertNotNull`):

```java
@Test
public void testUmamiAnalyticsParsing() throws Exception {
    ObaRegionsResponse response = Resources.readAs(getTargetContext(),
            Resources.getTestUri("regions_umami_test"), ObaRegionsResponse.class);
    ObaRegion[] regions = response.getRegions();

    ObaRegion withUmami = regions[0];
    assertEquals("https://umami.example.com", withUmami.getUmamiAnalyticsUrl());
    assertEquals("abc-123-uuid", withUmami.getUmamiAnalyticsId());

    ObaRegion withoutUmami = regions[1];
    assertNull(withoutUmami.getUmamiAnalyticsUrl());
    assertNull(withoutUmami.getUmamiAnalyticsId());
}

@Test
public void testUmamiAnalyticsPersistenceRoundTrip() {
    ContentResolver cr = getTargetContext().getContentResolver();
    int id = 987654;

    ContentValues values = new ContentValues();
    values.put(ObaContract.Regions._ID, id);
    values.put(ObaContract.Regions.NAME, "Umami Persist Region");
    values.put(ObaContract.Regions.OBA_BASE_URL, "https://api.example.com/");
    values.put(ObaContract.Regions.SIRI_BASE_URL, "");
    values.put(ObaContract.Regions.LANGUAGE, "en_US");
    values.put(ObaContract.Regions.CONTACT_EMAIL, "test@example.com");
    values.put(ObaContract.Regions.SUPPORTS_OBA_DISCOVERY, 1);
    values.put(ObaContract.Regions.SUPPORTS_OBA_REALTIME, 1);
    values.put(ObaContract.Regions.SUPPORTS_SIRI_REALTIME, 0);
    values.put(ObaContract.Regions.UMAMI_ANALYTICS_URL, "https://umami.example.com");
    values.put(ObaContract.Regions.UMAMI_ANALYTICS_ID, "uuid-persist-1");
    ObaContract.Regions.insertOrUpdate(getTargetContext(), id, values);

    ObaRegion region = ObaContract.Regions.get(cr, id);
    assertNotNull(region);
    assertEquals("https://umami.example.com", region.getUmamiAnalyticsUrl());
    assertEquals("uuid-persist-1", region.getUmamiAnalyticsId());

    cr.delete(ObaContract.Regions.buildUri(id), null, null);
}
```

- [ ] **Step 3: Run the tests to verify they fail**

Run: `./gradlew connectedObaGoogleDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.onebusaway.android.io.test.RegionsTest`
Expected: FAIL to compile — `getUmamiAnalyticsUrl()` / `UMAMI_ANALYTICS_URL` undefined.

- [ ] **Step 4: Add the interface getters**

In `ObaRegion.java`, immediately after the `getPlausibleAnalyticsServerUrl()` declaration (around line 79):

```java
    /**
     * @return The Umami analytics server URL for this region, or null if not configured.
     */
    public String getUmamiAnalyticsUrl();

    /**
     * @return The Umami analytics website ID for this region, or null if not configured.
     */
    public String getUmamiAnalyticsId();
```

- [ ] **Step 5: Add the nested config class to `ObaRegionElement.java`**

Add directly after the existing `Bounds` class (after line 86):

```java
    public static class UmamiAnalyticsConfig {

        private final String url;

        private final String id;

        UmamiAnalyticsConfig() {
            url = null;
            id = null;
        }

        public UmamiAnalyticsConfig(String url, String id) {
            this.url = url;
            this.id = id;
        }

        public String getUrl() {
            return url;
        }

        public String getId() {
            return id;
        }
    }
```

- [ ] **Step 6: Add the field, getters, and constructor wiring in `ObaRegionElement.java`**

Add the field after `plausibleAnalyticsServerUrl` (after line 148):

```java
    private final UmamiAnalyticsConfig umamiAnalytics;
```

In the no-arg constructor, after `plausibleAnalyticsServerUrl = "";` (line 218):

```java
        umamiAnalytics = null;
```

In the all-args constructor, change the final parameter line (line 246) to add the 27th param:

```java
                            String plausibleAnalyticsServerUrl,
                            UmamiAnalyticsConfig umamiAnalytics) {
```

and add the assignment after `this.plausibleAnalyticsServerUrl = plausibleAnalyticsServerUrl;`:

```java
        this.umamiAnalytics = umamiAnalytics;
```

Add the getters after `getPlausibleAnalyticsServerUrl()` (after line 303):

```java
    @Override
    public String getUmamiAnalyticsUrl() {
        return umamiAnalytics != null ? umamiAnalytics.getUrl() : null;
    }

    @Override
    public String getUmamiAnalyticsId() {
        return umamiAnalytics != null ? umamiAnalytics.getId() : null;
    }
```

- [ ] **Step 7: Add the content-provider column constants**

In `ObaContract.java` `RegionsColumns`, immediately after `PLAUSIBLE_ANALYTICS_SERVER_URL` (line 385, before `SIRI_BASE_URL`):

```java
        /**
         * The Umami analytics server URL for the region.
         * <P>
         * Type: TEXT
         * </P>
         */
        public static final String UMAMI_ANALYTICS_URL = "umami_analytics_url";

        /**
         * The Umami analytics website ID for the region.
         * <P>
         * Type: TEXT
         * </P>
         */
        public static final String UMAMI_ANALYTICS_ID = "umami_analytics_id";
```

- [ ] **Step 8: Update the single-region cursor reader (`ObaContract.Regions.get`)**

Append the two columns to the end of the `PROJECTION` array (after `PLAUSIBLE_ANALYTICS_SERVER_URL`, line 1426):

```java
                PLAUSIBLE_ANALYTICS_SERVER_URL,
                UMAMI_ANALYTICS_URL,
                UMAMI_ANALYTICS_ID
```

and replace the final `c.getString(22)` argument (line 1461) of the `new ObaRegionElement(...)` call:

```java
                        c.getString(22), // Plausible analytics server url
                        new ObaRegionElement.UmamiAnalyticsConfig(
                                c.getString(23),  // Umami analytics URL
                                c.getString(24))  // Umami analytics website ID
                );
```

- [ ] **Step 9: Update the bulk cursor reader (`RegionUtils.getRegionsFromProvider`)**

Append the two columns to the `PROJECTION` array (after `PLAUSIBLE_ANALYTICS_SERVER_URL`, line 424):

```java
                    ObaContract.Regions.PLAUSIBLE_ANALYTICS_SERVER_URL,
                    ObaContract.Regions.UMAMI_ANALYTICS_URL,
                    ObaContract.Regions.UMAMI_ANALYTICS_ID
```

and replace the final `c.getString(22)` argument (line 478) of the `new ObaRegionElement(...)` call:

```java
                        c.getString(22), // Plausible analytics server url
                        new ObaRegionElement.UmamiAnalyticsConfig(
                                c.getString(23),  // Umami analytics URL
                                c.getString(24))  // Umami analytics website ID
                ));
```

- [ ] **Step 10: Update the fixed-region caller (`RegionUtils.getRegionFromBuildFlavor`)**

Replace the final argument (line 669) `BuildConfig.FIXED_REGION_PLAUSIBLE_ANALYTICS_SERVER_URL);`:

```java
                BuildConfig.FIXED_REGION_PLAUSIBLE_ANALYTICS_SERVER_URL,
                null);   // No Umami config for the fixed-region build flavor
```

- [ ] **Step 11: Update the 8 `MockRegion` callers**

In `MockRegion.java`, each of the eight `new ObaRegionElement(...)` calls (lines 74, 116, 158, 200, 242, 284, 324, 364) currently ends with:

```java
                "https://onebusaway.co",
                null);
```

Change every one of the eight to:

```java
                "https://onebusaway.co",
                null,
                null);   // UmamiAnalyticsConfig — disabled in test regions
```

(The trailing `null` is the new 27th `UmamiAnalyticsConfig` argument. The `"https://onebusaway.co"` line is the sidecar URL and the preceding `null` is the Plausible URL — both unchanged.)

- [ ] **Step 12: Write the two columns in `RegionUtils.toContentValues`**

After the `PLAUSIBLE_ANALYTICS_SERVER_URL` put (line 749):

```java
        values.put(ObaContract.Regions.UMAMI_ANALYTICS_URL, region.getUmamiAnalyticsUrl());
        values.put(ObaContract.Regions.UMAMI_ANALYTICS_ID, region.getUmamiAnalyticsId());
```

- [ ] **Step 13: Bump the DB version and add the migration**

In `ObaProvider.java`, change the version constant (line 52):

```java
        private static final int DATABASE_VERSION = 34;
```

Then in `onUpgrade`, add `++oldVersion;` to the end of the existing `if (oldVersion == 32)` block and append a new block, so the tail reads:

```java
            if (oldVersion == 32){
                db.execSQL("ALTER TABLE " + ObaContract.Regions.PATH +
                        " ADD COLUMN " + ObaContract.Regions.PLAUSIBLE_ANALYTICS_SERVER_URL + " VARCHAR DEFAULT NULL");
                ++oldVersion;
            }
            if (oldVersion == 33){
                db.execSQL("ALTER TABLE " + ObaContract.Regions.PATH +
                        " ADD COLUMN " + ObaContract.Regions.UMAMI_ANALYTICS_URL + " VARCHAR DEFAULT NULL");
                db.execSQL("ALTER TABLE " + ObaContract.Regions.PATH +
                        " ADD COLUMN " + ObaContract.Regions.UMAMI_ANALYTICS_ID + " VARCHAR DEFAULT NULL");
                ++oldVersion;
            }
```

> `onCreate` calls `onUpgrade(db, 12, DATABASE_VERSION)`, so fresh installs replay this chain and get the columns. The round-trip test runs against a fresh DB, so it also verifies the migration produced the columns. The `++oldVersion;` added to the v32 block is required so the v33 block executes.

- [ ] **Step 14: Run the tests to verify they pass**

Run: `./gradlew connectedObaGoogleDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.onebusaway.android.io.test.RegionsTest`
Expected: PASS for both `testUmamiAnalyticsParsing` and `testUmamiAnalyticsPersistenceRoundTrip`.

- [ ] **Step 15: Commit**

```bash
git add onebusaway-android/src/main/java/org/onebusaway/android/io/elements/ObaRegion.java \
        onebusaway-android/src/main/java/org/onebusaway/android/io/elements/ObaRegionElement.java \
        onebusaway-android/src/main/java/org/onebusaway/android/provider/ObaContract.java \
        onebusaway-android/src/main/java/org/onebusaway/android/provider/ObaProvider.java \
        onebusaway-android/src/main/java/org/onebusaway/android/util/RegionUtils.java \
        onebusaway-android/src/androidTest/java/org/onebusaway/android/mock/MockRegion.java \
        onebusaway-android/src/androidTest/res/raw/regions_umami_test.json \
        onebusaway-android/src/androidTest/java/org/onebusaway/android/io/test/RegionsTest.java
git commit -m "Parse and persist umamiAnalytics config from region feed"
```

---

### Task 2: The `UmamiAnalytics` emitter

**Files:**
- Create: `onebusaway-android/src/main/java/org/onebusaway/android/io/UmamiAnalytics.java`
- Test: `onebusaway-android/src/androidTest/java/org/onebusaway/android/io/test/UmamiAnalyticsTest.java`

**Interfaces:**
- Produces: `new UmamiAnalytics(String serverUrl, String websiteId, String hostname)`; `void setRegionName(String)`; `void event(String name, String pageUrl, Map<String,Object> props)`; `void pageView(String pageUrl, Map<String,Object> props)`. Package-private pure helpers: `String buildPayload(String name, String path, Map)`, `static String reducePath(String)`, `static boolean isSuccessfulIngest(int, String)`, `static String buildUserAgent()`, `static Map<String,Object> sanitizeProps(Map)`.

- [ ] **Step 1: Write the failing tests**

Create `UmamiAnalyticsTest.java`:

```java
package org.onebusaway.android.io.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.onebusaway.android.io.UmamiAnalytics;

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
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew connectedObaGoogleDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.onebusaway.android.io.test.UmamiAnalyticsTest`
Expected: FAIL to compile — `UmamiAnalytics` does not exist.

- [ ] **Step 3: Create the emitter**

Create `UmamiAnalytics.java`:

```java
package org.onebusaway.android.io;

import android.os.Build;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;
import org.onebusaway.android.BuildConfig;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Fire-and-forget Umami analytics emitter. POSTs events to {@code <serverUrl>/api/send}
 * with a device-like User-Agent. All failures are swallowed; this class never throws
 * into its callers. Disabled regions never construct an instance (see Application).
 */
public class UmamiAnalytics {

    private static final String TAG = "UmamiAnalytics";

    private static final int TIMEOUT_MS = 5000;

    private static final Executor EXECUTOR = Executors.newSingleThreadExecutor();

    private final String mSendUrl;

    private final String mWebsiteId;

    private final String mHostname;

    private final String mUserAgent;

    private volatile String mRegionName;

    public UmamiAnalytics(String serverUrl, String websiteId, String hostname) {
        mSendUrl = joinUrl(serverUrl, "api/send");
        mWebsiteId = websiteId;
        mHostname = hostname;
        mUserAgent = buildUserAgent();
    }

    /** Persistent default data merged into every event (e.g. region name). */
    public void setRegionName(String regionName) {
        mRegionName = regionName;
    }

    public void pageView(String pageUrl, Map<String, Object> props) {
        send(null, pageUrl, props);
    }

    public void event(String name, String pageUrl, Map<String, Object> props) {
        send(name, pageUrl, props);
    }

    private void send(String name, String pageUrl, Map<String, Object> props) {
        final String payload;
        try {
            payload = buildPayload(name, reducePath(pageUrl), props);
        } catch (Exception e) {
            Log.w(TAG, "Failed to build Umami payload", e);
            return;
        }
        EXECUTOR.execute(new Runnable() {
            @Override
            public void run() {
                post(payload);
            }
        });
    }

    private void post(String payload) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(mSendUrl).openConnection();
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("User-Agent", mUserAgent);
            byte[] body = payload.getBytes(StandardCharsets.UTF_8);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body);
            }
            int code = conn.getResponseCode();
            String responseBody = readBody(conn);
            if (!isSuccessfulIngest(code, responseBody)) {
                Log.w(TAG, "Umami rejected event (code=" + code + ", body=" + responseBody
                        + "). Check User-Agent / website config.");
            }
        } catch (Exception e) {
            Log.w(TAG, "Umami send failed", e);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    String buildPayload(String name, String path, Map<String, Object> props) throws JSONException {
        JSONObject payload = new JSONObject();
        payload.put("website", mWebsiteId);
        payload.put("hostname", mHostname);
        payload.put("url", path);
        if (name != null) {
            payload.put("name", name);
        }
        JSONObject data = new JSONObject();
        if (mRegionName != null) {
            data.put("RegionName", mRegionName);
        }
        for (Map.Entry<String, Object> entry : sanitizeProps(props).entrySet()) {
            data.put(entry.getKey(), entry.getValue());
        }
        if (data.length() > 0) {
            payload.put("data", data);
        }
        JSONObject root = new JSONObject();
        root.put("type", "event");
        root.put("payload", payload);
        return root.toString();
    }

    static String reducePath(String pageUrl) {
        if (pageUrl == null) {
            return "/";
        }
        try {
            String path = new URI(pageUrl).getPath();
            if (path == null || path.isEmpty()) {
                return "/";
            }
            return path;
        } catch (Exception e) {
            return "/";
        }
    }

    /**
     * Umami returns HTTP 200 even when it silently drops a request (bot-like User-Agent or
     * bad config), replying with {@code {"beep":"boop"}}. Treat that as a failure.
     */
    static boolean isSuccessfulIngest(int httpCode, String body) {
        if (httpCode < 200 || httpCode >= 300) {
            return false;
        }
        if (body == null) {
            return true;
        }
        String trimmed = body.trim();
        if (trimmed.isEmpty()) {
            return true;
        }
        return !trimmed.contains("\"beep\"");
    }

    static String buildUserAgent() {
        return "OneBusAway/" + BuildConfig.VERSION_NAME
                + " (Android " + Build.VERSION.RELEASE + "; " + Build.MODEL + ")";
    }

    static Map<String, Object> sanitizeProps(Map<String, Object> props) {
        Map<String, Object> out = new HashMap<>();
        if (props == null) {
            return out;
        }
        for (Map.Entry<String, Object> entry : props.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            Object value = entry.getValue();
            if (value instanceof String || value instanceof Number || value instanceof Boolean) {
                out.put(entry.getKey(), value);
            } else {
                out.put(entry.getKey(), String.valueOf(value));
            }
        }
        return out;
    }

    private static String joinUrl(String base, String suffix) {
        if (base == null) {
            base = "";
        }
        return base.endsWith("/") ? base + suffix : base + "/" + suffix;
    }

    private static String readBody(HttpURLConnection conn) {
        InputStream stream = null;
        try {
            stream = conn.getInputStream();
        } catch (Exception e) {
            stream = conn.getErrorStream();
        }
        if (stream == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader =
                     new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        } catch (Exception e) {
            return null;
        }
        return sb.toString();
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew connectedObaGoogleDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.onebusaway.android.io.test.UmamiAnalyticsTest`
Expected: PASS (all six tests).

- [ ] **Step 5: Commit**

```bash
git add onebusaway-android/src/main/java/org/onebusaway/android/io/UmamiAnalytics.java \
        onebusaway-android/src/androidTest/java/org/onebusaway/android/io/test/UmamiAnalyticsTest.java
git commit -m "Add fire-and-forget Umami analytics emitter"
```

---

### Task 3: `UmamiAnalyticsReporter` — map OBA events to emitter calls

**Files:**
- Create: `onebusaway-android/src/main/java/org/onebusaway/android/io/UmamiAnalyticsReporter.kt`
- Test: `onebusaway-android/src/androidTest/java/org/onebusaway/android/io/test/UmamiAnalyticsTest.java`

**Interfaces:**
- Consumes: `UmamiAnalytics` from Task 2; `PlausibleAnalytics.REPORT_SEARCH_EVENT_URL`.
- Produces: `UmamiAnalyticsReporter.reportUiEvent(UmamiAnalytics?, String, String, String?)`, `reportSearchEvent(UmamiAnalytics?, String)`, `reportViewStopEvent(UmamiAnalytics?, String, String)` — each a no-op when the emitter is null. Mirrors `PlausibleAnalytics`.

- [ ] **Step 1: Write the failing null-safety test**

Add to `UmamiAnalyticsTest.java`:

```java
@Test
public void testReporterNullEmitterIsNoOp() {
    // Must not throw when Umami is unconfigured (null emitter).
    org.onebusaway.android.io.UmamiAnalyticsReporter.reportUiEvent(null, "app://localhost/map", "id", "state");
    org.onebusaway.android.io.UmamiAnalyticsReporter.reportSearchEvent(null, "bus");
    org.onebusaway.android.io.UmamiAnalyticsReporter.reportViewStopEvent(null, "stop-1", "DISTANCE_1");
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew connectedObaGoogleDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.onebusaway.android.io.test.UmamiAnalyticsTest#testReporterNullEmitterIsNoOp`
Expected: FAIL to compile — `UmamiAnalyticsReporter` does not exist.

- [ ] **Step 3: Create the reporter**

Create `UmamiAnalyticsReporter.kt` (mirrors `PlausibleAnalytics.kt`; event names match the Plausible labels so the two sinks report identically):

```kotlin
package org.onebusaway.android.io

/**
 * Maps OneBusAway analytics events to [UmamiAnalytics] emitter calls. Mirrors
 * [PlausibleAnalytics] so Umami receives the same events as Plausible. Every method
 * is a no-op when [umami] is null (region without Umami config).
 */
object UmamiAnalyticsReporter {

    private const val REPORT_VIEW_STOP_EVENT_URL = "app://localhost/stop"

    @JvmStatic
    fun reportUiEvent(umami: UmamiAnalytics?, pageURL: String, id: String, state: String?) {
        if (umami == null) return
        umami.event("Item Selected", pageURL, mapOf("item_id" to id, "item_variant" to state))
    }

    @JvmStatic
    fun reportSearchEvent(umami: UmamiAnalytics?, query: String) {
        if (umami == null) return
        umami.event("Search", PlausibleAnalytics.REPORT_SEARCH_EVENT_URL, mapOf("query" to query))
    }

    @JvmStatic
    fun reportViewStopEvent(umami: UmamiAnalytics?, id: String, stopDistance: String) {
        if (umami == null) return
        umami.pageView(REPORT_VIEW_STOP_EVENT_URL, mapOf("id" to id, "distance" to stopDistance))
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew connectedObaGoogleDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.onebusaway.android.io.test.UmamiAnalyticsTest#testReporterNullEmitterIsNoOp`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add onebusaway-android/src/main/java/org/onebusaway/android/io/UmamiAnalyticsReporter.kt \
        onebusaway-android/src/androidTest/java/org/onebusaway/android/io/test/UmamiAnalyticsTest.java
git commit -m "Add UmamiAnalyticsReporter mirroring PlausibleAnalytics"
```

---

### Task 4: Build and cache the emitter per region in `Application`

**Files:**
- Modify: `onebusaway-android/src/main/java/org/onebusaway/android/app/Application.java`

**Interfaces:**
- Consumes: `UmamiAnalytics` (Task 2); `ObaRegion.getUmamiAnalyticsUrl()/getUmamiAnalyticsId()` (Task 1).
- Produces: `Application.getUmamiInstance(): UmamiAnalytics` (nullable; null when the current region has no Umami config). Rebuilt whenever the region changes, alongside the Plausible instance.

- [ ] **Step 1: Add the field**

In `Application.java`, after `private Plausible mPlausible;` (line 106):

```java
    private org.onebusaway.android.io.UmamiAnalytics mUmami;
```

- [ ] **Step 2: Add the accessor and builder**

After the `buildPlausibleInstance` method (after line 389), add:

```java
    public org.onebusaway.android.io.UmamiAnalytics getUmamiInstance() {
        if (mUmami == null) {
            buildUmamiInstance(getCurrentRegion());
        }
        return mUmami;
    }

    private void buildUmamiInstance(ObaRegion region) {
        mUmami = null;
        if (region == null
                || region.getObaBaseUrl() == null
                || region.getUmamiAnalyticsUrl() == null
                || region.getUmamiAnalyticsId() == null) {
            return;
        }
        String host;
        try {
            host = new URI(region.getObaBaseUrl()).getHost();
        } catch (URISyntaxException e) {
            // Fire-and-forget telemetry must never throw on a malformed URL.
            return;
        }
        if (host == null) {
            return;
        }
        mUmami = new org.onebusaway.android.io.UmamiAnalytics(
                region.getUmamiAnalyticsUrl(), region.getUmamiAnalyticsId(), host);
    }
```

- [ ] **Step 3: Rebuild the emitter on region change**

Add a `buildUmamiInstance(...)` call immediately after each existing `buildPlausibleInstance(...)` call.

After `buildPlausibleInstance(region);` inside `setCurrentRegion` (line 352):

```java
                buildPlausibleInstance(region);
                buildUmamiInstance(region);
```

After `buildPlausibleInstance(getCurrentRegion());` near line 608 (the region-init path that precedes `ObaAnalytics.setRegion(...)`):

```java
            buildPlausibleInstance(getCurrentRegion());
            buildUmamiInstance(getCurrentRegion());
            ObaAnalytics.setRegion(mPlausible, mFirebaseAnalytics, getCurrentRegion().getName());
```

> `URI` / `URISyntaxException` are already imported in this file (used by `buildPlausibleInstance`). The line-352 call is nested inside an `if (regionChanged && region.getOtpBaseUrl() != null)` block — this mirrors the existing Plausible behavior exactly, and `getUmamiInstance()` lazily rebuilds from `getCurrentRegion()` on first use, so the emitter self-heals for regions reached by other paths.

- [ ] **Step 4: Verify it compiles**

Run: `./gradlew assembleObaGoogleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add onebusaway-android/src/main/java/org/onebusaway/android/app/Application.java
git commit -m "Build and cache Umami emitter per region in Application"
```

---

### Task 5: Emit Umami events from `ObaAnalytics`

**Files:**
- Modify: `onebusaway-android/src/main/java/org/onebusaway/android/io/ObaAnalytics.java`

**Interfaces:**
- Consumes: `Application.getUmamiInstance()` (Task 4); `UmamiAnalyticsReporter` (Task 3); `UmamiAnalytics.setRegionName` (Task 2).
- Produces: no signature changes — Umami is fetched internally, so the ~79 existing call sites are untouched.

- [ ] **Step 1: Emit on UI events**

In `reportUiEvent`, after `PlausibleAnalytics.reportUiEvent(plausible, pageURl, id, state);` (line 93):

```java
        PlausibleAnalytics.reportUiEvent(plausible, pageURl, id, state);
        UmamiAnalyticsReporter.reportUiEvent(Application.get().getUmamiInstance(), pageURl, id, state);
```

- [ ] **Step 2: Emit on search events**

In `reportSearchEvent`, after `PlausibleAnalytics.reportSearchEvent(plausible, searchTerm);` (line 129):

```java
        PlausibleAnalytics.reportSearchEvent(plausible, searchTerm);
        UmamiAnalyticsReporter.reportSearchEvent(Application.get().getUmamiInstance(), searchTerm);
```

- [ ] **Step 3: Emit on view-stop events**

In the private `reportViewStopEvent(...)` overload, after `PlausibleAnalytics.reportViewStopEvent(plausible, stopId, proximityToStopCategory);` (line 190):

```java
        PlausibleAnalytics.reportViewStopEvent(plausible, stopId, proximityToStopCategory);
        UmamiAnalyticsReporter.reportViewStopEvent(Application.get().getUmamiInstance(), stopId, proximityToStopCategory);
```

- [ ] **Step 4: Set the region as default data in `setRegion`**

In `setRegion`, after `analytics.setUserProperty(...)` (line 202):

```java
        analytics.setUserProperty(Application.get().getString(R.string.analytics_label_region_name), regionName);
        UmamiAnalytics umami = Application.get().getUmamiInstance();
        if (umami != null) {
            umami.setRegionName(regionName);
        }
```

> `Application` is already imported and used here; `UmamiAnalytics`/`UmamiAnalyticsReporter` share the `org.onebusaway.android.io` package with `ObaAnalytics`, so no imports are needed. The custom-URL `setRegion` path (region null) reaches `getUmamiInstance()`, which returns null and is guarded by the `if (umami != null)` check — no NPE.

- [ ] **Step 5: Verify it compiles**

Run: `./gradlew assembleObaGoogleDebug`
Expected: BUILD SUCCESSFUL.

> No new unit test is added here: `ObaAnalytics` depends on static `FirebaseAnalytics` and `Application` singletons that the existing instrumented suite does not mock (there is no `ObaAnalyticsTest` today). This task is verified by compilation, by the emitter/reporter tests from Tasks 2–3, and by the manual dashboard verification in Task 6.

- [ ] **Step 6: Commit**

```bash
git add onebusaway-android/src/main/java/org/onebusaway/android/io/ObaAnalytics.java
git commit -m "Emit Umami events alongside Plausible from ObaAnalytics"
```

---

### Task 6: Full build, test suite, and manual verification

**Files:** none (verification only).

- [ ] **Step 1: Run the full region + Umami test suite**

Run: `./gradlew connectedObaGoogleDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.onebusaway.android.io.test.RegionsTest,org.onebusaway.android.io.test.UmamiAnalyticsTest`
Expected: PASS — all parse, persistence, and emitter tests green.

- [ ] **Step 2: Build the debug APK and install**

Run: `./gradlew installObaGoogleDebug`
Expected: BUILD SUCCESSFUL, app installs.

- [ ] **Step 3: Manual dashboard verification**

With a build whose current region has live `umamiAnalytics` config (temporarily add a `umamiAnalytics` object to that region's entry in `src/main/res/raw/regions_v3.json`, or point at a region server that serves it):
- Launch the app, view a stop, run a search.
- Confirm events appear in the Umami dashboard under the correct website ID, with `url` paths like `/stop`, `/search`, `/map`, and `RegionName` present in event data.
- Switch to / start in a region with **no** `umamiAnalytics` and confirm **no** events are sent.
- Toggle the "send anonymous data" preference off and confirm Umami stops emitting.
- Watch logcat for `UmamiAnalytics` warnings — a `{"beep":"boop"}` rejection log indicates the User-Agent or website config is wrong.

- [ ] **Step 4: Revert any temporary `regions_v3.json` test edit**

Ensure no debugging change to `regions_v3.json` is committed unless intentionally enabling Umami for a production region.

---

## Notes on iOS parity

This plan mirrors the shipped iOS implementation (`~/repos/onebusaway/ios`): identical `{type:"event", payload:{website,hostname,url,name?,data?}}` wire format, reduced `url` paths, `hostname` = region OBA host, region name as default data on every event, device-like User-Agent, and beep-boop ingest detection. Intentional differences: Android reuses its existing per-call `isAnalyticsActive()` opt-out gate rather than nil-ing the emitter, and event *name strings* follow Android's existing Plausible labels (`"Item Selected"`, `"Search"`).
