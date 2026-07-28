package org.onebusaway.android.analytics

import android.os.Build
import android.util.Log
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.concurrent.Executors
import org.json.JSONException
import org.json.JSONObject
import org.onebusaway.android.BuildConfig

/** Fire-and-forget Umami event emitter; failures never escape to callers. */
class UmamiAnalytics(serverUrl: String?, private val websiteId: String?, private val hostname: String?) {
    private val sendUrl = joinUrl(serverUrl, "api/send")
    private val userAgent = buildUserAgent()

    @Volatile private var regionName: String? = null

    fun setRegionName(regionName: String?) {
        this.regionName = regionName
    }

    fun pageView(pageUrl: String?, props: Map<String?, Any?>?) = send(null, pageUrl, props)

    fun event(name: String?, pageUrl: String?, props: Map<String?, Any?>?) = send(name, pageUrl, props)

    private fun send(name: String?, pageUrl: String?, props: Map<String?, Any?>?) {
        val payload = try {
            buildPayload(name, reducePath(pageUrl), props)
        } catch (error: Exception) {
            Log.w(TAG, "Failed to build Umami payload", error)
            return
        }
        EXECUTOR.execute { post(payload) }
    }

    private fun post(payload: String) {
        var connection: HttpURLConnection? = null
        try {
            connection = URL(sendUrl).openConnection() as HttpURLConnection
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("User-Agent", userAgent)
            connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val body = readBody(connection)
            if (!isSuccessfulIngest(code, body)) {
                Log.w(TAG, "Umami rejected event (code=$code, body=$body). Check User-Agent / website config.")
            }
        } catch (error: Exception) {
            Log.w(TAG, "Umami send failed", error)
        } finally {
            connection?.disconnect()
        }
    }

    fun buildPayload(name: String?, path: String?, props: Map<String?, Any?>?): String {
        val payload = JSONObject()
            .put("website", websiteId)
            .put("hostname", hostname)
            .put("url", path)
        if (name != null) payload.put("name", name)
        val data = JSONObject()
        regionName?.let { data.put("RegionName", it) }
        sanitizeProps(props).forEach { (key, value) -> data.put(key, value) }
        if (data.length() > 0) payload.put("data", data)
        return JSONObject().put("type", "event").put("payload", payload).toString()
    }

    companion object {
        private const val TAG = "UmamiAnalytics"
        private const val TIMEOUT_MS = 5_000
        private val EXECUTOR = Executors.newSingleThreadExecutor()

        fun reducePath(pageUrl: String?): String = try {
            pageUrl?.let { URI(it).path }?.takeIf(String::isNotEmpty) ?: "/"
        } catch (_: Exception) {
            "/"
        }

        fun isSuccessfulIngest(httpCode: Int, body: String?): Boolean {
            if (httpCode !in 200..299) return false
            val trimmed = body?.trim().orEmpty()
            return trimmed.isEmpty() || "\"beep\"" !in trimmed
        }

        fun buildUserAgent(): String = "OneBusAway/${BuildConfig.VERSION_NAME} (Android ${Build.VERSION.RELEASE}; ${Build.MODEL})"

        fun sanitizeProps(props: Map<String?, Any?>?): Map<String, Any> = buildMap {
            props.orEmpty().forEach { (key, value) ->
                if (key == null || value == null) return@forEach
                put(key, if (value is String || value is Number || value is Boolean) value else value.toString())
            }
        }

        private fun joinUrl(base: String?, suffix: String): String = if (base.orEmpty().endsWith('/')) base.orEmpty() + suffix else base.orEmpty() + "/" + suffix

        private fun readBody(connection: HttpURLConnection): String? {
            val stream = runCatching { connection.inputStream }.getOrElse { connection.errorStream } ?: return null
            return runCatching { stream.bufferedReader(Charsets.UTF_8).use { it.readText() } }.getOrNull()
        }
    }
}
