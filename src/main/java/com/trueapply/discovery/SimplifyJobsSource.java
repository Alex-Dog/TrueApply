package com.trueapply.discovery;

import com.fasterxml.jackson.databind.JsonNode;
import com.trueapply.ats.AtsUrls;
import com.trueapply.model.Job;
import com.trueapply.model.UserProfile;
import com.trueapply.util.Http;
import com.trueapply.util.Text;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The community-maintained SimplifyJobs internship and new-grad lists on GitHub. Postings link
 * straight to the company's ATS, so many are Greenhouse forms we can fill.
 */
public class SimplifyJobsSource implements JobSource {
    private static final String RAW = "https://raw.githubusercontent.com/SimplifyJobs/%s/dev/.github/scripts/listings.json";
    private static final Set<String> NO_SPONSORSHIP = Set.of("does not offer sponsorship", "u.s. citizenship is required");

    @Override
    public String id() {
        return "simplify";
    }

    @Override
    public String name() {
        return "SimplifyJobs";
    }

    @Override
    public boolean isConfigured() {
        return true;
    }

    @Override
    public List<Job> fetch(UserProfile profile, Consumer<String> progress) throws IOException {
        String type = Text.orEmpty(profile.preferences.employmentType);
        boolean needsSponsorship = "Yes".equalsIgnoreCase(profile.demographics.requiresSponsorship);
        List<Job> jobs = new ArrayList<>();
        if (type.equals("Internship") || type.equals("Any")) {
            progress.accept("Reading SimplifyJobs internship list…");
            jobs.addAll(toJobs(internships(), "Internship", needsSponsorship));
        }
        if (type.equals("Full-time") || type.equals("Any")) {
            progress.accept("Reading SimplifyJobs new-grad list…");
            jobs.addAll(toJobs(Http.getJson(RAW.formatted("New-Grad-Positions")), "Full-time", needsSponsorship));
        }
        return jobs;
    }

    /** Recruiting for next summer starts around July, so switch to next year's list then. */
    private static JsonNode internships() throws IOException {
        LocalDate today = LocalDate.now();
        int season = today.getYear() + (today.getMonthValue() >= Month.JULY.getValue() ? 1 : 0);
        try {
            return Http.getJson(RAW.formatted("Summer" + season + "-Internships"));
        } catch (IOException notCreatedYet) {
            return Http.getJson(RAW.formatted("Summer" + (season - 1) + "-Internships"));
        }
    }

    static List<Job> toJobs(JsonNode listings, String jobType, boolean needsSponsorship) {
        List<Job> jobs = new ArrayList<>();
        for (JsonNode n : listings) {
            if (!n.path("active").asBoolean(false) || !n.path("is_visible").asBoolean(true)) continue;
            if (needsSponsorship && NO_SPONSORSHIP.contains(Text.normalize(n.path("sponsorship").asText("")))) continue;
            String url = n.path("url").asText("");
            if (url.isEmpty()) continue;

            Job job = new Job();
            job.source = "simplify";
            job.dedupeKey = "simplify:" + n.path("id").asText();
            job.url = url;
            AtsUrls.tag(job, url);
            job.company = n.path("company_name").asText("Unknown company");
            job.title = n.path("title").asText("").trim();
            List<String> locations = new ArrayList<>();
            for (JsonNode l : n.path("locations")) locations.add(l.asText());
            job.location = String.join("; ", locations);
            long posted = n.path("date_posted").asLong(0);
            job.postedAt = posted > 0 ? Instant.ofEpochSecond(posted) : null;
            job.discoveredAt = Instant.now();
            job.jobType = jobType;
            jobs.add(job);
        }
        return jobs;
    }
}
