package com.trueapply.ats;

import com.trueapply.util.Text;

import java.util.List;
import java.util.function.Predicate;

/** Picks the dropdown option that best matches an answer; shared by every form filler. */
public final class OptionMatcher {
    private OptionMatcher() {
    }

    /** Index of the best option for {@code value}, or -1. Exact, then prefix, then contains. */
    public static int bestMatch(List<String> optionTexts, String value) {
        String wanted = matchKey(value);
        if (wanted.isEmpty()) return -1;
        List<String> keys = optionTexts.stream().map(OptionMatcher::matchKey).toList();
        for (int i = 0; i < keys.size(); i++) if (keys.get(i).equals(wanted)) return i;
        // Shortest wins, so "United States" picks "United States +1" over "United States Minor Outlying Islands".
        int prefix = shortest(keys, o -> o.startsWith(wanted));
        if (prefix >= 0) return prefix;
        int contains = shortest(keys, o -> o.contains(wanted));
        if (contains >= 0) return contains;
        // "Computer Science and Engineering" → "Computer Science": the longest option inside the value.
        int best = -1;
        for (int i = 0; i < keys.size(); i++) {
            String k = keys.get(i);
            if (k.length() >= 4 && (" " + wanted + " ").contains(" " + k + " ")
                    && (best < 0 || k.length() > keys.get(best).length())) {
                best = i;
            }
        }
        return best;
    }

    /** Normalized with punctuation as spaces, so "UC, Berkeley" ≈ "UC - Berkeley" and "Ph.D." ≈ "Ph D". */
    private static String matchKey(String s) {
        return Text.normalize(s).replaceAll("[^a-z0-9+#]+", " ").trim();
    }

    private static int shortest(List<String> options, Predicate<String> test) {
        int best = -1;
        for (int i = 0; i < options.size(); i++) {
            if (test.test(options.get(i)) && (best < 0 || options.get(i).length() < options.get(best).length())) best = i;
        }
        return best;
    }
}
