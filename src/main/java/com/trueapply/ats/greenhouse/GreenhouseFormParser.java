package com.trueapply.ats.greenhouse;

import com.fasterxml.jackson.databind.JsonNode;
import com.trueapply.model.AnswerSource;
import com.trueapply.model.FieldCategory;
import com.trueapply.model.FieldType;
import com.trueapply.model.FormField;
import com.trueapply.model.UserProfile;
import com.trueapply.util.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * Converts the Greenhouse job JSON ({@code ?questions=true}) into {@link FormField}s whose keys
 * match the DOM ids on the hosted application form.
 */
public final class GreenhouseFormParser {
    static final String SELF_ID_GROUP = "Voluntary Self-Identification";
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
