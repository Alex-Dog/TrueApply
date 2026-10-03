package com.trueapply.email;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.GmailScopes;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePart;
import com.trueapply.settings.AppSettings;
import com.trueapply.util.AppPaths;
import com.trueapply.util.Text;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Read-only Gmail access (OAuth "installed app" flow) used to pick up the security codes some
 * job sites email during submission. The developer supplies an OAuth desktop-client JSON, either
 * at {@link AppPaths#googleCredentials()} or as the classpath resource /google-credentials.json.
 */
public class GmailService implements VerificationCodeSource {
    private static final String USER = "user";
    private static final List<String> SCOPES = List.of(GmailScopes.GMAIL_READONLY);
    private static final JsonFactory JSON = GsonFactory.getDefaultInstance();

    private final AppSettings settings;

    public GmailService(AppSettings settings) {
        this.settings = settings;
    }

    /** True when OAuth client credentials are available, so "Connect Gmail" can work. */
    public boolean isConfigured() {
        return Files.isRegularFile(AppPaths.googleCredentials())
                || GmailService.class.getResource("/google-credentials.json") != null;
    }

    public boolean isConnected() {
        return !Text.isBlank(settings.gmailAccount());
    }

    /** Runs the consent flow in the system browser and returns the connected address. Blocking. */
    public String connect() throws IOException, GeneralSecurityException {
        NetHttpTransport transport = GoogleNetHttpTransport.newTrustedTransport();
        GoogleAuthorizationCodeFlow flow = flow(transport);
        LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(0).build();
        Credential credential = new AuthorizationCodeInstalledApp(flow, receiver).authorize(USER);
        String email = client(transport, credential).users().getProfile("me").execute().getEmailAddress();
        settings.setGmailAccount(email);
        return email;
    }

    public void disconnect() throws IOException {
        try (Stream<Path> files = Files.list(AppPaths.googleTokens())) {
            for (Path p : files.toList()) Files.deleteIfExists(p);
        }
        settings.setGmailAccount("");
    }

    @Override
    public Optional<String> waitForCode(Instant since, Duration timeout) {
        return poll(since, timeout, "(subject:code OR subject:verification OR subject:verify OR subject:security)",
                message -> {
                    Optional<String> code = VerificationCodes.extract(bodyText(message.getPayload(), true));
                    return code.isPresent() ? code : VerificationCodes.extract(message.getSnippet());
                });
    }

    @Override
    public Optional<String> waitForLink(Instant since, String host, Duration timeout) {
        // Links live in the HTML (href), so read bodies without stripping tags.
        return poll(since, timeout, "(verify OR verification OR activate OR confirm)",
                message -> VerificationCodes.extractLink(bodyText(message.getPayload(), false), host));
    }

    /** Checks recent matching mail every few seconds until {@code extractor} finds something. */
    private Optional<String> poll(Instant since, Duration timeout, String filter, Function<Message, Optional<String>> extractor) {
        if (!isConnected()) return Optional.empty();
        Instant deadline = Instant.now().plus(timeout);
        try {
            NetHttpTransport transport = GoogleNetHttpTransport.newTrustedTransport();
            Credential credential = flow(transport).loadCredential(USER);
            if (credential == null) return Optional.empty();
            Gmail gmail = client(transport, credential);
            // Gmail's after: filter has 1-second resolution; back off a little for clock skew.
            String query = "after:" + (since.getEpochSecond() - 60) + " " + filter;
            while (Instant.now().isBefore(deadline)) {
                Optional<String> found = search(gmail, query, since, extractor);
                if (found.isPresent()) return found;
                Thread.sleep(5_000);
            }
        } catch (IOException | GeneralSecurityException e) {
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return Optional.empty();
    }

    private static Optional<String> search(Gmail gmail, String query, Instant since,
                                           Function<Message, Optional<String>> extractor) throws IOException {
        List<Message> refs = gmail.users().messages().list("me").setQ(query).setMaxResults(5L).execute().getMessages();
        if (refs == null) return Optional.empty();
        List<Message> messages = new java.util.ArrayList<>();
        for (Message ref : refs) messages.add(gmail.users().messages().get("me", ref.getId()).setFormat("full").execute());
        messages.sort(Comparator.comparing(Message::getInternalDate).reversed());
        for (Message message : messages) {
            if (message.getInternalDate() != null && message.getInternalDate() < since.toEpochMilli() - 60_000) continue;
            Optional<String> found = extractor.apply(message);
            if (found.isPresent()) return found;
        }
        return Optional.empty();
    }

    private static String bodyText(MessagePart part, boolean stripHtml) {
        if (part == null) return "";
        StringBuilder sb = new StringBuilder();
        String mime = Text.orEmpty(part.getMimeType());
        if (part.getBody() != null && part.getBody().getData() != null
                && (mime.startsWith("text/plain") || mime.startsWith("text/html"))) {
            String decoded = new String(Base64.getUrlDecoder().decode(part.getBody().getData()), StandardCharsets.UTF_8);
            sb.append(stripHtml && mime.startsWith("text/html") ? Text.stripHtml(decoded) : decoded).append('\n');
        }
        if (part.getParts() != null) for (MessagePart child : part.getParts()) sb.append(bodyText(child, stripHtml));
        return sb.toString();
    }

    private GoogleAuthorizationCodeFlow flow(NetHttpTransport transport) throws IOException {
        GoogleClientSecrets secrets;
        try (InputStream in = openCredentials()) {
            secrets = GoogleClientSecrets.load(JSON, new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        return new GoogleAuthorizationCodeFlow.Builder(transport, JSON, secrets, SCOPES)
                .setDataStoreFactory(new FileDataStoreFactory(AppPaths.googleTokens().toFile()))
                .setAccessType("offline")
                .build();
    }

    private static InputStream openCredentials() throws IOException {
        if (Files.isRegularFile(AppPaths.googleCredentials())) return Files.newInputStream(AppPaths.googleCredentials());
        InputStream in = GmailService.class.getResourceAsStream("/google-credentials.json");
        if (in == null) throw new IOException("Google OAuth client file not found at " + AppPaths.googleCredentials());
        return in;
    }

    private static Gmail client(NetHttpTransport transport, Credential credential) {
        return new Gmail.Builder(transport, JSON, credential).setApplicationName("TrueApply").build();
    }
}
