package com.trueapply.ats;

import com.trueapply.model.AtsType;
import com.trueapply.model.Job;
import com.trueapply.model.JobApplication;
import com.trueapply.model.UserProfile;

import java.io.IOException;

/**
 * One applicant tracking system (Greenhouse, Lever, Ashby...). Implementations know how to read
 * a job's questions and how to drive its application form in a browser.
 */
public interface ApplicationPlatform {

    AtsType type();

    /**
     * Fetches the job's questions and descriptions. Fields the platform can answer straight
     * from the profile (name, email, resume...) should come back as
     * {@link com.trueapply.model.FieldCategory#PROFILE} with answers set.
     */
    LoadedForm loadForm(Job job, UserProfile profile) throws IOException;

    /** The posting's description as HTML, for showing before applying. */
    default String loadDescription(Job job) throws IOException {
        return loadForm(job, new UserProfile()).jobDescriptionHtml();
    }

    /** Fills the form in a browser and, unless {@code context.dryRun()}, submits it. */
    SubmissionResult submit(JobApplication application, SubmissionContext context);

    /**
     * True when {@link #loadForm} returns every question before any browser work (Greenhouse).
     * Platforms that only reveal questions inside the application wizard (Workday) return false:
     * their {@code loadForm} supplies just the descriptions, and {@link #submit} discovers, answers
     * (via {@link SubmissionContext#answerer()}) and fills questions page by page, returning
     * {@link SubmissionResult.Outcome#NEEDS_INPUT} when a page needs the human. It is then called
     * again after the user answers, and resumes where it stopped.
     */
    default boolean questionsUpfront() {
        return true;
    }
}
