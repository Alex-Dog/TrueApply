package com.trueapply.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** One application to one job: its questions, answers, and lifecycle. */
public class JobApplication {
    public long id;
    public long jobId;
    /** Loaded alongside the application for convenience; not persisted with it. */
    public Job job;
    public ApplicationStatus status = ApplicationStatus.ANALYZING;
    public String statusMessage;
    public List<FormField> fields = new ArrayList<>();
    /** AI overview of the role, as JSON ({@link JobOverview}). */
    public String overviewJson;
    public String jobDescriptionHtml;
    public String companyDescriptionHtml;
    public Instant createdAt;
    public Instant updatedAt;
    public Instant submittedAt;

    public long pendingHumanFields(boolean includeOptional) {
        return fields.stream()
                .filter(FormField::needsHuman)
                .filter(f -> f.required || includeOptional)
                .filter(f -> !f.hasAnswer())
                .count();
    }

    public String displayTitle() {
        if (job == null) return "Application #" + id;
        return job.title + " — " + job.company;
    }
}
