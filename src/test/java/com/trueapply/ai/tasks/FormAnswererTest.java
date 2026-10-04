package com.trueapply.ai.tasks;

import com.trueapply.ai.AiProvider;
import com.trueapply.ai.AiRequest;
import com.trueapply.model.AnswerSource;
import com.trueapply.model.FieldCategory;
import com.trueapply.model.FieldType;
import com.trueapply.model.FormField;
import com.trueapply.model.UserProfile;
import com.trueapply.util.Text;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FormAnswererTest {

    /** Returns canned decisions so we can check the guardrails around the AI. */
    private record FakeAi(FieldDecisions decisions, List<AiRequest> calls) implements AiProvider {
        FakeAi(FieldDecisions decisions) {
            this(decisions, new ArrayList<>());
        }

        @Override
        public String describe() {
            return "fake";
        }

        @Override
        public String generateText(AiRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> T generateStructured(AiRequest request, Class<T> type) {
            calls.add(request);
            return type.cast(decisions);
        }
    }

    private static FieldDecisions.FieldDecision decision(String key, String category, String answer, String... options) {
        return new FieldDecisions.FieldDecision(key, category, answer, List.of(options), "note");
    }

    @Test
    void creativeTextareaIsNeverAnsweredEvenIfTheAiTries() {
        FormField why = new FormField("q1", "Why do you want to work at Discord?", FieldType.TEXTAREA, true);
        FakeAi ai = new FakeAi(new FieldDecisions(List.of()));

        new FormAnswerer(ai).answer(List.of(why), new UserProfile(), null);

        assertEquals(FieldCategory.CREATIVE, why.category);
        assertNull(why.answer);
        assertTrue(ai.calls().isEmpty(), "guard should catch it before spending an AI call");

        FormField sneaky = new FormField("q2", "Additional notes", FieldType.TEXTAREA, false);
        FormAnswerer.apply(sneaky, decision("q2", "FACTUAL", "I am passionate about…"));
        assertEquals(FieldCategory.FACTUAL, sneaky.category); // label alone doesn't look creative

        FormField coverLetter = new FormField("cover_letter_text", "Cover Letter", FieldType.TEXTAREA, false);
        FormAnswerer.apply(coverLetter, decision("cover_letter_text", "FACTUAL", "Dear hiring manager…"));
        assertEquals(FieldCategory.CREATIVE, coverLetter.category);
        assertNull(coverLetter.answer);
    }

    @Test
    void selectAnswersMustMatchAnOptionExactly() {
        FormField auth = new FormField("q", "Are you authorized to work in the US?", FieldType.SINGLE_SELECT, true);
        auth.options = List.of("Yes", "No");

        FormAnswerer.apply(auth, decision("q", "FACTUAL", "", "yes"));
        assertEquals("Yes", auth.answer); // normalized match, canonical spelling
        assertEquals(AnswerSource.AI, auth.source);

        FormField bad = new FormField("q", "Veteran?", FieldType.SINGLE_SELECT, true);
        bad.options = List.of("I am not a protected veteran", "I don't wish to answer");
        FormAnswerer.apply(bad, decision("q", "DEMOGRAPHIC", "", "Not a veteran"));
        assertEquals(FieldCategory.MISSING_INFO, bad.category);
        assertNull(bad.answer);
    }

    @Test
    void curlyApostrophesStillMatch() {
        FormField f = new FormField("q", "Disability", FieldType.SINGLE_SELECT, true);
        f.options = List.of("I don’t wish to answer");
        FormAnswerer.apply(f, decision("q", "DEMOGRAPHIC", "", "I don't wish to answer"));
        assertEquals("I don’t wish to answer", f.answer);
    }

    @Test
    void savedAnswersAreReusedWithoutCallingTheAi() {
        UserProfile profile = new UserProfile();
        profile.savedAnswers.put(Text.normalize("How did you hear about this job?"), "LinkedIn");
        FormField source = new FormField("q", "How did you hear about this job?", FieldType.TEXT, false);
        FakeAi ai = new FakeAi(new FieldDecisions(List.of()));

        new FormAnswerer(ai).answer(List.of(source), profile, null);

        assertEquals("LinkedIn", source.answer);
        assertEquals(AnswerSource.SAVED_ANSWER, source.source);
        assertTrue(ai.calls().isEmpty());
    }

    @Test
    void onlyRequiredOrOptedInCreativeQuestionsHoldAnApplication() {
        FormField addressLine2 = new FormField("a2", "Address Line 2", FieldType.TEXT, false);
        addressLine2.category = FieldCategory.MISSING_INFO;
        FormField optionalEssay = new FormField("e", "Anything else?", FieldType.TEXTAREA, false);
        optionalEssay.category = FieldCategory.CREATIVE;
        FormField requiredSource = new FormField("s", "How did you hear about us?", FieldType.TEXT, true);
        requiredSource.category = FieldCategory.MISSING_INFO;

        // "Also hold for optional creative questions" must not drag optional missing info along.
        assertTrue(!com.trueapply.model.JobApplication.blocksSubmission(addressLine2, true));
        assertTrue(com.trueapply.model.JobApplication.blocksSubmission(optionalEssay, true));
        assertTrue(!com.trueapply.model.JobApplication.blocksSubmission(optionalEssay, false));
        assertTrue(com.trueapply.model.JobApplication.blocksSubmission(requiredSource, false));
        requiredSource.answer = "LinkedIn";
        assertTrue(!com.trueapply.model.JobApplication.blocksSubmission(requiredSource, false));
    }

    @Test
    void fieldsTheAiSkipsBecomeMissingInfo() {
        FormField salary = new FormField("q9", "Desired salary", FieldType.TEXT, true);
        new FormAnswerer(new FakeAi(new FieldDecisions(List.of()))).answer(List.of(salary), new UserProfile(), null);
        assertEquals(FieldCategory.MISSING_INFO, salary.category);
    }
}
