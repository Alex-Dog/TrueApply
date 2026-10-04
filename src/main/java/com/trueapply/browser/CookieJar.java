package com.trueapply.browser;

import com.fasterxml.jackson.core.type.TypeReference;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.Cookie;
import com.microsoft.playwright.options.SameSiteAttribute;
import com.trueapply.security.Vault;
import com.trueapply.util.Json;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Keeps the automated browser's cookies between runs, including session cookies, which the
 * browser itself drops when it closes. Sites like Workday keep their sign-in in a session
 * cookie, so this is what lets the next run continue without logging in again (as long as the
 * site hasn't expired the session on its side). Stored encrypted with the {@link Vault} key.
 */
public final class CookieJar {
    private record StoredCookie(String name, String value, String domain, String path, double expires,
                                boolean httpOnly, boolean secure, String sameSite) {
    }

    private static final TypeReference<List<StoredCookie>> LIST = new TypeReference<>() {
    };

    private final Path file;
    private final Vault vault;

    public CookieJar(Path file, Vault vault) {
        this.file = file;
        this.vault = vault;
    }

    /** Restores saved, unexpired cookies into a freshly opened browser. */
    public void restoreInto(BrowserContext context) {
        List<Cookie> cookies = new ArrayList<>();
        double now = Instant.now().getEpochSecond();
        for (StoredCookie c : load()) {
            if (c.expires() > 0 && c.expires() < now) continue;
            Cookie cookie = new Cookie(c.name(), c.value())
                    .setDomain(c.domain())
                    .setPath(c.path())
                    .setHttpOnly(c.httpOnly())
                    .setSecure(c.secure());
            if (c.expires() > 0) cookie.setExpires(c.expires());
            if (c.sameSite() != null) cookie.setSameSite(SameSiteAttribute.valueOf(c.sameSite()));
            cookies.add(cookie);
        }
        if (cookies.isEmpty()) return;
        try {
            context.addCookies(cookies);
        } catch (PlaywrightException e) {
            // one malformed cookie shouldn't stop the run; the site will just ask to sign in
        }
    }

    /** Saves every cookie the browser currently holds. Safe to call repeatedly. */
    public void saveFrom(BrowserContext context) {
        try {
            List<StoredCookie> stored = context.cookies().stream()
                    .map(c -> new StoredCookie(c.name, c.value, c.domain, c.path, c.expires,
                            Boolean.TRUE.equals(c.httpOnly), Boolean.TRUE.equals(c.secure),
                            c.sameSite == null ? null : c.sameSite.name()))
                    .toList();
            Files.writeString(file, vault.encrypt(Json.write(stored)));
        } catch (PlaywrightException | IOException e) {
            // browser already gone or disk issue: keep the previous jar
        }
    }

    private List<StoredCookie> load() {
        try {
            if (!Files.exists(file)) return List.of();
            return Json.read(vault.decrypt(Files.readString(file).trim()), LIST);
        } catch (IOException | RuntimeException e) {
            return List.of(); // unreadable jar: start signed out rather than fail
        }
    }
}
