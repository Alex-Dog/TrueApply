package com.trueapply.ai.tasks;

import com.trueapply.ai.AiProvider;
import com.trueapply.ai.AiRequest;
import com.trueapply.model.UserProfile;
import com.trueapply.util.Text;

import java.util.ArrayList;
import java.util.List;

/** Turns resume text into structured profile data. */
public class ResumeExtractor {
    private static final String SYSTEM = """
            You extract structured data from a resume. Copy facts exactly as written; never invent,
            embellish, or infer details that are not on the page. Use an empty string for anything
            missing. Dates should be short and human-readable as they appear (e.g. "Jun 2023", "2021").
            For each job, set current=true only if the resume says Present/Current. For descriptions,
            copy the bullet points verbatim, one per line.""";

    private final AiProvider ai;

    public ResumeExtractor(AiProvider ai) {
        this.ai = ai;
    }

    public ResumeExtraction extract(String resumeText) {
        AiRequest request = AiRequest.of(SYSTEM, "Resume:\n\n" + resumeText).withEffort(AiRequest.Effort.LOW);
        return ai.generateStructured(request, ResumeExtraction.class);
    }

    /**
     * Copies extracted data into the profile: personal fields only where the profile is blank,
     * and experience/education/skills replaced wholesale when the resume has any.
     */
    public static void merge(ResumeExtraction data, UserProfile profile) {
        UserProfile.PersonalInfo p = profile.personal;
        p.firstName = keep(p.firstName, data.firstName());
        p.lastName = keep(p.lastName, data.lastName());
        p.email = keep(p.email, data.email());
        p.phone = keep(p.phone, data.phone());
        p.city = keep(p.city, data.city());
        p.state = keep(p.state, data.state());
        if (!Text.isBlank(data.country())) p.country = keep(p.country, data.country());
        p.linkedinUrl = keep(p.linkedinUrl, data.linkedinUrl());
        p.githubUrl = keep(p.githubUrl, data.githubUrl());
        p.websiteUrl = keep(p.websiteUrl, data.websiteUrl());

        if (data.experience() != null && !data.experience().isEmpty()) {
            List<UserProfile.WorkExperience> list = new ArrayList<>();
            for (ResumeExtraction.Experience e : data.experience()) {
                UserProfile.WorkExperience w = new UserProfile.WorkExperience();
                w.company = Text.orEmpty(e.company());
                w.title = Text.orEmpty(e.title());
                w.location = Text.orEmpty(e.location());
                w.startDate = Text.orEmpty(e.startDate());
                w.endDate = Text.orEmpty(e.endDate());
                w.current = e.current();
                w.description = Text.orEmpty(e.description());
                list.add(w);
            }
            profile.experience = list;
        }
        if (data.education() != null && !data.education().isEmpty()) {
            List<UserProfile.Education> list = new ArrayList<>();
            for (ResumeExtraction.School s : data.education()) {
                UserProfile.Education ed = new UserProfile.Education();
                ed.school = Text.orEmpty(s.school());
                ed.degree = Text.orEmpty(s.degree());
                ed.fieldOfStudy = Text.orEmpty(s.fieldOfStudy());
                ed.startDate = Text.orEmpty(s.startDate());
                ed.endDate = Text.orEmpty(s.endDate());
                ed.gpa = Text.orEmpty(s.gpa());
                list.add(ed);
            }
            profile.education = list;
        }
        if (data.skills() != null && !data.skills().isEmpty()) {
            profile.skills = new ArrayList<>(data.skills());
        }
    }

    private static String keep(String current, String extracted) {
        return Text.isBlank(current) ? Text.orEmpty(extracted).trim() : current;
    }
}
