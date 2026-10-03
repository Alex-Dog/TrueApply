package com.trueapply.ui;

import atlantafx.base.controls.ToggleSwitch;
import atlantafx.base.theme.Styles;
import com.trueapply.AppContext;
import com.trueapply.discovery.JobSource;
import com.trueapply.settings.AppSettings;
import com.trueapply.util.AppPaths;
import com.trueapply.util.Text;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.feather.Feather;

import java.util.function.Consumer;

public class SettingsView implements View {
    private final AppContext ctx;
    private final Consumer<Boolean> applyTheme;
    private final ScrollPane root;

    private final TextField adzunaId = new TextField();
    private final PasswordField adzunaKey = new PasswordField();
    private final TextArea boards = Ui.textArea("", 6);
    private final ToggleSwitch dryRun = new ToggleSwitch("Dry run — fill forms but never press Submit");
    private final ToggleSwitch showBrowser = new ToggleSwitch("Show the browser while applying");
    private final ToggleSwitch optionalCreative = new ToggleSwitch("Also hold applications for optional creative questions");
    private final ComboBox<String> channel = new ComboBox<>();
    private final ToggleSwitch dark = new ToggleSwitch("Dark theme");
    private final Label saved = Ui.muted("");
    private final java.util.Map<String, ToggleSwitch> sourceToggles = new java.util.LinkedHashMap<>();
    private final Label learned = Ui.muted("");
    private ConnectionsPanel connections;
    private final VBox connectionsHolder = new VBox();

    public SettingsView(AppContext ctx, Consumer<Boolean> applyTheme) {
        this.ctx = ctx;
        this.applyTheme = applyTheme;

        adzunaId.setPromptText("App ID from developer.adzuna.com");
        adzunaKey.setPromptText("App key");
        boards.setPromptText("One Greenhouse board token per line (the part after job-boards.greenhouse.io/)");
        channel.getItems().addAll("msedge", "chrome", "chromium");

        GridPane discovery = Ui.form();
        VBox sourceBox = new VBox(8);
        for (JobSource source : ctx.discovery.sources()) {
            ToggleSwitch toggle = new ToggleSwitch(source.name() + " — " + describe(source.id()));
            sourceToggles.put(source.id(), toggle);
            sourceBox.getChildren().add(toggle);
        }
        Ui.addRow(discovery, "Sources", sourceBox);
        Button forget = Ui.button("Forget", Feather.TRASH_2, Styles.FLAT, Styles.SMALL);
        forget.setOnAction(e -> {
            ctx.settings.clearLearnedGreenhouseBoards();
            learned.setText(learnedText());
        });
        Ui.addRow(discovery, "Learned boards", Ui.row(learned, forget));
        Ui.addRow(discovery, "Adzuna App ID", adzunaId);
        Ui.addRow(discovery, "Adzuna App key", adzunaKey);
        Ui.addRow(discovery, "Greenhouse boards", boards);
        Button resetBoards = Ui.button("Reset to defaults", Feather.REFRESH_CW, Styles.FLAT, Styles.SMALL);
        resetBoards.setOnAction(e -> boards.setText(String.join("\n", AppSettings.DEFAULT_GREENHOUSE_BOARDS)));
        Ui.addRow(discovery, "", resetBoards);

        GridPane applying = Ui.form();
        Ui.addRow(applying, "Safety", dryRun);
        Ui.addRow(applying, "Browser", showBrowser);
        Ui.addRow(applying, "Browser engine", channel);
        Ui.addRow(applying, "Creative questions", optionalCreative);
        Ui.addRow(applying, "", Ui.muted("Required creative questions always wait for you. Optional ones are left "
                + "blank unless this is on."));

        GridPane appearance = Ui.form();
        Ui.addRow(appearance, "Theme", dark);
        Button openFolder = Ui.button("Open data folder", Feather.EXTERNAL_LINK, Styles.FLAT);
        openFolder.setOnAction(e -> Ui.openUrl(AppPaths.root().toUri().toString()));
        Ui.addRow(appearance, "Data", Ui.row(Ui.muted(AppPaths.root().toString()), openFolder));

        Button save = Ui.button("Save settings", Feather.SAVE, Styles.ACCENT);
        save.setOnAction(e -> save());

        VBox page = Ui.page("Settings", null,
                connectionsHolder,
                Ui.heading("Job discovery"), discovery,
                Ui.heading("Applying"), applying,
                Ui.heading("Appearance"), appearance,
                Ui.row(save, saved));
        root = new ScrollPane(page);
        root.setFitToWidth(true);
    }

    private static String describe(String sourceId) {
        return switch (sourceId) {
            case "simplify" -> "community internship & new-grad lists (no key needed)";
            case "greenhouse" -> "every job on the company boards below, plus boards found by other sources";
            case "adzuna" -> "large job aggregator (needs a free API key)";
            default -> "";
        };
    }

    private String learnedText() {
        int n = ctx.settings.learnedGreenhouseBoards().size();
        return n == 0 ? "None yet. Boards found by other sources are added here automatically."
                : n + " Greenhouse boards found automatically and scanned on every search.";
    }

    private void save() {
        sourceToggles.forEach((id, toggle) -> ctx.settings.setSourceEnabled(id, toggle.isSelected()));
        connections.save();
        ctx.settings.setAdzunaAppId(adzunaId.getText().trim());
        ctx.settings.setAdzunaAppKey(adzunaKey.getText().trim());
        ctx.settings.setGreenhouseBoards(Text.splitList(boards.getText().toLowerCase()));
        ctx.settings.setDryRun(dryRun.isSelected());
        ctx.settings.setShowBrowser(showBrowser.isSelected());
        ctx.settings.setIncludeOptionalCreative(optionalCreative.isSelected());
        ctx.settings.setBrowserChannel(channel.getValue());
        ctx.settings.setDarkTheme(dark.isSelected());
        applyTheme.accept(dark.isSelected());
        saved.setText("Saved.");
        ctx.events.fireChanged();
    }

    @Override
    public Node root() {
        return root;
    }

    @Override
    public void refresh() {
        AppSettings s = ctx.settings;
        connections = new ConnectionsPanel(ctx);
        connectionsHolder.getChildren().setAll(connections);
        adzunaId.setText(s.adzunaAppId());
        adzunaKey.setText(s.adzunaAppKey());
        boards.setText(String.join("\n", s.greenhouseBoards()));
        dryRun.setSelected(s.dryRun());
        showBrowser.setSelected(s.showBrowser());
        optionalCreative.setSelected(s.includeOptionalCreative());
        channel.setValue(s.browserChannel());
        dark.setSelected(s.darkTheme());
        sourceToggles.forEach((id, toggle) -> toggle.setSelected(s.sourceEnabled(id)));
        learned.setText(learnedText());
        saved.setText("");
    }
}
