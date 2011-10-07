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
        // skip if already contains mixed case
        if (!str.equals(str.toLowerCase()) && !str.equals(str.toUpperCase())) {
            return str;
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
