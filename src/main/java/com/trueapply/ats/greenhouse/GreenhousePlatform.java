package com.trueapply.ats.greenhouse;

import com.fasterxml.jackson.databind.JsonNode;
import com.trueapply.ats.ApplicationPlatform;
import com.trueapply.ats.LoadedForm;
import com.trueapply.ats.SubmissionContext;
import com.trueapply.ats.SubmissionResult;
import com.trueapply.browser.BrowserSession;
import com.trueapply.model.AtsType;
import com.trueapply.model.Job;
import com.trueapply.model.JobApplication;
import com.trueapply.model.UserProfile;
import com.trueapply.util.Text;

import java.io.IOException;

public class GreenhousePlatform implements ApplicationPlatform {

    @Override
    public AtsType type() {
        return AtsType.GREENHOUSE;
    }

    @Override
    public LoadedForm loadForm(Job job, UserProfile profile) throws IOException {
        JsonNode detail = GreenhouseApi.jobWithQuestions(job.atsBoard, job.atsJobId);
        String companyHtml = "";
        try {
            companyHtml = GreenhouseApi.board(job.atsBoard).path("content").asText("");
        } catch (IOException e) {
            // company blurb is nice-to-have
        }
        return new LoadedForm(
                GreenhouseFormParser.parse(detail, profile),
                Text.unescapeHtml(detail.path("content").asText("")),
                companyHtml);
    }

    @Override
    public SubmissionResult submit(JobApplication application, SubmissionContext context) {
        Job job = application.job;
        context.progress().accept("Opening application form…");
        try (BrowserSession session = context.browser().open(context.visible())) {
            return new GreenhouseFormFiller(session.page(), application, context)
                    .run(GreenhouseUrls.embedFormUrl(job.atsBoard, job.atsJobId));
        } catch (RuntimeException e) {
            return SubmissionResult.failed("Browser error: " + e.getMessage());
        }
    }
}
