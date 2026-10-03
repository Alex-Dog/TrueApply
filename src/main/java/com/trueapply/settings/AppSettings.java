package com.trueapply.settings;

import com.trueapply.db.SettingsRepository;
import com.trueapply.util.Text;

import java.util.List;

/** Typed accessors for user-facing settings. */
public class AppSettings {
    public static final List<String> DEFAULT_GREENHOUSE_BOARDS = List.of(
            "airbnb", "anthropic", "asana", "brex", "chime", "cloudflare", "coinbase", "databricks", "datadog",
            "discord", "dropbox", "duolingo", "elastic", "figma", "gitlab", "gusto", "instacart", "lyft",
            "mongodb", "okta", "pinterest", "reddit", "robinhood", "samsara", "scaleai", "squarespace",
            "stripe", "toast", "twitch", "vercel", "waymo", "webflow");

    private final SettingsRepository repo;

    public AppSettings(SettingsRepository repo) {
        this.repo = repo;
    }

    public boolean onboardingComplete() {
        return bool("onboarding.complete", false);
    }

    public void setOnboardingComplete(boolean value) {
        putBool("onboarding.complete", value);
    }

    /** Provider id chosen by the user ("anthropic", "openai"), or empty for the developer default. */
    public String aiProvider() {
        return string("ai.provider", "");
    }

    public void setAiProvider(String id) {
        repo.put("ai.provider", id);
    }

    /** Model id the user picked for a provider, or empty for the developer default. */
    public String aiModel(String providerId) {
        return string("ai.model." + providerId, "");
    }

    public void setAiModel(String providerId, String modelId) {
        repo.put("ai.model." + providerId, modelId);
    }

    /** API key the user typed in for a provider; the developer env var (see trueapply.properties) wins if set. */
    public String aiApiKey(String providerId) {
        String key = string("ai.apiKey." + providerId, "");
        // Before multi-provider support the Anthropic key was stored without a suffix.
        if (key.isEmpty() && providerId.equals("anthropic")) key = string("ai.apiKey", "");
        return key;
    }

    public void setAiApiKey(String providerId, String value) {
        repo.put("ai.apiKey." + providerId, value);
    }

    public String adzunaAppId() {
        return string("adzuna.appId", "");
    }

    public void setAdzunaAppId(String value) {
        repo.put("adzuna.appId", value);
    }

    public String adzunaAppKey() {
        return string("adzuna.appKey", "");
    }

    public void setAdzunaAppKey(String value) {
        repo.put("adzuna.appKey", value);
    }

    public List<String> greenhouseBoards() {
        return repo.get("greenhouse.boards").map(Text::splitList).orElse(DEFAULT_GREENHOUSE_BOARDS);
    }

    public void setGreenhouseBoards(List<String> boards) {
        repo.put("greenhouse.boards", String.join("\n", boards));
    }

    /** Playwright browser channel: "msedge", "chrome", or "chromium" (bundled download). */
    public String browserChannel() {
        return string("browser.channel", "msedge");
    }

    public void setBrowserChannel(String value) {
        repo.put("browser.channel", value);
    }

    public boolean showBrowser() {
        return bool("browser.visible", true);
    }

    public void setShowBrowser(boolean value) {
        putBool("browser.visible", value);
    }

    /** When on, forms are filled but the final submit button is never clicked. */
    public boolean dryRun() {
        return bool("submit.dryRun", true);
    }

    public void setDryRun(boolean value) {
        putBool("submit.dryRun", value);
    }

    /** When on, optional creative questions also park the application in the inbox. */
    public boolean includeOptionalCreative() {
        return bool("creative.includeOptional", false);
    }

    public void setIncludeOptionalCreative(boolean value) {
        putBool("creative.includeOptional", value);
    }

    public boolean darkTheme() {
        return bool("ui.dark", false);
    }

    public void setDarkTheme(boolean value) {
        putBool("ui.dark", value);
    }

    public String gmailAccount() {
        return string("gmail.account", "");
    }

    public void setGmailAccount(String value) {
        repo.put("gmail.account", value);
    }

    private String string(String key, String fallback) {
        return repo.get(key).orElse(fallback);
    }

    private boolean bool(String key, boolean fallback) {
        return repo.get(key).map(Boolean::parseBoolean).orElse(fallback);
    }

    private void putBool(String key, boolean value) {
        repo.put(key, Boolean.toString(value));
    }
}
