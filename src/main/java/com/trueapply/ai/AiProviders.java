package com.trueapply.ai;

import com.trueapply.ai.anthropic.AnthropicProvider;
import com.trueapply.ai.openai.OpenAiProvider;
import com.trueapply.settings.AppSettings;
import com.trueapply.util.Text;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Function;

/**
 * Builds the {@link AiProvider} the user selected. To add a vendor:
 * <ol>
 *   <li>implement {@link AiProvider} (see {@link AnthropicProvider} / {@link OpenAiProvider}),</li>
 *   <li>add it to {@link #REGISTRY},</li>
 *   <li>add {@code ai.<id>.model} and {@code ai.<id>.apiKeyEnv} to {@code trueapply.properties}.</li>
 * </ol>
 * It then shows up in Settings → AI automatically.
 */
public final class AiProviders {

    /** What the UI needs to offer a provider. The first model is the fallback default. */
    public record ProviderInfo(String id, String displayName, String keyHint, List<ModelOption> models) {
        @Override
        public String toString() {
            return displayName;
        }

        public Optional<ModelOption> model(String modelId) {
            return models.stream().filter(m -> m.id().equals(modelId)).findFirst();
        }
    }

    /** A model offered in Settings. Users can also type any other model id. */
    public record ModelOption(String id, String label, String note) {
    }

    private record Entry(ProviderInfo info, Function<AiConfig, AiProvider> factory) {
    }

    private static final Map<String, Entry> REGISTRY = new LinkedHashMap<>();

    static {
        register(new ProviderInfo("anthropic", "Anthropic (Claude)", "sk-ant-…", List.of(
                new ModelOption("claude-opus-5-5", "Opus 5.5", "Best balance of quality and cost (recommended)"),
                new ModelOption("claude-sonnet-5-5", "Sonnet 5.5", "Faster, about half the price of Opus"),
                new ModelOption("claude-haiku-4-5", "Haiku 4.5", "Fastest and cheapest; less careful on tricky forms"),
                new ModelOption("claude-fable-5-1", "Fable 5.1", "Most capable, about 2.5x the price of Opus"))),
                AnthropicProvider::new);
        register(new ProviderInfo("openai", "OpenAI", "sk-…", List.of(
                new ModelOption("gpt-5.5", "GPT-5.5", "Flagship model (recommended)"),
                new ModelOption("gpt-5.4", "GPT-5.4", "Previous flagship"),
                new ModelOption("gpt-5.4-mini", "GPT-5.4 mini", "Faster and cheaper"),
                new ModelOption("gpt-5.4-nano", "GPT-5.4 nano", "Fastest and cheapest; least careful"))),
                OpenAiProvider::new);
    }

    private static final Properties PROPS = loadProperties();

    private AiProviders() {
    }

    private static void register(ProviderInfo info, Function<AiConfig, AiProvider> factory) {
        REGISTRY.put(info.id(), new Entry(info, factory));
    }

    public static List<ProviderInfo> available() {
        return REGISTRY.values().stream().map(Entry::info).toList();
    }

    public static ProviderInfo info(String id) {
        Entry entry = REGISTRY.get(id);
        return entry == null ? REGISTRY.values().iterator().next().info() : entry.info();
    }

    /** The user's choice if valid, else the developer default from trueapply.properties. */
    public static String selectedId(AppSettings settings) {
        String chosen = settings.aiProvider();
        if (REGISTRY.containsKey(chosen)) return chosen;
        String fallback = PROPS.getProperty("ai.provider", "anthropic").trim();
        return REGISTRY.containsKey(fallback) ? fallback : REGISTRY.keySet().iterator().next();
    }

    /** The developer default for a provider (trueapply.properties), else its first listed model. */
    public static String defaultModel(String id) {
        String configured = PROPS.getProperty("ai." + id + ".model", "").trim();
        return configured.isEmpty() ? info(id).models().getFirst().id() : configured;
    }

    /** The user's model choice for a provider, else the default. */
    public static String selectedModel(AppSettings settings, String id) {
        String chosen = settings.aiModel(id);
        return Text.isBlank(chosen) ? defaultModel(id) : chosen.trim();
    }

    /** True when the developer supplied this provider's key through the environment. */
    public static boolean keyFromEnvironment(String id) {
        return !Text.isBlank(envKey(id));
    }

    /**
     * Returns the selected provider, or a placeholder that throws a friendly error on use
     * when no API key is configured yet (so the app still starts on first boot).
     */
    public static AiProvider create(AppSettings settings) {
        String id = selectedId(settings);
        String apiKey = envKey(id);
        if (Text.isBlank(apiKey)) apiKey = settings.aiApiKey(id);
        if (Text.isBlank(apiKey)) return new MissingKeyProvider(info(id).displayName());
        return REGISTRY.get(id).factory().apply(new AiConfig(id, selectedModel(settings, id), apiKey.trim()));
    }

    private static String envKey(String id) {
        String envVar = PROPS.getProperty("ai." + id + ".apiKeyEnv", "").trim();
        return envVar.isEmpty() ? null : System.getenv(envVar);
    }

    private static Properties loadProperties() {
        Properties props = new Properties();
        try (InputStream in = AiProviders.class.getResourceAsStream("/trueapply.properties")) {
            if (in != null) props.load(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return props;
    }

    private record MissingKeyProvider(String name) implements AiProvider {
        @Override
        public String describe() {
            return name + " (no API key)";
        }

        @Override
        public String generateText(AiRequest request) {
            throw missing();
        }

        @Override
        public <T> T generateStructured(AiRequest request, Class<T> type) {
            throw missing();
        }

        private AiException missing() {
            return new AiException("No " + name + " API key configured. Add one in Settings → AI.");
        }
    }
}
