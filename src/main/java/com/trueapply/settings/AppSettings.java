package com.trueapply.settings;

import com.trueapply.db.SettingsRepository;
import com.trueapply.util.Text;

import java.util.List;

/** Typed accessors for user-facing settings. */
public class AppSettings {
    /** Workday career sites as "tenant.wdN/Site|Company"; all verified to answer the public job API. */
    public static final List<String> DEFAULT_WORKDAY_SITES = List.of(
            "nvidia.wd5/NVIDIAExternalCareerSite|NVIDIA", "salesforce.wd12/External_Career_Site|Salesforce",
            "intel.wd1/External|Intel", "adobe.wd5/external_experienced|Adobe",
            "mastercard.wd1/CorporateCareers|Mastercard", "workday.wd5/Workday|Workday",
            "capitalone.wd12/Capital_One|Capital One", "pg.wd5/1000|Procter & Gamble",
            "boeing.wd1/EXTERNAL_CAREERS|Boeing", "cisco.wd5/Cisco_Careers|Cisco");

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

    /** Each discovery source can be switched off in Settings. */
    public boolean sourceEnabled(String sourceId) {
        return bool("source." + sourceId + ".enabled", true);
    }

    public void setSourceEnabled(String sourceId, boolean enabled) {
        putBool("source." + sourceId + ".enabled", enabled);
    }

    /** Greenhouse boards spotted in other sources' results; scanned alongside the configured ones. */
    public java.util.Set<String> learnedGreenhouseBoards() {
        return new java.util.TreeSet<>(repo.get("greenhouse.learnedBoards").map(Text::splitList).orElse(List.of()));
    }

    public void addLearnedGreenhouseBoards(java.util.Collection<String> boards) {
        java.util.Set<String> all = learnedGreenhouseBoards();
        boards.forEach(b -> all.add(b.toLowerCase(java.util.Locale.ROOT)));
        repo.put("greenhouse.learnedBoards", String.join("\n", all));
    }

    public void clearLearnedGreenhouseBoards() {
        repo.put("greenhouse.learnedBoards", "");
    }

    /** Configured Workday sites: board ("tenant.wdN/Site") → company name (may be blank). */
    public java.util.Map<String, String> workdaySites() {
        return parseSites(repo.get("workday.sites").map(Text::splitList).orElse(DEFAULT_WORKDAY_SITES));
    }

    public void setWorkdaySites(List<String> lines) {
        repo.put("workday.sites", String.join("\n", lines));
    }

    public List<String> workdaySiteLines() {
        return repo.get("workday.sites").map(Text::splitList).orElse(DEFAULT_WORKDAY_SITES);
    }

    /** Workday sites spotted in other sources' results (board → company). */
    public java.util.Map<String, String> learnedWorkdaySites() {
        return parseSites(repo.get("workday.learnedSites").map(Text::splitList).orElse(List.of()));
    }

    public void addLearnedWorkdaySites(java.util.Map<String, String> sites) {
        java.util.Map<String, String> all = new java.util.TreeMap<>(learnedWorkdaySites());
        all.putAll(sites);
        repo.put("workday.learnedSites", String.join("\n",
                all.entrySet().stream().map(e -> e.getKey() + "|" + e.getValue()).toList()));
    }

    public void clearLearnedWorkdaySites() {
        repo.put("workday.learnedSites", "");
    }

    private static java.util.Map<String, String> parseSites(List<String> lines) {
        java.util.Map<String, String> sites = new java.util.LinkedHashMap<>();
        for (String line : lines) {
            int bar = line.indexOf('|');
            String board = (bar < 0 ? line : line.substring(0, bar)).trim();
            if (!board.isEmpty()) sites.put(board, bar < 0 ? "" : line.substring(bar + 1).trim());
        }
        return sites;
    }

    /**
     * Whether TrueApply may create Workday candidate accounts by itself. Doing so ticks the
     * company's "I agree" box for account terms, so it is off until the user opts in.
     */
    /**
     * Whether multi-page forms (Workday) look ahead with temporary answers so all questions can be
     * asked at once. The stand-ins sit in the site's draft until replaced; they're never submitted.
     */
    public boolean lookAhead() {
        return bool("workday.lookAhead", true);
    }

    public void setLookAhead(boolean value) {
        putBool("workday.lookAhead", value);
    }

    public boolean createWorkdayAccounts() {
        return bool("workday.createAccounts", false);
    }

    public void setCreateWorkdayAccounts(boolean value) {
        putBool("workday.createAccounts", value);
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
