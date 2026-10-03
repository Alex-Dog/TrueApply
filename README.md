# TrueApply

A desktop app that applies to jobs for you without writing slop. The AI does the
busywork: it finds postings, reads the forms, and fills in factual fields like your
name, school, experience, and work authorization. It never writes creative answers.
When a form asks "Why do you want to work at XYZ?" or wants a cover letter, the whole
application waits in your **Inbox** until you answer in your own words. Then the rest
is filled in and submitted.

Java 25 · JavaFX 27 · AtlantaFX · Playwright · SQLite · Claude or OpenAI

## Run

```bash
./mvnw compile exec:exec        # macOS/Linux
mvnw.cmd compile exec:exec      # Windows
./mvnw test
```

No separate Maven install is needed; the wrapper downloads it. On first launch a setup
wizard asks for your resume, profile, demographics, and job preferences.

**Dry run is on by default.** Forms get filled but Submit is never clicked. Turn it off
in Settings → Applying when you're ready to send real applications.

### Keys and credentials

| What | Where |
|---|---|
| AI provider + key | Pick **Anthropic** or **OpenAI** in Settings → AI and paste the key there, or set `ANTHROPIC_API_KEY` / `OPENAI_API_KEY` |
| Adzuna (optional discovery source) | App ID and key from developer.adzuna.com → Settings |
| Gmail (optional, reads emailed security codes) | Google Cloud OAuth *Desktop* client JSON saved as `%APPDATA%\TrueApply\google-credentials.json` (or `src/main/resources/google-credentials.json`, which is gitignored) |

App data (database, browser profile, resumes) lives in `%APPDATA%\TrueApply`, or
`~/.trueapply` on macOS/Linux. To use a scratch folder instead, pass `-Dtrueapply.home=<dir>`.

## How it works

```
Discover (Greenhouse boards + Adzuna)
   → Analyze: Greenhouse Job Board API returns every question as JSON
   → FormAnswerer (AI) sorts each field: FACTUAL / DEMOGRAPHIC / CREATIVE / MISSING_INFO
       └ CreativeGuard (regex backstop) forces essay-style questions to CREATIVE no matter what
   → any creative or missing required field?  yes → Inbox (you answer)  ─┐
                                               no ─────────────────────────┴→ Submit
   → Playwright fills the hosted form (installed Edge/Chrome, no download) and records
     every question + answer in History
```

## Code map

| Package | What's in it |
|---|---|
| `ai` | `AiProvider`, the only door to an LLM. Implemented by `anthropic/AnthropicProvider` and `openai/OpenAiProvider`. |
| `ai/tasks` | Resume extraction, form answering + `CreativeGuard`, and job summaries. They only use `AiProvider`. |
| `ats` | `ApplicationPlatform` (one per ATS) + `PlatformRegistry`. |
| `ats/greenhouse` | API client, JSON → `FormField` parser, Playwright form filler |
| `discovery` | `JobSource` implementations (Greenhouse, Adzuna) + preference filter |
| `pipeline` | `ApplicationPipeline`, the application state machine (single worker thread) |
| `email` | Gmail OAuth + verification-code extraction |
| `db`, `security` | SQLite repositories and the AES-GCM vault for saved site passwords |
| `ui` | JavaFX views, onboarding wizard, profile forms |

### AI providers

Users pick a provider and a model in Settings → AI. The model list is in `AiProviders`
(Opus / Sonnet / Haiku / Fable, GPT-5.5 / 5.4 / mini / nano), and any other model ID can
be typed in. To add another provider:

1. Implement `com.trueapply.ai.AiProvider` (`generateText` and `generateStructured`).
2. Register it in the static block of `AiProviders`. It then appears in the Settings dropdown.
3. Add `ai.<id>.model` and `ai.<id>.apiKeyEnv` to `src/main/resources/trueapply.properties`.

`ai.provider` in that file is only the default until the user chooses. Nothing outside
`ai/` imports a vendor SDK. `StructuredSchemaTest` checks that every output record is
accepted by every SDK's structured-output mode.

### Adding Lever / Ashby

1. Add an `ApplicationPlatform` implementation under `ats/lever` (or `ats/ashby`) and
   register it in `AppContext`.
2. Teach discovery to tag those jobs with `AtsType.LEVER` / `ASHBY`.

`AtsType` already has both values. The pipeline, inbox, and history are platform-agnostic.

### Debugging the form filler

With dry run on, set `-Dtrueapply.debugScreenshot=filled.png` to save a full-page
screenshot of the filled form.
