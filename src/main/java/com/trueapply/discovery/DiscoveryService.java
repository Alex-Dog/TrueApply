package com.trueapply.discovery;

import com.trueapply.db.JobRepository;
import com.trueapply.db.ProfileRepository;
import com.trueapply.model.Job;
import com.trueapply.model.UserProfile;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Runs every enabled {@link JobSource}, filters by preferences, and stores new postings. */
public class DiscoveryService {
    private final List<JobSource> sources;
    private final JobRepository jobs;
    private final ProfileRepository profiles;

    public DiscoveryService(List<JobSource> sources, JobRepository jobs, ProfileRepository profiles) {
        this.sources = sources;
        this.jobs = jobs;
        this.profiles = profiles;
    }

    public record Result(int matched, int added, List<String> warnings) {
    }

    public Result discover(Consumer<String> progress) {
        UserProfile.JobPreferences prefs = profiles.load().preferences;
        List<String> warnings = new ArrayList<>();
        List<Job> found = new ArrayList<>();
        for (JobSource source : sources) {
            if (!source.isEnabled()) {
                warnings.add(source.name() + " is not configured (see Settings).");
                continue;
            }
            try {
                found.addAll(source.fetch(prefs, progress));
            } catch (Exception e) {
                warnings.add(source.name() + " failed: " + e.getMessage());
            }
        }

        int matched = 0;
        int added = 0;
        for (Job job : found) {
            if (!JobFilter.matches(job, prefs)) continue;
            matched++;
            if (jobs.insertIfNew(job)) added++;
        }
        return new Result(matched, added, warnings);
    }
}
