package com.trueapply.ats.greenhouse;

import com.fasterxml.jackson.databind.JsonNode;
import com.trueapply.model.AtsType;
import com.trueapply.model.Job;
import com.trueapply.util.Http;
import com.trueapply.util.Text;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/** Client for the public Greenhouse Job Board API (no key needed for reads). */
public final class GreenhouseApi {
    private static final String BASE = "https://boards-api.greenhouse.io/v1/boards/";

    private GreenhouseApi() {
    }

    public static List<Job> listJobs(String board) throws IOException {
        JsonNode root = Http.getJson(BASE + enc(board) + "/jobs");
        List<Job> jobs = new ArrayList<>();
        for (JsonNode node : root.path("jobs")) {
            jobs.add(toJob(board, node));
        }
        return jobs;
    }

    /** Full job including {@code content} (escaped HTML) and all question sets. */
    public static JsonNode jobWithQuestions(String board, String jobId) throws IOException {
        return Http.getJson(BASE + enc(board) + "/jobs/" + enc(jobId) + "?questions=true");
    }

    /** Board metadata: {@code name} and {@code content} (company blurb, HTML). */
    public static JsonNode board(String board) throws IOException {
        return Http.getJson(BASE + enc(board));
    }

    static Job toJob(String board, JsonNode node) {
        Job job = new Job();
        job.source = "greenhouse";
        job.ats = AtsType.GREENHOUSE;
        job.atsBoard = board;
        job.atsJobId = node.path("id").asText();
        job.dedupeKey = dedupeKey(board, job.atsJobId);
        job.title = node.path("title").asText("").trim();
        job.location = node.path("location").path("name").asText("");
        job.url = node.path("absolute_url").asText(GreenhouseUrls.embedFormUrl(board, job.atsJobId));
        String company = node.path("company_name").asText("");
        job.company = Text.isBlank(company) ? capitalize(board) : company;
        job.postedAt = parseTime(node.path("first_published").asText(node.path("updated_at").asText(null)));
        job.discoveredAt = Instant.now();
        return job;
    }

    public static String dedupeKey(String board, String jobId) {
        return "greenhouse:" + board.toLowerCase() + ":" + jobId;
    }

    private static Instant parseTime(String value) {
        if (Text.isBlank(value)) return null;
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
