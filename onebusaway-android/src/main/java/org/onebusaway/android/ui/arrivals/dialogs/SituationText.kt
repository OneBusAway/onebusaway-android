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
package org.onebusaway.android.ui.arrivals.dialogs

/**
 * Which parts of a service alert the alert dialog's body shows, in order: the [headline] (the
 * feed's header text), then the description when [showDescription], or the "No description
 * available" text when [showNoDescription].
 */
internal data class SituationText(
    val headline: String?,
    val showDescription: Boolean,
    val showNoDescription: Boolean
)

/**
 * Decides what the alert dialog shows (#2448). The whole headline is always shown. The description
 * is shown when it has text that isn't the headline again; feeds often repeat the header as the
 * description, or leave the description empty. "No description available" is shown only when
 * there's neither, so a rider never sees it under a headline.
 *
 * @param summary the alert's header text
 * @param description the alert's description as the rider reads it (HTML already rendered)
 */
internal fun situationText(summary: String?, description: String): SituationText {
    val headline = summary?.trim()?.takeIf { it.isNotEmpty() }
    val body = description.trim()
    val showDescription = body.isNotEmpty() && body.collapseWhitespace() != headline?.collapseWhitespace()
    return SituationText(
        headline = headline,
        showDescription = showDescription,
        showNoDescription = headline == null && body.isEmpty()
    )
}

private val WHITESPACE = Regex("\\s+")

private fun String.collapseWhitespace(): String = replace(WHITESPACE, " ")
