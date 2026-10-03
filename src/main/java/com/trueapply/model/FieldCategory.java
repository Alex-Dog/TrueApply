package com.trueapply.model;

/** Who is allowed to answer a field. */
public enum FieldCategory {
    /** Filled straight from the profile (name, email, resume upload...). */
    PROFILE("Profile"),
    /** Factual question the AI answers from the profile/resume. */
    FACTUAL("Factual"),
    /** EEO / self-identification question mapped from the user's demographics. */
    DEMOGRAPHIC("Demographic"),
    /** Opinion, motivation, or essay question. Only the human writes these. */
    CREATIVE("Creative"),
    /** Factual question we can't answer from what we know; the human supplies it. */
    MISSING_INFO("Missing info"),
    /** Optional field we intentionally leave blank. */
    SKIPPED("Skipped");

    private final String displayName;

    FieldCategory(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
