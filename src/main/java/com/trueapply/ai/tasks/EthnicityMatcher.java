package com.trueapply.ai.tasks;

import com.trueapply.model.UserProfile;
import com.trueapply.util.Text;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * US EEO forms often merge Hispanic/Latino and race into one list ("White (Not Hispanic or
 * Latino)", "Hispanic or Latino", "Two or More Races (Not Hispanic or Latino)", "I do not wish to
 * answer"). A yes/no answer can't be mapped onto it, so pick the option from the profile directly.
 */
public final class EthnicityMatcher {
    private static final Pattern NOT_HISPANIC = Pattern.compile("not\\s+hispanic", Pattern.CASE_INSENSITIVE);
    private static final Pattern DECLINE = Pattern.compile(
            "decline|not wish|don.?t wish|do not want|prefer not|choose not|not to (say|answer|disclose|self)",
            Pattern.CASE_INSENSITIVE);

    private EthnicityMatcher() {
    }

    /** True when the options are the merged ethnicity + race list. */
    public static boolean isCombinedList(List<String> options) {
        return options.stream().anyMatch(o -> NOT_HISPANIC.matcher(o).find());
    }

    public static Optional<String> pick(List<String> options, UserProfile.Demographics d) {
        if (!isCombinedList(options)) return Optional.empty();
        String hispanic = Text.normalize(d.hispanicOrLatino);
        if (hispanic.equals("yes")) {
            return options.stream()
                    .filter(o -> Text.normalize(o).contains("hispanic") && !NOT_HISPANIC.matcher(o).find())
                    .findFirst();
        }
        List<String> races = d.race == null ? List.of() : d.race;
        if (races.size() > 1) {
            Optional<String> multi = options.stream().filter(o -> Text.normalize(o).contains("two or more")).findFirst();
            if (multi.isPresent()) return multi;
        }
        if (races.size() == 1) {
            String race = Text.normalize(races.getFirst());
            Optional<String> match = options.stream()
                    .filter(o -> Text.normalize(o).startsWith(race) || Text.normalize(o).contains(race))
                    .filter(o -> hispanic.equals("no") || NOT_HISPANIC.matcher(o).find())
                    .findFirst();
            if (match.isPresent()) return match;
        }
        return options.stream().filter(o -> DECLINE.matcher(o).find()).findFirst();
    }
}
