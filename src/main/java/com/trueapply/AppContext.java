package com.trueapply;

import com.trueapply.ai.AiProvider;
import com.trueapply.ai.AiProviders;
import com.trueapply.ats.PlatformRegistry;
import com.trueapply.ats.greenhouse.GreenhousePlatform;
import com.trueapply.ats.workday.WorkdayPlatform;
import com.trueapply.browser.BrowserLauncher;
import com.trueapply.db.AccountRepository;
import com.trueapply.db.ApplicationRepository;
import com.trueapply.db.Database;
import com.trueapply.db.JobRepository;
import com.trueapply.db.ProfileRepository;
import com.trueapply.db.SettingsRepository;
import com.trueapply.discovery.AdzunaJobSource;
import com.trueapply.discovery.DiscoveryService;
import com.trueapply.discovery.GreenhouseJobSource;
import com.trueapply.discovery.SimplifyJobsSource;
import com.trueapply.discovery.WorkdayJobSource;
import com.trueapply.email.GmailService;
import com.trueapply.pipeline.ApplicationPipeline;
import com.trueapply.security.Vault;
import com.trueapply.settings.AppSettings;
import com.trueapply.util.AppEvents;
import com.trueapply.util.AppPaths;

import java.util.List;

/** Wires the app's services together. One instance per running app. */
public final class AppContext implements AutoCloseable {
    public final Database database;
    public final AppSettings settings;
    public final ProfileRepository profiles;
    public final JobRepository jobs;
    public final ApplicationRepository applications;
    public final AccountRepository accounts;
    public final AppEvents events = new AppEvents();
    public final GmailService gmail;
    public final PlatformRegistry platforms;
    public final DiscoveryService discovery;
    public final ApplicationPipeline pipeline;

    private volatile AiProvider ai;

    public AppContext() {
        database = new Database(AppPaths.database());
        SettingsRepository settingsRepo = new SettingsRepository(database);
        settings = new AppSettings(settingsRepo);
        profiles = new ProfileRepository(settingsRepo);
        jobs = new JobRepository(database);
        applications = new ApplicationRepository(database, jobs);
        accounts = new AccountRepository(database, new Vault(AppPaths.vaultKey()));
        gmail = new GmailService(settings);
        platforms = new PlatformRegistry()
                .register(new GreenhousePlatform())
                .register(new WorkdayPlatform());
        // Board/site scans last: they also cover companies the list sources led us to.
        discovery = new DiscoveryService(List.of(
                new SimplifyJobsSource(),
                new AdzunaJobSource(settings),
                new GreenhouseJobSource(settings),
                new WorkdayJobSource(settings)), jobs, profiles, settings);
        pipeline = new ApplicationPipeline(applications, accounts, jobs, profiles, settings, platforms, this::ai,
                new BrowserLauncher(settings), gmail, events);
        reloadAi();
    }

    public AiProvider ai() {
        return ai;
    }

    /** Re-reads AI configuration, e.g. after the user pastes an API key. */
    public void reloadAi() {
        ai = AiProviders.create(settings);
    }

    @Override
    public void close() {
        pipeline.shutdown();
        database.close();
    }
}
