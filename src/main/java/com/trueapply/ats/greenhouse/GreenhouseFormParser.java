package com.trueapply.ats.greenhouse;

import com.fasterxml.jackson.databind.JsonNode;
import com.trueapply.model.AnswerSource;
import com.trueapply.model.FieldCategory;
import com.trueapply.model.FieldType;
import com.trueapply.model.FormField;
import com.trueapply.model.UserProfile;
import com.trueapply.util.DateParts;
import com.trueapply.util.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * Converts the Greenhouse job JSON ({@code ?questions=true}) into {@link FormField}s whose keys
 * match the DOM ids on the hosted application form.
 */
public final class GreenhouseFormParser {
    static final String SELF_ID_GROUP = "Voluntary Self-Identification";
    static final String EDUCATION_GROUP = "Education";
    static final String EMPLOYMENT_GROUP = "Employment";
    static final List<String> HISPANIC_OPTIONS = List.of("Yes", "No", "Decline To Self Identify");

    private GreenhouseFormParser() {
    }

    public static List<FormField> parse(JsonNode job, UserProfile profile) {
        List<FormField> fields = new ArrayList<>();

        for (JsonNode q : job.path("questions")) {
            for (JsonNode f : q.path("fields")) {
                String name = f.path("name").asText();
                String type = f.path("type").asText();
                // We upload the resume file and send cover letters as text.
                if (type.equals("input_hidden") || name.equals("resume_text") || name.equals("cover_letter")) continue;
                FormField field = field(name, q, type, f);
                fields.add(field);
                if (name.equals("phone")) {
                    // Not in the API, but the hosted form has a country picker next to the phone number.
                    FormField country = new FormField("country", "Phone country", FieldType.TEXT, false);
                    fields.add(country);
                }
            }
        }

        // The API only flags these sections ("education_required" etc.); their inputs follow a
        // fixed naming scheme on the hosted form. We fill one entry from the most recent item.
        String employment = job.path("employment").asText("");
        if (employment.startsWith("employment_")) {
            addEmployment(fields, profile, employment.equals("employment_required"));
        }
        String education = job.path("education").asText("");
        if (education.startsWith("education_")) {
            addEducation(fields, profile, education.equals("education_required"));
        }

        for (JsonNode q : job.path("location_questions")) {
            for (JsonNode f : q.path("fields")) {
                if (f.path("type").asText().equals("input_hidden")) continue;
                FormField field = field(f.path("name").asText(), q, f.path("type").asText(), f);
                if (field.key.equals("location")) field.type = FieldType.LOCATION;
                fields.add(field);
            }
        }

        for (JsonNode section : job.path("compliance")) {
            for (JsonNode q : section.path("questions")) {
                for (JsonNode f : q.path("fields")) {
                    String name = f.path("name").asText();
                    if (f.path("type").asText().equals("input_hidden")) continue;
                    // The hosted form asks Hispanic/Latino first and only then reveals race.
                    if (name.equals("race") && fields.stream().noneMatch(x -> x.key.equals("hispanic_ethnicity"))) {
                        FormField hispanic = new FormField("hispanic_ethnicity", "Are you Hispanic/Latino?",
                                FieldType.SINGLE_SELECT, q.path("required").asBoolean());
                        hispanic.options = new ArrayList<>(HISPANIC_OPTIONS);
                        hispanic.group = SELF_ID_GROUP;
                        hispanic.category = FieldCategory.DEMOGRAPHIC;
                        fields.add(hispanic);
                    }
                    FormField field = field(name, q, f.path("type").asText(), f);
                    field.group = SELF_ID_GROUP;
                    field.category = FieldCategory.DEMOGRAPHIC;
                    fields.add(field);
                }
            }
        }

        JsonNode demographic = job.path("demographic_questions");
        String demographicGroup = demographic.path("header").asText(SELF_ID_GROUP);
        for (JsonNode q : demographic.path("questions")) {
            FormField field = new FormField(q.path("id").asText(), q.path("label").asText(),
                    mapType(q.path("type").asText(), ""), q.path("required").asBoolean());
            for (JsonNode option : q.path("answer_options")) field.options.add(option.path("label").asText());
            field.group = demographicGroup;
            field.category = FieldCategory.DEMOGRAPHIC;
            fields.add(field);
        }

        prefill(fields, profile);
        return fields;
    }

    /** Most schools a form gets; the filler adds entries with the section's "Add another" button. */
    static final int MAX_EDUCATION_ENTRIES = 4;

    /** One entry per school in the profile (inputs "school--0", "school--1"...); only the first can be required. */
    private static void addEducation(List<FormField> fields, UserProfile profile, boolean required) {
        List<UserProfile.Education> schools = profile.education.isEmpty()
                ? List.of(new UserProfile.Education())
                : profile.education.subList(0, Math.min(profile.education.size(), MAX_EDUCATION_ENTRIES));
        for (int i = 0; i < schools.size(); i++) {
            UserProfile.Education ed = schools.get(i);
            boolean req = required && i == 0;
            String n = i == 0 ? "" : " (" + (i + 1) + ")";
            fields.add(sectionField("school--" + i, "School" + n, FieldType.SINGLE_SELECT, req, ed.school, EDUCATION_GROUP));
            fields.add(sectionField("degree--" + i, "Degree" + n, FieldType.SINGLE_SELECT, req, ed.degree, EDUCATION_GROUP));
            fields.add(sectionField("discipline--" + i, "Discipline" + n, FieldType.SINGLE_SELECT, req, ed.fieldOfStudy, EDUCATION_GROUP));
            // Some companies ask month and year, others only the year; the filler skips inputs a form lacks.
            DateParts start = DateParts.parse(ed.startDate);
            DateParts end = DateParts.parse(ed.endDate);
            fields.add(sectionField("start-month--" + i, "Education start month" + n, FieldType.SINGLE_SELECT, false,
                    start.month(), EDUCATION_GROUP));
            fields.add(sectionField("start-year--" + i, "Education start year" + n, FieldType.TEXT, false,
                    start.year(), EDUCATION_GROUP));
            fields.add(sectionField("end-month--" + i, "Education end month" + n, FieldType.SINGLE_SELECT, false,
                    end.month(), EDUCATION_GROUP));
            fields.add(sectionField("end-year--" + i, "Education end year" + n, FieldType.TEXT, false,
                    end.year(), EDUCATION_GROUP));
        }
    }

    private static void addEmployment(List<FormField> fields, UserProfile profile, boolean required) {
        UserProfile.WorkExperience job = profile.experience.isEmpty()
                ? new UserProfile.WorkExperience() : profile.experience.getFirst();
        DateParts start = DateParts.parse(job.startDate);
        DateParts end = DateParts.parse(job.endDate);
        fields.add(sectionField("company-name-0", "Most recent employer", FieldType.TEXT, required, job.company, EMPLOYMENT_GROUP));
        fields.add(sectionField("title-0", "Job title", FieldType.TEXT, required, job.title, EMPLOYMENT_GROUP));
        fields.add(sectionField("start-date-month-0", "Job start month", FieldType.SINGLE_SELECT, required, start.month(), EMPLOYMENT_GROUP));
        fields.add(sectionField("start-date-year-0", "Job start year", FieldType.TEXT, required, start.year(), EMPLOYMENT_GROUP));
        boolean known = !Text.isBlank(job.company);
        FormField current = sectionField("current-role-0", "Current role", FieldType.SINGLE_SELECT, false,
                known ? (job.current ? "Yes" : "No") : null, EMPLOYMENT_GROUP);
        current.options = new ArrayList<>(List.of("Yes", "No"));
        fields.add(current);
        boolean needEnd = required && !job.current;
        FormField endMonth = sectionField("end-date-month-0", "Job end month", FieldType.SINGLE_SELECT, needEnd,
                job.current ? null : end.month(), EMPLOYMENT_GROUP);
        FormField endYear = sectionField("end-date-year-0", "Job end year", FieldType.TEXT, needEnd,
                job.current ? null : end.year(), EMPLOYMENT_GROUP);
        fields.add(endMonth);
        fields.add(endYear);
    }

    /** A field answered from the profile when we know the value, else asked or skipped. */
    private static FormField sectionField(String key, String label, FieldType type, boolean required, String value, String group) {
        FormField field = new FormField(key, label, type, required);
        field.group = group;
        if (!Text.isBlank(value)) {
            field.answer = value.trim();
            field.category = FieldCategory.PROFILE;
            field.source = AnswerSource.PROFILE;
        } else if (required) {
            field.category = FieldCategory.MISSING_INFO;
            field.note = "Add this to the " + group.toLowerCase() + " section of your profile, or answer it here.";
        } else {
            field.category = FieldCategory.SKIPPED;
        }
        return field;
    }

    private static FormField field(String name, JsonNode question, String type, JsonNode f) {
        FormField field = new FormField(name, question.path("label").asText(name), mapType(type, name),
                question.path("required").asBoolean());
        String description = question.path("description").asText("");
        if (!Text.isBlank(description) && !description.equals("null")) field.description = Text.unescapeHtml(description);
        for (JsonNode v : f.path("values")) field.options.add(v.path("label").asText());
        return field;
    }

    static FieldType mapType(String greenhouseType, String name) {
        if (name.equals("cover_letter_text")) return FieldType.TEXTAREA;
        return switch (greenhouseType) {
            case "textarea" -> FieldType.TEXTAREA;
            case "multi_value_single_select" -> FieldType.SINGLE_SELECT;
            case "multi_value_multi_select" -> FieldType.MULTI_SELECT;
            case "input_file" -> FieldType.FILE;
            default -> FieldType.TEXT;
        };
    }

    /** Answers the standard contact/resume fields directly from the profile. */
    static void prefill(List<FormField> fields, UserProfile profile) {
        UserProfile.PersonalInfo p = profile.personal;
        for (FormField field : fields) {
            String value = switch (field.key) {
                case "first_name" -> p.firstName;
                case "last_name" -> p.lastName;
                case "preferred_name" -> p.preferredName;
                case "email" -> p.email;
                case "phone" -> p.phone;
                case "country" -> p.country;
                case "location" -> Text.isBlank(p.state) ? p.city : p.city + ", " + p.state;
                case "resume" -> profile.resumePath;
                default -> null;
            };
            boolean known = switch (field.key) {
                case "first_name", "last_name", "preferred_name", "email", "phone", "country", "location", "resume" -> true;
                default -> false;
            };
            if (known) {
                if (!Text.isBlank(value)) {
                    field.answer = value.trim();
                    field.category = FieldCategory.PROFILE;
                    field.source = AnswerSource.PROFILE;
                } else if (field.required) {
                    field.category = FieldCategory.MISSING_INFO;
                    field.note = "Add this to your profile, or answer it here.";
                } else {
                    field.category = FieldCategory.SKIPPED;
                }
            } else if (field.type == FieldType.FILE) {
                field.category = FieldCategory.MISSING_INFO;
                field.note = "This question needs a file upload.";
            }
        }
    }
}
