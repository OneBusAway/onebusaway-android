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

    public String buildPayload(String name, String path, Map<String, Object> props) throws JSONException {
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

    public static String reducePath(String pageUrl) {
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
    public static boolean isSuccessfulIngest(int httpCode, String body) {
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

    public static String buildUserAgent() {
        return "OneBusAway/" + BuildConfig.VERSION_NAME
                + " (Android " + Build.VERSION.RELEASE + "; " + Build.MODEL + ")";
    }

    public static Map<String, Object> sanitizeProps(Map<String, Object> props) {
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
