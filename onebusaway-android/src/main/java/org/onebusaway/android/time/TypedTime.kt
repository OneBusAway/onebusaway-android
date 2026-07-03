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
package org.onebusaway.android.time

import android.os.SystemClock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/*
 * Domain-tagged instants. Nearly every time bug in this app has been a domain or unit mix that is
 * silent until a user sees a wrong number: a server timestamp minus the device clock (#27/#1612), an
 * alert window in seconds compared against a millis "now", a wall-clock delta corrupted by an NTP
 * correction. A raw `Long` carries no record of which clock it came from, so the mistake passes review
 * and compiles. These wrappers make the clock **domain** part of the type: the only arithmetic defined
 * is same-domain subtraction (yielding a unit-safe `kotlin.time.Duration`), so a cross-domain subtraction
 * fails to *compile* rather than misbehaving at runtime.
 *
 * They are `@JvmInline value class`es — at runtime each is just its underlying `Long`, so the safety is
 * free. These are pure domain types: they carry no wire/unit knowledge. Mint one at the wire/Android
 * boundary (the API adapter normalizes units first, then wraps) and unwrap (`.epochMs` / `.ms`) only when
 * handing a value to a platform API that wants a raw `Long` (formatting, alarms, the renderer). Deltas are
 * `Duration`; the clock-domain tag is the app-specific part libraries don't provide. See CLAUDE.md "Time
 * domains" for which values belong to which domain.
 */

/**
 * An instant on the **OBA server** clock, epoch millis. The domain of everything the server timestamps —
 * `currentTime`, arrival/departure predictions, `lastUpdateTime` — against which ETAs and active windows
 * must be measured so device clock skew cancels (#1612). The API layer owns any wire normalization (e.g.
 * the alert-window seconds↔millis rule) and hands this type a value that is already epoch millis.
 */
@JvmInline
value class ServerTime(val epochMs: Long) : Comparable<ServerTime> {
    override fun compareTo(other: ServerTime): Int = epochMs.compareTo(other.epochMs)

    /** Elapsed time from [other] to this instant. Same-domain only — this is the point. */
    operator fun minus(other: ServerTime): Duration = (epochMs - other.epochMs).milliseconds
}

/**
 * An instant on the **device wall** clock (`System.currentTimeMillis()`), epoch millis. Extrapolation
 * deliberately lives here: it pairs each server time with the local receive time, so measuring the
 * elapsed interval against the same wall clock is immune to server/device skew. Cross to the server
 * clock with `TripState.toServerClock` before plotting against server-clock data.
 */
@JvmInline
value class WallTime(val epochMs: Long) : Comparable<WallTime> {
    override fun compareTo(other: WallTime): Int = epochMs.compareTo(other.epochMs)

    operator fun minus(other: WallTime): Duration = (epochMs - other.epochMs).milliseconds

    companion object {
        fun now(): WallTime = WallTime(System.currentTimeMillis())
    }
}

/**
 * A reading of the **monotonic** clock (`SystemClock.elapsedRealtime()`), millis since boot. Immune to
 * NTP corrections and the user changing the clock, so it's the correct source for measuring a real
 * elapsed interval ("data updated N sec ago"). Not an epoch instant — only differences are meaningful.
 */
@JvmInline
value class ElapsedTime(val ms: Long) : Comparable<ElapsedTime> {
    override fun compareTo(other: ElapsedTime): Int = ms.compareTo(other.ms)

    operator fun minus(other: ElapsedTime): Duration = (ms - other.ms).milliseconds

    companion object {
        fun now(): ElapsedTime = ElapsedTime(SystemClock.elapsedRealtime())
    }
}
