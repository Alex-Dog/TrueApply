package com.trueapply.ats.greenhouse;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.SelectOption;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.trueapply.ats.SubmissionContext;
import com.trueapply.ats.SubmissionResult;
import com.trueapply.model.FieldCategory;
import com.trueapply.model.FormField;
import com.trueapply.model.JobApplication;
import com.trueapply.util.AppPaths;
import com.trueapply.util.Text;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Drives the hosted Greenhouse application form (job-boards.greenhouse.io). Inputs carry the
 * API field name as their DOM id; dropdowns are react-select comboboxes.
 */
final class GreenhouseFormFiller {
    private static final Pattern CONFIRMATION = Pattern.compile(
            "thank you for applying|thanks for applying|application (has been )?(received|submitted)"
                    + "|we('ve| have) received your application",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SUBMIT = Pattern.compile("submit", Pattern.CASE_INSENSITIVE);
    private static final Duration HUMAN_TIMEOUT = Duration.ofMinutes(10);
    private static final Duration CONFIRM_TIMEOUT = Duration.ofSeconds(45);

    private final Page page;
    private final JobApplication app;
    private final SubmissionContext ctx;
    private final List<String> problems = new ArrayList<>();

    GreenhouseFormFiller(Page page, JobApplication app, SubmissionContext ctx) {
        this.page = page;
        this.app = app;
        this.ctx = ctx;
    }

    SubmissionResult run(String formUrl) {
        page.navigate(formUrl, new Page.NavigateOptions().setTimeout(60_000));
        if (!waitVisible(byId("first_name"), 20_000)) {
            return SubmissionResult.failed("Couldn't find the application form — the posting may be closed.");
        }

        int filled = 0;
        for (FormField field : app.fields) {
            if (field.category == FieldCategory.SKIPPED || !field.hasAnswer()) {
                if (field.required && field.category != FieldCategory.SKIPPED) {
                    problems.add(field.label + ": no answer");
                }
                continue;
            }
            ctx.progress().accept("Filling “" + Text.truncate(field.label, 60) + "”");
            try {
                fill(field);
                filled++;
            } catch (FillException | PlaywrightException e) {
                if (field.required) problems.add(field.label + ": " + firstLine(e.getMessage()));
            }
        }

        if (ctx.dryRun()) {
            String screenshot = System.getProperty("trueapply.debugScreenshot");
            if (screenshot != null) {
                page.screenshot(new Page.ScreenshotOptions().setPath(Path.of(screenshot)).setFullPage(true));
            }
            String message = "Filled " + filled + " fields; not submitted because dry run is on."
                    + (problems.isEmpty() ? "" : " Couldn't fill: " + String.join("; ", problems));
            if (ctx.visible()) {
                ctx.progress().accept("Dry run — review the form, then close the browser window.");
                waitForClose(HUMAN_TIMEOUT);
            }
            return SubmissionResult.dryRun(message);
        }

        if (!problems.isEmpty()) {
            String summary = String.join("; ", problems);
            if (!ctx.visible()) return SubmissionResult.needsHuman("Couldn't fill: " + summary);
            ctx.progress().accept("Please finish these in the browser and press Submit: " + summary);
            return waitForConfirmation(HUMAN_TIMEOUT);
        }
        return submitAndConfirm();
    }

    // ---- filling -------------------------------------------------------------------------

    private void fill(FormField field) throws FillException {
        switch (field.key) {
            case "resume" -> setFile("resume", Path.of(field.answer));
            case "cover_letter_text" -> fillCoverLetter(field.answer);
            case "country" -> chooseOption("country", field.answer);
            case "location" -> fillLocation("candidate-location", field.answer);
            default -> {
                switch (field.type) {
                    case TEXT, TEXTAREA -> fillText(field.key, field.answer);
                    case LOCATION -> fillLocation(field.key, field.answer);
                    case SINGLE_SELECT -> selectOptions(field, List.of(field.answer));
                    case MULTI_SELECT -> selectOptions(field, field.answers);
                    case FILE -> setFile(field.key, Path.of(field.answer));
                }
            }
        }
    }

    private void fillText(String id, String value) throws FillException {
        Locator input = require(byId(id));
        input.fill(value);
    }

    private void setFile(String id, Path file) throws FillException {
        if (!Files.isRegularFile(file)) throw new FillException("file not found: " + file);
        Locator input = require(page.locator("input[type='file'][id='" + id + "']").first());
        input.setInputFiles(file);
    }

    private void fillCoverLetter(String text) throws FillException {
        Locator enterManually = page.locator("[data-testid='cover_letter-text']");
        if (enterManually.count() > 0) {
            enterManually.first().click();
            Locator area = page.locator("textarea[id='cover_letter_text']").first();
            if (waitVisible(area, 3_000)) {
                area.fill(text);
                return;
            }
        }
        // Fallback: upload the user's text as a .txt cover letter.
        try {
            Path file = AppPaths.uploads().resolve("cover-letter-" + app.id + ".txt");
            Files.writeString(file, text);
            setFile("cover_letter", file);
        } catch (IOException e) {
            throw new FillException("couldn't write cover letter file: " + e.getMessage());
        }
    }

    private void selectOptions(FormField field, List<String> values) throws FillException {
        Locator el = byId(field.key);
        waitAttached(el, 3_000);
        if (el.count() == 0 && field.key.equals("race")) {
            return; // Greenhouse only shows race after "Hispanic/Latino: No".
        }
        if (el.count() > 0) {
            String tag = String.valueOf(el.evaluate("e => e.tagName.toLowerCase()"));
            if (tag.equals("select")) {
                el.selectOption(values.stream().map(v -> new SelectOption().setLabel(v)).toArray(SelectOption[]::new));
                return;
            }
            if ("combobox".equals(el.getAttribute("role"))) {
                for (String v : values) chooseOption(field.key, v);
                return;
            }
        }
        // Checkbox / radio groups: find the question's fieldset by its label text.
        Locator group = page.locator("fieldset").filter(new Locator.FilterOptions().setHasText(field.label)).first();
        Locator scope = group.count() > 0 ? group : page.locator("body");
        for (String v : values) {
            Locator box = scope.getByLabel(v, new Locator.GetByLabelOptions().setExact(true)).first();
            require(box).check();
        }
    }

    /** Picks an option from a react-select combobox. */
    private void chooseOption(String id, String value) throws FillException {
        Locator input = require(byId(id));
        input.scrollIntoViewIfNeeded();
        input.click();
        Locator options = optionsFor(id);
        waitVisible(options.first(), 3_000);
        int index = bestMatch(options.allInnerTexts(), value);
        if (index < 0) {
            input.fill(value);
            page.waitForTimeout(800);
            index = bestMatch(options.allInnerTexts(), value);
        }
        if (index < 0) {
            page.keyboard().press("Escape");
            throw new FillException("no option matching “" + value + "”");
        }
        options.nth(index).click();
    }

    /** Location fields search asynchronously as you type; take the first suggestion. */
    private void fillLocation(String id, String value) throws FillException {
        Locator input = require(byId(id));
        input.scrollIntoViewIfNeeded();
        input.click();
        input.pressSequentially(value, new Locator.PressSequentiallyOptions().setDelay(40));
        Locator options = optionsFor(id);
        if (!waitVisible(options.first(), 8_000)) {
            throw new FillException("no location suggestions for “" + value + "”");
        }
        options.first().click();
    }

    private Locator optionsFor(String id) {
        return page.locator("[id^='react-select-" + id + "-option'], .select__menu [role='option']");
    }

    static int bestMatch(List<String> optionTexts, String value) {
        String wanted = Text.normalize(value);
        if (wanted.isEmpty()) return -1;
        List<String> normalized = optionTexts.stream().map(Text::normalize).toList();
        for (int i = 0; i < normalized.size(); i++) if (normalized.get(i).equals(wanted)) return i;
        // Shortest wins, so "United States" picks "United States +1" over "United States Minor Outlying Islands".
        int prefix = shortest(normalized, o -> o.startsWith(wanted));
        return prefix >= 0 ? prefix : shortest(normalized, o -> o.contains(wanted));
    }

    private static int shortest(List<String> options, java.util.function.Predicate<String> test) {
        int best = -1;
        for (int i = 0; i < options.size(); i++) {
            if (test.test(options.get(i)) && (best < 0 || options.get(i).length() < options.get(best).length())) best = i;
        }
        return best;
    }

    // ---- submitting ----------------------------------------------------------------------

    private SubmissionResult submitAndConfirm() {
        Instant submittedAt = Instant.now();
        ctx.progress().accept("Submitting…");
        clickSubmit();
        Instant deadline = Instant.now().plus(CONFIRM_TIMEOUT);
        boolean codeEntered = false;
        while (Instant.now().isBefore(deadline)) {
            if (isConfirmed()) return SubmissionResult.submitted("Application submitted.");
            if (!codeEntered && securityCodeRequested()) {
                Optional<SubmissionResult> terminal = handleSecurityCode(submittedAt);
                if (terminal.isPresent()) return terminal.get();
                codeEntered = true;
                deadline = Instant.now().plus(CONFIRM_TIMEOUT);
            }
            if (captchaVisible()) {
                if (!ctx.visible()) return SubmissionResult.needsHuman("The site showed a captcha.");
                ctx.progress().accept("Please solve the captcha in the browser window.");
                return waitForConfirmation(HUMAN_TIMEOUT);
            }
            page.waitForTimeout(1_000);
        }
        String errors = String.join("; ", visibleErrors());
        String reason = errors.isEmpty() ? "The form didn't confirm the submission." : "The form reported: " + errors;
        if (!ctx.visible()) return SubmissionResult.needsHuman(reason);
        ctx.progress().accept(reason + " Fix it in the browser and press Submit.");
        return waitForConfirmation(HUMAN_TIMEOUT);
    }

    private Optional<SubmissionResult> handleSecurityCode(Instant since) {
        ctx.progress().accept("Waiting for the emailed security code…");
        Optional<String> code = ctx.verificationCodes() == null
                ? Optional.empty()
                : ctx.verificationCodes().waitForCode(since, Duration.ofMinutes(3));
        if (code.isEmpty()) {
            if (!ctx.visible()) {
                return Optional.of(SubmissionResult.needsHuman(
                        "Greenhouse emailed a security code. Connect Gmail in Settings to enter it automatically."));
            }
            ctx.progress().accept("Enter the security code from your email in the browser, then submit.");
            return Optional.of(waitForConfirmation(HUMAN_TIMEOUT));
        }
        enterCode(code.get());
        clickSubmit();
        return Optional.empty();
    }

    private void enterCode(String code) {
        Locator boxes = page.locator("input[id^='security-input']");
        int count = boxes.count();
        if (count > 1) {
            for (int i = 0; i < Math.min(count, code.length()); i++) {
                boxes.nth(i).fill(String.valueOf(code.charAt(i)));
            }
        } else if (count == 1) {
            boxes.first().fill(code);
        } else {
            page.locator("input[autocomplete='one-time-code'], input[name*='code']").first().fill(code);
        }
    }

    private void clickSubmit() {
        Locator byRole = page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(SUBMIT));
        Locator button = byRole.count() > 0 ? byRole.last() : page.locator("button[type='submit']").last();
        button.scrollIntoViewIfNeeded();
        button.click();
    }

    private boolean isConfirmed() {
        if (page.url().contains("confirmation")) return true;
        return page.getByText(CONFIRMATION).filter(new Locator.FilterOptions().setVisible(true)).count() > 0;
    }

    private boolean securityCodeRequested() {
        return page.locator("input[id^='security-input']").count() > 0;
    }

    private boolean captchaVisible() {
        return page.locator("iframe[src*='recaptcha'][src*='bframe'], iframe[src*='hcaptcha.com'][title*='challenge']")
                .filter(new Locator.FilterOptions().setVisible(true)).count() > 0;
    }

    private List<String> visibleErrors() {
        return page.locator("[id$='-error']").filter(new Locator.FilterOptions().setVisible(true))
                .allInnerTexts().stream().map(String::trim).filter(s -> !s.isEmpty()).distinct().toList();
    }

    /** Lets the human finish in the visible window; succeeds once the confirmation page shows. */
    private SubmissionResult waitForConfirmation(Duration timeout) {
        Instant deadline = Instant.now().plus(timeout);
        try {
            while (Instant.now().isBefore(deadline)) {
                if (page.isClosed()) return SubmissionResult.failed("The browser was closed before submitting.");
                if (isConfirmed()) return SubmissionResult.submitted("Application submitted (finished in the browser).");
                page.waitForTimeout(1_000);
            }
        } catch (PlaywrightException e) {
            return SubmissionResult.failed("The browser was closed before submitting.");
        }
        return SubmissionResult.failed("Timed out waiting for the application to be finished in the browser.");
    }

    private void waitForClose(Duration timeout) {
        Instant deadline = Instant.now().plus(timeout);
        try {
            while (!page.isClosed() && Instant.now().isBefore(deadline)) page.waitForTimeout(500);
        } catch (PlaywrightException ignored) {
            // window closed
        }
    }

    // ---- helpers -------------------------------------------------------------------------

    private Locator byId(String id) {
        return page.locator("[id='" + id.replace("'", "\\'") + "']").first();
    }

    private Locator require(Locator locator) throws FillException {
        waitAttached(locator, 3_000);
        if (locator.count() == 0) throw new FillException("field not found on the page");
        return locator;
    }

    private static boolean waitVisible(Locator locator, double timeoutMs) {
        try {
            locator.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(timeoutMs));
            return true;
        } catch (TimeoutError e) {
            return false;
        }
    }

    private static void waitAttached(Locator locator, double timeoutMs) {
        try {
            locator.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.ATTACHED).setTimeout(timeoutMs));
        } catch (TimeoutError ignored) {
            // caller checks count()
        }
    }

    private static String firstLine(String s) {
        if (s == null) return "error";
        int nl = s.indexOf('\n');
        return nl < 0 ? s : s.substring(0, nl);
    }

    static final class FillException extends Exception {
        FillException(String message) {
            super(message);
        }
    }
}
