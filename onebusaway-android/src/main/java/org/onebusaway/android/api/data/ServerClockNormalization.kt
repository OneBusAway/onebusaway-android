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
package org.onebusaway.android.api.data

/**
 * The response's server `currentTime` (epoch millis) when present, else the device clock. Every OK OBA
 * response carries a positive `currentTime`, but the wire model defaults it to 0 when the field is
 * absent/malformed; anchoring ETAs and vehicle ages on 0 would render ~28-million-minute countdowns and
 * "updated 0 sec ago". Falling back to the device clock keeps a skewed-but-sane baseline in that
 * degenerate case, while the normal path stays on the server clock so skew cancels (#1612).
 */
internal fun serverNowOrDeviceClock(serverCurrentTime: Long): Long =
    if (serverCurrentTime > 0L) serverCurrentTime else System.currentTimeMillis()

/**
 * Normalizes an OBA service-alert active-window timestamp to epoch milliseconds. The unit is **not
 * fixed** across the OBA ecosystem: GTFS-RT `active_period` is seconds per spec, but the server converts
 * it to millis on ingestion (`GtfsRealtimeAlertLibrary.toMillis`, magnitude threshold 1e12) while older
 * servers/feeds still emit seconds — so a response's `from`/`to` may be either. Normalizing here, at the
 * wire→domain adapter, lets the domain model ([org.onebusaway.android.models.ObaSituation.ActiveWindow])
 * be unambiguously millis so no downstream consumer has to re-guess. Mirrors the server's own `toMillis`.
 *
 * Failure mode (inherent to any magnitude rule): a genuine epoch-*millis* value below 1e12 (an instant
 * before 2001-09-09) is misread as seconds and scaled x1000; a genuine epoch-*seconds* value at/after
 * 1e12 (year ~33658) is misread as millis. Both are far outside any real alert window. Non-positive
 * values (an unset `from`, or `to == 0` meaning "no end") pass through unchanged.
 */
internal fun situationEpochToMillis(timestamp: Long): Long =
    if (timestamp in 1 until SECONDS_MILLIS_THRESHOLD) timestamp * 1_000L else timestamp

/** OBA's server-side seconds-vs-millis boundary (`GtfsRealtimeAlertLibrary.toMillis`). */
private const val SECONDS_MILLIS_THRESHOLD = 1_000_000_000_000L
