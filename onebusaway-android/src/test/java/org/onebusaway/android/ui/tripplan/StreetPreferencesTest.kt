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
package org.onebusaway.android.ui.tripplan

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Covers [enumValueOrDefault], the reader every street preference passes through on its way out of
 * preferences or a saved `Bundle`. The wire mapping of these enums lives in
 * `Otp2PlanRequestBuilderTest`; this is only about surviving the round trip through storage.
 */
class StreetPreferencesTest {

    @Test
    fun aStoredNameRoundTripsToItsOwnValue() {
        // Persisted by name rather than ordinal, so reordering an enum can't reinterpret a value
        // already sitting in storage. Every stop of both scales must come back as itself.
        for (preference in WalkPreference.entries) {
            assertEquals(preference, enumValueOrDefault(preference.name, WalkPreference.MEDIUM))
        }
        for (preference in BikePreference.entries) {
            assertEquals(preference, enumValueOrDefault(preference.name, BikePreference.MEDIUM))
        }
        for (preference in CyclingPreference.entries) {
            assertEquals(preference, enumValueOrDefault(preference.name, CyclingPreference.DEFAULT))
        }
    }

    @Test
    fun anUnknownOrMissingNameFallsBackToTheNeutralStop() {
        // An unrecognized name means a build that knows an option this one doesn't wrote it. Falling
        // back to the neutral stop is the only reading that can't misroute; picking a neighbour would
        // quietly change what the rider asked for.
        assertEquals(WalkPreference.MEDIUM, enumValueOrDefault("MODERATE_WALKS", WalkPreference.MEDIUM))
        assertEquals(WalkPreference.MEDIUM, enumValueOrDefault(null, WalkPreference.MEDIUM))
        assertEquals(WalkPreference.MEDIUM, enumValueOrDefault("", WalkPreference.MEDIUM))
        assertEquals(BikePreference.MEDIUM, enumValueOrDefault("MINIMUM_ISH", BikePreference.MEDIUM))
        assertEquals(CyclingPreference.DEFAULT, enumValueOrDefault("HILLIEST", CyclingPreference.DEFAULT))
    }

    @Test
    fun theMatchIsExactRatherThanFuzzy() {
        // Case and whitespace variants are not the stored form, so they must not resolve — a near
        // miss is a value this build doesn't understand, not a hint to guess from.
        assertEquals(WalkPreference.MEDIUM, enumValueOrDefault("minimum", WalkPreference.MEDIUM))
        assertEquals(WalkPreference.MEDIUM, enumValueOrDefault(" MINIMUM ", WalkPreference.MEDIUM))
    }
}
