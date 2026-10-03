package com.trueapply.ui;

import atlantafx.base.theme.Styles;
import com.trueapply.AppContext;
import com.trueapply.ai.AiProviders;
import com.trueapply.ai.AiProviders.ProviderInfo;
import com.trueapply.util.AppPaths;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.PasswordField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import org.kordamp.ikonli.feather.Feather;

import java.util.HashMap;
import java.util.Map;

/** AI provider, model + key and Gmail connection; used in onboarding and Settings. */
public class ConnectionsPanel extends VBox {
    private final AppContext ctx;
    private final ComboBox<ProviderInfo> provider = new ComboBox<>();
    private final PasswordField apiKey = new PasswordField();
    private final ComboBox<String> model = new ComboBox<>();
    private final Label modelNote = Ui.muted("");
    private final Label envNote = Ui.muted("");
    private final Label aiStatus = Ui.muted("");
    /** Keys typed for each provider in this session, so switching back and forth doesn't lose them. */
    private final Map<String, String> typedKeys = new HashMap<>();
    private final Map<String, String> chosenModels = new HashMap<>();
    private final Label gmailStatus = new Label();
    private final Button connect = Ui.button("Connect Gmail", Feather.MAIL);
    private final Button disconnect = Ui.button("Disconnect", Feather.X, Styles.FLAT);

    public ConnectionsPanel(AppContext ctx) {
        super(18);
        this.ctx = ctx;

        provider.getItems().setAll(AiProviders.available());
        provider.setValue(AiProviders.info(AiProviders.selectedId(ctx.settings)));
        provider.setMaxWidth(Double.MAX_VALUE);
        for (ProviderInfo p : AiProviders.available()) {
            typedKeys.put(p.id(), ctx.settings.aiApiKey(p.id()));
            chosenModels.put(p.id(), AiProviders.selectedModel(ctx.settings, p.id()));
        }
        provider.valueProperty().addListener((o, previous, next) -> {
            if (previous != null) remember(previous);
            showProvider(next);
        });
        setUpModelPicker();

        Button save = Ui.button("Save", Feather.CHECK);
        save.setOnAction(e -> {
            save();
            aiStatus.setText("Using " + ctx.ai().describe());
        });
        HBox keyRow = Ui.row(apiKey, save);
        HBox.setHgrow(apiKey, Priority.ALWAYS);

        GridPane ai = Ui.form();
        Ui.addRow(ai, "Provider", provider);
        Ui.addRow(ai, "Model", new VBox(4, model, modelNote));
        Ui.addRow(ai, "API key", new VBox(4, keyRow, envNote));
        Ui.addRow(ai, "", aiStatus);
        aiStatus.setText("Using " + ctx.ai().describe());
        showProvider(provider.getValue());

        GridPane gmail = Ui.form();
        connect.setOnAction(e -> connectGmail());
        disconnect.setOnAction(e -> {
            try {
                ctx.gmail.disconnect();
            } catch (Exception ex) {
                Ui.error("Couldn't disconnect", ex);
            }
            refreshGmail();
        });
        Ui.addRow(gmail, "Gmail", Ui.row(gmailStatus, connect, disconnect));
        Ui.addRow(gmail, "", Ui.muted("Read-only access, used to grab the security codes some job sites email you "
                + "while an application is being submitted."));
        if (!ctx.gmail.isConfigured()) {
            Ui.addRow(gmail, "", Ui.muted("Developer setup needed: put a Google OAuth desktop-client JSON at "
                    + AppPaths.googleCredentials() + " to enable this."));
        }
        getChildren().addAll(Ui.heading("AI"), ai, Ui.heading("Email"), gmail);
        refreshGmail();
    }

    /** Editable dropdown: friendly names in the list, the raw model id as the value. */
    private void setUpModelPicker() {
        model.setEditable(true);
        model.setMaxWidth(Double.MAX_VALUE);
        model.setConverter(new StringConverter<>() {
            @Override
            public String toString(String id) {
                return id == null ? "" : id;
            }

            @Override
            public String fromString(String text) {
                return text == null ? "" : text.trim();
            }
        });
        model.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(String id, boolean empty) {
                super.updateItem(id, empty);
                if (empty || id == null) {
                    setText(null);
                    return;
                }
                setText(provider.getValue().model(id)
                        .map(m -> m.label() + "  —  " + m.note() + "  (" + m.id() + ")")
                        .orElse(id));
            }
        });
        model.valueProperty().addListener((o, a, id) -> describeModel(id));
    }

    private void describeModel(String id) {
        ProviderInfo info = provider.getValue();
        if (info == null || id == null || id.isBlank()) {
            modelNote.setText("");
            return;
        }
        modelNote.setText(info.model(id)
                .map(m -> m.label() + ": " + m.note())
                .orElse("Custom model id — make sure your " + info.displayName() + " account can use it."));
    }

    private void remember(ProviderInfo info) {
        typedKeys.put(info.id(), apiKey.getText().trim());
        String chosen = model.getEditor().getText().trim();
        if (!chosen.isEmpty()) chosenModels.put(info.id(), chosen);
    }

    private void showProvider(ProviderInfo info) {
        model.getItems().setAll(info.models().stream().map(AiProviders.ModelOption::id).toList());
        model.setValue(chosenModels.getOrDefault(info.id(), AiProviders.defaultModel(info.id())));
        describeModel(model.getValue());

        boolean fromEnv = AiProviders.keyFromEnvironment(info.id());
        apiKey.setPromptText(info.keyHint());
        apiKey.setText(typedKeys.getOrDefault(info.id(), ""));
        apiKey.setDisable(fromEnv);
        envNote.setText(fromEnv ? "Provided by an environment variable; nothing to enter." : "");
        envNote.setVisible(fromEnv);
        envNote.setManaged(fromEnv);
    }

    /** Persists the chosen provider and any typed keys, then switches the app over. */
    public void save() {
        ProviderInfo selected = provider.getValue();
        remember(selected);
        for (ProviderInfo p : AiProviders.available()) {
            if (!AiProviders.keyFromEnvironment(p.id())) ctx.settings.setAiApiKey(p.id(), typedKeys.getOrDefault(p.id(), ""));
            ctx.settings.setAiModel(p.id(), chosenModels.getOrDefault(p.id(), ""));
        }
        ctx.settings.setAiProvider(selected.id());
        ctx.reloadAi();
    }

    private void connectGmail() {
        connect.setDisable(true);
        gmailStatus.setText("Waiting for you to approve in the browser…");
        Background.run(ctx.gmail::connect, email -> refreshGmail(), error -> {
            refreshGmail();
            Ui.error("Couldn't connect Gmail", error);
        });
    }

    private void refreshGmail() {
        boolean connected = ctx.gmail.isConnected();
        gmailStatus.setText(connected ? "Connected as " + ctx.settings.gmailAccount() : "Not connected");
        connect.setDisable(connected || !ctx.gmail.isConfigured());
        disconnect.setVisible(connected);
        disconnect.setManaged(connected);
    }
}
