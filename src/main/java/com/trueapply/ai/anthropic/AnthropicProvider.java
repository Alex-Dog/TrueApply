package com.trueapply.ai.anthropic;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.UnauthorizedException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.StructuredMessage;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.anthropic.models.messages.StructuredOutputConfig;
import com.anthropic.models.messages.TextBlock;
import com.trueapply.ai.AiConfig;
import com.trueapply.ai.AiException;
import com.trueapply.ai.AiProvider;
import com.trueapply.ai.AiRequest;
import com.trueapply.util.Text;

import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/** {@link AiProvider} backed by the official Anthropic Java SDK. */
public final class AnthropicProvider implements AiProvider {
    private static final String DEFAULT_MODEL = "claude-opus-5-5";
    /** Lets the API retry a safety-classifier refusal on a suitable fallback model. */
    private static final String FALLBACK_BETA = "server-side-fallback-2026-07-01";
    private static final Set<String> FALLBACK_MODELS =
            Set.of("claude-fable-5-1", "claude-opus-5-5", "claude-opus-5", "claude-sonnet-5-5");

    private final AnthropicClient client;
    private final String model;

    public AnthropicProvider(AiConfig config) {
        this.model = Text.isBlank(config.model()) ? DEFAULT_MODEL : config.model();
        this.client = AnthropicOkHttpClient.builder().apiKey(config.apiKey()).build();
    }

    @Override
    public String describe() {
        return "Anthropic (" + model + ")";
    }

    @Override
    public String generateText(AiRequest request) {
        MessageCreateParams params = textParams(model, request);
        Message message = call(() -> client.messages().create(params));
        checkStop(message.stopReason());
        return message.content().stream()
                .flatMap(block -> block.text().stream())
                .map(TextBlock::text)
                .collect(Collectors.joining());
    }

    @Override
    public <T> T generateStructured(AiRequest request, Class<T> type) {
        StructuredMessageCreateParams<T> params = structuredParams(model, request, type);
        StructuredMessage<T> message = call(() -> client.messages().create(params));
        checkStop(message.stopReason());
        return message.content().stream()
                .flatMap(block -> block.text().stream())
                .map(typed -> typed.text())
                .findFirst()
                .orElseThrow(() -> new AiException("The AI returned no structured output."));
    }

    static MessageCreateParams textParams(String model, AiRequest request) {
        MessageCreateParams.Builder builder = base(model, request);
        if (supportsEffort(model)) builder.outputConfig(OutputConfig.builder().effort(effort(request)).build());
        return builder.build();
    }

    static <T> StructuredMessageCreateParams<T> structuredParams(String model, AiRequest request, Class<T> type) {
        StructuredOutputConfig.Builder<T> config = StructuredOutputConfig.<T>builder().format(type);
        if (supportsEffort(model)) config.effort(effort(request));
        return base(model, request).outputConfig(config.build()).build();
    }

    private static MessageCreateParams.Builder base(String model, AiRequest request) {
        MessageCreateParams.Builder builder = MessageCreateParams.builder()
                .model(model)
                .maxTokens(request.maxTokens())
                .system(request.system())
                .addUserMessage(request.prompt());
        if (supportsFallbacks(model)) {
            builder.putAdditionalHeader("anthropic-beta", FALLBACK_BETA)
                    .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"));
        }
        return builder;
    }

    /** Haiku 4.5 (and older models) reject the effort parameter. */
    static boolean supportsEffort(String model) {
        return !model.startsWith("claude-haiku");
    }

    /** Server-side refusal fallbacks exist for the newest models only. */
    static boolean supportsFallbacks(String model) {
        return FALLBACK_MODELS.contains(model);
    }

    private static OutputConfig.Effort effort(AiRequest request) {
        return switch (request.effort()) {
            case LOW -> OutputConfig.Effort.LOW;
            case MEDIUM -> OutputConfig.Effort.MEDIUM;
            case HIGH -> OutputConfig.Effort.HIGH;
        };
    }

    private static void checkStop(Optional<StopReason> stopReason) {
        if (stopReason.isEmpty()) return;
        StopReason reason = stopReason.get();
        if (reason.equals(StopReason.REFUSAL)) {
            throw new AiException("The AI declined this request.");
        }
        if (reason.equals(StopReason.MAX_TOKENS)) {
            throw new AiException("The AI response was cut off (max tokens reached).");
        }
    }

    private static <R> R call(Supplier<R> request) {
        try {
            return request.get();
        } catch (UnauthorizedException e) {
            throw new AiException("The Anthropic API key was rejected. Check Settings → AI.", e);
        } catch (RateLimitException e) {
            throw new AiException("Anthropic rate limit hit; try again in a minute.", e);
        } catch (AnthropicServiceException e) {
            throw new AiException("Anthropic API error (HTTP " + e.statusCode() + "): " + e.getMessage(), e);
        } catch (RuntimeException e) {
            throw new AiException("Could not reach the Anthropic API: " + e.getMessage(), e);
        }
    }
}
