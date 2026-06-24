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
package org.onebusaway.android.location

import android.location.Location
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow

/**
 * A controllable [LocationRepository] for ViewModel tests: returns a fixed [lastKnownLocation] and is
 * otherwise inert (the feed methods are no-ops). `android.location.Location` isn't constructible in
 * plain JVM unit tests, so callers typically rely on the null default to exercise the "no location"
 * path; the "has location" path is covered by instrumented/equivalence tests.
 */
internal class FakeLocationRepository(private val last: Location? = null) : LocationRepository {
    private val _location = MutableStateFlow(last)
    override val location: StateFlow<Location?> = _location.asStateFlow()
    override fun lastKnownLocation(): Location? = last
    override fun startUpdates() {}
    override fun stopUpdates() {}
    override fun locationUpdates(intervalSeconds: Int): Flow<Location> = emptyFlow()
}
