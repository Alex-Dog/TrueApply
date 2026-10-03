package com.trueapply.ai;

/**
 * The only door to an LLM in the app. Everything else (resume parsing, form answering,
 * job summaries) talks to this interface, so switching vendors means writing one new
 * implementation and registering it in {@link AiProviders}.
 */
public interface AiProvider {

    /** Human-readable provider/model, e.g. "Anthropic (claude-opus-5-5)". */
    String describe();

    /** Free-form text completion. */
    String generateText(AiRequest request);

    /**
     * Completion constrained to JSON matching {@code type} (a record or simple POJO), returned
     * already deserialized. Implementations should use the vendor's native structured-output
     * mode where available.
     */
    <T> T generateStructured(AiRequest request, Class<T> type);
}
