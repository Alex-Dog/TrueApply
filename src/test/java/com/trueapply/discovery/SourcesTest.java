package com.trueapply.discovery;

import com.fasterxml.jackson.databind.JsonNode;
import com.trueapply.ats.AtsUrls;
import com.trueapply.db.Database;
import com.trueapply.db.JobRepository;
import com.trueapply.db.ProfileRepository;
import com.trueapply.db.SettingsRepository;
import com.trueapply.model.AtsType;
import com.trueapply.model.Job;
import com.trueapply.model.UserProfile;
import com.trueapply.settings.AppSettings;
import com.trueapply.util.Json;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SourcesTest {

    @Test
    void detectsEachAtsFromItsUrl() {
        var gh = AtsUrls.detect("https://job-boards.greenhouse.io/figma/jobs/5551234?gh_src=simplify").orElseThrow();
        assertEquals(AtsType.GREENHOUSE, gh.ats());
        assertEquals("greenhouse:figma:5551234", gh.dedupeKey());

        var lever = AtsUrls.detect("https://jobs.lever.co/palantir/0b1c2d3e-4f50-6789-abcd-ef0123456789/apply").orElseThrow();
        assertEquals(AtsType.LEVER, lever.ats());
        assertEquals("palantir", lever.board());

        var ashby = AtsUrls.detect("https://jobs.ashbyhq.com/ramp/0b1c2d3e-4f50-6789-abcd-ef0123456789").orElseThrow();
        assertEquals(AtsType.ASHBY, ashby.ats());

        assertEquals(AtsType.WORKDAY, AtsUrls.detect("https://acme.wd1.myworkdayjobs.com/en-US/careers/job/123").orElseThrow().ats());
        assertTrue(AtsUrls.detect("https://jobs.smartrecruiters.com/Acme/123").isEmpty());
    }

    @Test
    void simplifyListingsBecomeJobs() {
        JsonNode listings = Json.read("""
                [
                  {"id": "a1", "active": true, "is_visible": true, "company_name": "Figma",
                   "title": "Software Engineer, Summer 2027", "locations": ["San Francisco, CA", "New York, NY"],
                   "url": "https://job-boards.greenhouse.io/figma/jobs/5551234", "date_posted": 1790000000,
                   "sponsorship": "Offers Sponsorship"},
                  {"id": "a2", "active": false, "company_name": "Closed Co", "title": "Intern", "locations": [],
                   "url": "https://job-boards.greenhouse.io/closed/jobs/1"},
                  {"id": "a3", "active": true, "company_name": "Citizens Only", "title": "SWE Intern",
                   "locations": ["Remote"], "url": "https://acme.wd1.myworkdayjobs.com/job/1",
                   "sponsorship": "U.S. Citizenship is Required"}
                ]""", JsonNode.class);

        List<Job> jobs = SimplifyJobsSource.toJobs(listings, "Internship", false);
        assertEquals(2, jobs.size()); // inactive one dropped
        Job figma = jobs.getFirst();
        assertEquals("greenhouse:figma:5551234", figma.dedupeKey); // same key as the Greenhouse board scan
        assertEquals("San Francisco, CA; New York, NY", figma.location);
        assertEquals("Internship", figma.jobType);
        assertNull(jobs.get(1).ats); // Workday: manual only

        List<Job> sponsored = SimplifyJobsSource.toJobs(listings, "Internship", true);
        assertEquals(1, sponsored.size()); // the citizens-only role is skipped for someone needing sponsorship
    }

    @Test
    void knownJobTypeBeatsTitleGuessing() {
        Job job = new Job();
        job.title = "Software Engineer, Summer 2027"; // no "intern" in the title
        job.location = "San Francisco, CA";
        job.company = "Figma";
        job.jobType = "Internship";
        UserProfile.JobPreferences prefs = new UserProfile.JobPreferences();
        prefs.titles = List.of("Software Engineer");
        prefs.employmentType = "Internship";
        assertTrue(JobFilter.matches(job, prefs, "United States"));
        prefs.employmentType = "Full-time";
        assertFalse(JobFilter.matches(job, prefs, "United States"));
    }

    @Test
    void boardsSeenInOtherSourcesAreLearned(@TempDir Path dir) {
        try (Database db = new Database(dir.resolve("t.db"))) {
            SettingsRepository repo = new SettingsRepository(db);
            AppSettings settings = new AppSettings(repo);
            ProfileRepository profiles = new ProfileRepository(repo);
            UserProfile profile = new UserProfile();
            profile.preferences.employmentType = "Any";
            profiles.save(profile);

            Job fromList = new Job();
            fromList.source = "fake";
            fromList.title = "Engineer";
            fromList.company = "Newco";
            fromList.location = "Remote";
            AtsUrls.tag(fromList, "https://job-boards.greenhouse.io/newco/jobs/42");

            JobSource fake = new JobSource() {
                public String id() { return "fake"; }
                public String name() { return "Fake"; }
                public boolean isConfigured() { return true; }
                public List<Job> fetch(UserProfile p, Consumer<String> progress) { return List.of(fromList); }
            };
            DiscoveryService.Result result = new DiscoveryService(List.of(fake), new JobRepository(db), profiles, settings)
                    .discover(m -> { });

            assertEquals(1, result.added());
            assertEquals(1, result.learnedBoards());
            assertEquals(Set.of("newco"), settings.learnedGreenhouseBoards());

            settings.setSourceEnabled("fake", false);
            var skipped = new DiscoveryService(List.of(fake), new JobRepository(db), profiles, settings).discover(m -> { });
            assertTrue(skipped.perSource().isEmpty());
        }
    }
}
