package com.trueapply.ats;

import com.trueapply.model.AtsType;
import com.trueapply.model.Job;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/** Looks up the {@link ApplicationPlatform} for a job. Register Lever/Ashby here once written. */
public class PlatformRegistry {
    private final Map<AtsType, ApplicationPlatform> platforms = new EnumMap<>(AtsType.class);

    public PlatformRegistry register(ApplicationPlatform platform) {
        platforms.put(platform.type(), platform);
        return this;
    }

    public Optional<ApplicationPlatform> forJob(Job job) {
        if (job == null || job.ats == null) return Optional.empty();
        return Optional.ofNullable(platforms.get(job.ats));
    }

    public boolean supports(AtsType type) {
        return platforms.containsKey(type);
    }
}
