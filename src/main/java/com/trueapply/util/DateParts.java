package com.trueapply.util;

import java.time.Month;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Splits free-form dates ("Jun 2023", "06/2023", "05/14/2026", "2026-05-14", "May 14, 2026",
 * "2021", "Present") into a full English month name, a four-digit year and a day of the month;
 * any may be null.
 */
public record DateParts(String month, String year, Integer day) {
    private static final Pattern YEAR = Pattern.compile("\\b(19|20)\\d{2}\\b");
    private static final Pattern NUMERIC_MONTH = Pattern.compile("\\b(0?[1-9]|1[0-2])\\s*[/.-]\\s*(19|20)\\d{2}\\b");
    /** 05/14/2026 (US order: month first). */
    private static final Pattern US_DATE =
            Pattern.compile("\\b(0?[1-9]|1[0-2])\\s*[/.-]\\s*(0?[1-9]|[12]\\d|3[01])\\s*[/.-]\\s*(?:19|20)\\d{2}\\b");
    /** 2026-05-14 */
    private static final Pattern ISO_DATE =
            Pattern.compile("\\b(?:19|20)\\d{2}-(0?[1-9]|1[0-2])-(0?[1-9]|[12]\\d|3[01])\\b");

    public DateParts(String month, String year) {
        this(month, year, null);
    }

    /** The day, or the 1st when none was given (forms that insist on a day accept that). */
    public int dayOrFirst() {
        return day == null ? 1 : day;
    }

    public static DateParts parse(String text) {
        if (text == null || text.isBlank()) return new DateParts(null, null);
        Matcher y = YEAR.matcher(text);
        String year = y.find() ? y.group() : null;

        for (Pattern full : new Pattern[] {US_DATE, ISO_DATE}) {
            Matcher m = full.matcher(text);
            if (m.find()) return new DateParts(monthName(Integer.parseInt(m.group(1))), year, Integer.parseInt(m.group(2)));
        }

        String month = null;
        Integer day = null;
        Matcher numeric = NUMERIC_MONTH.matcher(text);
        if (numeric.find()) {
            month = monthName(Integer.parseInt(numeric.group(1)));
        } else {
            String lower = text.toLowerCase(Locale.ROOT);
            for (Month m : Month.values()) {
                String full = m.getDisplayName(TextStyle.FULL, Locale.ENGLISH);
                // "May 14, 2026": a day may follow the month name; "May 2026" has none.
                Matcher named = Pattern.compile("\\b" + full.substring(0, 3).toLowerCase(Locale.ROOT)
                        + "[a-z]*\\.?(?:\\s+(0?[1-9]|[12]\\d|3[01])(?:st|nd|rd|th)?\\b)?").matcher(lower);
                if (named.find()) {
                    month = full;
                    if (named.group(1) != null) day = Integer.parseInt(named.group(1));
                    break;
                }
            }
        }
        return new DateParts(month, year, day);
    }

    private static String monthName(int month) {
        return Month.of(month).getDisplayName(TextStyle.FULL, Locale.ENGLISH);
    }
}
