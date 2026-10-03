package com.trueapply.discovery;

import com.trueapply.db.JobRepository;
import com.trueapply.db.ProfileRepository;
import com.trueapply.model.AtsType;
import com.trueapply.model.Job;
import com.trueapply.model.UserProfile;
import com.trueapply.settings.AppSettings;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;

/**
 * Runs every enabled {@link JobSource}, filters by preferences, and stores new postings.
 * Sources run in the order given; Greenhouse company boards spotted in earlier sources are
 * remembered, so the Greenhouse board scan (keep it last) picks up those companies' other jobs.
 */
public class DiscoveryService {
    private final List<JobSource> sources;
    private final JobRepository jobs;
    private final ProfileRepository profiles;
    private final AppSettings settings;

    public DiscoveryService(List<JobSource> sources, JobRepository jobs, ProfileRepository profiles, AppSettings settings) {
        this.sources = sources;
        this.jobs = jobs;
        this.profiles = profiles;
        this.settings = settings;
    }

    public List<JobSource> sources() {
        return sources;
    }

    /**
     * @param perSource matching jobs per source name (sources that were skipped are absent)
     * @param learnedBoards Greenhouse boards discovered for the first time during this run
     */
    public record Result(int matched, int added, Map<String, Integer> perSource, int learnedBoards,
                         List<String> warnings) {
    }

    public Result discover(Consumer<String> progress) {
        UserProfile profile = profiles.load();
        List<String> warnings = new ArrayList<>();
        Map<String, Integer> perSource = new LinkedHashMap<>();
        int matched = 0;
        int added = 0;
        int learned = 0;

        for (JobSource source : sources) {
            if (!settings.sourceEnabled(source.id())) continue;
            if (!source.isConfigured()) {
                warnings.add(source.name() + " is not configured (see Settings).");
                continue;
            }
            List<Job> found;
            try {
                found = source.fetch(profile, progress);
            } catch (Exception e) {
                warnings.add(source.name() + " failed: " + e.getMessage());
                continue;
            }
            learned += learnBoards(found);

            int sourceMatches = 0;
            for (Job job : found) {
                if (!JobFilter.matches(job, profile.preferences, profile.personal.country)) continue;
                sourceMatches++;
                if (jobs.insertIfNew(job)) added++;
            }
            perSource.put(source.name(), sourceMatches);
            matched += sourceMatches;
        }
        return new Result(matched, added, perSource, learned, warnings);
    }

    private int learnBoards(List<Job> found) {
        Set<String> known = settings.learnedGreenhouseBoards();
        known.addAll(settings.greenhouseBoards());
        Set<String> fresh = new TreeSet<>();
        for (Job job : found) {
            if (job.ats == AtsType.GREENHOUSE && job.atsBoard != null && !known.contains(job.atsBoard.toLowerCase())) {
                fresh.add(job.atsBoard.toLowerCase());
            }
        }
        if (!fresh.isEmpty()) settings.addLearnedGreenhouseBoards(fresh);
        return fresh.size();
    }
}
