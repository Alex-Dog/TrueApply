package com.trueapply.util;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class Text {
    private Text() {
    }

    public static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    public static String orEmpty(String s) {
        return s == null ? "" : s;
    }

    /** Lowercase, straight quotes, collapsed whitespace — for comparing labels and options. */
    public static String normalize(String s) {
        if (s == null) return "";
        return s.replace('’', '\'')
                .replace('‘', '\'')
                .replace('“', '"')
                .replace('”', '"')
                .replace(' ', ' ')
                .replaceAll("\\s+", " ")
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    /** Splits a comma- or newline-separated list, dropping blanks. */
    public static List<String> splitList(String s) {
        if (isBlank(s)) return List.of();
        return Arrays.stream(s.split("[,\\n]"))
                .map(String::trim)
                .filter(t -> !t.isEmpty())
                .toList();
    }

    /** Converts the HTML-entity-escaped markup Greenhouse returns into real HTML. */
    public static String unescapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&amp;", "&");
    }

    /** Crude tag stripper, good enough to feed descriptions to the AI. */
    public static String stripHtml(String html) {
        if (html == null) return "";
        return html.replaceAll("(?i)<br\\s*/?>|</p>|</li>|</h\\d>", "\n")
                .replaceAll("<[^>]+>", "")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&rsquo;", "'")
                .replace("&ldquo;", "\"")
                .replace("&rdquo;", "\"")
                .replaceAll("[ \\t]+", " ")
                .replaceAll("\\n\\s*\\n+", "\n\n")
                .trim();
    }

    public static String truncate(String s, int max) {
        if (s == null || s.length() <= max) return s;
        return s.substring(0, max) + "…";
    }
}
