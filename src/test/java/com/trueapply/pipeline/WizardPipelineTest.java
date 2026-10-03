package com.trueapply.pipeline;

import com.trueapply.ai.AiException;
import com.trueapply.ai.AiProvider;
import com.trueapply.ai.AiRequest;
import com.trueapply.ats.ApplicationPlatform;
import com.trueapply.ats.LoadedForm;
import com.trueapply.ats.PlatformRegistry;
import com.trueapply.ats.SubmissionContext;
import com.trueapply.ats.SubmissionResult;
import com.trueapply.browser.BrowserLauncher;
import com.trueapply.db.AccountRepository;
import com.trueapply.db.ApplicationRepository;
import com.trueapply.db.Database;
import com.trueapply.db.JobRepository;
import com.trueapply.db.ProfileRepository;
import com.trueapply.db.SettingsRepository;
import com.trueapply.model.ApplicationStatus;
import com.trueapply.model.AtsType;
import com.trueapply.model.FieldCategory;
import com.trueapply.model.FieldType;
import com.trueapply.model.FormField;
import com.trueapply.model.Job;
import com.trueapply.model.JobApplication;
import com.trueapply.model.UserProfile;
import com.trueapply.security.Vault;
import com.trueapply.settings.AppSettings;
import com.trueapply.util.AppEvents;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A Workday-style platform: questions show up during the walk, and the walk resumes after the user answers. */
class WizardPipelineTest {

    /** Page 1 has a creative question; once it's answered the walk reaches the end and submits. */
    private static final class FakeWizard implements ApplicationPlatform {
        final AtomicInteger walks = new AtomicInteger();

        public AtsType type() { return AtsType.WORKDAY; }

        public boolean questionsUpfront() { return false; }

        public LoadedForm loadForm(Job job, UserProfile profile) {
            return new LoadedForm(List.of(), "<p>About the role</p>", "");
        }

        public SubmissionResult submit(JobApplication app, SubmissionContext ctx) {
            walks.incrementAndGet();
            FormField why = app.fields.stream().filter(f -> f.key.equals("page1|why")).findFirst().orElse(null);
            if (why == null) {
                why = new FormField("page1|why", "Why do you want to work here?", FieldType.TEXTAREA, true);
                app.fields.add(why);
                ctx.answerer().accept(List.of(why));
            }
            if (!why.hasAnswer()) return SubmissionResult.needsInput("Answer the page-1 question to continue.");
            return SubmissionResult.submitted("done");
        }
    }

    /** No AI available: the creative guard still classifies the question, and summaries are skipped. */
    private static final AiProvider NO_AI = new AiProvider() {
        public String describe() { return "none"; }
        public String generateText(AiRequest r) { throw new AiException("offline"); }
        public <T> T generateStructured(AiRequest r, Class<T> t) { throw new AiException("offline"); }
    };

    @Test
    void stopsForTheUserThenResumesAndSubmits(@TempDir Path dir) throws Exception {
        try (Database db = new Database(dir.resolve("t.db"))) {
            SettingsRepository repo = new SettingsRepository(db);
            AppSettings settings = new AppSettings(repo);
            JobRepository jobs = new JobRepository(db);
            ApplicationRepository apps = new ApplicationRepository(db, jobs);
            FakeWizard wizard = new FakeWizard();
            ApplicationPipeline pipeline = new ApplicationPipeline(apps, new AccountRepository(db, new Vault(dir.resolve("k"))),
                    jobs, new ProfileRepository(repo), settings, new PlatformRegistry().register(wizard), () -> NO_AI,
                    new BrowserLauncher(settings), null, new AppEvents());

            Job job = new Job();
            job.dedupeKey = "workday:acme.wd1/site:R1";
            job.source = "workday";
            job.ats = AtsType.WORKDAY;
            job.atsBoard = "acme.wd1/site";
            job.atsJobId = "R1";
            job.title = "Engineer";
            job.company = "Acme";
            job.url = "https://acme.wd1.myworkdayjobs.com/site/job/x/Engineer_R1";
            jobs.insertIfNew(job);

            pipeline.enqueue(List.of(job));
            JobApplication app = waitFor(apps, ApplicationStatus.NEEDS_INPUT);
            assertEquals(1, wizard.walks.get());
            assertEquals(FieldCategory.CREATIVE, app.fields.getFirst().category);
            assertEquals("<p>About the role</p>", app.jobDescriptionHtml);

            app.fields.getFirst().answer = "Because I use your product every day.";
            pipeline.completeByUser(app, List.of());
            JobApplication done = waitFor(apps, ApplicationStatus.SUBMITTED);
            assertEquals(2, wizard.walks.get());
            assertTrue(done.submittedAt != null);
            pipeline.shutdown();
        }
    }

    private static JobApplication waitFor(ApplicationRepository apps, ApplicationStatus status) throws InterruptedException {
        Instant deadline = Instant.now().plusSeconds(10);
        while (Instant.now().isBefore(deadline)) {
            List<JobApplication> found = apps.findByStatus(EnumSet.of(status));
            if (!found.isEmpty()) return found.getFirst();
            Thread.sleep(50);
        }
        throw new AssertionError("application never reached " + status);
    }
}
