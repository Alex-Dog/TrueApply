package com.trueapply.browser;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;

/** A running browser with one page. Close it to shut the browser down. */
public final class BrowserSession implements AutoCloseable {
    private final Playwright playwright;
    private final BrowserContext context;
    private final CookieJar cookies;
    private final Page page;

    BrowserSession(Playwright playwright, BrowserContext context, CookieJar cookies) {
        this.playwright = playwright;
        this.context = context;
        this.cookies = cookies;
        if (cookies != null) cookies.restoreInto(context); // stay signed in from the last run
        this.page = context.pages().isEmpty() ? context.newPage() : context.pages().getFirst();
    }

    public Page page() {
        return page;
    }

    @Override
    public void close() {
        try {
            if (cookies != null) cookies.saveFrom(context);
            context.close();
        } finally {
            playwright.close();
        }
    }
}
