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
package org.onebusaway.android.ui.compose.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink

private val URL_REGEX = Regex("""https?://[^\s)]+""")

/**
 * Wraps every bare URL in [text] with a tappable, underlined [LinkAnnotation.Url] — the Compose
 * equivalent of a TextView's `autoLink="web"`. Shared by the About screen and the info dialogs.
 */
fun linkifyUrls(text: String, linkColor: Color): AnnotatedString =
    buildAnnotatedString {
        var last = 0
        for (match in URL_REGEX.findAll(text)) {
            val url = match.value.trimEnd('.', ',')
            append(text.substring(last, match.range.first))
            withLink(
                LinkAnnotation.Url(
                    url,
                    TextLinkStyles(
                        style = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)
                    )
                )
            ) {
                append(url)
            }
            last = match.range.first + url.length
        }
        append(text.substring(last))
    }
