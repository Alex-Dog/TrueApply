package com.trueapply.discovery;

import com.trueapply.model.Job;
import com.trueapply.model.UserProfile;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JobFilterTest {

    private static Job job(String title, String location) {
        Job job = new Job();
        job.title = title;
        job.location = location;
        job.company = "Acme";
        return job;
    }

    private static UserProfile.JobPreferences prefs(String type, String... locations) {
        UserProfile.JobPreferences prefs = new UserProfile.JobPreferences();
        prefs.titles = List.of("Software Engineer");
        prefs.excludeKeywords = List.of("Senior", "Staff");
        prefs.locations = List.of(locations);
        prefs.employmentType = type;
        prefs.remoteOk = true;
        return prefs;
    }

    @Test
    void titlesAndExclusions() {
        UserProfile.JobPreferences p = prefs("Any");
        assertTrue(JobFilter.matches(job("Software Engineer, Backend", "Anywhere"), p, "United States"));
        assertTrue(JobFilter.matches(job("Software Engineering Manager", "Anywhere"), p, "United States"));
        assertFalse(JobFilter.matches(job("Senior Software Engineer", "Anywhere"), p, "United States"));
        assertFalse(JobFilter.matches(job("Product Designer", "Anywhere"), p, "United States"));
    }

    @Test
    void countryPreferenceUnderstandsCitiesStatesAndRemoteQualifiers() {
        UserProfile.JobPreferences us = prefs("Any", "United States");
        assertTrue(match("Austin, TX", us));
        assertTrue(match("San Francisco Bay Area", us));
        assertTrue(match("New York City, New York", us));
        assertTrue(match("Remote - US", us));
        assertTrue(match("US-Remote", us));
        assertTrue(match("Remote", us));
        assertTrue(match("London, UK; Seattle, WA", us));
        assertTrue(match("Albuquerque, New Mexico", us));
        assertFalse(match("Remote - UK", us));
        assertFalse(match("London, United Kingdom", us));
        assertFalse(match("Toronto, ON, Canada", us));
        assertFalse(match("Bengaluru, India", us));
        assertFalse(match("Remote (EMEA)", us));
        assertFalse(match("Dublin", us));

        us.remoteOk = false;
        assertFalse(match("Remote - US", us));
        assertTrue(match("Denver, CO", us));
    }

    @Test
    void cityPreferenceUsesHomeCountryForRemoteJobs() {
        UserProfile.JobPreferences sf = prefs("Any", "San Francisco");
        assertTrue(match("San Francisco, CA", sf));
        assertTrue(match("Remote - USA", sf));
        assertFalse(match("Remote - Canada", sf));
        assertFalse(match("New York, NY", sf));
    }

    @Test
    void employmentTypeIsInferredFromTheTitle() {
        assertTrue(JobFilter.matchesEmploymentType("Software Engineer Intern (Summer 2027)", "Internship"));
        assertTrue(JobFilter.matchesEmploymentType("Software Engineering Co-op", "Internship"));
        assertFalse(JobFilter.matchesEmploymentType("Internal Tools Engineer", "Internship"));
        assertFalse(JobFilter.matchesEmploymentType("Software Engineer", "Internship"));
        assertTrue(JobFilter.matchesEmploymentType("Internal Tools Engineer", "Full-time"));
        assertFalse(JobFilter.matchesEmploymentType("Software Engineering Intern", "Full-time"));
        assertFalse(JobFilter.matchesEmploymentType("Software Engineer (Contract)", "Full-time"));
        assertTrue(JobFilter.matchesEmploymentType("Anything", "Any"));
    }

    private static boolean match(String location, UserProfile.JobPreferences p) {
        return JobFilter.matches(job("Software Engineer", location), p, "United States");
    }
}
