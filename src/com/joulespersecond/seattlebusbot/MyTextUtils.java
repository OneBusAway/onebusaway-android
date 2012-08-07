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
package com.joulespersecond.seattlebusbot;

public final class MyTextUtils {
    /**
     * Converts a string to title casing.
     * @param str
     *      The string to convert.
     * @return
     *      The converted string.
     */
    public static String toTitleCase(String str) {
        if (str == null) {
            return null;
        }

        boolean isSeparator = true;
        StringBuilder builder = new StringBuilder(str);
        final int len = builder.length();

        for (int i=0; i < len; ++i) {
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
}
