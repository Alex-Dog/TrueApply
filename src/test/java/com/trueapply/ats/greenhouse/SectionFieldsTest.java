package com.trueapply.ats.greenhouse;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.trueapply.model.FieldCategory;
import com.trueapply.model.FormField;
import com.trueapply.model.UserProfile;
import com.trueapply.util.Json;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SectionFieldsTest {

    private static ObjectNode fixture() throws Exception {
        try (InputStream in = SectionFieldsTest.class.getResourceAsStream("/greenhouse/discord-job.json")) {
            return (ObjectNode) Json.MAPPER.readTree(in);
        }
    }

    private static FormField byKey(List<FormField> fields, String key) {
        return fields.stream().filter(f -> f.key.equals(key)).findFirst().orElseThrow(() -> new AssertionError(key));
    }

    @Test
    void educationAndEmploymentSectionsComeFromTheProfile() throws Exception {
        ObjectNode job = fixture();
        job.put("education", "education_required");
        job.put("employment", "employment_required");

        UserProfile profile = new UserProfile();
        UserProfile.Education ed = new UserProfile.Education();
        ed.school = "Stanford University";
        ed.degree = "B.S.";
        ed.fieldOfStudy = "Computer Science";
        ed.startDate = "Sep 2019";
        ed.endDate = "Jun 2023";
        profile.education.add(ed);
        UserProfile.WorkExperience w = new UserProfile.WorkExperience();
        w.company = "Acme";
        w.title = "Engineer";
        w.startDate = "Jul 2023";
        w.current = true;
        profile.experience.add(w);

        List<FormField> fields = GreenhouseFormParser.parse(job, profile);
        assertEquals("Stanford University", byKey(fields, "school--0").answer);
        assertEquals("2023", byKey(fields, "end-year--0").answer);
        assertEquals("June", byKey(fields, "end-month--0").answer);
        assertEquals("July", byKey(fields, "start-date-month-0").answer);
        assertEquals("Yes", byKey(fields, "current-role-0").answer);
        assertEquals(FieldCategory.SKIPPED, byKey(fields, "end-date-month-0").category);
        assertEquals(FieldCategory.PROFILE, byKey(fields, "degree--0").category);
    }

    @Test
    void everySchoolGetsItsOwnEntry() throws Exception {
        ObjectNode job = fixture();
        job.put("education", "education_required");
        UserProfile profile = new UserProfile();
        for (String school : List.of("University of Michigan", "Washtenaw Community College")) {
            UserProfile.Education ed = new UserProfile.Education();
            ed.school = school;
            ed.startDate = "Jan 2023";
            profile.education.add(ed);
        }
        List<FormField> fields = GreenhouseFormParser.parse(job, profile);
        assertEquals("University of Michigan", byKey(fields, "school--0").answer);
        assertEquals("Washtenaw Community College", byKey(fields, "school--1").answer);
        assertEquals("January", byKey(fields, "start-month--1").answer);
        assertTrue(byKey(fields, "school--0").required);
        assertEquals(false, byKey(fields, "school--1").required); // extra entries never block
        assertEquals(FieldCategory.SKIPPED, byKey(fields, "degree--1").category);
    }

    @Test
    void missingEducationIsAskedWhenRequired() throws Exception {
        ObjectNode job = fixture();
        job.put("education", "education_required");
        List<FormField> fields = GreenhouseFormParser.parse(job, new UserProfile());
        assertEquals(FieldCategory.MISSING_INFO, byKey(fields, "school--0").category);
        assertEquals(FieldCategory.SKIPPED, byKey(fields, "start-year--0").category);
        assertTrue(fields.stream().noneMatch(f -> f.key.startsWith("company-name")));
    }

    @Test
    void degreeNamesMapToEachCompanysWording() {
        List<String> dropbox = List.of("Associate", "Bachelor", "Doctoral", "Master",
                "Master of Business Administration", "Other");
        List<String> elastic = List.of("Associate's Degree", "Bachelor's Degree", "Doctor of Philosophy (Ph.D.)",
                "Master of Business Administration (M.B.A.)", "Master's Degree", "Other");

        assertEquals("Bachelor", pick(dropbox, "B.S. in Computer Science"));
        assertEquals("Bachelor's Degree", pick(elastic, "Bachelor of Arts"));
        assertEquals("Master's Degree", pick(elastic, "M.S."));
        assertEquals("Master of Business Administration", pick(dropbox, "MBA"));
        assertEquals("Doctor of Philosophy (Ph.D.)", pick(elastic, "PhD, Physics"));
        assertEquals("Doctoral", pick(dropbox, "Ph.D."));
        assertEquals("Other", pick(dropbox, "Some Certificate Program"));
    }

    @Test
    void optionMatchingToleratesPunctuationAndLongerValues() {
        assertEquals(0, GreenhouseFormFiller.bestMatch(List.of("University of California - Berkeley"),
                "University of California, Berkeley"));
        assertEquals(1, GreenhouseFormFiller.bestMatch(List.of("Engineering", "Computer Science", "Art"),
                "Computer Science and Engineering"));
    }

    private static String pick(List<String> options, String degree) {
        for (String term : OptionHints.degreeTerms(degree)) {
            int i = GreenhouseFormFiller.bestMatch(options, term);
            if (i >= 0) return options.get(i);
        }
        return null;
    }
}
