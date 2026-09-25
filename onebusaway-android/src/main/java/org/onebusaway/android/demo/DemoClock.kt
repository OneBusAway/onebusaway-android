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

import org.onebusaway.android.time.WallTime

/**
 * The demo transit system's clock — the single place demo mode reads the time.
 *
 * The demo deployment answers entirely from the device, so this fake server's *server* clock **is** the
 * device wall clock. That identity is the one thing worth stating explicitly: everywhere else in the
 * app confusing the two is the #1612 bug class, and it is only harmless here because there is no second
 * clock to be wrong about. Minting through [WallTime] says which clock is being read, rather than
 * laundering a bare `System.currentTimeMillis()` into an untyped `Long`.
 *
 * It is unwrapped straight back to millis because everything downstream is **wire construction**: the
 * OBA DTOs carry epoch millis by design (see the `WireTimeEscape` note in `lint-rules/README.md`), and
 * demo mode is standing in for the server that fills them in. [DemoScenario] then takes its `now` as a
 * plain parameter, which keeps the simulation pure and JVM-testable — no clock is read inside it.
 */
internal object DemoClock {

    /** The demo deployment's current time, in epoch millis. */
    @Suppress("PrematureUnwrap")
    fun nowMs(): Long = WallTime.now().epochMs
}
