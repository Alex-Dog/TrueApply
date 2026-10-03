package com.trueapply.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Everything the app knows about the applicant. Persisted as one JSON document. */
public class UserProfile {
    public PersonalInfo personal = new PersonalInfo();
    public Demographics demographics = new Demographics();
    public JobPreferences preferences = new JobPreferences();
    public List<WorkExperience> experience = new ArrayList<>();
    public List<Education> education = new ArrayList<>();
    public List<String> skills = new ArrayList<>();

    /** Absolute path to the resume file that gets uploaded to applications. */
    public String resumePath;
    /** Plain text extracted from the resume; gives the AI context for factual questions. */
    public String resumeText;

    /**
     * Non-creative answers the user chose to remember (e.g. "Desired salary"), keyed by
     * normalized question label. Creative answers are never stored here.
     */
    public Map<String, String> savedAnswers = new LinkedHashMap<>();

    public static class PersonalInfo {
        public String firstName = "";
        public String lastName = "";
        public String preferredName = "";
        public String email = "";
        public String phone = "";
        public String city = "";
        public String state = "";
        public String country = "United States";
        public String postalCode = "";
        public String linkedinUrl = "";
        public String githubUrl = "";
        public String websiteUrl = "";

        public String fullName() {
            return (firstName + " " + lastName).trim();
        }

        public String locationLine() {
            StringBuilder sb = new StringBuilder();
            for (String part : new String[] {city, state, country}) {
                if (part != null && !part.isBlank()) {
                    if (!sb.isEmpty()) sb.append(", ");
                    sb.append(part.trim());
                }
            }
            return sb.toString();
        }
    }

    public static class Demographics {
        public static final String DECLINE = "Prefer not to say";

        /** "Yes" / "No" */
        public String authorizedToWork = "Yes";
        /** "Yes" / "No" — will now or in the future require visa sponsorship */
        public String requiresSponsorship = "No";
        public String willingToRelocate = "No";
        public String gender = DECLINE;
        /** "Yes" / "No" / decline */
        public String hispanicOrLatino = DECLINE;
        /** One or more race categories, or empty for decline. */
        public List<String> race = new ArrayList<>();
        public String veteranStatus = DECLINE;
        public String disabilityStatus = DECLINE;
        public String lgbtq = DECLINE;
        public String pronouns = "";
    }

    public static class JobPreferences {
        /** Job titles / keywords, e.g. "Software Engineer", "Data Analyst". */
        public List<String> titles = new ArrayList<>();
        /** Preferred locations, e.g. "San Francisco", "New York". */
        public List<String> locations = new ArrayList<>();
        public boolean remoteOk = true;
        /** "Full-time", "Part-time", "Internship", "Contract", or "Any". */
        public String employmentType = "Full-time";
        public Integer minimumSalary;
        /** Titles containing any of these are skipped (e.g. "Senior", "Manager"). */
        public List<String> excludeKeywords = new ArrayList<>();
        public List<String> excludeCompanies = new ArrayList<>();
        /** Two-letter Adzuna country code. */
        public String adzunaCountry = "us";
    }

    public static class WorkExperience {
        public String company = "";
        public String title = "";
        public String location = "";
        public String startDate = "";
        public String endDate = "";
        public boolean current;
        public String description = "";

        @Override
        public String toString() {
            return title + " @ " + company;
        }
    }

    public static class Education {
        public String school = "";
        public String degree = "";
        public String fieldOfStudy = "";
        public String startDate = "";
        public String endDate = "";
        public String gpa = "";

        @Override
        public String toString() {
            return school + (degree.isBlank() ? "" : " — " + degree);
        }
    }
}
