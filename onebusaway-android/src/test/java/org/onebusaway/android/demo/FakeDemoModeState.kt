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
package org.onebusaway.android.demo

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A [DemoModeState] a test can drive directly — the whole reason the read-only half of
 * [DemoModeController] is a separate type: the controller needs a `Context` for its bundled fixture,
 * and a consumer that only reacts to the flag shouldn't have to supply one to be tested.
 */
class FakeDemoModeState(initial: Boolean = false) : DemoModeState {

    private val _active = MutableStateFlow(initial)

    override val active: StateFlow<Boolean> = _active.asStateFlow()

    override val isActive: Boolean get() = _active.value

    /** Enter or leave demo mode, as the scripted tutorial's host does. */
    fun set(active: Boolean) {
        _active.value = active
    }
}
