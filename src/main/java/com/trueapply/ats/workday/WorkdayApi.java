package com.trueapply.ats.workday;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.trueapply.ats.AtsUrls;
import com.trueapply.model.AtsType;
import com.trueapply.model.Job;
import com.trueapply.util.Http;
import com.trueapply.util.Json;
import com.trueapply.util.Text;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Workday's public career-site JSON API (the one the job search page itself uses; no login). */
public final class WorkdayApi {
    private static final int PAGE_SIZE = 20; // the API's maximum
    private static final Pattern DAYS_AGO = Pattern.compile("(\\d+)\\+?\\s+Days?\\s+Ago", Pattern.CASE_INSENSITIVE);

    private WorkdayApi() {
    }

    public static List<Job> search(WorkdayUrls.Site site, String text, int maxPages, String company) throws IOException {
        List<Job> jobs = new ArrayList<>();
        for (int page = 0; page < maxPages; page++) {
            ObjectNode body = Json.MAPPER.createObjectNode();
            body.putObject("appliedFacets");
            body.put("limit", PAGE_SIZE);
            body.put("offset", page * PAGE_SIZE);
            body.put("searchText", Text.orEmpty(text));
            JsonNode root = Http.postJson(site.apiBase() + "/jobs", Json.write(body));
            JsonNode postings = root.path("jobPostings");
            for (JsonNode posting : postings) jobs.add(toJob(site, posting, company));
            if (postings.size() < PAGE_SIZE || (page + 1) * PAGE_SIZE >= root.path("total").asInt(0)) break;
        }
        return jobs;
    }

    /** {@code jobPostingInfo} (title, jobDescription HTML, location, timeType...) for one job. */
    public static JsonNode detail(WorkdayUrls.JobRef ref) throws IOException {
        return Http.getJson(ref.site().apiBase() + ref.externalPath());
    }

    static Job toJob(WorkdayUrls.Site site, JsonNode posting, String company) {
        String path = posting.path("externalPath").asText("");
        Job job = new Job();
        job.source = "workday";
        job.url = site.jobUrl(path);
        AtsUrls.tag(job, job.url);
        if (job.ats == null) { // malformed path; keep it as a manual job rather than drop it
            job.ats = AtsType.WORKDAY;
            job.dedupeKey = "workday:" + site.board().toLowerCase() + ":" + path;
        }
        job.title = posting.path("title").asText("").trim();
        job.company = Text.isBlank(company) ? capitalize(site.tenant()) : company;
        job.location = posting.path("locationsText").asText("");
        job.postedAt = postedAt(posting.path("postedOn").asText(""));
        job.discoveredAt = Instant.now();
        return job;
    }

    /** "Posted Today" / "Posted Yesterday" / "Posted 17 Days Ago" / "Posted 30+ Days Ago". */
    static Instant postedAt(String text) {
        String t = text.toLowerCase();
        if (t.contains("today")) return Instant.now();
        if (t.contains("yesterday")) return Instant.now().minus(Duration.ofDays(1));
        Matcher m = DAYS_AGO.matcher(text);
        return m.find() ? Instant.now().minus(Duration.ofDays(Long.parseLong(m.group(1)))) : null;
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
