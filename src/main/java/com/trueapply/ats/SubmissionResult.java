package com.trueapply.ats;

public record SubmissionResult(Outcome outcome, String message) {

    public enum Outcome {
        SUBMITTED,
        /** Filled but intentionally not submitted. */
        DRY_RUN,
        /** Blocked on something only a human can do (captcha, odd field); retry with a visible browser. */
        NEEDS_HUMAN,
        /** Reached questions only the user should answer; they are in the application's fields. */
        NEEDS_INPUT,
        /**
         * The user closed the browser after it reached the submit step, where they could have
         * submitted it themselves; only they know whether they did.
         */
        WINDOW_CLOSED,
        FAILED
    }

    public static SubmissionResult submitted(String message) {
        return new SubmissionResult(Outcome.SUBMITTED, message);
    }

    public static SubmissionResult dryRun(String message) {
        return new SubmissionResult(Outcome.DRY_RUN, message);
    }

    public static SubmissionResult needsHuman(String message) {
        return new SubmissionResult(Outcome.NEEDS_HUMAN, message);
    }

    public static SubmissionResult needsInput(String message) {
        return new SubmissionResult(Outcome.NEEDS_INPUT, message);
    }

    public static SubmissionResult windowClosed(String message) {
        return new SubmissionResult(Outcome.WINDOW_CLOSED, message);
    }

    public static SubmissionResult failed(String message) {
        return new SubmissionResult(Outcome.FAILED, message);
    }
}
