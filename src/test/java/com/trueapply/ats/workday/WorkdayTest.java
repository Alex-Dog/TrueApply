package com.trueapply.ats.workday;

import com.fasterxml.jackson.databind.JsonNode;
import com.trueapply.ats.AtsUrls;
import com.trueapply.discovery.LocationMatcher;
import com.trueapply.email.VerificationCodes;
import com.trueapply.model.AtsType;
import com.trueapply.model.Job;
import com.trueapply.util.Json;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkdayTest {

    @Test
    void parsesCareerSiteAndJobUrls() {
        WorkdayUrls.JobRef ref = WorkdayUrls.job(
                "https://nvidia.wd5.myworkdayjobs.com/en-US/NVIDIAExternalCareerSite/job/China-Shanghai/Custom-SOC-Intern---2027_JR2024692/apply/applyManually")
                .orElseThrow();
        assertEquals("nvidia.wd5/NVIDIAExternalCareerSite", ref.site().board());
        assertEquals("/job/China-Shanghai/Custom-SOC-Intern---2027_JR2024692", ref.externalPath());
        assertEquals("JR2024692", ref.reqId());
        assertEquals("https://nvidia.wd5.myworkdayjobs.com/wday/cxs/nvidia/NVIDIAExternalCareerSite", ref.site().apiBase());

        // No locale segment, different data center
        WorkdayUrls.JobRef noLocale = WorkdayUrls.job(
                "https://globalhr.wd5.myworkdayjobs.com/REC_RTX_Ext_Gateway/job/US-TX-Austin/Software-Engineer-Intern_01776543?source=Simplify")
                .orElseThrow();
        assertEquals("01776543", noLocale.reqId());

        assertTrue(WorkdayUrls.job("https://nvidia.wd5.myworkdayjobs.com/NVIDIAExternalCareerSite").isEmpty());
        assertEquals("NVIDIAExternalCareerSite",
                WorkdayUrls.site("https://nvidia.wd5.myworkdayjobs.com/NVIDIAExternalCareerSite").orElseThrow().site());
        assertEquals("pg.wd5/1000", WorkdayUrls.parseBoard("pg.wd5/1000|Procter & Gamble").orElseThrow().board());
    }

    @Test
    void sameJobFromAnySourceGetsOneKey() {
        String fromSimplify = "https://nvidia.wd5.myworkdayjobs.com/en-US/NVIDIAExternalCareerSite/job/US-CA-Santa-Clara/Intern_JR1";
        String fromSearch = new WorkdayUrls.Site("nvidia", "wd5", "NVIDIAExternalCareerSite").jobUrl("/job/US-CA-Santa-Clara/Intern_JR1");
        assertEquals(AtsUrls.detect(fromSimplify).orElseThrow().dedupeKey(), AtsUrls.detect(fromSearch).orElseThrow().dedupeKey());
        assertEquals(AtsType.WORKDAY, AtsUrls.detect(fromSimplify).orElseThrow().ats());
    }

    @Test
    void searchResultsBecomeJobs() {
        JsonNode posting = Json.read("""
                {"title": "Software Engineering Intern", "externalPath": "/job/US-CA-Santa-Clara/Software-Engineering-Intern_JR2025555",
                 "locationsText": "US, CA, Santa Clara", "postedOn": "Posted 3 Days Ago"}""", JsonNode.class);
        Job job = WorkdayApi.toJob(new WorkdayUrls.Site("nvidia", "wd5", "NVIDIAExternalCareerSite"), posting, "NVIDIA");
        assertEquals(AtsType.WORKDAY, job.ats);
        assertEquals("JR2025555", job.atsJobId);
        assertEquals("NVIDIA", job.company);
        assertTrue(job.url.startsWith("https://nvidia.wd5.myworkdayjobs.com/en-US/NVIDIAExternalCareerSite/job/"));
        assertTrue(Duration.between(job.postedAt, Instant.now()).toDays() == 3);
        assertNull(WorkdayApi.postedAt("Posted sometime"));
    }

    @Test
    void workdayLocationsMatchCountryPreferences() {
        assertTrue(LocationMatcher.matches("US, CA, Santa Clara", List.of("United States"), true, "United States"));
        assertTrue(LocationMatcher.matches("3 Locations", List.of("United States"), true, "United States"));
        assertTrue(!LocationMatcher.matches("China, Shanghai", List.of("United States"), true, "United States"));
    }

    @Test
    void twoLevelPickerAnswersSplitIntoAPath() {
        assertEquals(List.of("Job Board", "LinkedIn"), WorkdayWalker.splitPath("Job Board › LinkedIn"));
        assertEquals(List.of("Job Board", "LinkedIn"), WorkdayWalker.splitPath("Job Board > LinkedIn"));
        assertEquals(List.of("United States of America (+1)"), WorkdayWalker.splitPath("United States of America (+1)"));
    }

    @Test
    void playwrightErrorsBecomeReadable() {
        RuntimeException timeout = new RuntimeException("""
                Error {
                  message='Timeout 10000ms exceeded.
                  name='TimeoutError
                  stack='TimeoutError: Timeout 10000ms exceeded.""");
        assertEquals("the field didn't respond (Timeout 10000ms exceeded.)", WorkdayWalker.readableError(timeout));
        assertEquals("no choice matching “Yes” among []",
                WorkdayWalker.readableError(new IllegalStateException("no choice matching “Yes” among []")));
    }

    @Test
    void findsVerificationLinkForTheRightSite() {
        String html = """
                <p>Welcome!</p><a href="https://example.com/unsubscribe">unsubscribe</a>
                <a href="https://nvidia.wd5.myworkdayjobs.com/en-US/NVIDIAExternalCareerSite/activate/abc123?x=1&amp;y=2">Verify account</a>""";
        assertEquals(Optional.of("https://nvidia.wd5.myworkdayjobs.com/en-US/NVIDIAExternalCareerSite/activate/abc123?x=1&y=2"),
                VerificationCodes.extractLink(html, "nvidia.wd5.myworkdayjobs.com"));
        assertTrue(VerificationCodes.extractLink(html, "intel.wd1.myworkdayjobs.com").isEmpty());
    }
}
