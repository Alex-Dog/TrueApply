package com.trueapply.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.ArrayList;
import java.util.List;

/** One question on an application form, together with its answer once we have one. */
public class FormField {
    /** Platform field name. For Greenhouse this is also the DOM id of the input. */
    public String key;
    public String label;
    public String description;
    public FieldType type;
    public boolean required;
    public List<String> options = new ArrayList<>();
    /** Section heading, e.g. "Application" or "Voluntary Self-Identification". */
    public String group = "Application";
    /** Platform-specific widget hint (Workday: "dropdown", "prompt", "date"...); null when obvious. */
    public String control;

    public FieldCategory category = FieldCategory.FACTUAL;
    /** Text answer, chosen option for single selects, or a file path for file fields. */
    public String answer;
    /** Chosen options for multi-select fields. */
    public List<String> answers = new ArrayList<>();
    public AnswerSource source = AnswerSource.NONE;
    /** Short explanation from the AI (why it was classified this way / what is missing). */
    public String note;

    public FormField() {
    }

    public FormField(String key, String label, FieldType type, boolean required) {
        this.key = key;
        this.label = label;
        this.type = type;
        this.required = required;
    }

    @JsonIgnore
    public boolean hasAnswer() {
        if (type == FieldType.MULTI_SELECT) return answers != null && !answers.isEmpty();
        return answer != null && !answer.isBlank();
    }

    /** Fields the human has to deal with: creative questions and facts we don't know. */
    @JsonIgnore
    public boolean needsHuman() {
        return category == FieldCategory.CREATIVE || category == FieldCategory.MISSING_INFO;
    }

    @JsonIgnore
    public String displayAnswer() {
        if (type == FieldType.MULTI_SELECT) return answers == null ? "" : String.join(", ", answers);
        return answer == null ? "" : answer;
    }
}
