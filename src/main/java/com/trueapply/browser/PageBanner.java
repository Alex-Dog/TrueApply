package com.trueapply.browser;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;

/**
 * A bar pinned to the top of the automated browser window, so the user knows when TrueApply is
 * waiting for them and what to do. It ignores clicks, so it never blocks the page. Call
 * {@link #show} repeatedly while waiting: navigation wipes it, and showing it again is cheap.
 */
public final class PageBanner {
    private static final String SHOW_JS = """
            (message) => {
              let bar = document.getElementById('trueapply-banner');
              if (!bar) {
                bar = document.createElement('div');
                bar.id = 'trueapply-banner';
                Object.assign(bar.style, {
                  position: 'fixed', top: '0', left: '0', right: '0', zIndex: '2147483647',
                  background: '#0969da', color: '#ffffff', font: '600 15px "Segoe UI", system-ui, sans-serif',
                  padding: '12px 20px', boxShadow: '0 2px 10px rgba(0,0,0,.35)', pointerEvents: 'none',
                  lineHeight: '1.4'
                });
                document.documentElement.appendChild(bar);
              }
              const text = 'TrueApply is waiting for you: ' + message;
              if (bar.textContent !== text) bar.textContent = text;
            }""";

    private PageBanner() {
    }

    public static void show(Page page, String message) {
        try {
            if (!page.isClosed()) page.evaluate(SHOW_JS, message);
        } catch (PlaywrightException ignored) {
            // page mid-navigation; the next call re-adds it
        }
    }

    public static void clear(Page page) {
        try {
            if (!page.isClosed()) page.evaluate("() => document.getElementById('trueapply-banner')?.remove()");
        } catch (PlaywrightException ignored) {
            // nothing to clear
        }
    }
}
