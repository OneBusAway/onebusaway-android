/*
 * Copyright (C) 2020 Sean J. Barbeau (sjbarbeau@gmail.com)
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
 * The status of the trip, used in [ObaTripStatus].
 */
enum class Status(private val value: String) {
    DEFAULT("default"),
    CANCELED("CANCELED");

    override fun toString(): String = value

    companion object {
        /**
         * Converts from the string representation of status to the enumeration, or null if [status]
         * isn't a recognized value.
         */
        @JvmStatic
        fun fromString(status: String?): Status? = entries.firstOrNull { it.value == status }
    }
}
