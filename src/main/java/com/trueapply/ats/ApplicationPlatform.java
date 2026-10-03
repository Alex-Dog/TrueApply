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

    /** Fills the form in a browser and, unless {@code context.dryRun()}, submits it. */
    SubmissionResult submit(JobApplication application, SubmissionContext context);
}
