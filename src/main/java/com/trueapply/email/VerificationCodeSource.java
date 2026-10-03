package com.trueapply.email;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/** Somewhere we can read emailed one-time codes from. */
public interface VerificationCodeSource {

    /** Polls for a verification code in a message received after {@code since}. */
    Optional<String> waitForCode(Instant since, Duration timeout);
}
