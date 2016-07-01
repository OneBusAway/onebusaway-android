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
package org.onebusaway.android.util;

public final class MyTextUtils {

    /**
     * Converts a string to title casing.
     *
     * @param str The string to convert.
     * @return The converted string.
     */
    public static String toTitleCase(String str) {
        if (str == null) {
            return null;
        }

        boolean isSeparator = true;
        StringBuilder builder = new StringBuilder(str);
        final int len = builder.length();

        for (int i = 0; i < len; ++i) {
            char c = builder.charAt(i);
            if (isSeparator) {
                if (Character.isLetterOrDigit(c)) {
                    // Convert to title case and switch out of whitespace mode.
                    builder.setCharAt(i, Character.toTitleCase(c));
                    isSeparator = false;
                }
            } else if (!Character.isLetterOrDigit(c)) {
                isSeparator = true;
            } else {
                builder.setCharAt(i, Character.toLowerCase(c));
            }
        }

        return builder.toString();
    }

    /**
     * Returns true if the provided string is all caps, and false if it is not
     *
     * @param str
     * @return true if the provided string is all caps, and false if it is not
     */
    public static boolean isAllCaps(String str) {
        return str.equals(str.toUpperCase());
    }

    /**
     * Converts the given string to sentence case, where the first
     * letter is capitalized and the rest of the string is in
     * lower case.
     *
     * @param inputVal The string to convert.
     * @return The converted string.
     */
    public static String toSentenceCase(String inputVal) {
        if (inputVal == null)
            return null;

        if (inputVal.length() == 0)
            return "";

        // Strings with only one character uppercased.

        if (inputVal.length() == 1)
            return inputVal.toUpperCase();

        // Otherwise uppercase first letter, lowercase the rest.

        return inputVal.substring(0, 1).toUpperCase()
                + inputVal.substring(1).toLowerCase();
    }
}
