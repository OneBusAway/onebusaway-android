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

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import org.onebusaway.android.R

/*
 * A stop name the rider gave the stop (#2414) is led by a label glyph wherever it's shown, so it's never
 * mistaken for the name on the sign. The glyph is inline content rather than a sibling icon so it wraps
 * and scales with the text. [StopNameText] is the drop-in for a plain `Text`; [stopNameText] and
 * [stopNamePlaceholders] are for a caller that measures the text first (the focus banner's shrink-to-fit
 * title), which then draws it with [StopNameText] too.
 */

/** A stop's name as text: [name], led by the custom-name glyph when [isCustomName]. */
@Composable
fun StopNameText(
    name: String,
    isCustomName: Boolean,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = null,
    textDecoration: TextDecoration? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip
) {
    if (!isCustomName) {
        // Most names: plain text, on Text's cheaper String path, with no inline content to carry.
        Text(
            text = name,
            modifier = modifier,
            style = style,
            color = color,
            fontSize = fontSize,
            fontWeight = fontWeight,
            textDecoration = textDecoration,
            maxLines = maxLines,
            overflow = overflow
        )
        return
    }
    val text = remember(name) { stopNameText(name, isCustomName = true) }
    Text(
        text = text,
        modifier = modifier.customStopNameSemantics(name),
        style = style,
        color = color,
        fontSize = fontSize,
        fontWeight = fontWeight,
        textDecoration = textDecoration,
        maxLines = maxLines,
        overflow = overflow,
        inlineContent = CustomNameInlineContent
    )
}

private const val CUSTOM_NAME_GLYPH_ID = "customStopName"

// Sized in em so the glyph tracks the text's font size; the space after it in [stopNameText] is its gap.
private val CustomNamePlaceholder = Placeholder(1.em, 1.em, PlaceholderVerticalAlign.TextCenter)

/** [name], led by the glyph's placeholder (at index 0) when [isCustomName]. */
fun stopNameText(name: String, isCustomName: Boolean): AnnotatedString = buildAnnotatedString {
    if (isCustomName) {
        appendInlineContent(CUSTOM_NAME_GLYPH_ID, "�")
        append(' ')
    }
    append(name)
}

/** The placeholders [stopNameText] lays out, for a caller measuring it with a `TextMeasurer`. */
fun stopNamePlaceholders(isCustomName: Boolean): List<AnnotatedString.Range<Placeholder>> = if (isCustomName) CustomNamePlaceholders else emptyList()

private val CustomNamePlaceholders = listOf(AnnotatedString.Range(CustomNamePlaceholder, 0, 1))

/**
 * The inline content that draws [stopNameText]'s glyph. One shared map, not one per call: it's the
 * same for every name, and a fresh map each recomposition would defeat `Text`'s input equality in every
 * list row that shows one.
 */
private val CustomNameInlineContent = mapOf(
    CUSTOM_NAME_GLYPH_ID to InlineTextContent(CustomNamePlaceholder) {
        CustomStopNameGlyph(contentDescription = null, modifier = Modifier.fillMaxSize())
    }
)

/**
 * The custom-name glyph on its own, for a name that can't carry inline content — an editable field,
 * whose text the rider can type over. [StopNameText] is the one to use everywhere else.
 */
@Composable
fun CustomStopNameGlyph(contentDescription: String?, modifier: Modifier = Modifier) {
    Icon(
        painter = painterResource(R.drawable.label_24),
        contentDescription = contentDescription,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    )
}

/**
 * The glyph says "your name" by sight only; this says it to TalkBack too, in place of the placeholder
 * character it would otherwise read.
 */
@Composable
private fun Modifier.customStopNameSemantics(name: String): Modifier {
    val spoken = stringResource(R.string.stop_info_custom_name_description, name)
    return clearAndSetSemantics { contentDescription = spoken }
}
