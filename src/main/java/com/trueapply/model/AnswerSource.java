package com.trueapply.model;

/** Who produced an answer; shown in the application history. */
public enum AnswerSource {
    NONE("—"),
    PROFILE("Profile"),
    AI("AI"),
    SAVED_ANSWER("Saved answer"),
    USER("You");

    private final String displayName;

    AnswerSource(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
