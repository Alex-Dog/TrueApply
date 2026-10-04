package com.trueapply.ui;

import atlantafx.base.theme.Styles;
import com.trueapply.AppContext;
import com.trueapply.model.ApplicationStatus;
import com.trueapply.model.JobApplication;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.feather.Feather;

import java.util.function.Consumer;

/** Sidebar navigation plus the current page. */
public class MainWindow {
    private final AppContext ctx;
    private final BorderPane root = new BorderPane();
    private final ToggleGroup nav = new ToggleGroup();
    private final Label inboxBadge = Ui.chip("0", "warning");

    private final InboxView inbox;
    private final DiscoverView discover;
    private final HistoryView history;
    private final ProfileView profile;
    private final AccountsView accounts;
    private final SettingsView settings;
    private View current;

    public MainWindow(AppContext ctx, Consumer<Boolean> applyTheme) {
        this.ctx = ctx;
        inbox = new InboxView(ctx);
        discover = new DiscoverView(ctx);
        history = new HistoryView(ctx);
        profile = new ProfileView(ctx);
        accounts = new AccountsView(ctx);
        settings = new SettingsView(ctx, applyTheme);

        Label brand = new Label("TrueApply");
        brand.getStyleClass().addAll(Styles.TITLE_3, "brand");
        Label tagline = Ui.muted("No slop. Just applications.");
        VBox header = new VBox(2, brand, tagline);
        header.setPadding(new Insets(0, 8, 18, 8));

        ToggleButton inboxButton = navButton("Inbox", Feather.INBOX, inbox, inboxBadge);
        VBox sidebar = new VBox(4, header,
                inboxButton,
                navButton("Discover", Feather.SEARCH, discover, null),
                navButton("History", Feather.CLOCK, history, null),
                section("You"),
                navButton("Profile", Feather.USER, profile, null),
                navButton("Accounts", Feather.KEY, accounts, null),
                navButton("Settings", Feather.SETTINGS, settings, null));
        sidebar.getStyleClass().add("sidebar");
        sidebar.setPadding(new Insets(20, 12, 20, 12));
        sidebar.setPrefWidth(220);
        root.setLeft(sidebar);

        ctx.events.onChange(this::onDataChanged);
        nav.selectToggle(inboxButton);
        show(inbox);
        updateBadge();
        askWhetherApplied();
    }

    /** Applications already asked about this session (a dismissed question stays in the Inbox). */
    private final java.util.Set<Long> askedWhetherApplied = new java.util.HashSet<>();
    private boolean asking;

    /** When the user closed the browser at the submit step, ask right away whether they applied. */
    private void askWhetherApplied() {
        if (asking) return;
        for (JobApplication app : ctx.applications.findByStatus(java.util.EnumSet.of(ApplicationStatus.AWAITING_CONFIRMATION))) {
            if (!askedWhetherApplied.add(app.id)) continue;
            asking = true;
            // After the current event: showAndWait can't run inside some event/layout passes.
            javafx.application.Platform.runLater(() -> {
                try {
                    String what = app.job == null ? "this job" : app.job.title + " at " + app.job.company;
                    Ui.askYesNo("Did you finish applying to " + what + "?",
                                    "You closed the browser at the submit step. Yes moves it to History;"
                                            + " No keeps it in your Inbox so you can finish it later.")
                            .ifPresent(applied -> {
                                if (applied) ctx.pipeline.confirmApplied(app);
                                else ctx.pipeline.returnToInbox(app, InboxView.NOT_FINISHED);
                            });
                } finally {
                    asking = false;
                }
                askWhetherApplied(); // another one may be waiting
            });
            return;
        }
    }

    public Node root() {
        return root;
    }

    /** Lands on Discover — used right after onboarding. */
    public void showDiscover() {
        nav.getToggles().stream()
                .filter(t -> t.getUserData() == discover)
                .findFirst()
                .ifPresent(nav::selectToggle);
    }

    private ToggleButton navButton(String text, Ikon icon, View view, Label badge) {
        Label label = new Label(text);
        Region grow = new Region();
        HBox.setHgrow(grow, Priority.ALWAYS);
        HBox graphic = new HBox(10, Ui.icon(icon), label, grow);
        graphic.setAlignment(Pos.CENTER_LEFT);
        if (badge != null) graphic.getChildren().add(badge);
        ToggleButton button = new ToggleButton(null, graphic);
        graphic.prefWidthProperty().bind(button.widthProperty().subtract(24));
        button.setUserData(view);
        button.setToggleGroup(nav);
        button.setMaxWidth(Double.MAX_VALUE);
        button.getStyleClass().add("nav-button");
        button.setOnAction(e -> {
            if (!button.isSelected()) button.setSelected(true); // keep one page selected
            show(view);
        });
        button.selectedProperty().addListener((o, was, is) -> {
            if (is && current != view) show(view);
        });
        return button;
    }

    private static Label section(String text) {
        Label label = Ui.muted(text.toUpperCase());
        label.getStyleClass().addAll(Styles.TEXT_SMALL, "nav-section");
        label.setPadding(new Insets(16, 8, 4, 8));
        return label;
    }

    private void show(View view) {
        current = view;
        if (view == profile) profile.reload();
        else view.refresh();
        root.setCenter(view.root());
    }

    private void onDataChanged() {
        updateBadge();
        askWhetherApplied();
        // Only data-driven pages refresh on background events; forms keep unsaved edits.
        if (current == inbox || current == history || current == discover) current.refresh();
    }

    private void updateBadge() {
        int count = ctx.applications.countByStatus(ApplicationStatus.NEEDS_INPUT)
                + ctx.applications.countByStatus(ApplicationStatus.FAILED)
                + ctx.applications.countByStatus(ApplicationStatus.AWAITING_CONFIRMATION);
        inboxBadge.setText(String.valueOf(count));
        inboxBadge.setVisible(count > 0);
    }
}
