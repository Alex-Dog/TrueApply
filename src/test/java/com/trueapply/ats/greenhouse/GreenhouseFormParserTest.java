package com.trueapply.ats.greenhouse;

import com.fasterxml.jackson.databind.JsonNode;
import com.trueapply.model.AnswerSource;
import com.trueapply.model.FieldCategory;
import com.trueapply.model.FieldType;
import com.trueapply.model.FormField;
import com.trueapply.model.UserProfile;
import com.trueapply.util.Json;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GreenhouseFormParserTest {

    private static JsonNode fixture() throws Exception {
        try (InputStream in = GreenhouseFormParserTest.class.getResourceAsStream("/greenhouse/discord-job.json")) {
            return Json.MAPPER.readTree(in);
        }
    }

    private static FormField byKey(List<FormField> fields, String key) {
        return fields.stream().filter(f -> f.key.equals(key)).findFirst().orElseThrow(() -> new AssertionError(key));
    }

    @Test
    void parsesQuestionsWithDomIdsAndPrefillsProfile() throws Exception {
        UserProfile profile = new UserProfile();
        profile.personal.firstName = "Ada";
        profile.personal.lastName = "Lovelace";
        profile.personal.email = "ada@example.com";
        profile.personal.phone = "555-0100";
        profile.personal.city = "San Francisco";
        profile.personal.state = "CA";
        profile.resumePath = "C:/resume.pdf";

        List<FormField> fields = GreenhouseFormParser.parse(fixture(), profile);

        FormField first = byKey(fields, "first_name");
        assertEquals("Ada", first.answer);
        assertEquals(FieldCategory.PROFILE, first.category);
        assertEquals(AnswerSource.PROFILE, first.source);
        assertEquals("C:/resume.pdf", byKey(fields, "resume").answer);
        assertEquals("San Francisco, CA", byKey(fields, "location").answer);
        assertEquals(FieldType.LOCATION, byKey(fields, "location").type);

        // resume_text and the cover letter file input are replaced by our own handling
        assertTrue(fields.stream().noneMatch(f -> f.key.equals("resume_text") || f.key.equals("cover_letter")));
        assertEquals(FieldType.TEXTAREA, byKey(fields, "cover_letter_text").type);

        // optional preferred name with nothing in the profile is skipped, not asked
        assertEquals(FieldCategory.SKIPPED, byKey(fields, "preferred_name").category);
    }

    @Test
    void addsHispanicQuestionBeforeRaceAndMarksSelfIdAsDemographic() throws Exception {
        List<FormField> fields = GreenhouseFormParser.parse(fixture(), new UserProfile());
        int hispanic = fields.indexOf(byKey(fields, "hispanic_ethnicity"));
        int race = fields.indexOf(byKey(fields, "race"));
        assertTrue(hispanic >= 0 && hispanic < race);
        assertEquals(FieldCategory.DEMOGRAPHIC, byKey(fields, "veteran_status").category);

        FormField gender = byKey(fields, "4033064002"); // demographic_questions use numeric ids
        assertEquals(FieldType.SINGLE_SELECT, gender.type);
        assertTrue(gender.options.contains("I don't wish to answer"));
        assertEquals(FieldType.MULTI_SELECT, byKey(fields, "4033069002").type);
    }

    @Test
    void selectQuestionsCarryTheirOptions() throws Exception {
        List<FormField> fields = GreenhouseFormParser.parse(fixture(), new UserProfile());
        FormField authorized = byKey(fields, "question_38318892002");
        assertEquals(List.of("Yes", "No"), authorized.options);
        assertTrue(authorized.required);
        assertFalse(byKey(fields, "question_38318889002").required); // LinkedIn
    }
}
