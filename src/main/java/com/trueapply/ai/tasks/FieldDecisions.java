package com.trueapply.ai.tasks;

import java.util.List;

/** Structured-output schema for {@link FormAnswerer}. */
public record FieldDecisions(List<FieldDecision> fields) {

    /**
     * @param key             the field key from the request, copied exactly
     * @param category        FACTUAL, DEMOGRAPHIC, CREATIVE, or MISSING_INFO
     * @param answer          text answer for text fields; empty otherwise or when not answering
     * @param selectedOptions exact option strings for select fields; empty when not answering
     * @param note            one short sentence: where the answer came from or what is missing
     */
    public record FieldDecision(
            String key,
            String category,
            String answer,
            List<String> selectedOptions,
            String note) {
    }
}
