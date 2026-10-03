package com.trueapply.browser;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.trueapply.settings.AppSettings;
import com.trueapply.util.AppPaths;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Starts Playwright. By default it drives the Edge/Chrome already installed on the machine so
 * nothing extra is downloaded; "chromium" uses Playwright's bundled build (downloaded on first use).
 * Each channel gets its own persistent profile so cookies and logins survive between runs.
 */
public class BrowserLauncher {
    private final AppSettings settings;

    public BrowserLauncher(AppSettings settings) {
        this.settings = settings;
    }

    /** Must be used from a single thread for its whole life (Playwright isn't thread-safe). */
    public BrowserSession open(boolean visible) {
        String channel = settings.browserChannel();
        if (!"chromium".equals(channel)) {
            Playwright playwright = Playwright.create(new Playwright.CreateOptions()
                    .setEnv(Map.of("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1")));
            try {
                return new BrowserSession(playwright, launch(playwright, channel, visible));
            } catch (PlaywrightException e) {
                playwright.close(); // channel not installed — fall back to bundled Chromium
            }
        }
        Playwright playwright = Playwright.create();
        try {
            return new BrowserSession(playwright, launch(playwright, null, visible));
        } catch (RuntimeException e) {
            playwright.close();
            throw e;
        }
    }

    private static BrowserContext launch(Playwright playwright, String channel, boolean visible) {
        Path profile = AppPaths.browserProfile().resolve(channel == null ? "chromium" : channel);
        BrowserType.LaunchPersistentContextOptions options = new BrowserType.LaunchPersistentContextOptions()
                .setHeadless(!visible)
                .setViewportSize(1280, 900)
                // Playwright adds --no-sandbox, which makes Edge/Chrome show a scary warning bar.
                .setIgnoreDefaultArgs(List.of("--no-sandbox"));
        if (channel != null) options.setChannel(channel);
        return playwright.chromium().launchPersistentContext(profile, options);
    }
}
