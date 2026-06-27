/*
 * Copyright (C) 2010 Paul Watts (paulcwatts@gmail.com)
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

interface ObaSituation : ObaElement {

    interface AllAffects {
        /** The affected direction ID, if any. */
        val directionId: String?

        /** The affected stop ID, if any. */
        val stopId: String?

        /** The affected trip ID, if any. */
        val tripId: String?

        /** The affected application ID, if any. */
        val applicationId: String?

        /** The affected route ID, if any. */
        val routeId: String?

        /** The affected agency ID, if any. */
        val agencyId: String?
    }

    interface ConditionDetails {
        /** For diversion conditions, the stop IDs that are diverted. */
        val diversionStopIds: List<String>

        /** For diversion conditions, the optional path the vehicle will take in the diversion. */
        val diversionPath: ObaShape?
    }

    interface Consequence {
        /** The string describing the consequence condition. */
        val condition: String?

        /** Optional details of the consequence condition. */
        val details: ConditionDetails?

        companion object {
            const val CONDITION_DIVERSION = "diversion"
            const val CONDITION_ALTERED = "altered"
            const val CONDITION_DETOUR = "detour"
        }
    }

    interface ActiveWindow {
        /** The starting time of the active window for this situation. */
        val from: Long

        /** The ending time of the active window for this situation. */
        val to: Long
    }

    /** Optional short summary of the situation. */
    val summary: String?

    /** Optional longer description of the situation. */
    val description: String?

    /** Optional advice to the rider. */
    val advice: String?

    /** The service alert code. */
    val reason: String?

    /** The Unix timestamp of when this situation was created. */
    val creationTime: Long

    /** Information on what stops and routes this situation affects. */
    val allAffects: Array<AllAffects>

    /** An array of consequences of this situation. */
    val consequences: Array<Consequence>

    /** The severity of the situation. */
    val severity: String?

    /** An array of active windows of this situation. */
    val activeWindows: Array<ActiveWindow>

    /** A URL to a human-readable website with more details on the alert. */
    val url: String?

    companion object {
        const val SEVERITY_UNKNOWN = "unknown"
        const val SEVERITY_NO_IMPACT = "noImpact"
        const val SEVERITY_VERY_SLIGHT = "verySlight"
        const val SEVERITY_SLIGHT = "slight"
        const val SEVERITY_NORMAL = "normal"
        const val SEVERITY_SEVERE = "severe"
        const val SEVERITY_VERY_SEVERE = "verySevere"
    }
}
