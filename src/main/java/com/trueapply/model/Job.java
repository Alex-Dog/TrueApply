package com.trueapply.model;

import java.time.Instant;

/** A job posting found by discovery. */
public class Job {
    public long id;
    /** Unique identity across sources, e.g. "greenhouse:discord:8806482002". */
    public String dedupeKey;
    /** Where we found it: "greenhouse", "adzuna". */
    public String source;
    /** Platform hosting the application form; null when unsupported. */
    public AtsType ats;
    /** Platform-specific board/company identifier (Greenhouse board token). */
    public String atsBoard;
    public String atsJobId;
    public String company;
    public String title;
    public String location;
    /** Public posting URL. */
    public String url;
    public String snippet;
    public Instant postedAt;
    public Instant discoveredAt;
    public JobStatus status = JobStatus.NEW;
    /**
     * Employment type when the source states it ("Internship", "Full-time"...); null means
     * unknown, and filters fall back to reading the title.
     */
    public String jobType;

    public boolean isSupported() {
        return ats != null && atsBoard != null && atsJobId != null;
    }

    public enum JobStatus {
        /** Found by discovery, not acted on. */
        NEW,
        /** An application exists for this job. */
        QUEUED,
        DISMISSED
    }
}
