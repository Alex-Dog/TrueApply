package com.trueapply.browser;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;

/** A running browser with one page. Close it to shut the browser down. */
public final class BrowserSession implements AutoCloseable {
    private final Playwright playwright;
    private final BrowserContext context;
    private final Page page;

    BrowserSession(Playwright playwright, BrowserContext context) {
        this.playwright = playwright;
        this.context = context;
        this.page = context.pages().isEmpty() ? context.newPage() : context.pages().getFirst();
    }

    public Page page() {
        return page;
    }

    @Override
    public void close() {
        try {
            context.close();
        } finally {
            playwright.close();
        }
    }
}
