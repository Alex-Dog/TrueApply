package com.trueapply.ai;

/**
 * Vendor-neutral request. Keep this small: anything vendor-specific belongs inside the
 * provider implementation.
 *
 * @param system    instructions / persona
 * @param prompt    the user turn
 * @param effort    how hard the model should think; providers map this to their own knob
 * @param maxTokens upper bound on the response length
 */
public record AiRequest(String system, String prompt, Effort effort, int maxTokens) {

    public enum Effort { LOW, MEDIUM, HIGH }

    public static AiRequest of(String system, String prompt) {
        return new AiRequest(system, prompt, Effort.MEDIUM, 16_000);
    }

    public AiRequest withEffort(Effort value) {
        return new AiRequest(system, prompt, value, maxTokens);
    }
}
