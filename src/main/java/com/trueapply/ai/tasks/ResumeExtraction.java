package com.trueapply.ai.tasks;

import java.util.List;

/** Structured-output schema for {@link ResumeExtractor}. */
public record ResumeExtraction(
        String firstName,
        String lastName,
        String email,
        String phone,
        String city,
        String state,
        String country,
        String linkedinUrl,
        String githubUrl,
        String websiteUrl,
        List<String> skills,
        List<Experience> experience,
        List<School> education) {

    public record Experience(
            String company,
            String title,
            String location,
            String startDate,
            String endDate,
            boolean current,
            String description) {
    }

    public record School(
            String school,
            String degree,
            String fieldOfStudy,
            String startDate,
            String endDate,
            String gpa) {
    }
}
