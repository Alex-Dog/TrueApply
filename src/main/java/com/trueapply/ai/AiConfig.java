package com.trueapply.ai;

/** What a provider needs to start. {@code model} is vendor-specific and opaque to the rest of the app. */
public record AiConfig(String provider, String model, String apiKey) {
}
