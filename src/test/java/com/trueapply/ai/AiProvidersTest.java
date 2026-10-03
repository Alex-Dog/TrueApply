package com.trueapply.ai;

import com.trueapply.db.Database;
import com.trueapply.db.SettingsRepository;
import com.trueapply.settings.AppSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiProvidersTest {

    @Test
    void userChoiceSelectsTheProvider(@TempDir Path dir) {
        try (Database db = new Database(dir.resolve("t.db"))) {
            AppSettings settings = new AppSettings(new SettingsRepository(db));

            assertEquals("anthropic", AiProviders.selectedId(settings)); // developer default

            settings.setAiProvider("openai");
            settings.setAiApiKey("openai", "sk-test");
            assertEquals("openai", AiProviders.selectedId(settings));
            assertTrue(AiProviders.create(settings).describe().startsWith("OpenAI"));

            settings.setAiProvider("no-such-vendor");
            assertEquals("anthropic", AiProviders.selectedId(settings));
        }
    }

    @Test
    void userCanPickAModelPerProvider(@TempDir Path dir) {
        try (Database db = new Database(dir.resolve("t.db"))) {
            AppSettings settings = new AppSettings(new SettingsRepository(db));
            assertEquals("claude-opus-5-5", AiProviders.selectedModel(settings, "anthropic"));
            assertEquals("gpt-5.5", AiProviders.selectedModel(settings, "openai"));

            settings.setAiProvider("openai");
            settings.setAiApiKey("openai", "sk-test");
            settings.setAiModel("openai", "gpt-5.4-mini");
            settings.setAiModel("anthropic", "claude-sonnet-5-5");
            assertEquals("OpenAI (gpt-5.4-mini)", AiProviders.create(settings).describe());
            assertEquals("claude-sonnet-5-5", AiProviders.selectedModel(settings, "anthropic"));

            settings.setAiModel("openai", "my-fine-tune"); // free-typed ids are allowed
            assertEquals("OpenAI (my-fine-tune)", AiProviders.create(settings).describe());
        }
    }

    @Test
    void legacyAnthropicKeyIsStillRead(@TempDir Path dir) {
        try (Database db = new Database(dir.resolve("t.db"))) {
            SettingsRepository repo = new SettingsRepository(db);
            repo.put("ai.apiKey", "sk-ant-old");
            assertEquals("sk-ant-old", new AppSettings(repo).aiApiKey("anthropic"));
        }
    }
}
