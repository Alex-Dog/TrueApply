package com.trueapply.ats.greenhouse;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GreenhouseFormFillerTest {

    @Test
    void bestMatchPrefersExactThenPrefixThenContains() {
        List<String> options = List.of("United States Minor Outlying Islands +1", "United States +1", "Canada +1");
        assertEquals(1, GreenhouseFormFiller.bestMatch(List.of("Yes", "No"), "no"));
        assertEquals(1, GreenhouseFormFiller.bestMatch(options, "United States"));
        assertEquals(2, GreenhouseFormFiller.bestMatch(options, "canada"));
        assertEquals(-1, GreenhouseFormFiller.bestMatch(options, "Mexico"));
        assertEquals(1, GreenhouseFormFiller.bestMatch(List.of("Decline", "I don’t wish to answer"), "I don't wish to answer"));
    }
}
