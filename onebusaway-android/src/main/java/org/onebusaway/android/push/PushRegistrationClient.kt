/*
 * Copyright (C) 2026 Open Transit Software Foundation
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
package org.onebusaway.android.push

import android.content.Context
import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.HttpURLConnection
import javax.inject.Inject
import javax.inject.Singleton
import org.onebusaway.android.R
import org.onebusaway.android.api.contract.PushRegistrationWebService
import org.onebusaway.android.api.contract.sidecarRegionUrl
import org.onebusaway.android.util.runCatchingCancellable
import retrofit2.Response

/**
 * Carries a push-registration server rejection to the remote error channel. A dedicated type so these
 * group as their own Crashlytics issue rather than mixing into generic [IllegalStateException]s.
 */
class PushRegistrationException(message: String) : Exception(message)

/**
 * Performs the two push_registrations calls (issue #1957) and answers a single question about each:
 * **did the server end up in the state we wanted?** Everything a caller would otherwise have to get
 * right about an unreliable network lives here — URL assembly, which non-2xx codes still count as
 * success, and which failures are visible where (all log locally; only systematic rejections report
 * remotely) — so [PushRegistrationManager] can reason about reconciliation rather than about HTTP.
 *
 * Nothing here retries. A false return means "not achieved this time"; the manager's next reconcile
 * pass tries again, which is the only retry policy the feature needs given it already runs on every
 * app foreground.
 */
@Singleton
class PushRegistrationClient internal constructor(
    private val service: PushRegistrationWebService,
    // The resolved /api/v2/regions/ segment (a string resource, so it can't be a compile-time const).
    private val regionsPath: String,
    // Seams for the Android-only reporting sinks, so failure handling is assertable from a plain JVM
    // test: android.util.Log and Crashlytics are both unavailable there.
    private val logWarning: (String, Throwable?) -> Unit,
    private val reportError: (Throwable) -> Unit
) {

    /** Production constructor Hilt builds from: resolves the seams from [context] and delegates. */
    @Inject
    constructor(
        @ApplicationContext context: Context,
        service: PushRegistrationWebService
    ) : this(
        service = service,
        regionsPath = context.getString(R.string.arrivals_reminders_api_endpoint),
        logWarning = { message, cause -> Log.w(TAG, message, cause) },
        reportError = { FirebaseCrashlytics.getInstance().recordException(it) }
    )

    /** POSTs [target]. True only on a 2xx (204) success. */
    suspend fun register(target: PushRegistration): Boolean = runCatchingCancellable {
        val response = service.register(
            url = registrationUrl(target),
            token = target.token,
            locale = target.locale,
            testDevice = target.testDevice,
            // Required by the server for a test device, omitted otherwise (see the service KDoc).
            description = target.description
        )
        val registered = response.isSuccessful
        if (!registered) reportHttpFailure("register", target, response)
        registered
    }.onFailure { logTransportFailure("register", target, it) }.getOrDefault(false)

    /**
     * DELETEs [previous]. True on 204 **or 404** — the row already being gone is the state we wanted, so
     * the caller can clear its record either way.
     */
    suspend fun unregister(previous: PushRegistration): Boolean = runCatchingCancellable {
        val response = service.unregister(url = registrationUrl(previous), token = previous.token)
        val removed = response.isSuccessful || response.code() == HttpURLConnection.HTTP_NOT_FOUND
        if (!removed) reportHttpFailure("unregister", previous, response)
        removed
    }.onFailure { logTransportFailure("unregister", previous, it) }.getOrDefault(false)

    /** `{sidecarBaseUrl}/api/v2/regions/{regionId}/push_registrations`. */
    private fun registrationUrl(registration: PushRegistration): String = sidecarRegionUrl(
        sidecarBaseUrl = registration.sidecarBaseUrl,
        regionsPath = regionsPath,
        regionId = registration.regionId,
        resource = "push_registrations"
    )

    /**
     * Reports a non-2xx response. Without this an HTTP error would be *invisible*: a failed status is a
     * perfectly successful call as far as [runCatchingCancellable] is concerned, so the `onFailure`
     * handlers above only ever see transport exceptions. Since a failed call also persists nothing, the
     * same doomed request then repeats on every foreground — which is exactly how the missing-
     * `description` 422 went unnoticed until a packet capture.
     *
     * Systematic rejections also go to the remote channel: registrations are the server's ONLY audience
     * source for service alerts, so a contract failure — a field added server-side, a schema change —
     * must not be visible solely in one developer's logcat. [Transient][isTransient] statuses stay
     * local-only, like transport failures.
     */
    private fun reportHttpFailure(
        operation: String,
        registration: PushRegistration,
        response: Response<Unit>
    ) {
        // The server's error body carries the actionable message (e.g. "Description can't be blank").
        val body = response.errorBody()?.string()?.trim().orEmpty()
        val detail = if (body.isEmpty()) "" else " $body"
        val message = "${failurePrefix(operation, registration)}: HTTP ${response.code()}$detail"
        logWarning(message, null)
        if (!isTransient(response.code())) reportError(PushRegistrationException(message))
    }

    /**
     * Whether [code] signals a transient condition rather than a systematic rejection: throttling (429 —
     * the endpoint is unauthenticated and limited per IP, so on a shared-NAT network — public wifi,
     * carrier CGNAT — other clients can spend this device's budget) or server-side trouble (5xx). A
     * later reconcile pass retries both, so reporting each occurrence would flood the remote channel
     * and drown the systematic signal it exists to carry; only statuses that indicate *this request*
     * can never succeed are worth a report.
     */
    private fun isTransient(code: Int): Boolean = code == HTTP_TOO_MANY_REQUESTS || code in 500..599

    /**
     * Logs a transport-level failure. Local only — being offline is ordinary and self-healing, and
     * reporting every such moment remotely would drown the systematic-rejection signal above.
     */
    private fun logTransportFailure(
        operation: String,
        registration: PushRegistration,
        cause: Throwable
    ) = logWarning(failurePrefix(operation, registration), cause)

    /**
     * The shared opening of every failure message. Identifies the endpoint by region + host — never the
     * token, and never the URL, since the unregister URL carries the token as a query param.
     */
    private fun failurePrefix(operation: String, registration: PushRegistration): String = "push $operation failed for region ${registration.regionId} at ${registration.sidecarBaseUrl}"

    private companion object {
        const val TAG = "PushRegistration"

        /** RFC 6585 Too Many Requests — [HttpURLConnection] predates it and has no constant. */
        const val HTTP_TOO_MANY_REQUESTS = 429
    }
}
