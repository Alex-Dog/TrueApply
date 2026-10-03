package com.trueapply.discovery;

import com.trueapply.ats.workday.WorkdayApi;
import com.trueapply.ats.workday.WorkdayUrls;
import com.trueapply.model.Job;
import com.trueapply.model.UserProfile;
import com.trueapply.settings.AppSettings;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;

/**
 * Searches Workday career sites (configured in Settings, plus sites other sources linked to)
 * with Workday's public job-search API, once per preferred job title.
 */
public class WorkdayJobSource implements JobSource {
    private static final int MAX_SITES = 120;
    private static final int MAX_TITLES = 3;
    private static final int PAGES_PER_SEARCH = 2;

    private final AppSettings settings;

    public WorkdayJobSource(AppSettings settings) {
        this.settings = settings;
    }

    @Override
    public String id() {
        return "workday";
    }

    @Override
    public String name() {
        return "Workday sites";
    }

    @Override
    public boolean isConfigured() {
        return !settings.workdaySites().isEmpty() || !settings.learnedWorkdaySites().isEmpty();
    }

    @Override
    public List<Job> fetch(UserProfile profile, Consumer<String> progress) throws IOException {
        Map<String, String> sites = new LinkedHashMap<>(settings.workdaySites());
        settings.learnedWorkdaySites().forEach(sites::putIfAbsent);
        List<String> titles = profile.preferences.titles.isEmpty()
                ? List.of("") : profile.preferences.titles.stream().limit(MAX_TITLES).toList();
        int siteCount = Math.min(sites.size(), MAX_SITES);
        progress.accept("Searching " + siteCount + " Workday career sites…");

        try (ExecutorService pool = Executors.newFixedThreadPool(8)) {
            List<Future<List<Job>>> futures = new ArrayList<>();
            sites.entrySet().stream().limit(MAX_SITES).forEach(entry -> {
                Optional<WorkdayUrls.Site> site = WorkdayUrls.parseBoard(entry.getKey());
                if (site.isEmpty()) return;
                for (String title : titles) {
                    futures.add(pool.submit(() -> WorkdayApi.search(site.get(), title, PAGES_PER_SEARCH, entry.getValue())));
                }
            });
            List<Job> jobs = new ArrayList<>();
            for (Future<List<Job>> f : futures) {
                try {
                    jobs.addAll(f.get());
                } catch (ExecutionException e) {
                    // one company's site being down or renamed shouldn't stop the rest
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted", e);
                }
            }
            return jobs;
        }
    }
}
