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
package org.onebusaway.android.ui.mylists

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The starred-stop badge says "NOW" only for a bus arriving this minute (issue #2180).
 *
 * It used to test `eta <= 0`, unbounded, so every departed bus in the response got a badge claiming
 * it was here now — and departed buses are routine on this path, since the arrivals request sends no
 * `minutesBefore` and negative ETAs survive `convertArrivals` by default.
 */
class BadgeEtaCutoffTest {

    @Test
    fun `only an arrival in the current minute reads NOW`() {
        assertTrue(badgeEtaIsNow(0))
        assertFalse(badgeEtaIsNow(1))
    }

    @Test
    fun `a departed bus never reads NOW`() {
        // The regression itself. -5 is about as far back as the server's default past window reaches;
        // -1 is the value the old `<= 0` test got wrong first, and the one a rider is most likely to
        // catch, since every bus passes through it.
        assertFalse(badgeEtaIsNow(-1))
        assertFalse(badgeEtaIsNow(-5))
    }
}
