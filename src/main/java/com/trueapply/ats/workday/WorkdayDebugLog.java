package com.trueapply.ats.workday;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.trueapply.model.FormField;
import com.trueapply.model.JobApplication;
import com.trueapply.util.AppPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

/**
 * Appends the HTML of Workday fields we failed to fill to {@code logs/workday-debug.log} in the
 * app data folder. Workday varies by company, and this is what's needed to fix a new variant.
 * Only the failing field's markup is written (labels and options; no passwords).
 */
final class WorkdayDebugLog {
    private static final int MAX_HTML = 15_000;

    private WorkdayDebugLog() {
    }

    static void record(JobApplication app, FormField field, Locator container, String error) {
        String html;
        try {
            html = container.count() > 0 ? (String) container.evaluate("e => e.outerHTML") : "(container not found)";
        } catch (PlaywrightException e) {
            html = "(couldn't read container: " + e.getMessage() + ")";
        }
        if (html.length() > MAX_HTML) html = html.substring(0, MAX_HTML) + "…";
        write(header(app) + "field: " + field.key + " | " + field.label + " | control=" + field.control
                + " | answer=" + field.displayAnswer() + "\n"
                + "error: " + error + "\n"
                + html + "\n\n");
    }

    /** When the walk gets stuck: which step, and every visible button/link with its automation id. */
    static void recordPage(JobApplication app, String step, Page page, String error) {
        String buttons;
        try {
            buttons = String.valueOf(page.evaluate("""
                    () => [...document.querySelectorAll('button, [role="button"], a[data-automation-id]')]
                      .filter(e => e.offsetParent !== null)
                      .map(e => (e.getAttribute('data-automation-id') || '-') + ' | ' + (e.innerText || e.getAttribute('aria-label') || '').trim().slice(0, 60))
                      .join('\\n')"""));
        } catch (PlaywrightException e) {
            buttons = "(couldn't read the page: " + e.getMessage() + ")";
        }
        write(header(app) + "stuck on step: " + step + " | url: " + safeUrl(page) + "\n"
                + "error: " + error + "\nvisible buttons:\n" + buttons + "\n\n");
    }

    /**
     * Once per page visit: every form field's automation id, label, and its enclosing groups.
     * Labels and ids only, never values. Used to adapt to a company's Workday layout.
     */
    static void recordStructure(JobApplication app, String step, Page page) {
        String structure;
        try {
            structure = String.valueOf(page.evaluate("""
                    () => [...document.querySelectorAll('[data-automation-id^="formField-"]')]
                      .filter(c => c.offsetParent !== null)
                      .map(c => {
                        const l = c.querySelector('label, legend');
                        const chain = [];
                        let g = c.parentElement;
                        while (g && chain.length < 4) {
                          const id = g.getAttribute('data-automation-id');
                          const role = g.getAttribute('role');
                          const lb = g.getAttribute('aria-labelledby');
                          const title = lb && document.getElementById(lb) ? document.getElementById(lb).innerText.trim() : '';
                          if (id || role === 'group') chain.push((id || 'group') + (title ? '[' + title.slice(0, 30) + ']' : ''));
                          g = g.parentElement;
                        }
                        const buttons = [...c.querySelectorAll('button')].map(b => b.getAttribute('aria-label') || b.innerText).filter(Boolean);
                        return c.getAttribute('data-automation-id') + ' | ' + (l ? l.innerText.trim().slice(0, 40) : '-')
                          + ' | in: ' + chain.join(' < ') + (buttons.length ? ' | buttons: ' + buttons.join(', ').slice(0, 80) : '');
                      }).join('\\n')"""));
        } catch (PlaywrightException e) {
            structure = "(couldn't read the page: " + e.getMessage() + ")";
        }
        write(header(app) + "page structure for step: " + step + "\n" + structure + "\n\n");
    }

    /** Workday's "Something went wrong" screen: when, where, which code, and which recovery attempt. */
    static void recordError(JobApplication app, Page page, String code, int attempt) {
        write(header(app) + "workday error page | code: " + code + " | recovery attempt " + attempt
                + " | url: " + safeUrl(page) + "\n\n");
    }

    /** One line in the step-by-step trace of a run (what was read, what happened on Next). */
    static void note(JobApplication app, String message) {
        write("---- " + Instant.now() + " | " + (app.job == null ? "" : app.job.company) + " | " + message + "\n");
    }

    private static String header(JobApplication app) {
        return "==== " + Instant.now() + " | " + (app.job == null ? "" : app.job.company + " | " + app.job.url) + "\n";
    }

    private static String safeUrl(Page page) {
        try {
            return page.url();
        } catch (PlaywrightException e) {
            return "?";
        }
    }

    private static void write(String entry) {
        try {
            Path file = AppPaths.root().resolve("logs");
            Files.createDirectories(file);
            Files.writeString(file.resolve("workday-debug.log"), entry,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
            // diagnostics only
        }
    }
}
