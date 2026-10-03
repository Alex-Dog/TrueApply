package com.trueapply.ui;

import atlantafx.base.controls.Card;
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
import javafx.scene.control.TitledPane;
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
            ApplicationStatus.ANALYZING, ApplicationStatus.READY, ApplicationStatus.SUBMITTING, ApplicationStatus.FAILED);

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
        apps.sort(Comparator.comparing((JobApplication a) -> a.status != ApplicationStatus.NEEDS_INPUT)
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

        VBox content = new VBox(16);
        content.setPadding(new Insets(24, 28, 24, 20));
        content.getChildren().add(header(app));
        Node banner = statusBanner(app);
        if (banner != null) content.getChildren().add(banner);

        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getTabs().add(new Tab("Questions", questions(app)));
        tabs.getTabs().add(new Tab("Role overview", roleOverview(app)));
        tabs.getTabs().add(new Tab("Company", company(app)));
        content.getChildren().add(tabs);

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        detail.getChildren().setAll(scroll);
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
                Message m = new Message(pending + " question" + (pending == 1 ? "" : "s") + " for you",
                        "Everything else has been filled from your profile. Answer these in your own words, then submit.",
                        Ui.icon(Feather.EDIT_3));
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
        box.setPadding(new Insets(16, 0, 0, 0));
        if (app.fields.isEmpty()) {
            box.getChildren().add(Ui.muted("Questions will appear here once the form has been read."));
            return box;
        }
        boolean editable = app.status == ApplicationStatus.NEEDS_INPUT || app.status == ApplicationStatus.FAILED;

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
            autoBox.getChildren().add(new VBox(4, Ui.row(label, Ui.hgrow(), Ui.categoryChip(field.category), source), editor.node()));
        }
        TitledPane auto = new TitledPane("Filled automatically (" + automatic.size() + ") — review or change", autoBox);
        auto.setExpanded(human.isEmpty());
        box.getChildren().add(auto);

        if (editable) {
            Button save = Ui.button("Save draft", Feather.SAVE);
            save.setOnAction(e -> saveDraft(app));
            Button submit = Ui.button("Submit application", Feather.SEND, Styles.ACCENT);
            submit.setDefaultButton(true);
            submit.setOnAction(e -> submit(app));
            Button discard = Ui.button("Discard", Feather.TRASH_2, Styles.FLAT, Styles.DANGER);
            discard.setOnAction(e -> discard(app));
            String mode = ctx.settings.dryRun() ? "Dry run is on: the form will be filled but not submitted." : null;
            box.getChildren().add(Ui.row(submit, save, Ui.hgrow(), discard));
            if (mode != null) box.getChildren().add(Ui.muted(mode));
        }
        return box;
    }

    private Node humanCard(FormField field, boolean editable) {
        FieldEditor editor = new FieldEditor(field, editable);
        editors.put(field, editor);
        Card card = new Card();
        Label label = Ui.bold(field.label);
        card.setHeader(Ui.row(label, Ui.hgrow(), Ui.categoryChip(field.category),
                Ui.chip(field.required ? "Required" : "Optional", field.required ? "danger" : "neutral")));
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
        card.setBody(body);
        return card;
    }

    private Node roleOverview(JobApplication app) {
        VBox box = new VBox(10);
        box.setPadding(new Insets(16, 0, 0, 0));
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
        box.setPadding(new Insets(16, 0, 0, 0));
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
