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
package org.onebusaway.android.app

/**
 * Switches for features that are in the source tree but not offered to riders yet.
 *
 * These are plain `val`s rather than `const val`s on purpose: a compile-time constant lets the
 * compiler prove branches dead, which turns "flag is off" into unreachable-code warnings, and this
 * build treats warnings as errors. Reading a property keeps the disabled code compiling — and
 * therefore refactored, formatted, and type-checked — while it waits.
 */
object FeatureFlags {

    /**
     * Destination reminders: the foreground session that watches the rider's position along a
     * planned itinerary and announces when to get ready and when to get off.
     *
     * Off while the feature is reworked. What exists is a modernization of the original
     * notifications feature — a pure progression engine over a three-stop-per-ride model — and the
     * intended shape is a full navigation mode: every leg including the walks, an ordered waypoint
     * sequence with a cursor, off-route detection, and schedule-aware callouts. The engine, its
     * geometry, and the service around it are worth keeping and are left in place; what they are
     * pointed at is what changes.
     *
     * Turning this on restores the two entry points (the trip-results Start/Stop control and the
     * trip-details long-press) and lets [org.onebusaway.android.nav.NavigationService] run. With it
     * off the service refuses to start and clears any session a previous build left stored, so a
     * rider who upgrades mid-trip is not left with an orphaned foreground GPS session.
     */
    val DESTINATION_REMINDERS = false
}
