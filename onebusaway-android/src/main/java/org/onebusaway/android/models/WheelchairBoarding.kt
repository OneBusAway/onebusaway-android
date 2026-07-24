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
package org.onebusaway.android.models

/**
 * A stop's wheelchair-boarding accessibility, from the GTFS `wheelchair_boarding` field as surfaced
 * by the OBA REST `stop` element (issue #1029). The OBA server maps the numeric GTFS values to these
 * three string states on the wire; [fromString] mints the wire string back into the domain.
 *
 * A stop with no `wheelchairBoarding` on the wire (an older server, or a feed that omits the field)
 * decodes to [UNKNOWN] — the GTFS default of "no accessibility information", not a guess.
 */
enum class WheelchairBoarding {
    ACCESSIBLE,
    NOT_ACCESSIBLE,
    UNKNOWN;

    companion object {
        /**
         * Maps the wire string to the enum. A null (field absent) or unrecognized value is treated as
         * [UNKNOWN] — the GTFS default when accessibility is unstated — so callers get a total,
         * non-null domain to switch on.
         *
         * The wire strings are exactly the constant names, so [name] round-trips a value back onto the
         * wire (and into storage) without a second spelling of each constant to keep in sync.
         */
        fun fromString(value: String?): WheelchairBoarding = when (value) {
            ACCESSIBLE.name -> ACCESSIBLE
            NOT_ACCESSIBLE.name -> NOT_ACCESSIBLE
            else -> UNKNOWN
        }
    }
}
