package com.trueapply.util;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Per-user data directory: %APPDATA%\TrueApply on Windows, ~/.trueapply elsewhere. */
public final class AppPaths {
    private static final Path ROOT = resolveRoot();

    private AppPaths() {
    }

    private static Path resolveRoot() {
        String override = System.getProperty("trueapply.home");
        if (override != null && !override.isBlank()) return Path.of(override);
        String appData = System.getenv("APPDATA");
        if (appData != null && !appData.isBlank()) return Path.of(appData, "TrueApply");
        return Path.of(System.getProperty("user.home"), ".trueapply");
    }

    public static Path root() {
        return ensureDir(ROOT);
    }

    public static Path database() {
        return root().resolve("trueapply.db");
    }

    public static Path browserProfile() {
        return ensureDir(ROOT.resolve("browser-profile"));
    }

    public static Path googleTokens() {
        return ensureDir(ROOT.resolve("google-tokens"));
    }

    public static Path resumes() {
        return ensureDir(ROOT.resolve("resumes"));
    }

    public static Path uploads() {
        return ensureDir(ROOT.resolve("uploads"));
    }

    public static Path vaultKey() {
        return root().resolve("vault.key");
    }

    /** Where a developer drops the Google OAuth desktop-client JSON. */
    public static Path googleCredentials() {
        return root().resolve("google-credentials.json");
    }

    private static Path ensureDir(Path dir) {
        try {
            return Files.createDirectories(dir);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
