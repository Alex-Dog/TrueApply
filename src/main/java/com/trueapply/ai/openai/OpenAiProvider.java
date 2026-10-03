package com.trueapply.ai.openai;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.errors.OpenAIServiceException;
import com.openai.errors.RateLimitException;
import com.openai.errors.UnauthorizedException;
import com.openai.models.ReasoningEffort;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.StructuredChatCompletion;
import com.openai.models.chat.completions.StructuredChatCompletionCreateParams;
import com.trueapply.ai.AiConfig;
import com.trueapply.ai.AiException;
import com.trueapply.ai.AiProvider;
import com.trueapply.ai.AiRequest;
import com.trueapply.util.Text;

import java.util.Optional;
import java.util.function.Supplier;

/** {@link AiProvider} backed by the official OpenAI Java SDK (Chat Completions + structured outputs). */
public final class OpenAiProvider implements AiProvider {
    private static final String DEFAULT_MODEL = "gpt-5.5";

    private final OpenAIClient client;
    private final String model;

    public OpenAiProvider(AiConfig config) {
        this.model = Text.isBlank(config.model()) ? DEFAULT_MODEL : config.model();
        this.client = OpenAIOkHttpClient.builder().apiKey(config.apiKey()).build();
    }

    @Override
    public String describe() {
        return "OpenAI (" + model + ")";
    }

    @Override
    public String generateText(AiRequest request) {
        ChatCompletionCreateParams params = base(request).build();
        ChatCompletion completion = call(() -> client.chat().completions().create(params));
        if (completion.choices().isEmpty()) throw new AiException("The AI returned no response.");
        ChatCompletion.Choice choice = completion.choices().getFirst();
        checkFinish(choice.finishReason(), choice.message().refusal());
        return choice.message().content().orElse("");
    }

    @Override
    public <T> T generateStructured(AiRequest request, Class<T> type) {
        StructuredChatCompletionCreateParams<T> params = base(request).responseFormat(type).build();
        StructuredChatCompletion<T> completion = call(() -> client.chat().completions().create(params));
        if (completion.choices().isEmpty()) throw new AiException("The AI returned no response.");
        StructuredChatCompletion.Choice<T> choice = completion.choices().getFirst();
        checkFinish(choice.finishReason(), choice.message().refusal());
        return choice.message().content()
                .orElseThrow(() -> new AiException("The AI returned no structured output."));
    }

    private ChatCompletionCreateParams.Builder base(AiRequest request) {
        return ChatCompletionCreateParams.builder()
                .model(model)
                .maxCompletionTokens(request.maxTokens())
                .reasoningEffort(effort(request))
                .addDeveloperMessage(request.system())
                .addUserMessage(request.prompt());
    }

    private static ReasoningEffort effort(AiRequest request) {
        return switch (request.effort()) {
            case LOW -> ReasoningEffort.LOW;
            case MEDIUM -> ReasoningEffort.MEDIUM;
            case HIGH -> ReasoningEffort.HIGH;
        };
    }

    private static void checkFinish(ChatCompletion.Choice.FinishReason reason, Optional<String> refusal) {
        if (refusal.isPresent()) throw new AiException("The AI declined this request: " + refusal.get());
        if (reason.equals(ChatCompletion.Choice.FinishReason.CONTENT_FILTER)) {
            throw new AiException("The AI declined this request (content filter).");
        }
        if (reason.equals(ChatCompletion.Choice.FinishReason.LENGTH)) {
            throw new AiException("The AI response was cut off (max tokens reached).");
        }
    }

    private static <R> R call(Supplier<R> request) {
        try {
            return request.get();
        } catch (UnauthorizedException e) {
            throw new AiException("The OpenAI API key was rejected. Check Settings → AI.", e);
        } catch (RateLimitException e) {
            throw new AiException("OpenAI rate limit hit; try again in a minute.", e);
        } catch (OpenAIServiceException e) {
            throw new AiException("OpenAI API error (HTTP " + e.statusCode() + "): " + e.getMessage(), e);
        } catch (AiException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new AiException("Could not reach the OpenAI API: " + e.getMessage(), e);
        }
    }
}
