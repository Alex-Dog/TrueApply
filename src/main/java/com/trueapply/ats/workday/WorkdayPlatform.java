package com.trueapply.ats.workday;

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

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Workday career sites. Questions only exist inside the signed-in application wizard, so
 * {@link #submit} walks the wizard page by page (see {@link WorkdayWalker}).
 */
public class WorkdayPlatform implements ApplicationPlatform {

    @Override
    public AtsType type() {
        return AtsType.WORKDAY;
    }

    @Override
    public boolean questionsUpfront() {
        return false;
    }

    /** Only the description is available without signing in; questions come from the walker. */
    @Override
    public LoadedForm loadForm(Job job, UserProfile profile) throws IOException {
        Optional<WorkdayUrls.JobRef> ref = WorkdayUrls.job(job.url);
        if (ref.isEmpty()) return new LoadedForm(List.of(), "", "");
        JsonNode detail = WorkdayApi.detail(ref.get());
        JsonNode info = detail.path("jobPostingInfo");
        String company = detail.path("hiringOrganization").path("name").asText("");
        String facts = "<p><b>Location:</b> " + info.path("location").asText("")
                + " · <b>Time type:</b> " + info.path("timeType").asText("")
                + " · <b>Requisition:</b> " + info.path("jobReqId").asText("") + "</p>";
        return new LoadedForm(List.of(), facts + info.path("jobDescription").asText(""),
                company.isBlank() ? "" : "<p>" + company + "</p>");
    }

    @Override
    public SubmissionResult submit(JobApplication application, SubmissionContext context) {
        context.progress().accept("Opening Workday…");
        try (BrowserSession session = context.browser().open(context.visible())) {
            return new WorkdayWalker(session.page(), application, context).run();
        } catch (RuntimeException e) {
            return SubmissionResult.failed("Workday automation error: " + WorkdayWalker.readableError(e));
        }
    }
}
