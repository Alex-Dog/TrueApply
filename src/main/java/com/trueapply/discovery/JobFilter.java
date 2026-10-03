package com.trueapply.discovery;

import com.trueapply.model.Job;
import com.trueapply.model.UserProfile;
import com.trueapply.util.Text;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/** Matches postings against the user's job preferences. */
public final class JobFilter {
    // Greenhouse doesn't expose employment type, so it's inferred from the title.
    private static final Pattern INTERN = Pattern.compile(
            "\\b(intern|interns|internship|internships|co-?op|apprentice|apprenticeship)\\b");
    private static final Pattern PART_TIME = Pattern.compile("\\bpart[- ]?time\\b");
    private static final Pattern CONTRACT = Pattern.compile("\\b(contract|contractor|freelance|temporary|temp)\\b");

    private JobFilter() {
    }

    /**
     * @param homeCountry the user's country (profile), used to judge remote postings when the
     *                    location preferences name only cities
     */
    public static boolean matches(Job job, UserProfile.JobPreferences prefs, String homeCountry) {
        List<String> titleTokens = tokens(job.title);

        if (!prefs.titles.isEmpty()
                && prefs.titles.stream().noneMatch(t -> allWordsPresent(titleTokens, tokens(t)))) {
            return false;
        }
        if (prefs.excludeKeywords.stream().anyMatch(k -> allWordsPresent(titleTokens, tokens(k)))) {
            return false;
        }
        String company = Text.normalize(job.company);
        if (prefs.excludeCompanies.stream().anyMatch(c -> !Text.isBlank(c) && company.contains(Text.normalize(c)))) {
            return false;
        }
        if (!matchesEmploymentType(job.title, prefs.employmentType)) return false;
        return LocationMatcher.matches(job.location, prefs.locations, prefs.remoteOk, homeCountry);
    }

    static boolean matchesEmploymentType(String title, String type) {
        String t = Text.normalize(title);
        boolean intern = INTERN.matcher(t).find();
        boolean partTime = PART_TIME.matcher(t).find();
        boolean contract = CONTRACT.matcher(t).find();
        return switch (Text.orEmpty(type)) {
            case "Internship" -> intern;
            case "Full-time" -> !intern && !partTime && !contract;
            case "Part-time" -> partTime;
            case "Contract" -> contract;
            default -> true;
        };
    }

    /** Each wanted word must start some word of the title ("engineer" matches "engineering"). */
    private static boolean allWordsPresent(List<String> title, List<String> wanted) {
        if (wanted.isEmpty()) return false;
        return wanted.stream().allMatch(w -> title.stream().anyMatch(t -> t.startsWith(w)));
    }

    private static List<String> tokens(String s) {
        return Arrays.stream(Text.normalize(s).split("[^a-z0-9+#]+")).filter(t -> !t.isEmpty()).toList();
    }
}
