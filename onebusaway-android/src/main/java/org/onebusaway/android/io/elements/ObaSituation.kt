/*
 * Copyright (C) 2010-2012 Paul Watts (paulcwatts@gmail.com),
 * 2026 Aaron Brethorst
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
package org.onebusaway.android.io.elements

import com.fasterxml.jackson.annotation.JsonProperty

data class ObaSituation(
    private val id: String = "",
    @JsonProperty("summary") val summaryText: Text? = null,
    @JsonProperty("description") val descriptionText: Text? = null,
    @JsonProperty("advice") val adviceText: Text? = null,
    val reason: String? = "",
    val creationTime: Long = 0,
    val allAffects: Array<AllAffects>? = null,
    val consequences: Array<Consequence>? = null,
    val severity: String? = "",
    val activeWindows: Array<ActiveWindow>? = null,
    @JsonProperty("url") val urlText: Text? = null,
) : ObaElement {

    override fun getId(): String = id

    @get:JvmName("getSummary")
    val summary: String? get() = summaryText?.value

    @get:JvmName("getDescription")
    val description: String? get() = descriptionText?.value

    @get:JvmName("getAdvice")
    val advice: String? get() = adviceText?.value

    @get:JvmName("getUrl")
    val url: String? get() = urlText?.value

    data class Text(val value: String? = null)

    data class AllAffects(
        val directionId: String? = "",
        val stopId: String? = "",
        val tripId: String? = "",
        val applicationId: String? = "",
        val routeId: String? = "",
        val agencyId: String? = "",
    ) {
        companion object {
            @JvmField val EMPTY_OBJECT = AllAffects()
            @JvmField val EMPTY_ARRAY = emptyArray<AllAffects>()
        }
    }

    data class ConditionDetails(
        val diversionStopIds: Array<String>? = null,
        val diversionPath: ObaShapeElement? = null,
    ) {
        fun getDiversionStopIds(): List<String> =
            diversionStopIds?.toList() ?: emptyList()

        fun getDiversionPath(): ObaShape? = diversionPath
    }

    data class Consequence(
        val condition: String? = "",
        val conditionDetails: ConditionDetails? = null,
    ) {
        @get:JvmName("getDetails")
        val details: ConditionDetails? get() = conditionDetails

        companion object {
            const val CONDITION_DIVERSION = "diversion"
            const val CONDITION_ALTERED = "altered"
            const val CONDITION_DETOUR = "detour"

            @JvmField val EMPTY_ARRAY = emptyArray<Consequence>()
        }
    }

    data class ActiveWindow(
        val from: Long = 0,
        val to: Long = 0,
    ) {
        companion object {
            @JvmField val EMPTY_ARRAY = emptyArray<ActiveWindow>()
        }
    }

    companion object {
        const val SEVERITY_UNKNOWN = "unknown"
        const val SEVERITY_NO_IMPACT = "noImpact"
        const val SEVERITY_VERY_SLIGHT = "verySlight"
        const val SEVERITY_SLIGHT = "slight"
        const val SEVERITY_NORMAL = "normal"
        const val SEVERITY_SEVERE = "severe"
        const val SEVERITY_VERY_SEVERE = "verySevere"

        @JvmField val EMPTY_OBJECT = ObaSituation()
        @JvmField val EMPTY_ARRAY = emptyArray<ObaSituation>()
    }
}
