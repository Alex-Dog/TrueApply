package com.trueapply.ats.greenhouse;

import com.trueapply.util.Text;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Greenhouse degree lists differ per company ("Bachelor" vs "Bachelor's Degree"). This turns
 * whatever the resume says ("B.S. Computer Science") into search terms that match any of them.
 */
final class OptionHints {
    private OptionHints() {
    }

    /** Terms to look for in the degree dropdown, most specific first; ends with "Other". */
    static List<String> degreeTerms(String degree) {
        String text = Text.normalize(degree).replace(".", "").replace("'", "");
        Set<String> tokens = Set.copyOf(Arrays.asList(text.split("[^a-z0-9]+")));
        List<String> terms = new ArrayList<>();
        if (tokens.contains("mba") || text.contains("business administration")) {
            terms.addAll(List.of("Master of Business Administration", "MBA"));
        }
        if (tokens.contains("phd") || text.contains("philosophy") || text.contains("doctorate") || text.contains("doctoral")) {
            terms.addAll(List.of("Doctor of Philosophy", "Ph.D", "Doctoral", "Doctorate"));
        }
        if (tokens.contains("jd") || text.contains("juris")) terms.add("Juris Doctor");
        if (tokens.contains("md") || text.contains("doctor of medicine")) terms.add("Doctor of Medicine");
        if (text.contains("master") || hasAny(tokens, "ms", "ma", "msc", "meng", "mfa", "mph")) terms.add("Master");
        if (text.contains("bachelor") || hasAny(tokens, "bs", "ba", "bsc", "beng", "bfa", "bba", "bse", "ab")) terms.add("Bachelor");
        if (text.contains("associate") || hasAny(tokens, "aa", "as", "aas")) terms.add("Associate");
        if (text.contains("high school") || hasAny(tokens, "ged", "diploma")) terms.add("High School");
        if (text.contains("bootcamp")) terms.add("Bootcamp");
        if (text.contains("certific")) terms.add("Certification");
        if (!Text.isBlank(degree)) terms.add(degree.trim());
        terms.add("Other");
        return terms;
    }

    private static boolean hasAny(Set<String> tokens, String... wanted) {
        for (String w : wanted) if (tokens.contains(w)) return true;
        return false;
    }
}
