package com.trueapply.pipeline;

import com.trueapply.ai.AiProvider;
import com.trueapply.ai.tasks.FormAnswerer;
import com.trueapply.ai.tasks.JobSummarizer;
import com.trueapply.ats.ApplicationPlatform;
import com.trueapply.ats.LoadedForm;
import com.trueapply.ats.PlatformRegistry;
import com.trueapply.ats.SubmissionContext;
import com.trueapply.ats.SubmissionResult;
import com.trueapply.browser.BrowserLauncher;
import com.trueapply.db.AccountRepository;
import com.trueapply.db.ApplicationRepository;
import com.trueapply.db.JobRepository;
import com.trueapply.db.ProfileRepository;
import com.trueapply.email.VerificationCodeSource;
import com.trueapply.model.AnswerSource;
import com.trueapply.model.ApplicationStatus;
import com.trueapply.model.FieldCategory;
import com.trueapply.model.FieldType;
import com.trueapply.model.FormField;
import com.trueapply.model.Job;
import com.trueapply.model.JobApplication;
import com.trueapply.model.JobOverview;
import com.trueapply.model.UserProfile;
import com.trueapply.settings.AppSettings;
import com.trueapply.util.AppEvents;
import com.trueapply.util.Json;
import com.trueapply.util.Text;

import java.time.Instant;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/**
 * Moves applications through their lifecycle:
 * <pre>
 *   queue → ANALYZING → (creative/missing fields?) → NEEDS_INPUT → user answers ─┐
 *                     └────────────── no ──────────→ READY ←───────────────────┘
 *                                                     ↓
 *                                   SUBMITTING → SUBMITTED | DRY_RUN | FAILED
 * </pre>
 * All work runs on one background thread, which also keeps Playwright single-threaded.
 */
public class ApplicationPipeline {
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "application-pipeline");
        t.setDaemon(true);
        return t;
    });

    private final ApplicationRepository applications;
    private final AccountRepository accounts;
    private final JobRepository jobs;
    private final ProfileRepository profiles;
    private final AppSettings settings;
    private final PlatformRegistry platforms;
    private final Supplier<AiProvider> ai;
    private final BrowserLauncher browser;
    private final VerificationCodeSource verificationCodes;
    private final AppEvents events;

    public ApplicationPipeline(ApplicationRepository applications, AccountRepository accounts,
                               JobRepository jobs, ProfileRepository profiles,
                               AppSettings settings, PlatformRegistry platforms, Supplier<AiProvider> ai,
                               BrowserLauncher browser, VerificationCodeSource verificationCodes, AppEvents events) {
        this.applications = applications;
        this.accounts = accounts;
        this.jobs = jobs;
        this.profiles = profiles;
        this.settings = settings;
        this.platforms = platforms;
        this.ai = ai;
        this.browser = browser;
        this.verificationCodes = verificationCodes;
        this.events = events;
    }

    /** Creates applications for the given jobs and starts analyzing them. Returns how many were queued. */
    public int enqueue(Collection<Job> selected) {
        int queued = 0;
        for (Job job : selected) {
            if (!job.isSupported() || platforms.forJob(job).isEmpty()) continue;
            JobApplication app = applications.create(job.id);
            jobs.updateStatus(job.id, Job.JobStatus.QUEUED);
            worker.submit(() -> analyze(app.id));
            queued++;
        }
        events.fireChanged();
        return queued;
    }

    /** Called once at startup: anything cut off mid-flight by a crash or quit gets retried or flagged. */
    public void recoverInterrupted() {
        for (JobApplication app : applications.findByStatus(EnumSet.of(ApplicationStatus.ANALYZING))) {
            worker.submit(() -> analyze(app.id));
        }
        for (JobApplication app : applications.findByStatus(EnumSet.of(ApplicationStatus.SUBMITTING))) {
            app.status = ApplicationStatus.FAILED;
            app.statusMessage = "Interrupted while submitting — it may or may not have gone through. Check before retrying.";
            applications.save(app);
        }
        for (JobApplication app : applications.findByStatus(EnumSet.of(ApplicationStatus.READY))) {
            worker.submit(() -> submit(app.id));
        }
    }

    /** The user finished the creative/missing fields in the inbox. */
    public void completeByUser(JobApplication app, List<FormField> remember) {
        UserProfile profile = profiles.load();
        boolean profileChanged = false;
        for (FormField field : remember) {
            if (field.category == FieldCategory.CREATIVE || !field.hasAnswer()) continue; // never reuse creative answers
            profile.savedAnswers.put(Text.normalize(field.label),
                    field.type == FieldType.MULTI_SELECT ? String.join(", ", field.answers) : field.answer);
            profileChanged = true;
        }
        if (profileChanged) profiles.save(profile);

        app.status = ApplicationStatus.READY;
        app.statusMessage = "Queued for submission";
        applications.save(app);
        events.fireChanged();
        worker.submit(() -> submit(app.id));
    }

    public void retry(JobApplication app) {
        boolean analyzed = !app.fields.isEmpty();
        app.status = analyzed ? ApplicationStatus.READY : ApplicationStatus.ANALYZING;
        app.statusMessage = "Retrying…";
        applications.save(app);
        events.fireChanged();
        worker.submit(() -> {
            if (analyzed) submit(app.id);
            else analyze(app.id);
        });
    }

    public void discard(JobApplication app) {
        applications.delete(app.id);
        jobs.updateStatus(app.jobId, Job.JobStatus.DISMISSED);
        events.fireChanged();
    }

    // ---- steps (run on the worker thread) ------------------------------------------------

    private void analyze(long applicationId) {
        JobApplication app = applications.find(applicationId).orElse(null);
        if (app == null || app.job == null) return;
        try {
            setStatus(app, ApplicationStatus.ANALYZING, "Reading the application form…");
            ApplicationPlatform platform = platforms.forJob(app.job)
                    .orElseThrow(() -> new IllegalStateException("No form filler for this job's site yet."));
            UserProfile profile = profiles.load();
            LoadedForm form = platform.loadForm(app.job, profile);
            app.fields = form.fields();
            app.jobDescriptionHtml = form.jobDescriptionHtml();
            app.companyDescriptionHtml = form.companyDescriptionHtml();

            if (!platform.questionsUpfront()) {
                // Questions only appear inside the site's wizard; the browser walk finds and answers them.
                setStatus(app, ApplicationStatus.READY, "Opening the application wizard…");
                submit(app.id);
                return;
            }

            setStatus(app, ApplicationStatus.ANALYZING, "Answering factual questions…");
            new FormAnswerer(ai.get()).answer(app.fields, profile, app.job);

            if (app.pendingHumanFields(settings.includeOptionalCreative()) > 0) {
                summarizeIfNeeded(app);
                setStatus(app, ApplicationStatus.NEEDS_INPUT, null);
            } else {
                setStatus(app, ApplicationStatus.READY, "Queued for submission");
                submit(app.id);
            }
        } catch (Exception e) {
            setStatus(app, ApplicationStatus.FAILED, "Couldn't analyze: " + e.getMessage());
        }
    }

    private void submit(long applicationId) {
        JobApplication app = applications.find(applicationId).orElse(null);
        if (app == null || app.job == null) return;
        ApplicationPlatform platform = platforms.forJob(app.job).orElse(null);
        if (platform == null) {
            setStatus(app, ApplicationStatus.FAILED, "No form filler for this job's site yet.");
            return;
        }
        UserProfile profile = profiles.load();
        clearUnansweredOptional(app);
        boolean visible = settings.showBrowser();
        setStatus(app, ApplicationStatus.SUBMITTING, "Opening browser…");

        SubmissionResult result;
        try {
            result = platform.submit(app, context(app, profile, visible));
        } catch (RuntimeException e) { // e.g. the AI call for newly discovered questions failed
            result = SubmissionResult.failed(e.getMessage());
        }
        if (result.outcome() == SubmissionResult.Outcome.NEEDS_HUMAN && !visible) {
            setStatus(app, ApplicationStatus.SUBMITTING, result.message() + " Opening a visible browser so you can finish…");
            result = platform.submit(app, context(app, profile, true));
        }

        switch (result.outcome()) {
            case SUBMITTED -> {
                app.submittedAt = Instant.now();
                setStatus(app, ApplicationStatus.SUBMITTED, result.message());
            }
            case DRY_RUN -> {
                app.submittedAt = Instant.now();
                setStatus(app, ApplicationStatus.DRY_RUN, result.message());
            }
            case NEEDS_INPUT -> {
                summarizeIfNeeded(app);
                setStatus(app, ApplicationStatus.NEEDS_INPUT, result.message());
            }
            case NEEDS_HUMAN, FAILED -> setStatus(app, ApplicationStatus.FAILED, result.message());
        }
    }

    private void summarizeIfNeeded(JobApplication app) {
        if (app.overviewJson != null) return;
        setStatus(app, app.status, "Summarizing the role…");
        try {
            JobOverview overview = new JobSummarizer(ai.get())
                    .summarize(app.job, app.jobDescriptionHtml, app.companyDescriptionHtml);
            app.overviewJson = Json.write(overview);
        } catch (RuntimeException e) {
            app.overviewJson = null; // the raw description is still shown
        }
    }

    private SubmissionContext context(JobApplication app, UserProfile profile, boolean visible) {
        return new SubmissionContext(profile, browser, verificationCodes, settings.dryRun(), visible,
                message -> {
                    app.statusMessage = message;
                    applications.save(app);
                    events.fireChanged();
                },
                fields -> new FormAnswerer(ai.get()).answer(fields, profile, app.job),
                settings.includeOptionalCreative(),
                accounts,
                settings.createWorkdayAccounts());
    }

    /** Optional questions nobody answered are skipped rather than blocking submission. */
    private static void clearUnansweredOptional(JobApplication app) {
        for (FormField f : app.fields) {
            if (!f.required && !f.hasAnswer() && f.category != FieldCategory.PROFILE) {
                f.category = FieldCategory.SKIPPED;
                f.source = AnswerSource.NONE;
            }
        }
    }

    private void setStatus(JobApplication app, ApplicationStatus status, String message) {
        app.status = status;
        app.statusMessage = message;
        applications.save(app);
        events.fireChanged();
    }

    public void shutdown() {
        worker.shutdownNow();
    }
}
