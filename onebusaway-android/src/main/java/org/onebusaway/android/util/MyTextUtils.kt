/*
 * Copyright (C) 2011 individual contributors
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
package org.onebusaway.android.util

object MyTextUtils {

    /**
     * Converts a string to title casing.
     *
     * @param str The string to convert.
     * @return The converted string.
     */
    @JvmStatic
    fun toTitleCase(str: String?): String? {
        if (str == null) {
            return null
        }

        var isSeparator = true
        val builder = StringBuilder(str)
        val len = builder.length

        for (i in 0 until len) {
            val c = builder[i]
            if (isSeparator) {
                if (Character.isLetterOrDigit(c)) {
                    // Convert to title case and switch out of whitespace mode.
                    builder.setCharAt(i, Character.toTitleCase(c))
                    isSeparator = false
                }
            } else if (!Character.isLetterOrDigit(c)) {
                isSeparator = true
            } else {
                builder.setCharAt(i, Character.toLowerCase(c))
            }
        }

        return builder.toString()
    }

    /**
     * Returns true if the provided string is all caps, and false if it is not
     *
     * @param str
     * @return true if the provided string is all caps, and false if it is not
     */
    @JvmStatic
    fun isAllCaps(str: String): Boolean {
        return str == str.uppercase()
    }

    /**
     * Converts the given string to sentence case, where the first
     * letter is capitalized and the rest of the string is in
     * lower case.
     *
     * @param inputVal The string to convert.
     * @return The converted string.
     */
    @JvmStatic
    fun toSentenceCase(inputVal: String?): String? {
        if (inputVal == null)
            return null

        if (inputVal.isEmpty())
            return ""

        // Strings with only one character uppercased.

        if (inputVal.length == 1)
            return inputVal.uppercase()

        // Otherwise uppercase first letter, lowercase the rest.

        return inputVal.substring(0, 1).uppercase() +
                inputVal.substring(1).lowercase()
    }

    /**
     * Returns a formatted displayText for displaying in the UI for stops, routes, and headsigns, or
     * null if the displayText is null.  If the displayText IS ALL CAPS and more than one word and
     * does not contain SPLC (see #883), it will be converted to title case (Is All Caps), otherwise
     * the returned string will match the input.
     *
     * @param displayText displayText to be formatted
     * @return formatted text for stop, route, and heasigns for displaying in the UI, or null if the
     * displayText is null.  If the displayText IS ALL CAPS and more than one word and does not
     * contain SPLC (see #883), it will be converted to title case (Is All Caps), otherwise the
     * returned string will match the input.
     */
    @JvmStatic
    fun formatDisplayText(displayText: String?): String? {
        if (displayText == null) {
            return null
        }
        // See #883 for "SPLC" logic
        return if (isAllCaps(displayText) && displayText.contains(" ") && !displayText.contains("SPLC")) {
            toTitleCase(displayText)
        } else {
            displayText
        }
    }
}
