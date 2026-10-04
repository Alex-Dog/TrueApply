package com.trueapply.ai.tasks;

import com.trueapply.model.UserProfile;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EthnicityMatcherTest {
    private static final List<String> COMBINED = List.of(
            "American Indian or Alaska Native (Not Hispanic or Latino)",
            "Asian (Not Hispanic or Latino)",
            "Black or African American (Not Hispanic or Latino)",
            "Hispanic or Latino",
            "White (Not Hispanic or Latino)",
            "Two or More Races (Not Hispanic or Latino)",
            "I do not wish to answer");

    private static UserProfile.Demographics demographics(String hispanic, String... races) {
        UserProfile.Demographics d = new UserProfile.Demographics();
        d.hispanicOrLatino = hispanic;
        d.race = List.of(races);
        return d;
    }

    @Test
    void recognizesOnlyTheCombinedList() {
        assertTrue(EthnicityMatcher.isCombinedList(COMBINED));
        assertFalse(EthnicityMatcher.isCombinedList(List.of("Yes", "No", "Decline to self-identify")));
        assertEquals(Optional.empty(), EthnicityMatcher.pick(List.of("Yes", "No"), demographics("No", "White")));
    }

    @Test
    void picksFromTheProfile() {
        assertEquals("White (Not Hispanic or Latino)", EthnicityMatcher.pick(COMBINED, demographics("No", "White")).orElseThrow());
        assertEquals("Hispanic or Latino", EthnicityMatcher.pick(COMBINED, demographics("Yes", "White")).orElseThrow());
        assertEquals("Two or More Races (Not Hispanic or Latino)",
                EthnicityMatcher.pick(COMBINED, demographics("No", "White", "Asian")).orElseThrow());
        assertEquals("I do not wish to answer",
                EthnicityMatcher.pick(COMBINED, demographics(UserProfile.Demographics.DECLINE)).orElseThrow());
    }
}
