package com.trueapply.email;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/** Somewhere we can read emailed one-time codes and verification links from. */
public interface VerificationCodeSource {

    /** Polls for a verification code in a message received after {@code since}. */
    Optional<String> waitForCode(Instant since, Duration timeout);

    /**
     * Polls for an account-verification link pointing at {@code host} in a message received
     * after {@code since}.
     */
    default Optional<String> waitForLink(Instant since, String host, Duration timeout) {
        return Optional.empty();
    }
}
