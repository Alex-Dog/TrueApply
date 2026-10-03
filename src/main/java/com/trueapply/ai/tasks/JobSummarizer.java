package com.trueapply.ai.tasks;

import com.trueapply.ai.AiProvider;
import com.trueapply.ai.AiRequest;
import com.trueapply.model.Job;
import com.trueapply.model.JobOverview;
import com.trueapply.util.Text;

/** Condenses a posting into a quick overview for the inbox. */
public class JobSummarizer {
    private static final String SYSTEM = """
            Summarize a job posting for a candidate who is about to write application answers.
            Be concrete and neutral; use only what the posting says. roleSummary: 2-3 sentences.
            responsibilities and requirements: up to 6 short bullets each. companySummary: 1-2 sentences
            about what the company does. compensation: the stated pay range, or an empty string.""";

    private final AiProvider ai;

    public JobSummarizer(AiProvider ai) {
        this.ai = ai;
    }

    public JobOverview summarize(Job job, String jobHtml, String companyHtml) {
        String prompt = """
                Company: %s
                Title: %s
                Location: %s

                <company_description>
                %s
                </company_description>

                <job_description>
                %s
                </job_description>""".formatted(
                job.company, job.title, Text.orEmpty(job.location),
                Text.truncate(Text.stripHtml(companyHtml), 4_000),
                Text.truncate(Text.stripHtml(jobHtml), 20_000));
        return ai.generateStructured(
                new AiRequest(SYSTEM, prompt, AiRequest.Effort.LOW, 4_000), JobOverview.class);
    }
}
