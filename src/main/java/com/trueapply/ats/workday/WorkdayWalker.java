package com.trueapply.ats.workday;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.trueapply.ats.OptionMatcher;
import com.trueapply.ats.SubmissionContext;
import com.trueapply.ats.SubmissionResult;
import com.trueapply.browser.PageBanner;
import com.trueapply.model.AnswerSource;
import com.trueapply.model.FieldCategory;
import com.trueapply.model.FieldType;
import com.trueapply.model.FormField;
import com.trueapply.model.JobApplication;
import com.trueapply.model.SavedAccount;
import com.trueapply.model.UserProfile;
import com.trueapply.security.PasswordGenerator;
import com.trueapply.util.DateParts;
import com.trueapply.util.Text;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.Month;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Drives the Workday application wizard: Sign In → My Information → My Experience →
 * Application Questions → Voluntary Disclosures → (Self Identify) → Review.
 *
 * <p>Workday tags its widgets with stable {@code data-automation-id} attributes across
 * companies, so pages are read generically: every visible {@code formField-*} container becomes
 * a {@link FormField} (keyed by step + container id), new fields are answered by the AI, and
 * the page is filled and saved. When a page has creative or unknown required questions, the
 * walk stops with NEEDS_INPUT; the next run resumes from the draft Workday keeps in the account.
 */
final class WorkdayWalker {
    private static final Duration HUMAN_TIMEOUT = Duration.ofMinutes(10);
    private static final int MAX_STEPS = 15;
    private static final Pattern CONFIRMATION = Pattern.compile(
            "application (has been )?(successfully )?submitted|thank you for (applying|your application)|congratulations",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ALREADY_APPLIED = Pattern.compile("already applied", Pattern.CASE_INSENSITIVE);
    /** Post-signup notice; deliberately not matching the form's own "Verify New Password" label. */
    private static final Pattern VERIFY_NOTICE = Pattern.compile(
            "verify your (email|account)|verification (email|link)|activate your account|check your email",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern RESUME = Pattern.compile("resume|cv|curriculum", Pattern.CASE_INSENSITIVE);
    private static final Pattern RESUME_STEP = Pattern.compile("autofill|resume|quick apply", Pattern.CASE_INSENSITIVE);
    private static final Pattern PREFERRED_NAME = Pattern.compile("preferred name", Pattern.CASE_INSENSITIVE);

    /** Reads every visible Workday form field on the page (see class comment). */
    private static final String EXTRACT_JS = """
            () => {
              const visible = e => !!(e && (e.offsetWidth || e.offsetHeight || e.getClientRects().length));
              const labelFor = input => {
                const byFor = input.id ? document.querySelector('label[for="' + input.id + '"]') : null;
                return ((byFor && byFor.innerText) || input.getAttribute('aria-label')
                        || (input.closest('label') && input.closest('label').innerText) || '').trim();
              };
              const out = [];
              const counts = {};
              for (const c of document.querySelectorAll('[data-automation-id^="formField-"]')) {
                if (!visible(c)) continue;
                if (c.parentElement && c.parentElement.closest('[data-automation-id^="formField-"]')) continue;
                const id = c.getAttribute('data-automation-id');
                counts[id] = counts[id] === undefined ? 0 : counts[id] + 1;
                const labelEl = c.querySelector('label, legend');
                let label = labelEl ? labelEl.innerText : (c.getAttribute('aria-label') || '');
                const required = label.includes('*') || !!c.querySelector('[aria-required="true"], [required]');
                const sectionEl = c.closest('[data-automation-id^="workExperience-"], [data-automation-id^="education-"],'
                    + ' [data-automation-id^="language-"], [data-automation-id^="websites-"], [data-automation-id^="certification-"]');
                let section = '';
                if (sectionEl) {
                  const h = sectionEl.querySelector('h3, h4, h5');
                  section = h ? h.innerText.trim() : sectionEl.getAttribute('data-automation-id');
                }
                if (!section) { // newer layout: entries are role=group, titled by aria-labelledby
                  let g = c.parentElement && c.parentElement.closest('[role="group"][aria-labelledby]');
                  while (g && !section) {
                    const t = document.getElementById(g.getAttribute('aria-labelledby'));
                    const text = t ? t.innerText.trim() : '';
                    if (text && /\\d/.test(text)) section = text; // "Work Experience 2", "Education 1"
                    g = g.parentElement && g.parentElement.closest('[role="group"][aria-labelledby]');
                  }
                }
                let control = null;
                let options = [];
                const radios = [...c.querySelectorAll('input[type="radio"]')];
                const boxes = [...c.querySelectorAll('input[type="checkbox"]')];
                if (c.querySelector('[data-automation-id^="dateSection"]')) control = 'date';
                else if (c.querySelector('input[type="file"]')) control = 'file';
                else if (c.querySelector('button[aria-haspopup="listbox"]')) control = 'dropdown';
                else if (c.querySelector('[data-automation-id="multiselectInputContainer"], input[data-automation-id="searchBox"]')) control = 'prompt';
                else if (radios.length) { control = 'radio'; options = radios.map(labelFor); }
                else if (boxes.length > 1) { control = 'checkboxes'; options = boxes.map(labelFor); }
                else if (boxes.length === 1) control = 'checkbox';
                else if (c.querySelector('textarea')) control = 'textarea';
                else if (c.querySelector('input:not([type="hidden"]):not([readonly]):not([disabled]):not([type="password"]):not([data-automation-id="beecatcher"])')) control = 'text';
                if (!control) continue;
                label = label.split('*').join('').trim();
                if (!label && control === 'checkbox') label = labelFor(boxes[0]);
                if (!label) label = (c.innerText || '').split('\\n')[0].split('*').join('').trim();
                const anyValue = sel => [...c.querySelectorAll(sel)].some(i => (i.value || '').trim() !== '');
                let hasValue = false;
                if (control === 'text' || control === 'textarea') hasValue = anyValue('input, textarea');
                else if (control === 'date') hasValue = anyValue('input');
                else if (control === 'dropdown') {
                  const t = (c.querySelector('button[aria-haspopup="listbox"]').innerText || '').trim().toLowerCase();
                  hasValue = t !== '' && t !== 'select one';
                }
                else if (control === 'prompt') hasValue = !!c.querySelector('[data-automation-id="selectedItem"]');
                else if (control === 'file') hasValue = !!c.querySelector('[data-automation-id="file-upload-successful"], [data-automation-id="delete-file"]');
                else hasValue = [...c.querySelectorAll('input')].some(i => i.checked);
                out.push({ id, index: counts[id], label, section, required, control, options, hasValue });
              }
              return out;
            }""";

    private final Page page;
    private final JobApplication app;
    private final SubmissionContext ctx;
    private final UserProfile profile;
    private String host = "";
    /** True once we went through "Autofill with Resume" (Workday then creates the history entries). */
    private boolean autofilled;
    /** Fields Workday had already filled when we read the page (this run only). */
    private final java.util.Set<String> prefilled = new java.util.HashSet<>();

    WorkdayWalker(Page page, JobApplication app, SubmissionContext ctx) {
        this.page = page;
        this.app = app;
        this.ctx = ctx;
        this.profile = ctx.profile();
        // Fail fast on a missing element instead of Playwright's default 30 s per action.
        page.setDefaultTimeout(10_000);
    }

    SubmissionResult run() {
        String url = app.job.url;
        host = URI.create(url).getHost();
        try {
            Optional<SubmissionResult> entered = enterApplyFlow(url);
            if (entered.isPresent()) return entered.get();
            return walk(url);
        } catch (PlaywrightException | IllegalStateException e) {
            // Anything unexpected: log the page's buttons for diagnosis and let the user finish.
            String step = safeCurrentStep();
            String reason = readableError(e);
            WorkdayDebugLog.recordPage(app, step, page, reason);
            return handOffOrFail("Got stuck on Workday's “" + (step.isEmpty() ? "current" : step) + "” page (" + reason + ").");
        }
    }

    private SubmissionResult walk(String url) {
        String lastStep = null;
        int sameStepCount = 0;
        for (int i = 0; i < MAX_STEPS; i++) {
            page.waitForTimeout(1_500);
            if (isConfirmation()) return SubmissionResult.submitted("Application submitted on Workday.");
            if (signInVisible()) {
                Optional<SubmissionResult> auth = authenticate(url);
                if (auth.isPresent()) return auth.get();
                continue;
            }
            String step = currentStep();
            if (step.isEmpty()) {
                return handOffOrFail("Couldn't find the Workday application wizard on this page.");
            }
            sameStepCount = step.equals(lastStep) ? sameStepCount + 1 : 0;
            lastStep = step;
            if (sameStepCount >= 2) {
                return handOffOrFail("Workday wouldn't move past “" + step + "”: " + String.join("; ", visibleErrors()));
            }
            ctx.progress().accept("Workday: " + step);

            waitForStepReady(); // Workday renders each page's form asynchronously
            if (step.toLowerCase().contains("review")) return review();
            if (RESUME_STEP.matcher(step).find()) { // "Autofill with Resume" / "Quick Apply" upload page
                uploadResumeIfAsked();
                autofilled = true;
                clickNext();
                continue;
            }

            boolean experienceStep = step.toLowerCase().contains("experience");
            filledThisVisit.clear();
            List<FormField> fields;
            List<String> problems;
            try {
                if (experienceStep) {
                    prepareExperiencePage();
                    WorkdayDebugLog.recordStructure(app, step, page);
                }
                PageBanner.working(page, "reading the questions on this page…");
                fields = readPage(step);
                // Fill what we can first: leftover blank entries are only recognizable once the real ones are filled.
                problems = fillPage(fields);
                if (experienceStep && removeBlankEntries() > 0) {
                    page.waitForTimeout(1_000);
                    fields = readPage(step);
                    problems = fillPage(fields);
                }
            } catch (PageChanged moved) {
                // The wizard advanced on its own (it only does that once required fields are valid);
                // carry on with whatever page we're on now.
                ctx.progress().accept("Workday moved on from “" + step + "” early; continuing.");
                continue;
            }
            PageBanner.clear(page);
            List<FormField> blocking = fields.stream()
                    .filter(f -> JobApplication.blocksSubmission(f, ctx.includeOptionalCreative()))
                    .filter(f -> !prefilled.contains(f.key))
                    .toList();
            if (!blocking.isEmpty()) {
                return SubmissionResult.needsInput("Answer " + blocking.size() + " question" + (blocking.size() == 1 ? "" : "s")
                        + " from Workday's “" + step + "” page to continue. Workday reveals questions page by page,"
                        + " so more may follow.");
            }
            if (!problems.isEmpty()) {
                return handOffOrFail("Couldn't fill on “" + step + "”: " + String.join("; ", problems));
            }
            clickNext();
        }
        return handOffOrFail("Workday's wizard had more steps than expected.");
    }

    // ---- getting into the wizard -----------------------------------------------------------

    private Optional<SubmissionResult> enterApplyFlow(String url) {
        page.navigate(url, new Page.NavigateOptions().setTimeout(60_000));
        page.waitForTimeout(4_000);
        if (page.getByText(ALREADY_APPLIED).filter(visibleOnly()).count() > 0) {
            return Optional.of(SubmissionResult.failed("Workday says you've already applied to this job."));
        }
        if (!currentStep().isEmpty() || signInVisible()) return Optional.empty(); // already inside the flow
        Locator start = startButton(15_000);
        if (start == null) {
            WorkdayDebugLog.recordPage(app, "job posting", page, "no Apply / Continue Application button");
            return Optional.of(SubmissionResult.failed("This Workday posting has no Apply button (it may be closed)."));
        }
        boolean continuing = CONTINUE_LABEL.matcher(Text.orEmpty(start.innerText())).find();
        start.click();
        if (continuing) {
            // A draft exists (we're signed in from the last run): Workday reopens it at the saved step.
            ctx.progress().accept("Continuing your saved Workday application…");
            page.waitForTimeout(4_000);
            return Optional.empty();
        }
        // Prefer "Autofill with Resume": Workday parses the resume into work/education entries,
        // which beats clicking its "Add" buttons; every field is still checked against the profile.
        Locator autofill = page.locator("[data-automation-id='autofillWithResume']");
        Locator manual = page.locator("[data-automation-id='applyManually']");
        boolean haveResume = !Text.isBlank(profile.resumePath) && Files.isRegularFile(Path.of(profile.resumePath));
        if (haveResume && waitVisible(autofill, 8_000)) {
            autofill.first().click();
            autofilled = true;
        } else if (waitVisible(manual, haveResume ? 1_000 : 8_000)) {
            manual.first().click();
        }
        page.waitForTimeout(4_000);
        return Optional.empty();
    }

    private static final Pattern START_LABEL =
            Pattern.compile("^\\s*(apply|apply now|continue application|continue)\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern CONTINUE_LABEL = Pattern.compile("continue", Pattern.CASE_INSENSITIVE);

    /**
     * The posting's start button: "Apply" for a new application, "Continue Application" when a
     * draft exists. Workday gives them different ids, so match the known ids or the label.
     */
    private Locator startButton(long timeoutMs) {
        Instant deadline = Instant.now().plusMillis(timeoutMs);
        while (Instant.now().isBefore(deadline)) {
            Locator byId = page.locator("[data-automation-id='adventureButton'], [data-automation-id='continueButton'],"
                    + " [data-automation-id='continueApplicationButton']").filter(visibleOnly());
            if (byId.count() > 0) return byId.first();
            for (AriaRole role : List.of(AriaRole.BUTTON, AriaRole.LINK)) {
                Locator byLabel = page.getByRole(role, new Page.GetByRoleOptions().setName(START_LABEL)).filter(visibleOnly());
                if (byLabel.count() > 0) return byLabel.first();
            }
            page.waitForTimeout(500);
        }
        return null;
    }

    // ---- accounts ----------------------------------------------------------------------------

    private boolean signInVisible() {
        return isVisible("[data-automation-id='SignInWithEmailButton']")
                || isVisible("input[data-automation-id='password']")
                || isVisible("[data-automation-id='signInContent']");
    }

    /** Signs in with a saved account, creates one if allowed, or hands the step to the user. */
    private Optional<SubmissionResult> authenticate(String url) {
        if (isVisible("[data-automation-id='SignInWithEmailButton']")) {
            page.locator("[data-automation-id='SignInWithEmailButton']").first().click();
            page.waitForTimeout(1_500);
        }
        Optional<SavedAccount> saved = savedAccount();
        Instant since = Instant.now();
        if (saved.isPresent()) {
            ctx.progress().accept("Signing in to " + host + "…");
            if (isVisible("[data-automation-id='signInLink']") && isVisible("input[data-automation-id='verifyPassword']")) {
                page.locator("[data-automation-id='signInLink']").first().click();
                page.waitForTimeout(1_000);
            }
            fillCredentials(saved.get().username, saved.get().password, false);
            clickButton("signInSubmitButton");
            return afterAuthentication(url, since, null);
        }
        if (!ctx.createAccounts()) {
            return waitForHumanSignIn("create an account (or sign in) for " + app.job.company
                    + " in this window. TrueApply saves the login to its Accounts page and continues on its own."
                    + " To skip this step next time, turn on Settings → Workday accounts.");
        }
        String email = profile.personal.email;
        if (Text.isBlank(email)) return Optional.of(SubmissionResult.failed("Add your email to your profile first."));

        ctx.progress().accept("Creating a Workday account for " + app.job.company + "…");
        if (isVisible("[data-automation-id='createAccountLink']")) {
            page.locator("[data-automation-id='createAccountLink']").first().click();
            page.waitForTimeout(1_500);
        }
        // Save the login before submitting, so the password is never lost.
        SavedAccount account = new SavedAccount();
        account.company = app.job.company;
        account.siteUrl = "https://" + host;
        account.username = email;
        account.password = PasswordGenerator.generate(16);
        account.notes = "Workday candidate account created by TrueApply";
        ctx.accounts().insert(account);

        fillCredentials(email, account.password, true);
        Locator agree = page.locator("input[data-automation-id='createAccountCheckbox']");
        if (agree.count() > 0 && !agree.first().isChecked()) agree.first().check(new Locator.CheckOptions().setForce(true));
        clickButton("createAccountSubmitButton");
        return afterAuthentication(url, since, account);
    }

    private Optional<SubmissionResult> afterAuthentication(String url, Instant since, SavedAccount created) {
        Instant deadline = Instant.now().plusSeconds(30);
        while (Instant.now().isBefore(deadline)) {
            page.waitForTimeout(1_000);
            if (!signInVisible() && !currentStep().isEmpty()) {
                rememberSignIn();
                return Optional.empty();
            }
            if (captchaVisible()) return waitForHumanSignIn("Please solve the captcha in the browser window.");
            List<String> errors = visibleErrors();
            if (!errors.isEmpty()) {
                String text = String.join("; ", errors);
                if (created != null && text.toLowerCase().contains("already")) {
                    ctx.accounts().delete(created.id);
                    return waitForHumanSignIn("A Workday account for your email already exists at " + app.job.company
                            + ". Sign in in the browser, or add the login on the Accounts page.");
                }
                return waitForHumanSignIn("Workday rejected the sign-in: " + text);
            }
            if (page.getByText(VERIFY_NOTICE).filter(visibleOnly()).count() > 0) {
                return verifyEmail(url, since);
            }
        }
        return waitForHumanSignIn("Workday didn't confirm the sign-in.");
    }

    private Optional<SubmissionResult> verifyEmail(String url, Instant since) {
        ctx.progress().accept("Waiting for Workday's verification email…");
        Optional<String> link = ctx.verificationCodes() == null
                ? Optional.empty()
                : ctx.verificationCodes().waitForLink(since, host, Duration.ofMinutes(3));
        if (link.isEmpty()) {
            return waitForHumanSignIn("Workday emailed you a verification link. Click it, then sign in in the browser"
                    + " (connect Gmail in Settings to do this automatically).");
        }
        page.navigate(link.get());
        page.waitForTimeout(4_000);
        return enterApplyFlow(url); // the walk loop signs in with the saved account next
    }

    /**
     * Lets the user sign in / create the account in the visible window, with a banner saying so.
     * The email and password they type are remembered and saved to Accounts once sign-in
     * succeeds, so the next application at this company signs in by itself.
     */
    private Optional<SubmissionResult> waitForHumanSignIn(String message) {
        if (!ctx.visible()) return Optional.of(SubmissionResult.needsHuman(message));
        ctx.progress().accept("In the browser window: " + message);
        String closed = "The browser window was closed before signing in to " + app.job.company + "'s Workday."
                + " Press Retry and sign in when the window opens, or turn on Settings → Workday accounts.";
        String[] typed = new String[2]; // email, password
        Instant deadline = Instant.now().plus(HUMAN_TIMEOUT);
        try {
            while (Instant.now().isBefore(deadline)) {
                if (page.isClosed()) return Optional.of(SubmissionResult.failed(closed));
                if (!signInVisible() && !currentStep().isEmpty()) {
                    PageBanner.clear(page);
                    saveTypedLogin(typed[0], typed[1]);
                    rememberSignIn();
                    return Optional.empty();
                }
                PageBanner.show(page, message);
                rememberTypedLogin(typed);
                page.waitForTimeout(1_000);
            }
        } catch (PlaywrightException e) {
            return Optional.of(SubmissionResult.failed(closed));
        }
        return Optional.of(SubmissionResult.failed("Timed out waiting for the Workday sign-in."));
    }

    private void rememberTypedLogin(String[] typed) {
        try {
            Locator email = page.locator("input[data-automation-id='email']").filter(visibleOnly());
            Locator password = page.locator("input[data-automation-id='password']").filter(visibleOnly());
            if (email.count() == 0 || password.count() == 0) return;
            String e = email.first().inputValue();
            String p = password.first().inputValue();
            if (!Text.isBlank(e) && !Text.isBlank(p)) {
                typed[0] = e.trim();
                typed[1] = p;
            }
        } catch (PlaywrightException ignored) {
            // the form changed under us; keep what we had
        }
    }

    private void saveTypedLogin(String email, String password) {
        if (Text.isBlank(email) || Text.isBlank(password) || savedAccount().isPresent()) return;
        SavedAccount account = new SavedAccount();
        account.company = app.job.company;
        account.siteUrl = "https://" + host;
        account.username = email;
        account.password = password;
        account.notes = "Saved when you signed in to Workday through TrueApply";
        ctx.accounts().insert(account);
        ctx.progress().accept("Saved your " + app.job.company + " Workday login to Accounts.");
    }

    /** Saves cookies right away, so the sign-in survives even if the window is closed mid-run. */
    private void rememberSignIn() {
        if (ctx.browser() != null) ctx.browser().rememberCookies(page.context());
    }

    private Optional<SavedAccount> savedAccount() {
        return ctx.accounts().findAll().stream()
                .filter(a -> !Text.isBlank(a.siteUrl))
                .filter(a -> host.equalsIgnoreCase(hostOf(a.siteUrl)))
                .findFirst();
    }

    private void fillCredentials(String email, String password, boolean verify) {
        page.locator("input[data-automation-id='email']").first().fill(email);
        page.locator("input[data-automation-id='password']").first().fill(password);
        if (verify) page.locator("input[data-automation-id='verifyPassword']").first().fill(password);
        // Never touch the hidden "beecatcher" input: it's a honeypot that flags bots.
    }

    // ---- reading and answering a page -------------------------------------------------------

    /** Adds Work Experience / Education entries so the profile's history has somewhere to go. */
    private void prepareExperiencePage() {
        uploadResumeIfAsked();
        // After "Autofill with Resume", Workday already created entries from the resume; adding
        // more would only leave blank ones behind.
        if (autofilled) return;
        addEntries("Work Experience", JOB_TITLE_LABEL, Math.min(profile.experience.size(), 3));
        addEntries("Education", SCHOOL_LABEL, Math.min(profile.education.size(), 2));
    }

    private static final String JOB_TITLE_LABEL = "^job title$";
    private static final String SCHOOL_LABEL = "^(school|school or university|university|institution)( name)?$";

    /** How many entries a section has, counted by one field every entry has (e.g. "Job Title"). */
    private int countEntries(String labelRegex) {
        Object n = page.evaluate("""
                (re) => [...document.querySelectorAll('[data-automation-id^="formField-"]')]
                  .filter(c => c.offsetParent !== null)
                  .filter(c => {
                    const l = c.querySelector('label, legend');
                    return l && new RegExp(re, 'i').test(l.innerText.split('*').join('').trim());
                  }).length""", labelRegex);
        return ((Number) n).intValue();
    }

    /**
     * Deletes experience/education entries that are still completely empty after filling.
     * Returns how many were removed.
     */
    private int removeBlankEntries() {
        Object n = page.evaluate("""
                () => {
                  let removed = 0;
                  const groups = [...document.querySelectorAll('[role="group"], [data-automation-id^="workExperience-"], [data-automation-id^="education-"]')]
                    .filter(g => g.offsetParent !== null && g.querySelectorAll('[data-automation-id^="formField-"]').length >= 2);
                  for (const g of groups) {
                    const inputs = [...g.querySelectorAll('input:not([type="hidden"]):not([type="checkbox"]):not([type="radio"]):not([type="file"]), textarea')];
                    const filled = inputs.some(i => (i.value || '').trim() !== '') || g.querySelector('[data-automation-id="selectedItem"]');
                    if (filled || inputs.length === 0) continue;
                    const del = [...g.querySelectorAll('button')].find(b =>
                      /^delete/i.test((b.getAttribute('aria-label') || b.innerText || '').trim()));
                    if (del) { del.click(); removed++; }
                  }
                  return removed;
                }""");
        int removed = ((Number) n).intValue();
        if (removed > 0) ctx.progress().accept("Removed " + removed + " empty entr" + (removed == 1 ? "y" : "ies") + " on Workday.");
        return removed;
    }

    /** Waits (up to 20 s) until the page's Continue/Submit button has rendered. */
    private void waitForStepReady() {
        Instant deadline = Instant.now().plusSeconds(20);
        while (Instant.now().isBefore(deadline)) {
            if (page.locator(NEXT_BUTTON_IDS).filter(visibleOnly()).count() > 0
                    || page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(NEXT_BUTTON_TEXT))
                    .filter(visibleOnly()).count() > 0) {
                return;
            }
            page.waitForTimeout(500);
        }
    }

    private void uploadResumeIfAsked() {
        if (Text.isBlank(profile.resumePath) || !Files.isRegularFile(Path.of(profile.resumePath))) return;
        Locator input = page.locator("input[type='file']");
        if (input.count() == 0) return;
        Locator uploaded = page.locator("[data-automation-id='file-upload-successful'], [data-automation-id='delete-file']");
        if (uploaded.count() > 0) return;
        input.first().setInputFiles(Path.of(profile.resumePath));
        // With "Autofill with Resume" Workday parses the file before continuing; give it time.
        if (!waitVisible(uploaded, 20_000)) page.waitForTimeout(3_000);
    }

    private void addEntries(String sectionName, String entryLabelRegex, int wanted) {
        Pattern addButton = Pattern.compile("^Add( Another)?( " + sectionName + ")?$", Pattern.CASE_INSENSITIVE);
        for (int guard = 0; guard < wanted; guard++) {
            if (countEntries(entryLabelRegex) >= wanted) return;
            Locator section = page.locator("[role='group']").filter(new Locator.FilterOptions().setHasText(sectionName));
            Locator button = section.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(addButton));
            if (button.count() == 0) {
                button = page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions()
                        .setName(Pattern.compile("Add( Another)? " + sectionName, Pattern.CASE_INSENSITIVE)));
            }
            if (button.count() == 0) return;
            button.first().click();
            page.waitForTimeout(1_000);
        }
    }

    /**
     * Turns the visible fields into FormFields, merging with what we already know about this
     * application (so answers survive a resume) and asking the AI about new ones.
     */
    @SuppressWarnings("unchecked")
    private List<FormField> readPage(String step) {
        List<Map<String, Object>> raw = (List<Map<String, Object>>) page.evaluate(EXTRACT_JS);
        Map<String, FormField> known = new LinkedHashMap<>();
        for (FormField f : app.fields) known.put(f.key, f);

        // Same field repeated (one per job/school) but no heading found: number the entries so the
        // AI can tell "Entry 2 › Company" from "Entry 1 › Company".
        Map<String, Long> repeats = new java.util.HashMap<>();
        for (Map<String, Object> r : raw) repeats.merge(String.valueOf(r.get("id")), 1L, Long::sum);
        java.util.Set<String> present = new java.util.HashSet<>();

        List<FormField> pageFields = new ArrayList<>();
        List<FormField> fresh = new ArrayList<>();
        for (Map<String, Object> original : raw) {
            Map<String, Object> r = new java.util.HashMap<>(original);
            if (Text.isBlank(String.valueOf(r.getOrDefault("section", ""))) && repeats.get(String.valueOf(r.get("id"))) > 1) {
                r.put("section", "Entry " + (((Number) r.get("index")).intValue() + 1));
            }
            String key = step + "|" + r.get("id") + "|" + r.get("index");
            present.add(key);
            if (Boolean.TRUE.equals(r.get("hasValue"))) prefilled.add(key);
            FormField field = known.get(key);
            if (field == null) {
                field = toField(key, step, r);
                if (field == null) continue;
                app.fields.add(field);
                fresh.add(field);
            } else if ("checkbox".equals(field.control) && PREFERRED_NAME.matcher(field.label).find()) {
                field.answer = Text.isBlank(profile.personal.preferredName) ? "No" : "Yes";
                field.category = FieldCategory.PROFILE;
                field.source = AnswerSource.PROFILE;
            } else if ("dropdown".equals(field.control)) {
                // Re-read the real choices; drop an earlier answer that isn't one of them.
                List<String> current = dropdownOptions(r);
                if (!current.isEmpty() && !current.equals(field.options)) {
                    field.options = new ArrayList<>(current);
                    if (field.hasAnswer() && OptionMatcher.bestMatch(current, field.answer) < 0) {
                        field.answer = null;
                        field.category = FieldCategory.FACTUAL;
                        field.source = AnswerSource.NONE;
                        fresh.add(field);
                    }
                }
            }
            pageFields.add(field);
        }
        // Entries removed since the last read (e.g. blank ones we deleted) shouldn't linger as questions.
        app.fields.removeIf(f -> step.equals(f.group) && !present.contains(f.key));
        if (!fresh.isEmpty()) {
            ctx.progress().accept("Reading " + fresh.size() + " questions on “" + step + "”…");
            ctx.answerer().accept(fresh);
            for (FormField f : fresh) {
                // Workday already filled it (resume autofill, default phone code...) and we have
                // nothing better: keep its value rather than asking the user.
                if (prefilled.contains(f.key) && f.category == FieldCategory.MISSING_INFO) {
                    f.category = FieldCategory.SKIPPED;
                    f.note = "Kept the value Workday pre-filled.";
                }
            }
        }
        return pageFields;
    }

    private FormField toField(String key, String step, Map<String, Object> r) {
        String control = String.valueOf(r.get("control"));
        String label = String.valueOf(r.get("label"));
        String section = String.valueOf(r.getOrDefault("section", ""));
        boolean required = Boolean.TRUE.equals(r.get("required"));
        FieldType type = switch (control) {
            case "textarea" -> FieldType.TEXTAREA;
            case "file" -> FieldType.FILE;
            case "dropdown", "prompt", "radio", "checkbox" -> FieldType.SINGLE_SELECT;
            case "checkboxes" -> FieldType.MULTI_SELECT;
            default -> FieldType.TEXT;
        };
        FormField field = new FormField(key, section.isBlank() ? label : section + " › " + label, type, required);
        field.group = step;
        field.control = control;
        if (r.get("options") instanceof List<?> opts) opts.forEach(o -> field.options.add(String.valueOf(o)));
        switch (control) {
            case "checkbox" -> {
                field.options = new ArrayList<>(List.of("Yes", "No"));
                if (PREFERRED_NAME.matcher(label).find()) { // straight from the profile, not the AI
                    field.answer = Text.isBlank(profile.personal.preferredName) ? "No" : "Yes";
                    field.category = FieldCategory.PROFILE;
                    field.source = AnswerSource.PROFILE;
                }
            }
            case "dropdown" -> field.options = dropdownOptions(r);
            case "date" -> field.description = "A date; answer as MM/YYYY (or just YYYY if only a year is asked).";
            case "prompt" -> field.description = "A searchable list; answer with the value to search for. Some lists are"
                    + " two-level (a category, then an item): answer as \"Category" + PATH_SEPARATOR + "Item\" when known.";
            default -> {
            }
        }
        if (type == FieldType.FILE) {
            if (RESUME.matcher(label).find() && !Text.isBlank(profile.resumePath)) {
                field.answer = profile.resumePath;
                field.category = FieldCategory.PROFILE;
                field.source = AnswerSource.PROFILE;
            } else {
                field.category = FieldCategory.MISSING_INFO;
                field.note = "This question needs a file upload.";
            }
        }
        return field;
    }

    private List<String> dropdownOptions(Map<String, Object> r) {
        Locator container = container(r.get("id").toString(), ((Number) r.get("index")).intValue());
        Locator button = container.locator("button[aria-haspopup='listbox']").first();
        try {
            button.click();
            waitForOptions(container, 3_000);
            List<String> texts = collectAllOptions(container).stream()
                    .filter(t -> !t.equalsIgnoreCase("select one")).toList();
            page.keyboard().press("Escape");
            return new ArrayList<>(texts);
        } catch (PlaywrightException e) {
            return new ArrayList<>();
        }
    }

    /**
     * Lists the options of the list that is open right now, for the field in {@code c}, and tags
     * them so {@link #clickOption} can click one. Skips hidden leftovers of lists opened earlier,
     * the "selected" pills of other pickers, and options belonging to other fields; those were
     * once read as this field's choices.
     */
    private static final String OPEN_OPTIONS_JS = """
            (field) => {
              document.querySelectorAll('[data-trueapply-opt]').forEach(e => e.removeAttribute('data-trueapply-opt'));
              const shown = e => !!(e.offsetWidth || e.offsetHeight || e.getClientRects().length)
                  && getComputedStyle(e).visibility !== 'hidden';
              const candidates = [...document.querySelectorAll('[data-automation-id="promptOption"], [role="option"]')]
                .filter(shown)
                .filter(e => !e.closest('[data-automation-id="selectedItem"]'))
                .filter(e => { const f = e.closest('[data-automation-id^="formField-"]'); return !f || f === field; });
              const outer = candidates.filter(e => !candidates.some(o => o !== e && o.contains(e)));
              outer.forEach((e, i) => e.setAttribute('data-trueapply-opt', String(i)));
              return outer.map(e => (e.innerText || '').trim());
            }""";

    @SuppressWarnings("unchecked")
    private List<String> openOptions(Locator field) {
        List<String> texts = (List<String>) field.evaluate(OPEN_OPTIONS_JS);
        return texts.stream().map(String::trim).toList();
    }

    private List<String> waitForOptions(Locator field, long timeoutMs) {
        Instant deadline = Instant.now().plusMillis(timeoutMs);
        while (true) {
            List<String> texts = openOptions(field);
            if (!texts.isEmpty() || Instant.now().isAfter(deadline)) return texts;
            page.waitForTimeout(250);
        }
    }

    /** Clicks option {@code index} from the last {@link #openOptions} call. */
    private void clickOption(int index) {
        page.locator("[data-trueapply-opt='" + index + "']").first().click(new Locator.ClickOptions().setForce(true));
    }

    /**
     * Scrolls the open list (Workday only renders the visible part of long lists). "top" jumps to
     * the start; "down" moves one screen. Returns whether it moved.
     */
    private boolean scrollOpenList(String direction) {
        Object moved = page.evaluate("""
                (dir) => {
                  const o = document.querySelector('[data-trueapply-opt]');
                  if (!o) return false;
                  let s = o.parentElement;
                  while (s && !(s.scrollHeight > s.clientHeight + 4 && /(auto|scroll)/.test(getComputedStyle(s).overflowY))) {
                    s = s.parentElement;
                  }
                  if (!s) return false;
                  const before = s.scrollTop;
                  s.scrollTop = dir === 'top' ? 0 : before + Math.max(40, s.clientHeight * 0.9);
                  return s.scrollTop !== before;
                }""", direction);
        page.waitForTimeout(120);
        return Boolean.TRUE.equals(moved);
    }

    /** Every option of the open list, scrolling through it if it's long. */
    private List<String> collectAllOptions(Locator c) {
        java.util.LinkedHashSet<String> all = new java.util.LinkedHashSet<>();
        openOptions(c);
        scrollOpenList("top");
        for (int i = 0; i < 60; i++) {
            openOptions(c).stream().filter(t -> !t.isBlank()).forEach(all::add);
            if (!scrollOpenList("down")) break;
        }
        openOptions(c);
        scrollOpenList("top");
        return new ArrayList<>(all);
    }

    /**
     * Picks {@code want} from the open list even when it's far down a long list: collects every
     * option, chooses the best match, then scrolls until that exact option is rendered and clicks it.
     */
    private boolean selectFromOpenList(Locator c, String want) {
        List<String> visible = openOptions(c);
        int quick = OptionMatcher.bestMatch(visible, want);
        if (quick >= 0 && Text.normalize(visible.get(quick)).equals(Text.normalize(want))) {
            return clickOptionText(c, visible.get(quick));
        }
        List<String> all = collectAllOptions(c);
        int index = OptionMatcher.bestMatch(all, want);
        if (index < 0) return false;
        String target = all.get(index);
        for (int i = 0; i < 60; i++) {
            if (openOptions(c).contains(target)) return clickOptionText(c, target);
            if (!scrollOpenList("down")) break;
        }
        return false;
    }

    /**
     * Clicks the rendered option with this exact text. Long lists re-render rows as they scroll,
     * so bring the row to the middle first, find it again, then click (direct DOM click as fallback).
     */
    private boolean clickOptionText(Locator c, String target) {
        for (int attempt = 0; attempt < 3; attempt++) {
            int at = openOptions(c).indexOf(target);
            if (at < 0) return false;
            page.locator("[data-trueapply-opt='" + at + "']").first().evaluate("e => e.scrollIntoView({block: 'center'})");
            page.waitForTimeout(250);
            at = openOptions(c).indexOf(target);
            if (at < 0) continue;
            Locator option = page.locator("[data-trueapply-opt='" + at + "']").first();
            try {
                option.click(new Locator.ClickOptions().setForce(true).setTimeout(3_000));
                return true;
            } catch (PlaywrightException e) {
                try {
                    option.evaluate("e => e.click()");
                    return true;
                } catch (PlaywrightException ignored) {
                    // re-rendered under us; try again
                }
            }
        }
        return false;
    }

    // ---- filling ------------------------------------------------------------------------------

    /** The wizard moved to another page while we were filling this one. */
    static final class PageChanged extends RuntimeException {
        PageChanged() {
            super("the page changed");
        }
    }

    /** Keys filled successfully during the current visit to a page (so a second pass skips them). */
    private final java.util.Set<String> filledThisVisit = new java.util.HashSet<>();

    private List<String> fillPage(List<FormField> fields) {
        List<String> problems = new ArrayList<>();
        for (FormField field : fields) {
            boolean hadValue = prefilled.contains(field.key);
            if (field.category == FieldCategory.SKIPPED || !field.hasAnswer()) {
                // Questions for the user are handled by the caller (NEEDS_INPUT), not as fill problems.
                if (field.required && !field.needsHuman() && field.category != FieldCategory.SKIPPED && !hadValue) {
                    problems.add(field.label + ": no answer");
                }
                continue;
            }
            if (filledThisVisit.contains(field.key)) continue;
            // Stop at once if the wizard moved on (e.g. a stray key press submitted the page),
            // instead of timing out on every field that's no longer there.
            if (!field.group.equals(currentStep())) throw new PageChanged();
            String[] parts = field.key.split("\\|");
            Locator container = container(parts[1], Integer.parseInt(parts[2]));
            if (container.count() == 0) {
                if (field.required && !hadValue) problems.add(field.label + " (no longer on the page)");
                continue;
            }
            PageBanner.working(page, "filling “" + Text.truncate(field.label, 70) + "”");
            try {
                fill(container, field);
                filledThisVisit.add(field.key);
            } catch (ChoiceNeeded e) {
                // The value may have landed anyway (Enter auto-picks, or a slow re-render): check before asking.
                if (promptHas(container, field.answer)) {
                    closeList();
                    continue;
                }
                // Turn it into an Inbox question with the picker's real options.
                String tried = field.answer;
                WorkdayDebugLog.record(app, field, container, "asked the user (" + e.choices.size() + " choices)");
                field.options = new ArrayList<>(e.choices);
                field.answer = null;
                field.category = FieldCategory.MISSING_INFO;
                field.source = AnswerSource.NONE;
                field.note = e.choices.isEmpty()
                        ? "Workday's search found nothing for “" + tried + "”. Type the name the way Workday would list it"
                          + " (TrueApply searches for what you type). Tick Remember to reuse it."
                        : "Workday needs a more specific answer than “" + tried + "”. Pick one, or type a value"
                          + " (e.g. LinkedIn) and TrueApply will search for it. Tick Remember to reuse it at other companies.";
            } catch (PlaywrightException | IllegalStateException e) {
                if ("prompt".equals(field.control) && promptHas(container, field.answer)) {
                    closeList(); // it took after all
                    continue;
                }
                String reason = readableError(e);
                WorkdayDebugLog.record(app, field, container, reason);
                // A value Workday filled in itself is good enough when ours won't take.
                if (field.required && !hadValue) problems.add(field.label + " (" + reason + ")");
            }
        }
        return problems;
    }

    private void fill(Locator c, FormField field) {
        switch (Text.orEmpty(field.control)) {
            case "date" -> fillDate(c, field.answer);
            case "file" -> c.locator("input[type='file']").first().setInputFiles(Path.of(field.answer));
            case "dropdown" -> chooseFromListbox(c, field.answer);
            case "prompt" -> choosePrompt(c, field.answer);
            case "radio" -> chooseRadio(c, field);
            case "checkbox" -> setChecked(c, c.locator("input[type='checkbox']").first(), "Yes".equalsIgnoreCase(field.answer));
            case "checkboxes" -> {
                for (String option : field.answers) {
                    Locator box = c.getByLabel(option, new Locator.GetByLabelOptions().setExact(true)).first();
                    setChecked(c, box, true);
                }
            }
            default -> c.locator("textarea, input:not([type='hidden']):not([type='password']):not([data-automation-id='beecatcher'])")
                    .first().fill(field.answer);
        }
    }

    private void fillDate(Locator c, String answer) {
        DateParts parts = DateParts.parse(answer);
        if (parts.year() == null) throw new IllegalStateException("no year in “" + answer + "”");
        Locator month = c.locator("input[data-automation-id='dateSectionMonth-input']");
        Locator year = c.locator("input[data-automation-id='dateSectionYear-input']");
        if (parts.month() != null && month.count() > 0) {
            int m = Month.valueOf(parts.month().toUpperCase()).getValue();
            setSpinButton(c, month.first(), "dateSectionMonth-display", String.format("%02d", m), String.valueOf(m));
        }
        if (year.count() > 0) {
            setSpinButton(c, year.first(), "dateSectionYear-display", parts.year(), parts.year());
        }
    }

    /**
     * Workday date parts are spin-button inputs covered by an aria-hidden "display" div, so a
     * normal click never reaches the input. Focus it directly and type; verify the value took.
     */
    private void setSpinButton(Locator c, Locator input, String displayId, String typed, String alsoAccepted) {
        if (hasValue(input, typed, alsoAccepted)) return;
        for (int attempt = 0; attempt < 2; attempt++) {
            input.focus();
            if (!isFocused(input)) {
                c.locator("[data-automation-id='" + displayId + "']").first().click(new Locator.ClickOptions().setForce(true));
            }
            // Never type blind: digits typed elsewhere could land in another field or press a button.
            if (!isFocused(input)) throw new IllegalStateException("couldn't focus the date field");
            if (!input.inputValue().isEmpty()) { // replace, don't prepend to, an existing value
                if (attempt == 0) {
                    page.keyboard().press("Control+A");
                    page.keyboard().press("Backspace");
                } else {
                    page.keyboard().press("End");
                    for (int i = 0; i < 6; i++) page.keyboard().press("Backspace");
                }
            }
            page.keyboard().type(typed, new com.microsoft.playwright.Keyboard.TypeOptions().setDelay(60));
            page.waitForTimeout(300);
            if (hasValue(input, typed, alsoAccepted)) return;
        }
        throw new IllegalStateException("the date didn't take “" + typed + "” (shows “" + input.inputValue() + "”)");
    }

    private static boolean hasValue(Locator input, String... accepted) {
        String value = input.inputValue().trim();
        for (String a : accepted) {
            if (value.equals(a)) return true;
        }
        return false;
    }

    /**
     * Yes/No style radio groups: real {@code input type=radio} elements under Workday's custom
     * styling. Find the input whose label matches (or whose value is "true"/"false" for Yes/No)
     * and set it with {@link #setChecked}.
     */
    private void chooseRadio(Locator c, FormField field) {
        String answer = field.answer;
        Locator radios = c.locator("input[type='radio']");
        @SuppressWarnings("unchecked")
        List<String> labels = (List<String>) c.evaluate("""
                c => [...c.querySelectorAll('input[type="radio"]')].map(r => {
                  const l = r.id ? c.querySelector('label[for="' + r.id + '"]') : null;
                  return ((l && l.innerText) || r.getAttribute('aria-label') || r.value || '').trim();
                })""");
        int index = OptionMatcher.bestMatch(labels, answer);
        if (index < 0) {
            String value = answer.equalsIgnoreCase("yes") ? "true" : answer.equalsIgnoreCase("no") ? "false" : null;
            List<String> values = new ArrayList<>();
            for (int i = 0; i < radios.count(); i++) values.add(Text.orEmpty(radios.nth(i).getAttribute("value")));
            if (value != null) index = values.indexOf(value);
        }
        if (index < 0) throw new IllegalStateException("no choice matching “" + answer + "” among " + labels);
        setChecked(c, radios.nth(index), true);
    }

    /**
     * Sets a radio/checkbox under Workday's custom styling, where a simulated mouse click hits the
     * decoration instead of the input. Tries a direct DOM click, then its label, then Playwright's
     * forced check, and verifies the state each time.
     */
    private void setChecked(Locator c, Locator input, boolean wanted) {
        if (isOn(input) == wanted) return;
        List<Runnable> attempts = new ArrayList<>();
        attempts.add(() -> input.evaluate("e => e.click()"));                     // DOM click on the input
        attempts.add(() -> {                                                     // keyboard, like a person tabbing in
            input.focus();
            // Only if the checkbox really has focus: a stray Space would press whatever button
            // does (e.g. "Save and Continue").
            if (isFocused(input)) page.keyboard().press("Space");
        });
        attempts.add(() -> input.locator("xpath=..").click(new Locator.ClickOptions().setForce(true))); // the drawn box
        String id = input.getAttribute("id");
        if (id != null) {
            attempts.add(() -> c.locator("label[for='" + id + "']").first().click(new Locator.ClickOptions().setForce(true)));
        }
        for (Runnable attempt : attempts) {
            try {
                attempt.run();
            } catch (PlaywrightException ignored) {
                // try the next way
            }
            page.waitForTimeout(300);
            if (isOn(input) == wanted) return;
        }
        throw new IllegalStateException("the " + (input.getAttribute("type")) + " wouldn't change");
    }

    private static boolean isFocused(Locator input) {
        try {
            return Boolean.TRUE.equals(input.evaluate("e => document.activeElement === e"));
        } catch (PlaywrightException e) {
            return false;
        }
    }

    /** Checked state, trusting aria-checked too (Workday's components keep it in sync). */
    private static boolean isOn(Locator input) {
        return input.isChecked() || "true".equals(input.getAttribute("aria-checked"));
    }

    private void chooseFromListbox(Locator c, String value) {
        Locator button = c.locator("button[aria-haspopup='listbox']").first();
        // Already showing the right value (e.g. Workday's defaults)? Leave it.
        if (OptionMatcher.bestMatch(List.of(button.innerText().trim()), value) == 0) return;
        button.click();
        List<String> seen = waitForOptions(c, 3_000);
        if (!selectFromOpenList(c, value)) {
            page.keyboard().press("Escape");
            throw new IllegalStateException("no option matching “" + value + "” (saw "
                    + seen.stream().limit(8).toList() + (seen.size() > 8 ? "…" : "") + ")");
        }
    }

    /** Separator for answers to two-level pickers: "Job Board › LinkedIn". */
    static final String PATH_SEPARATOR = " › ";

    /** The picker needs a sub-option the answer doesn't name; carries the choices for the user. */
    static final class ChoiceNeeded extends RuntimeException {
        final List<String> choices;

        ChoiceNeeded(List<String> choices) {
            super("needs a more specific choice");
            this.choices = choices;
        }
    }

    /**
     * Search-or-browse pickers ("How did you hear about us?", school, field of study). Some are
     * two-level: categories ("Job Board") that open a sub-list ("LinkedIn", "Indeed"). The search
     * box only matches sub-items, so: search for the most specific part of the answer, and if that
     * fails, browse category → sub-option. Never guesses: if the sub-option isn't known, throws
     * {@link ChoiceNeeded} with the real choices so the user can pick one.
     */
    private void choosePrompt(Locator c, String value) {
        List<String> path = splitPath(value);
        String leaf = path.getLast();
        // Already set to something matching (Workday pre-fills e.g. the phone country code, and
        // restores saved draft answers a moment after the page loads)? Keep it.
        Instant settle = Instant.now().plusMillis(2_000);
        while (true) {
            if (promptHas(c, leaf)) return;
            if (Instant.now().isAfter(settle)) break;
            page.waitForTimeout(250);
        }

        Locator input = c.locator("input[data-automation-id='searchBox']");
        if (input.count() == 0) input = c.locator("input:not([type='hidden'])");
        input = input.first();

        // 1. Browse the clean, unfiltered list. Typing first leaves a filter behind (or opens a
        //    category), and the list we'd read afterwards is no longer the real top level.
        List<String> top = openFreshList(c, input);
        String first = exactOrBest(realOptions(top), path.getFirst());
        if (first != null) {
            List<String> chosen = new ArrayList<>(List.of(first));
            selectFromOpenList(c, first);
            for (int level = 1; level < 3; level++) {
                page.waitForTimeout(800);
                // Picked: the value shows as selected (multi-select lists stay open), or the list closed.
                if (promptHas(c, chosen.getLast())) {
                    closeList();
                    return;
                }
                if (waitForOptions(c, 1_500).isEmpty()) return;
                // It opened a category: pick the named item, or hand the full sub-list to the user.
                List<String> sub = collectAllOptions(c);
                String want = level < path.size() ? path.get(level) : null;
                String next = want == null ? null : exactOrBest(sub, want);
                if (next == null && level >= path.size() - 1 && path.size() > 1) next = exactOrBest(sub, leaf);
                if (next == null) {
                    page.keyboard().press("Escape");
                    String prefix = String.join(PATH_SEPARATOR, chosen) + PATH_SEPARATOR;
                    throw new ChoiceNeeded(sub.stream().map(t -> prefix + t).toList());
                }
                chosen.add(next);
                selectFromOpenList(c, next);
            }
            page.keyboard().press("Escape");
            throw new IllegalStateException("couldn't pick “" + value + "”");
        }

        // 2. Not a top-level entry (e.g. "LinkedIn" inside "Job Board", or a search-only picker like
        //    School / Field of Study whose list is empty until you type): use the search box.
        page.keyboard().press("Escape");
        input.click(new Locator.ClickOptions().setForce(true));
        input.fill(leaf);
        input.press("Enter");
        waitForOptions(c, 4_000);
        List<String> results = realOptions(collectAllOptions(c));
        SearchPick pick = pickSearchResult(results, leaf);
        if (pick.choice() != null && clickOptionText(c, pick.choice())) {
            page.waitForTimeout(800);
            if (promptHas(c, pick.choice())) {
                closeList();
                return;
            }
        }

        // 3. Couldn't place it on our own: ask, offering the most useful real choices.
        input.fill("");
        page.keyboard().press("Escape");
        if (!pick.ambiguous().isEmpty()) throw new ChoiceNeeded(pick.ambiguous());
        throw new ChoiceNeeded(!results.isEmpty() ? results : realOptions(top));
    }

    /** Either one result to click, or several equally plausible ones to ask about. */
    record SearchPick(String choice, List<String> ambiguous) {
    }

    /**
     * Chooses among search results without guessing: an exact match, or the only result that
     * starts with / contains the answer. Several such results ("University of Michigan" → Ann
     * Arbor, Dearborn, Flint) come back as ambiguous so the user decides.
     */
    static SearchPick pickSearchResult(List<String> results, String want) {
        String w = Text.normalize(want).replaceAll("[^a-z0-9+#]+", " ").trim();
        if (w.isEmpty()) return new SearchPick(null, List.of());
        List<String> keys = results.stream().map(r -> Text.normalize(r).replaceAll("[^a-z0-9+#]+", " ").trim()).toList();
        for (int i = 0; i < keys.size(); i++) if (keys.get(i).equals(w)) return new SearchPick(results.get(i), List.of());
        List<String> starts = new ArrayList<>();
        List<String> contains = new ArrayList<>();
        for (int i = 0; i < keys.size(); i++) {
            if (keys.get(i).startsWith(w)) starts.add(results.get(i));
            else if (keys.get(i).contains(w)) contains.add(results.get(i));
        }
        List<String> candidates = !starts.isEmpty() ? starts : contains;
        if (candidates.size() == 1) return new SearchPick(candidates.getFirst(), List.of());
        if (candidates.size() > 1) return new SearchPick(null, candidates);
        int fuzzy = OptionMatcher.bestMatch(results, want); // e.g. "Computer Science and Engineering" → "Computer Science"
        return fuzzy >= 0 ? new SearchPick(results.get(fuzzy), List.of()) : new SearchPick(null, List.of());
    }

    /** Drops Workday's placeholder rows ("No Items.", "Partial List (First 500 Entries)", "All"). */
    static List<String> realOptions(List<String> options) {
        return options.stream()
                .filter(o -> !o.isBlank())
                .filter(o -> !o.matches("(?i)no items\\.?|all|partial list.*|search results.*"))
                .distinct()
                .toList();
    }

    /**
     * What a picker shows as chosen. Workday versions mark it differently: a "selectedItem" pill,
     * a "selectedItemList", a "promptSelectionLabel", or a pill with a "Delete …" (×) button.
     */
    private static final String SELECTION_JS = """
            c => {
              const out = [];
              const marks = c.querySelectorAll('[data-automation-id="selectedItem"], [data-automation-id="selectedItemList"] li,'
                  + ' [data-automation-id="promptSelectionLabel"], [data-automation-id="DELETE_charm"], [aria-label^="Delete "],'
                  + ' [data-automation-id="selectedItemList"] [data-automation-id="menuItem"]');
              for (const e of marks) {
                const text = ((e.innerText || '').trim() || (e.getAttribute('aria-label') || '').replace(/^Delete\\s+/i, '')).trim();
                if (text) out.push(text);
              }
              return out;
            }""";

    /** True if the picker shows a selection matching {@code want} (any selection when want is null). */
    @SuppressWarnings("unchecked")
    private boolean promptHas(Locator c, String want) {
        List<String> chosen;
        try {
            chosen = (List<String>) c.evaluate(SELECTION_JS);
        } catch (PlaywrightException e) {
            return false;
        }
        if (chosen.isEmpty()) return false;
        return want == null || OptionMatcher.bestMatch(chosen, want) >= 0
                || chosen.stream().anyMatch(t -> Text.normalize(t).contains(Text.normalize(want)));
    }

    private void closeList() {
        page.keyboard().press("Escape");
        page.waitForTimeout(200);
    }

    /** Clears any search text, opens the picker at its top level, and returns all its entries. */
    private List<String> openFreshList(Locator c, Locator input) {
        page.keyboard().press("Escape");
        input.click(new Locator.ClickOptions().setForce(true));
        input.fill("");
        if (waitForOptions(c, 2_000).isEmpty()) {
            Locator listButton = c.locator("[data-automation-id='promptSearchButton']");
            if (listButton.count() > 0) listButton.first().click(new Locator.ClickOptions().setForce(true));
        }
        if (waitForOptions(c, 4_000).isEmpty()) {
            page.keyboard().press("Escape");
            throw new IllegalStateException("the list didn't open");
        }
        return collectAllOptions(c);
    }

    /** An option equal to {@code want} (ignoring case/punctuation), else the best fuzzy match, else null. */
    private static String exactOrBest(List<String> options, String want) {
        for (String o : options) {
            if (Text.normalize(o).equals(Text.normalize(want))) return o;
        }
        int index = OptionMatcher.bestMatch(options, want);
        return index < 0 ? null : options.get(index);
    }

    static List<String> splitPath(String value) {
        List<String> parts = java.util.Arrays.stream(value.split("\\s*(›|>|»)\\s*"))
                .map(String::trim).filter(s -> !s.isEmpty()).toList();
        return parts.isEmpty() ? List.of(value.trim()) : parts;
    }

    /** "Save and Continue" (or "Submit" on Review); the id differs between Workday versions. */
    private static final String NEXT_BUTTON_IDS =
            "[data-automation-id='pageFooterNextButton'], [data-automation-id='bottom-navigation-next-button']";
    private static final Pattern NEXT_BUTTON_TEXT =
            Pattern.compile("^\\s*(save and continue|continue|next|submit)\\s*$", Pattern.CASE_INSENSITIVE);

    private Locator nextButton() {
        Locator byId = page.locator(NEXT_BUTTON_IDS).filter(visibleOnly());
        if (byId.count() > 0) return byId.first();
        Locator byText = page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(NEXT_BUTTON_TEXT))
                .filter(visibleOnly());
        if (byText.count() > 0) return byText.last();
        throw new IllegalStateException("couldn't find Workday's Save and Continue button");
    }

    private void clickNext() {
        clickWithOverlayFallback(nextButton());
        page.waitForTimeout(2_500);
    }

    // ---- finishing ----------------------------------------------------------------------------

    private SubmissionResult review() {
        if (ctx.dryRun()) {
            if (ctx.visible()) {
                String note = "dry run is on, so nothing was submitted. Every page is filled and saved as a draft"
                        + " in your Workday account; review it, then close this window.";
                ctx.progress().accept(note);
                waitForClose(note);
            }
            return SubmissionResult.dryRun("Filled every Workday page and stopped at Review because dry run is on."
                    + " The draft is saved in your " + app.job.company + " Workday account.");
        }
        ctx.progress().accept("Submitting on Workday…");
        clickWithOverlayFallback(nextButton());
        Instant deadline = Instant.now().plusSeconds(30);
        while (Instant.now().isBefore(deadline)) {
            page.waitForTimeout(1_000);
            if (isConfirmation()) return SubmissionResult.submitted("Application submitted on Workday.");
        }
        return handOffOrFail("Workday didn't confirm the submission: " + String.join("; ", visibleErrors()));
    }

    private SubmissionResult handOffOrFail(String reason) {
        if (!ctx.visible()) return SubmissionResult.needsHuman(reason);
        String banner = reason + " Please finish the application in this window and press Submit;"
                + " TrueApply records it when Workday confirms.";
        ctx.progress().accept(banner);
        Instant deadline = Instant.now().plus(HUMAN_TIMEOUT);
        try {
            while (Instant.now().isBefore(deadline)) {
                if (page.isClosed()) return SubmissionResult.failed(reason);
                if (isConfirmation()) return SubmissionResult.submitted("Application submitted (finished in the browser).");
                PageBanner.show(page, banner);
                page.waitForTimeout(1_000);
            }
        } catch (PlaywrightException e) {
            return SubmissionResult.failed(reason);
        }
        return SubmissionResult.failed(reason);
    }

    private boolean isConfirmation() {
        return page.getByText(CONFIRMATION).filter(visibleOnly()).count() > 0;
    }

    // ---- helpers -------------------------------------------------------------------------------

    /** Name of the active wizard step ("My Information"), or "" outside the wizard. */
    private String currentStep() {
        Locator active = page.locator("[data-automation-id='progressBarActiveStep']");
        if (active.count() == 0) return "";
        String[] lines = active.first().innerText().split("\\n");
        return lines[lines.length - 1].trim();
    }

    private Locator container(String automationId, int index) {
        return page.locator("[data-automation-id='" + automationId + "']").nth(index);
    }

    private void clickButton(String automationId) {
        clickWithOverlayFallback(page.locator("[data-automation-id='" + automationId + "']").first());
    }

    /** Workday lays a "click_filter" overlay over its buttons; fall back to clicking through it. */
    private static void clickWithOverlayFallback(Locator button) {
        try {
            button.click(new Locator.ClickOptions().setTimeout(4_000));
        } catch (PlaywrightException e) {
            button.click(new Locator.ClickOptions().setForce(true));
        }
    }

    private String safeCurrentStep() {
        try {
            return currentStep();
        } catch (PlaywrightException e) {
            return "";
        }
    }

    private List<String> visibleErrors() {
        return page.locator("[data-automation-id='errorMessage'], [data-automation-id^='errorBanner'], [role='alert']")
                .filter(visibleOnly()).allInnerTexts().stream()
                .map(String::trim).filter(s -> !s.isEmpty()).distinct().limit(5).toList();
    }

    private boolean captchaVisible() {
        return page.locator("iframe[src*='hcaptcha'], iframe[src*='recaptcha'], iframe[title*='challenge']")
                .filter(visibleOnly()).count() > 0;
    }

    private boolean isVisible(String selector) {
        return page.locator(selector).filter(visibleOnly()).count() > 0;
    }

    private void waitForClose(String banner) {
        Instant deadline = Instant.now().plus(HUMAN_TIMEOUT);
        try {
            while (!page.isClosed() && Instant.now().isBefore(deadline)) {
                PageBanner.show(page, banner);
                page.waitForTimeout(500);
            }
        } catch (PlaywrightException ignored) {
            // closed
        }
    }

    private static Locator.FilterOptions visibleOnly() {
        return new Locator.FilterOptions().setVisible(true);
    }

    private static boolean waitVisible(Locator locator, double timeoutMs) {
        try {
            locator.first().waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(timeoutMs));
            return true;
        } catch (TimeoutError e) {
            return false;
        }
    }

    private static String hostOf(String url) {
        try {
            return URI.create(url.trim()).getHost();
        } catch (IllegalArgumentException e) {
            return "";
        }
    }

    /**
     * Playwright errors start with "Error {" and bury the useful part in a "message='…'" line;
     * pull that out so the user sees e.g. "Timeout 10000ms exceeded" instead of "Error {".
     */
    static String readableError(Throwable e) {
        String s = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        int at = s.indexOf("message='");
        if (at >= 0) {
            String rest = s.substring(at + "message='".length());
            int end = rest.indexOf('\n');
            s = (end < 0 ? rest : rest.substring(0, end)).replaceAll("'\\s*$", "").trim();
        } else {
            int nl = s.indexOf('\n');
            if (nl >= 0) s = s.substring(0, nl);
        }
        if (s.startsWith("Timeout")) return "the field didn't respond (" + s + ")";
        return s;
    }
}
