package com.trueapply.ats;

import com.trueapply.browser.BrowserLauncher;
import com.trueapply.email.VerificationCodeSource;
import com.trueapply.model.UserProfile;

import java.util.function.Consumer;

/**
 * Everything a platform needs while submitting.
 *
 * @param visible  show the browser window; also means a human may step in to finish
 * @param progress short status updates for the UI
 */
public record SubmissionContext(
        UserProfile profile,
        BrowserLauncher browser,
        VerificationCodeSource verificationCodes,
        boolean dryRun,
        boolean visible,
        Consumer<String> progress) {
}
