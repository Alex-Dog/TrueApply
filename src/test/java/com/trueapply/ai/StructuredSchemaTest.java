package com.trueapply.ai;

import com.anthropic.models.messages.MessageCreateParams;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.trueapply.ai.tasks.FieldDecisions;
import com.trueapply.ai.tasks.ResumeExtraction;
import com.trueapply.model.JobOverview;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/** Both SDKs derive and locally validate a JSON schema from our output records; no network involved. */
class StructuredSchemaTest {

    @ParameterizedTest
    @ValueSource(classes = {FieldDecisions.class, ResumeExtraction.class, JobOverview.class})
    void outputTypesAreAcceptedByEveryProvider(Class<?> type) {
        assertDoesNotThrow(() -> MessageCreateParams.builder()
                .model("claude-opus-5-5").maxTokens(1).addUserMessage("x").outputConfig(type).build());
        assertDoesNotThrow(() -> ChatCompletionCreateParams.builder()
                .model("gpt-5.5").addUserMessage("x").responseFormat(type).build());
    }
}
