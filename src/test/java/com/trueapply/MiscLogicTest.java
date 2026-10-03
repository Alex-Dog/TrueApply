package com.trueapply;

import com.trueapply.ats.greenhouse.GreenhouseUrls;
import com.trueapply.discovery.JobFilter;
import com.trueapply.email.VerificationCodes;
import com.trueapply.model.Job;
import com.trueapply.model.UserProfile;
import com.trueapply.security.PasswordGenerator;
import com.trueapply.security.Vault;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MiscLogicTest {

    @Test
    void recognizesGreenhouseUrls() {
        assertEquals(Optional.of(new GreenhouseUrls.Ref("discord", "8806482002")),
                GreenhouseUrls.parse("https://job-boards.greenhouse.io/discord/jobs/8806482002?gh_src=x"));
        assertEquals(Optional.of(new GreenhouseUrls.Ref("stripe", "123")),
                GreenhouseUrls.parse("https://boards.greenhouse.io/embed/job_app?token=123&for=stripe"));
        assertEquals(Optional.of("777"), GreenhouseUrls.ghJid("https://stripe.com/jobs/listing/x?gh_jid=777"));
        assertEquals(Optional.of("acme"),
                GreenhouseUrls.boardFromHtml("<script src=\"https://boards.greenhouse.io/embed/job_board/js?for=acme\"></script>"));
        assertTrue(GreenhouseUrls.parse("https://example.com/careers").isEmpty());
    }

    @Test
    void filtersJobsByPreferences() {
        UserProfile.JobPreferences prefs = new UserProfile.JobPreferences();
        prefs.titles = List.of("Software Engineer");
        prefs.excludeKeywords = List.of("Senior", "Staff");
        prefs.locations = List.of("San Francisco");
        prefs.remoteOk = true;

        assertTrue(JobFilter.matches(job("Software Engineer, Backend", "San Francisco, CA"), prefs));
        assertTrue(JobFilter.matches(job("Software Engineering Intern", "Remote - US"), prefs));
        assertFalse(JobFilter.matches(job("Senior Software Engineer", "San Francisco, CA"), prefs));
        assertFalse(JobFilter.matches(job("Software Engineer", "New York, NY"), prefs));
        assertFalse(JobFilter.matches(job("Product Designer", "San Francisco, CA"), prefs));
    }

    private static Job job(String title, String location) {
        Job job = new Job();
        job.title = title;
        job.location = location;
        job.company = "Acme";
        return job;
    }

    @Test
    void extractsVerificationCodes() {
        assertEquals(Optional.of("Xk4P9qLm"), VerificationCodes.extract(
                "Copy and paste this code into the security code field on your application: Xk4P9qLm\nThanks"));
        assertEquals(Optional.of("482913"), VerificationCodes.extract("Your verification code is 482913."));
        assertEquals(Optional.of("A1B2C3D4"), VerificationCodes.extract("Hello,\n\n   A1B2C3D4  \n\nExpires soon."));
        assertTrue(VerificationCodes.extract("Use the code below to continue. It expires in 10 minutes.").isEmpty());
    }

    @Test
    void vaultRoundTripsAndPasswordsAreStrong(@TempDir Path dir) {
        Vault vault = new Vault(dir.resolve("vault.key"));
        String secret = PasswordGenerator.generate();
        String encrypted = vault.encrypt(secret);
        assertNotEquals(secret, encrypted);
        assertEquals(secret, new Vault(dir.resolve("vault.key")).decrypt(encrypted)); // key persisted
        assertEquals(20, secret.length());
        assertTrue(secret.chars().anyMatch(Character::isDigit));
        assertTrue(secret.chars().anyMatch(Character::isUpperCase));
    }
}
