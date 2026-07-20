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

import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the domain-tagged time types: same-domain arithmetic yields a [kotlin.time.Duration] and stays
 * signed/ordered. These are pure domain wrappers with no wire knowledge — the seconds↔millis wire
 * normalization lives on the API side (`situationEpochToMillis`, covered by `SituationEpochToMillisTest`).
 * The cross-domain safety (no `ServerTime.minus(WallTime)`) is enforced by the compiler, so it can't be
 * unit-tested here — its proof is that the app compiles.
 */
class TypedTimeTest {

    @Test
    fun `same-domain subtraction yields a Duration`() {
        assertEquals(5.minutes, ServerTime(600_000L) - ServerTime(300_000L))
        assertEquals(5.minutes, WallTime(600_000L) - WallTime(300_000L))
        assertEquals(5.minutes, ElapsedTime(600_000L) - ElapsedTime(300_000L))
    }

    @Test
    fun `subtraction is signed`() {
        assertEquals((-1_000L).milliseconds, ServerTime(1_000L) - ServerTime(2_000L))
    }

    @Test
    fun `Comparable orders within a domain`() {
        assertTrue(ServerTime(1_000L) < ServerTime(2_000L))
        assertTrue(WallTime(2_000L) > WallTime(1_000L))
        assertEquals(ServerTime(1_000L), ServerTime(1_000L))
    }

    @Test
    fun `group action shifts an instant and stays in-domain`() {
        assertEquals(ServerTime(360_000L), ServerTime(300_000L) + 1.minutes)
        assertEquals(WallTime(240_000L), WallTime(300_000L) - 1.minutes)
        assertEquals(ElapsedTime(360_000L), ElapsedTime(300_000L) + 1.minutes)
    }

    @Test
    fun `affine laws hold`() {
        val p = ServerTime(1_000_000L)
        val d = 90.seconds
        // (p + d) − p == d : the group action is the inverse of subtraction.
        assertEquals(d, (p + d) - p)
        // p + d − d == p : plus and minus by the same vector cancel.
        assertEquals(p, (p + d) - d)
    }

    @Test
    fun `both minus overloads coexist by argument type`() {
        // minus(Point) yields a Duration; minus(Duration) yields a Point — resolved by argument type.
        val elapsed: kotlin.time.Duration = ServerTime(5_000L) - ServerTime(2_000L)
        val shifted: ServerTime = ServerTime(5_000L) - 3.seconds
        assertEquals(3.seconds, elapsed)
        assertEquals(ServerTime(2_000L), shifted)
    }
}
