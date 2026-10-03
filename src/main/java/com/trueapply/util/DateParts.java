package com.trueapply.util;

import java.time.Month;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Splits free-form resume dates ("Jun 2023", "06/2023", "2021", "Present") into a full English
 * month name and a four-digit year; either may be null.
 */
public record DateParts(String month, String year) {
    private static final Pattern YEAR = Pattern.compile("\\b(19|20)\\d{2}\\b");
    private static final Pattern NUMERIC_MONTH = Pattern.compile("\\b(0?[1-9]|1[0-2])\\s*[/.-]\\s*(19|20)\\d{2}\\b");

    public static DateParts parse(String text) {
        if (text == null || text.isBlank()) return new DateParts(null, null);
        Matcher y = YEAR.matcher(text);
        String year = y.find() ? y.group() : null;

        String month = null;
        Matcher numeric = NUMERIC_MONTH.matcher(text);
        if (numeric.find()) {
            month = Month.of(Integer.parseInt(numeric.group(1))).getDisplayName(TextStyle.FULL, Locale.ENGLISH);
        } else {
            String lower = text.toLowerCase(Locale.ROOT);
            for (Month m : Month.values()) {
                String full = m.getDisplayName(TextStyle.FULL, Locale.ENGLISH);
                if (Pattern.compile("\\b" + full.substring(0, 3).toLowerCase(Locale.ROOT)).matcher(lower).find()) {
                    month = full;
                    break;
                }
            }
        }
        return new DateParts(month, year);
    }
}
