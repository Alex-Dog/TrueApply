package com.trueapply.ai.anthropic;

import com.anthropic.models.messages.MessageCreateParams;
import com.trueapply.ai.AiRequest;
import com.trueapply.ai.tasks.FieldDecisions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnthropicProviderTest {
    private static final AiRequest REQUEST = AiRequest.of("system", "prompt");

    @Test
    void newerModelsGetEffortAndRefusalFallbacks() {
        MessageCreateParams opus = AnthropicProvider.textParams("claude-opus-5-5", REQUEST);
        assertTrue(opus.outputConfig().flatMap(c -> c.effort()).isPresent());
        assertTrue(opus._additionalBodyProperties().containsKey("fallbacks"));

        MessageCreateParams structured = AnthropicProvider
                .structuredParams("claude-sonnet-5-5", REQUEST, FieldDecisions.class).rawParams();
        assertTrue(structured.outputConfig().flatMap(c -> c.effort()).isPresent());
        assertTrue(structured._additionalBodyProperties().containsKey("fallbacks"));
    }

    @Test
    void haikuSkipsTheParametersItRejects() {
        MessageCreateParams text = AnthropicProvider.textParams("claude-haiku-4-5", REQUEST);
        assertTrue(text.outputConfig().isEmpty());
        assertFalse(text._additionalBodyProperties().containsKey("fallbacks"));

        MessageCreateParams structured = AnthropicProvider
                .structuredParams("claude-haiku-4-5", REQUEST, FieldDecisions.class).rawParams();
        assertTrue(structured.outputConfig().flatMap(c -> c.effort()).isEmpty());
        assertTrue(structured.outputConfig().flatMap(c -> c.format()).isPresent());
    }
}
