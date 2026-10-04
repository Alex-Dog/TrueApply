package com.trueapply.ui;

import atlantafx.base.controls.Message;
import atlantafx.base.theme.Styles;
import com.trueapply.AppContext;
import com.trueapply.model.ApplicationStatus;
import com.trueapply.model.FieldCategory;
import com.trueapply.model.FormField;
import com.trueapply.model.JobApplication;
import com.trueapply.model.JobOverview;
import com.trueapply.util.Json;
import com.trueapply.util.Text;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.feather.Feather;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Applications that need the user (creative questions), plus anything in flight or failed. */
public class InboxView implements View {
    private static final EnumSet<ApplicationStatus> SHOWN = EnumSet.of(ApplicationStatus.NEEDS_INPUT,
            ApplicationStatus.AWAITING_CONFIRMATION, ApplicationStatus.ANALYZING, ApplicationStatus.READY, ApplicationStatus.SUBMITTING, ApplicationStatus.FAILED);

    /** Shown when the user says they didn't finish in the browser. */
    public static final String NOT_FINISHED = "You didn't finish applying in the browser. Your answers are kept;"
            + " press Submit application to open it again, or Discard.";

    private final AppContext ctx;
    private final ListView<JobApplication> list = new ListView<>();
    private final StackPane detail = new StackPane();
    private final SplitPane root;
    private final Map<FormField, FieldEditor> editors = new LinkedHashMap<>();
    private final Map<FormField, CheckBox> rememberBoxes = new LinkedHashMap<>();

    private long shownId = -1;
    private ApplicationStatus shownStatus;
    private String shownMessage;

    public InboxView(AppContext ctx) {
        this.ctx = ctx;
        list.setPlaceholder(Ui.muted("Nothing needs you right now. Queue some jobs from Discover."));
        list.setCellFactory(v -> new ApplicationCell());
        list.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> showDetail(b, false));

        VBox left = new VBox(12, Ui.title("Inbox"),
                Ui.muted("Applications waiting on answers only you should write."), list);
        left.setPadding(new Insets(24, 12, 24, 24));
        VBox.setVgrow(list, javafx.scene.layout.Priority.ALWAYS);
        left.setMinWidth(300);

        root = new SplitPane(left, detail);
        root.setDividerPositions(0.32);
        showEmpty();
    }

    @Override
    public Node root() {
        return root;
    }

    @Override
    public void refresh() {
        long selectedId = list.getSelectionModel().getSelectedItem() == null ? -1 : list.getSelectionModel().getSelectedItem().id;
        List<JobApplication> apps = new ArrayList<>(ctx.applications.findByStatus(SHOWN));
        apps.sort(Comparator.comparing((JobApplication a) -> a.status != ApplicationStatus.AWAITING_CONFIRMATION)
                .thenComparing(a -> a.status != ApplicationStatus.NEEDS_INPUT)
                .thenComparing(a -> a.updatedAt, Comparator.reverseOrder()));
        list.getItems().setAll(apps);
        JobApplication reselect = apps.stream().filter(a -> a.id == selectedId).findFirst().orElse(null);
        if (reselect != null) {
            list.getSelectionModel().select(reselect);
            showDetail(reselect, false);
        } else if (selectedId != -1 || shownId != -1) {
            showEmpty();
        }
    }

    // ---- detail --------------------------------------------------------------------------

    private void showEmpty() {
        shownId = -1;
        detail.getChildren().setAll(centered(Ui.muted("Select an application to see its questions.")));
    }

    private void showDetail(JobApplication app, boolean force) {
        if (app == null) {
            showEmpty();
            return;
        }
        // Don't wipe what the user is typing just because something else refreshed.
        boolean same = app.id == shownId && app.status == shownStatus && Objects.equals(app.statusMessage, shownMessage);
        if (same && !force) return;
        shownId = app.id;
        shownStatus = app.status;
        shownMessage = app.statusMessage;
        editors.clear();
        rememberBoxes.clear();

        // Fixed header, one scroll area per tab, and a pinned action bar. Putting the TabPane
        // inside a ScrollPane made JavaFX under-measure wrapped text and clip the bottom.
        VBox top = new VBox(14, header(app));
        Node banner = statusBanner(app);
        if (banner != null) top.getChildren().add(banner);
        top.setPadding(new Insets(20, 28, 8, 20));

        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getTabs().add(new Tab("Questions", scrolling(questions(app))));
        tabs.getTabs().add(new Tab("Role overview", scrolling(roleOverview(app))));
        tabs.getTabs().add(new Tab("Company", scrolling(company(app))));

        BorderPane layout = new BorderPane(tabs);
        layout.setTop(top);
        BorderPane.setMargin(tabs, new Insets(0, 8, 0, 8));
        if (isEditable(app) && !app.fields.isEmpty()) layout.setBottom(actionBar(app));
        detail.getChildren().setAll(layout);
    }

    private static ScrollPane scrolling(Node content) {
        ((javafx.scene.layout.Region) content).setPadding(new Insets(16, 20, 24, 12));
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        return scroll;
    }

    private static boolean isEditable(JobApplication app) {
        return app.status == ApplicationStatus.NEEDS_INPUT || app.status == ApplicationStatus.FAILED;
    }

    private Node actionBar(JobApplication app) {
        Button save = Ui.button("Save draft", Feather.SAVE);
        save.setOnAction(e -> saveDraft(app));
        Button submit = Ui.button("Submit application", Feather.SEND, Styles.ACCENT);
        submit.setDefaultButton(true);
        submit.setOnAction(e -> submit(app));
        Button discard = Ui.button("Discard", Feather.TRASH_2, Styles.FLAT, Styles.DANGER);
        discard.setOnAction(e -> discard(app));
        for (Button b : List.of(submit, save, discard)) b.setMinWidth(Region.USE_PREF_SIZE);
        HBox bar = Ui.row(submit, save, Ui.hgrow(), discard);
        if (ctx.settings.dryRun()) {
            Label note = Ui.muted("Dry run is on: the form will be filled but not submitted.");
            HBox.setHgrow(note, Priority.ALWAYS);
            bar.getChildren().set(2, note); // replaces the spacer
        }
        bar.getStyleClass().add("action-bar");
        return bar;
    }

    private Node header(JobApplication app) {
        Label title = Ui.title(app.job == null ? "Unknown job" : app.job.title);
        title.setWrapText(true);
        String sub = app.job == null ? "" : app.job.company + (Text.isBlank(app.job.location) ? "" : " · " + app.job.location);
        Button open = Ui.button("Open posting", Feather.EXTERNAL_LINK, Styles.FLAT);
        open.setOnAction(e -> Ui.openUrl(app.job == null ? null : app.job.url));
        return new VBox(6, title, Ui.row(Ui.muted(sub), Ui.statusChip(app.status), Ui.hgrow(), open));
    }

    private Node statusBanner(JobApplication app) {
        return switch (app.status) {
            case NEEDS_INPUT -> {
                long pending = app.pendingHumanFields(ctx.settings.includeOptionalCreative());
                String detail = Text.isBlank(app.statusMessage)
                        ? "Everything else has been filled from your profile. Answer these in your own words, then submit."
                        : app.statusMessage; // e.g. Workday: more questions may follow on later pages
                String title = pending > 0 ? pending + " question" + (pending == 1 ? "" : "s") + " for you"
                        : "Nothing left to answer";
                Message m = new Message(title, detail, Ui.icon(Feather.EDIT_3));
                m.getStyleClass().add(Styles.WARNING);
                yield m;
            }
            case FAILED -> {
                Message m = new Message("Something went wrong", Text.orEmpty(app.statusMessage), Ui.icon(Feather.ALERT_TRIANGLE));
                m.getStyleClass().add(Styles.DANGER);
                Button retry = Ui.button("Retry", Feather.REFRESH_CW);
                retry.setOnAction(e -> ctx.pipeline.retry(app));
                Button discard = Ui.button("Discard", Feather.TRASH_2, Styles.FLAT);
                discard.setOnAction(e -> discard(app));
                yield new VBox(8, m, Ui.row(retry, discard));
            }
            case AWAITING_CONFIRMATION -> {
                Message m = new Message("Did you finish applying to this job?",
                        Text.orEmpty(app.statusMessage).replace(" Did you finish applying to this job?", "")
                                + " Yes moves it to History; No keeps it here so you can finish it later.",
                        Ui.icon(Feather.HELP_CIRCLE));
                m.getStyleClass().add(Styles.WARNING);
                Button yes = Ui.button("Yes, I applied", Feather.CHECK, Styles.ACCENT);
                yes.setOnAction(e -> ctx.pipeline.confirmApplied(app));
                Button no = Ui.button("No, not yet", Feather.X);
                no.setOnAction(e -> ctx.pipeline.returnToInbox(app, NOT_FINISHED));
                yield new VBox(8, m, Ui.row(yes, no));
            }
            case ANALYZING, READY, SUBMITTING -> {
                Message m = new Message(app.status.displayName() + "…", Text.orEmpty(app.statusMessage), Ui.icon(Feather.LOADER));
                m.getStyleClass().add(Styles.ACCENT);
                yield m;
            }
            default -> null;
        };
    }

    private Node questions(JobApplication app) {
        VBox box = new VBox(14);
        if (app.fields.isEmpty()) {
            box.getChildren().add(Ui.muted("Questions will appear here once the form has been read."));
            return box;
        }
        boolean editable = isEditable(app);

        List<FormField> human = app.fields.stream().filter(FormField::needsHuman).toList();
        List<FormField> automatic = app.fields.stream()
                .filter(f -> !f.needsHuman() && f.category != FieldCategory.SKIPPED).toList();

        if (!human.isEmpty()) {
            box.getChildren().add(Ui.heading("Your answers"));
            for (FormField field : human) box.getChildren().add(humanCard(field, editable));
        }

        VBox autoBox = new VBox(12);
        for (FormField field : automatic) {
            FieldEditor editor = new FieldEditor(field, editable);
            editors.put(field, editor);
            Label label = Ui.bold(field.label + (field.required ? " *" : ""));
            Label source = Ui.chip(field.source.displayName(), "neutral");
            HBox.setHgrow(label, Priority.ALWAYS);
            HBox header = Ui.row(label, Ui.categoryChip(field.category), source);
            header.setAlignment(Pos.TOP_LEFT);
            autoBox.getChildren().add(new VBox(4, header, editor.node()));
        }
        // Hand-rolled collapsible: TitledPane has the same wrapped-text sizing bug.
        autoBox.getStyleClass().add("question-card");
        boolean expanded = human.isEmpty();
        autoBox.setVisible(expanded);
        autoBox.setManaged(expanded);
        Button toggle = Ui.button("Filled automatically (" + automatic.size() + ") — review or change",
                expanded ? Feather.CHEVRON_DOWN : Feather.CHEVRON_RIGHT, Styles.FLAT);
        toggle.setOnAction(e -> {
            boolean show = !autoBox.isVisible();
            autoBox.setVisible(show);
            autoBox.setManaged(show);
            toggle.setGraphic(Ui.icon(show ? Feather.CHEVRON_DOWN : Feather.CHEVRON_RIGHT));
        });
        box.getChildren().addAll(toggle, autoBox);
        return box;
    }

    private Node humanCard(FormField field, boolean editable) {
        FieldEditor editor = new FieldEditor(field, editable);
        editors.put(field, editor);
        Label label = Ui.bold(field.label);
        label.getStyleClass().add(Styles.TITLE_4);
        // Tags on their own line so long questions get the full width to wrap.
        VBox card = new VBox(10, Ui.row(Ui.categoryChip(field.category),
                Ui.chip(field.required ? "Required" : "Optional", field.required ? "danger" : "neutral")), label);
        card.getStyleClass().add("question-card");
        VBox body = new VBox(8);
        if (!Text.isBlank(field.description)) body.getChildren().add(Ui.muted(Text.stripHtml(field.description)));
        if (field.category == FieldCategory.MISSING_INFO && !Text.isBlank(field.note)) {
            body.getChildren().add(Ui.muted("Why you're seeing this: " + field.note));
        }
        body.getChildren().add(editor.node());
        if (field.category == FieldCategory.MISSING_INFO && field.type != com.trueapply.model.FieldType.FILE) {
            CheckBox remember = new CheckBox("Remember this answer for future applications");
            remember.setDisable(!editable);
            rememberBoxes.put(field, remember);
            body.getChildren().add(remember);
        }
        card.getChildren().add(body);
        return card;
    }

    private Node roleOverview(JobApplication app) {
        VBox box = new VBox(10);
        JobOverview overview = overview(app);
        if (overview != null) {
            box.getChildren().add(Ui.bold(Text.orEmpty(overview.roleSummary())));
            if (!Text.isBlank(overview.compensation())) box.getChildren().add(Ui.muted("Compensation: " + overview.compensation()));
            addBullets(box, "What you'd do", overview.responsibilities());
            addBullets(box, "What they're looking for", overview.requirements());
        }
        box.getChildren().add(Ui.heading("Full description"));
        box.getChildren().add(Ui.html(app.jobDescriptionHtml, 520));
        return box;
    }

    private Node company(JobApplication app) {
        VBox box = new VBox(10);
        JobOverview overview = overview(app);
        if (overview != null && !Text.isBlank(overview.companySummary())) box.getChildren().add(Ui.bold(overview.companySummary()));
        if (!Text.isBlank(app.companyDescriptionHtml)) {
            box.getChildren().add(Ui.html(app.companyDescriptionHtml, 420));
        } else if (overview == null) {
            box.getChildren().add(Ui.muted("No company description available."));
        }
        return box;
    }

    private static void addBullets(VBox box, String heading, List<String> items) {
        if (items == null || items.isEmpty()) return;
        box.getChildren().add(Ui.heading(heading));
        for (String item : items) {
            Label l = new Label("•  " + item);
            l.setWrapText(true);
            box.getChildren().add(l);
        }
    }

    private static JobOverview overview(JobApplication app) {
        if (Text.isBlank(app.overviewJson)) return null;
        try {
            return Json.read(app.overviewJson, JobOverview.class);
        } catch (RuntimeException e) {
            return null;
        }
    }

    // ---- actions -------------------------------------------------------------------------

    private void commitAll() {
        editors.values().forEach(FieldEditor::commit);
    }

    private void saveDraft(JobApplication app) {
        commitAll();
        ctx.applications.save(app);
        shownMessage = app.statusMessage; // keep the editors on screen
        ctx.events.fireChanged();
    }

    private void submit(JobApplication app) {
        List<String> missing = new ArrayList<>();
        for (FieldEditor editor : editors.values()) {
            FormField f = editor.field();
            boolean blocking = f.required && f.category != FieldCategory.SKIPPED;
            boolean ok = !blocking || editor.isAnswered();
            editor.markInvalid(!ok);
            if (!ok) missing.add(f.label);
        }
        if (!missing.isEmpty()) {
            Ui.info("A few required questions are still empty", String.join("\n", missing));
            return;
        }
        commitAll();
        List<FormField> remember = new ArrayList<>();
        rememberBoxes.forEach((field, box) -> {
            if (box.isSelected()) remember.add(field);
        });
        ctx.pipeline.completeByUser(app, remember);
    }

    private void discard(JobApplication app) {
        if (Ui.confirm("Discard this application?", app.displayTitle() + "\n\nYour answers for it will be deleted.")) {
            ctx.pipeline.discard(app);
        }
    }

    private static Node centered(Node node) {
        StackPane pane = new StackPane(node);
        pane.setPadding(new Insets(40));
        return pane;
    }

    private final class ApplicationCell extends ListCell<JobApplication> {
        @Override
        protected void updateItem(JobApplication app, boolean empty) {
            super.updateItem(app, empty);
            if (empty || app == null) {
                setGraphic(null);
                setText(null);
                return;
            }
            Label title = Ui.bold(app.job == null ? "Unknown job" : app.job.title);
            title.setWrapText(false);
            Label company = Ui.muted(app.job == null ? "" : app.job.company);
            Node chip = Ui.statusChip(app.status);
            long pending = app.pendingHumanFields(ctx.settings.includeOptionalCreative());
            VBox box = new VBox(4, title, Ui.row(company, Ui.hgrow(), chip));
            if (app.status == ApplicationStatus.NEEDS_INPUT && pending > 0) {
                box.getChildren().add(Ui.muted(pending + " question" + (pending == 1 ? "" : "s") + " to answer"));
            }
            box.setPadding(new Insets(4, 2, 4, 2));
            setText(null);
            setGraphic(box);
        }
    }
}
