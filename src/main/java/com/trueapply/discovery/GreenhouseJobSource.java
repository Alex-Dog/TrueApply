package com.trueapply.discovery;

import com.trueapply.ats.greenhouse.GreenhouseApi;
import com.trueapply.model.Job;
import com.trueapply.model.UserProfile;
import com.trueapply.settings.AppSettings;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;

/**
 * Greenhouse has no cross-company search, so we read the job boards of a configurable list of
 * companies (Settings → Discovery) plus any boards other sources led us to.
 */
public class GreenhouseJobSource implements JobSource {
    private final AppSettings settings;

    public GreenhouseJobSource(AppSettings settings) {
        this.settings = settings;
    }

    @Override
    public String id() {
        return "greenhouse";
    }

    @Override
    public String name() {
        return "Greenhouse boards";
    }

    @Override
    public boolean isConfigured() {
        return !settings.greenhouseBoards().isEmpty() || !settings.learnedGreenhouseBoards().isEmpty();
    }

    @Override
    public List<Job> fetch(UserProfile profile, Consumer<String> progress) throws IOException {
        java.util.Set<String> boards = new java.util.LinkedHashSet<>(settings.greenhouseBoards());
        boards.addAll(settings.learnedGreenhouseBoards());
        progress.accept("Reading " + boards.size() + " Greenhouse job boards…");
        try (ExecutorService pool = Executors.newFixedThreadPool(16)) {
            List<Future<List<Job>>> futures = new ArrayList<>();
            for (String board : boards) futures.add(pool.submit(() -> GreenhouseApi.listJobs(board)));
            List<Job> jobs = new ArrayList<>();
            for (Future<List<Job>> f : futures) {
                try {
                    jobs.addAll(f.get());
                } catch (ExecutionException e) {
                    // a board that moved off Greenhouse shouldn't sink the whole search
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted", e);
                }
            }
            return jobs;
        }
    }
}
