package com.trueapply.model;

public enum ApplicationStatus {
    ANALYZING("Analyzing"),
    /** Has creative or missing-info fields waiting for the user. */
    NEEDS_INPUT("Needs your input"),
    /** Every field answered; waiting for the browser. */
    READY("Ready to submit"),
    SUBMITTING("Submitting"),
    SUBMITTED("Submitted"),
    /** Form was filled but deliberately not submitted (dry-run setting). */
    DRY_RUN("Dry run"),
    /** The browser was closed at the submit step; waiting for the user to say whether they applied. */
    AWAITING_CONFIRMATION("Did you apply?"),
    FAILED("Failed");

    private final String displayName;

    ApplicationStatus(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public boolean isFinished() {
        return this == SUBMITTED || this == DRY_RUN;
    }
}
