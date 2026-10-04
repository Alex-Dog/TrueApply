package com.trueapply.ats;

import com.trueapply.browser.BrowserLauncher;
import com.trueapply.db.AccountRepository;
import com.trueapply.email.VerificationCodeSource;
import com.trueapply.model.FormField;
import com.trueapply.model.UserProfile;

import java.util.List;
import java.util.function.Consumer;

/**
 * Everything a platform needs while submitting.
 *
 * @param visible                 show the browser window; also means a human may step in to finish
 * @param progress                short status updates for the UI
 * @param answerer                classifies and answers newly discovered fields (AI + saved answers);
 *                                used by platforms whose questions aren't known upfront
 * @param includeOptionalCreative whether optional creative questions also wait for the user
 * @param accounts                saved job-site logins (encrypted)
 * @param createAccounts          whether the platform may create accounts on its own
 * @param lookAhead               fill questions meant for the user with temporary answers to reach later
 *                                pages, so every question can be asked at once (never submitted)
 */
public record SubmissionContext(
        UserProfile profile,
        BrowserLauncher browser,
        VerificationCodeSource verificationCodes,
        boolean dryRun,
        boolean visible,
        Consumer<String> progress,
        Consumer<List<FormField>> answerer,
        boolean includeOptionalCreative,
        AccountRepository accounts,
        boolean createAccounts,
        boolean lookAhead) {
}
