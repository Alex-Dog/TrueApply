package com.trueapply.ats;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OptionMatcherTest {
    @Test
    void matchesWholeWordsOnly() {
        List<String> options = List.of("Not Hispanic or Latino", "Hispanic or Latino", "No", "Yes");
        assertEquals(2, OptionMatcher.bestMatch(options, "No"));
        assertEquals(-1, OptionMatcher.bestMatch(List.of("Not Hispanic or Latino", "Hispanic or Latino"), "No"));
    }

    @Test
    void stillMatchesPrefixesAndContainedWords() {
        assertEquals(0, OptionMatcher.bestMatch(
                List.of("United States of America (+1)", "United States Minor Outlying Islands (+1)"), "United States"));
        assertEquals(1, OptionMatcher.bestMatch(List.of("Mathematics", "Computer Science"), "Computer Science and Engineering"));
        assertEquals(0, OptionMatcher.bestMatch(List.of("Yes, I am authorized", "No, I am not"), "Yes"));
    }
}
