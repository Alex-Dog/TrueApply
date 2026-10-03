package com.trueapply.discovery;

import com.trueapply.model.Job;
import com.trueapply.model.UserProfile;
import com.trueapply.util.Text;

import java.util.Arrays;
import java.util.List;

/** Matches postings against the user's job preferences. */
public final class JobFilter {
    private JobFilter() {
    }

    public static boolean matches(Job job, UserProfile.JobPreferences prefs) {
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
        if (!prefs.locations.isEmpty()) {
            String location = Text.normalize(job.location);
            boolean near = prefs.locations.stream().anyMatch(l -> location.contains(Text.normalize(l)));
            boolean remote = prefs.remoteOk && location.contains("remote");
            return near || remote;
        }
        return true;
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
