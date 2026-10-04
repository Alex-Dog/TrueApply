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

    public long pendingHumanFields(boolean includeOptionalCreative) {
        return fields.stream().filter(f -> blocksSubmission(f, includeOptionalCreative)).count();
    }

    /**
     * Whether an unanswered field holds the application for the user: required creative or
     * missing-info fields always do; optional creative ones only when the user asked for that.
     * Optional missing info (e.g. "Address Line 2") is simply left blank.
     */
    public static boolean blocksSubmission(FormField f, boolean includeOptionalCreative) {
        if (!f.needsHuman() || f.hasAnswer()) return false;
        if (f.required) return true;
        return includeOptionalCreative && f.category == FieldCategory.CREATIVE;
    }

    public String displayTitle() {
        if (job == null) return "Application #" + id;
        return job.title + " — " + job.company;
    }
}
