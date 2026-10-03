package com.trueapply.ai.tasks;

import com.trueapply.ai.AiProvider;
import com.trueapply.ai.AiRequest;
import com.trueapply.model.AnswerSource;
import com.trueapply.model.FieldCategory;
import com.trueapply.model.FieldType;
import com.trueapply.model.FormField;
import com.trueapply.model.Job;
import com.trueapply.model.UserProfile;
import com.trueapply.util.Json;
import com.trueapply.util.Text;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Classifies each form field and answers the non-creative ones from the profile. Creative
 * questions are never answered here — they go to the user.
 */
public class FormAnswerer {
    private static final int MAX_RESUME_CHARS = 15_000;

    static final String SYSTEM = """
            You fill out job application forms on behalf of a candidate, using ONLY facts from the
            candidate's profile, resume, and saved answers. You never write creative content.

            For every field, choose exactly one category:
            - FACTUAL: an objective question answerable from the provided data (contact details, links,
              employers, titles, dates, schools, degrees, GPA, skills, years of experience with something
              named on the resume, work authorization, sponsorship, current location, willingness to relocate).
            - DEMOGRAPHIC: voluntary self-identification / EEO questions (gender, race, ethnicity, Hispanic or
              Latino, veteran status, disability, LGBTQ+, pronouns). Map the candidate's stated answer to the
              closest option. If the candidate prefers not to say, pick the option meaning "decline" / "I don't
              wish to answer".
            - CREATIVE: anything asking for opinion, motivation, interest in the company or role, personal
              stories, examples of past behavior, essays, cover letters, or "anything else". Leave answer and
              selectedOptions empty. When unsure between CREATIVE and FACTUAL for free text, choose CREATIVE.
            - MISSING_INFO: a factual question whose answer is not in the provided data (salary expectations,
              notice period, start date, referrals, how they heard about the job, legal acknowledgements or
              consent checkboxes, etc.). Leave answer and selectedOptions empty. Never guess.

            Answer rules:
            - Copy select options character-for-character from the field's options list. SINGLE_SELECT gets
              exactly one option; MULTI_SELECT gets one or more.
            - Text answers are short and factual (a name, URL, number, date, or brief phrase). No prose.
            - Never fabricate employers, dates, numbers, or credentials.
            - Return one entry per input field, using the field's key exactly.""";

    private final AiProvider ai;

    public FormAnswerer(AiProvider ai) {
        this.ai = ai;
    }

    /**
     * Fills in category/answer/source on every field that hasn't been answered yet (fields
     * already set to PROFILE are left alone).
     */
    public void answer(List<FormField> fields, UserProfile profile, Job job) {
        List<FormField> pending = new ArrayList<>();
        for (FormField field : fields) {
            if (field.category == FieldCategory.PROFILE || field.category == FieldCategory.SKIPPED) continue;
            if (field.type == FieldType.FILE) continue; // the AI can't produce files
            if (CreativeGuard.isCreative(field)) {
                markCreative(field, "Free-response question — reserved for you.");
                continue;
            }
            if (applySavedAnswer(field, profile)) continue;
            pending.add(field);
        }
        if (pending.isEmpty()) return;

        FieldDecisions decisions = ai.generateStructured(
                AiRequest.of(SYSTEM, buildPrompt(pending, profile, job)), FieldDecisions.class);
        Map<String, FieldDecisions.FieldDecision> byKey = new LinkedHashMap<>();
        if (decisions != null && decisions.fields() != null) {
            for (FieldDecisions.FieldDecision d : decisions.fields()) {
                if (d != null && d.key() != null) byKey.put(d.key(), d);
            }
        }
        for (FormField field : pending) {
            apply(field, byKey.get(field.key));
        }
    }

    /** Applies one AI decision, enforcing the creative guard and option validity. */
    static void apply(FormField field, FieldDecisions.FieldDecision decision) {
        if (decision == null) {
            markMissing(field, "The AI didn't return an answer for this question.");
            return;
        }
        FieldCategory category = parseCategory(decision.category());
        if (category == FieldCategory.CREATIVE || CreativeGuard.isCreative(field)) {
            markCreative(field, decision.note());
            return;
        }
        if (category == FieldCategory.MISSING_INFO) {
            markMissing(field, decision.note());
            return;
        }

        field.category = category;
        field.note = decision.note();
        field.source = AnswerSource.AI;
        switch (field.type) {
            case SINGLE_SELECT -> {
                if (field.options.isEmpty()) { // options only known in the browser (e.g. school)
                    if (Text.isBlank(decision.answer())) markMissing(field, decision.note());
                    else field.answer = decision.answer().trim();
                    return;
                }
                Optional<String> match = firstMatchingOption(field, decision.selectedOptions(), decision.answer());
                if (match.isEmpty()) {
                    markMissing(field, "The AI's answer didn't match any of the options.");
                } else {
                    field.answer = match.get();
                }
            }
            case MULTI_SELECT -> {
                List<String> matched = new ArrayList<>();
                if (decision.selectedOptions() != null) {
                    for (String s : decision.selectedOptions()) matchOption(field, s).ifPresent(matched::add);
                }
                if (matched.isEmpty()) {
                    markMissing(field, "The AI's answer didn't match any of the options.");
                } else {
                    field.answers = matched;
                }
            }
            default -> {
                if (Text.isBlank(decision.answer())) {
                    markMissing(field, decision.note());
                } else {
                    field.answer = decision.answer().trim();
                }
            }
        }
    }

    private static boolean applySavedAnswer(FormField field, UserProfile profile) {
        String saved = profile.savedAnswers.get(Text.normalize(field.label));
        if (Text.isBlank(saved)) return false;
        if (field.type == FieldType.SINGLE_SELECT && field.options.isEmpty()) {
            field.answer = saved; // options only known in the browser (e.g. Workday pickers)
        } else if (field.type == FieldType.SINGLE_SELECT) {
            Optional<String> match = matchOption(field, saved);
            if (match.isEmpty()) return false;
            field.answer = match.get();
        } else if (field.type == FieldType.MULTI_SELECT) {
            List<String> matched = new ArrayList<>();
            for (String s : Text.splitList(saved)) matchOption(field, s).ifPresent(matched::add);
            if (matched.isEmpty()) return false;
            field.answers = matched;
        } else if (field.type == FieldType.FILE) {
            return false;
        } else {
            field.answer = saved;
        }
        field.category = FieldCategory.FACTUAL;
        field.source = AnswerSource.SAVED_ANSWER;
        field.note = "From your saved answers.";
        return true;
    }

    private static Optional<String> firstMatchingOption(FormField field, List<String> selected, String answer) {
        if (selected != null) {
            for (String s : selected) {
                Optional<String> m = matchOption(field, s);
                if (m.isPresent()) return m;
            }
        }
        return matchOption(field, answer);
    }

    static Optional<String> matchOption(FormField field, String candidate) {
        if (Text.isBlank(candidate)) return Optional.empty();
        String wanted = Text.normalize(candidate);
        for (String option : field.options) {
            if (Text.normalize(option).equals(wanted)) return Optional.of(option);
        }
        return Optional.empty();
    }

    private static FieldCategory parseCategory(String raw) {
        if (raw == null) return FieldCategory.MISSING_INFO;
        return switch (raw.trim().toUpperCase()) {
            case "FACTUAL" -> FieldCategory.FACTUAL;
            case "DEMOGRAPHIC" -> FieldCategory.DEMOGRAPHIC;
            case "CREATIVE" -> FieldCategory.CREATIVE;
            default -> FieldCategory.MISSING_INFO;
        };
    }

    private static void markCreative(FormField field, String note) {
        field.category = FieldCategory.CREATIVE;
        field.answer = null;
        field.answers = new ArrayList<>();
        field.source = AnswerSource.NONE;
        field.note = Text.isBlank(note) ? "Creative question — reserved for you." : note;
    }

    private static void markMissing(FormField field, String note) {
        field.category = FieldCategory.MISSING_INFO;
        field.answer = null;
        field.answers = new ArrayList<>();
        field.source = AnswerSource.NONE;
        field.note = Text.isBlank(note) ? "Not in your profile." : note;
    }

    private static String buildPrompt(List<FormField> fields, UserProfile profile, Job job) {
        Map<String, Object> candidate = new LinkedHashMap<>();
        candidate.put("personal", profile.personal);
        candidate.put("demographics", profile.demographics);
        candidate.put("experience", profile.experience);
        candidate.put("education", profile.education);
        candidate.put("skills", profile.skills);
        candidate.put("savedAnswers", profile.savedAnswers);

        List<Map<String, Object>> questions = new ArrayList<>();
        for (FormField f : fields) {
            Map<String, Object> q = new LinkedHashMap<>();
            q.put("key", f.key);
            q.put("label", f.label);
            if (!Text.isBlank(f.description)) q.put("description", Text.truncate(Text.stripHtml(f.description), 500));
            q.put("type", f.type.name());
            q.put("required", f.required);
            if (!f.options.isEmpty()) q.put("options", f.options);
            questions.add(q);
        }

        String resume = Text.truncate(Text.orEmpty(profile.resumeText), MAX_RESUME_CHARS);
        return """
                Job: %s at %s (%s)

                <candidate_profile>
                %s
                </candidate_profile>

                <resume_text>
                %s
                </resume_text>

                <form_fields>
                %s
                </form_fields>""".formatted(
                job == null ? "" : job.title,
                job == null ? "" : job.company,
                job == null ? "" : Text.orEmpty(job.location),
                Json.writePretty(candidate),
                resume,
                Json.writePretty(questions));
    }
}
