package com.trueapply.discovery;

import com.fasterxml.jackson.databind.JsonNode;
import com.trueapply.ats.greenhouse.GreenhouseApi;
import com.trueapply.ats.greenhouse.GreenhouseUrls;
import com.trueapply.model.AtsType;
import com.trueapply.model.Job;
import com.trueapply.model.UserProfile;
import com.trueapply.settings.AppSettings;
import com.trueapply.util.Http;
import com.trueapply.util.Text;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;

/**
 * Adzuna aggregates postings from everywhere. We follow each result's redirect to see whether
 * it lands on a Greenhouse form we can fill; the rest are kept as "apply manually".
 */
public class AdzunaJobSource implements JobSource {
    private static final int MAX_TITLES = 5;

    private final AppSettings settings;

    public AdzunaJobSource(AppSettings settings) {
        this.settings = settings;
    }

    @Override
    public String name() {
        return "Adzuna";
    }

    @Override
    public boolean isEnabled() {
        return !Text.isBlank(settings.adzunaAppId()) && !Text.isBlank(settings.adzunaAppKey());
    }

    @Override
    public List<Job> fetch(UserProfile.JobPreferences prefs, Consumer<String> progress) throws IOException {
        List<String> titles = prefs.titles.isEmpty() ? List.of("") : prefs.titles.stream().limit(MAX_TITLES).toList();
        String where = prefs.locations.isEmpty() ? "" : prefs.locations.getFirst();
        List<Job> jobs = new ArrayList<>();
        for (String title : titles) {
            progress.accept("Searching Adzuna for “" + (title.isEmpty() ? "all jobs" : title) + "”…");
            jobs.addAll(search(prefs, title, where));
        }
        progress.accept("Checking which Adzuna results use Greenhouse…");
        resolveGreenhouse(jobs);
        return jobs;
    }

    private List<Job> search(UserProfile.JobPreferences prefs, String what, String where) throws IOException {
        StringBuilder url = new StringBuilder("https://api.adzuna.com/v1/api/jobs/")
                .append(enc(Text.isBlank(prefs.adzunaCountry) ? "us" : prefs.adzunaCountry.toLowerCase()))
                .append("/search/1?app_id=").append(enc(settings.adzunaAppId()))
                .append("&app_key=").append(enc(settings.adzunaAppKey()))
                .append("&results_per_page=50&max_days_old=30&content-type=application/json");
        if (!what.isEmpty()) url.append("&what=").append(enc(what));
        if (!where.isEmpty()) url.append("&where=").append(enc(where));
        if (prefs.minimumSalary != null) url.append("&salary_min=").append(prefs.minimumSalary);
        switch (Text.orEmpty(prefs.employmentType)) {
            case "Full-time" -> url.append("&full_time=1");
            case "Part-time" -> url.append("&part_time=1");
            case "Contract" -> url.append("&contract=1");
            default -> {
            }
        }

        JsonNode root = Http.getJson(url.toString());
        List<Job> jobs = new ArrayList<>();
        for (JsonNode r : root.path("results")) {
            Job job = new Job();
            job.source = "adzuna";
            job.dedupeKey = "adzuna:" + r.path("id").asText();
            job.title = Text.stripHtml(r.path("title").asText(""));
            job.company = r.path("company").path("display_name").asText("Unknown company");
            job.location = r.path("location").path("display_name").asText("");
            job.url = r.path("redirect_url").asText("");
            job.snippet = Text.stripHtml(r.path("description").asText(""));
            job.postedAt = parseInstant(r.path("created").asText(null));
            job.discoveredAt = Instant.now();
            jobs.add(job);
        }
        return jobs;
    }

    private void resolveGreenhouse(List<Job> jobs) throws IOException {
        try (ExecutorService pool = Executors.newFixedThreadPool(8)) {
            List<Future<?>> futures = new ArrayList<>();
            for (Job job : jobs) futures.add(pool.submit(() -> resolve(job)));
            for (Future<?> f : futures) {
                try {
                    f.get();
                } catch (ExecutionException e) {
                    // unresolved jobs stay "apply manually"
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted", e);
                }
            }
        }
    }

    private static void resolve(Job job) {
        if (Text.isBlank(job.url)) return;
        try {
            HttpResponse<String> response = Http.send(HttpRequest.newBuilder(URI.create(job.url))
                    .header("User-Agent", Http.USER_AGENT)
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build());
            String finalUrl = response.uri().toString();
            Optional<GreenhouseUrls.Ref> ref = GreenhouseUrls.parse(finalUrl);
            if (ref.isEmpty()) {
                Optional<String> jobId = GreenhouseUrls.ghJid(finalUrl);
                Optional<String> board = GreenhouseUrls.boardFromHtml(response.body());
                if (jobId.isPresent() && board.isPresent()) ref = Optional.of(new GreenhouseUrls.Ref(board.get(), jobId.get()));
            }
            if (ref.isPresent()) {
                job.ats = AtsType.GREENHOUSE;
                job.atsBoard = ref.get().board();
                job.atsJobId = ref.get().jobId();
                job.dedupeKey = GreenhouseApi.dedupeKey(job.atsBoard, job.atsJobId);
                job.url = finalUrl;
            }
        } catch (IOException | IllegalArgumentException e) {
            // leave as unsupported
        }
    }

    private static Instant parseInstant(String value) {
        if (Text.isBlank(value)) return null;
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
