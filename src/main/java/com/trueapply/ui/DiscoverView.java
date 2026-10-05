package com.trueapply.ui;

import atlantafx.base.theme.Styles;
import atlantafx.base.theme.Tweaks;
import com.trueapply.AppContext;
import com.trueapply.discovery.DiscoveryService;
import com.trueapply.model.Job;
import com.trueapply.util.Text;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Separator;
import javafx.scene.control.SplitPane;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.geometry.Insets;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.feather.Feather;

import java.util.List;

/** Run discovery, browse matches, and queue applications. */
public class DiscoverView implements View {
    private final AppContext ctx;
    private final FilteredList<Job> jobs = new FilteredList<>(FXCollections.observableArrayList());
    private final TableView<Job> table = new TableView<>(jobs);
    private final Label status = Ui.muted("");
    private final Label hiddenNote = Ui.muted("");
    private final CheckBox showManual = new CheckBox("Show jobs I'd have to apply to myself");
    private final ProgressIndicator spinner = new ProgressIndicator();
    private final Button find = Ui.button("Find jobs", Feather.SEARCH, Styles.ACCENT);
    private final StackPane details = new StackPane();
    /** Descriptions fetched this session, by job id (switching back and forth is instant). */
    private final java.util.Map<Long, String> descriptions = new java.util.HashMap<>();
    private final VBox root;

    public DiscoverView(AppContext ctx) {
        this.ctx = ctx;
        spinner.setPrefSize(20, 20);
        spinner.setVisible(false);
        find.setOnAction(e -> discover());

        TextField filter = new TextField();
        filter.setPromptText("Filter by title, company, location");
        filter.setPrefWidth(280);
        filter.textProperty().addListener((o, a, text) -> {
            String q = Text.normalize(text);
            jobs.setPredicate(job -> q.isEmpty()
                    || Text.normalize(job.title + " " + job.company + " " + job.location).contains(q));
        });
        showManual.selectedProperty().addListener((o, a, b) -> refresh());

        Button apply = Ui.button("Apply to selected", Feather.SEND, Styles.SUCCESS);
        apply.setOnAction(e -> applySelected());
        apply.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        Button dismiss = Ui.button("Dismiss", Feather.X);
        dismiss.setOnAction(e -> dismissSelected());
        dismiss.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());

        buildTable();
        table.getSelectionModel().selectedItemProperty().addListener((o, a, job) -> showDetails(job));
        showDetails(null);
        details.setMinWidth(320);
        SplitPane split = new SplitPane(table, details);
        split.setDividerPositions(0.58);
        VBox.setVgrow(split, Priority.ALWAYS);
        root = Ui.page("Discover",
                "Matches for your job preferences from SimplifyJobs, Greenhouse company boards and Adzuna. "
                        + "Select jobs (Ctrl/Shift-click) and apply.",
                Ui.row(find, spinner, Ui.hgrow(), showManual, filter),
                status,
                split,
                Ui.row(apply, dismiss, hiddenNote, Ui.hgrow(),
                        Ui.muted("Double-click a row to open the posting.")));
    }

    private void buildTable() {
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        table.getStyleClass().addAll(Styles.STRIPED, Tweaks.EDGE_TO_EDGE);
        table.setPlaceholder(Ui.muted("No new jobs. Press “Find jobs” to search."));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        TableColumn<Job, String> title = column("Title", j -> j.title, 320);
        TableColumn<Job, String> company = column("Company", j -> j.company, 160);
        TableColumn<Job, String> location = column("Location", j -> j.location, 200);
        TableColumn<Job, String> posted = column("Posted", j -> Ui.ago(j.postedAt), 90);
        TableColumn<Job, String> via = column("Apply via", this::applyVia, 150);
        via.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                Job job = empty ? null : getTableRow().getItem();
                setGraphic(job == null ? null : Ui.chip(value, job.isSupported() ? "success" : "neutral"));
                setText(null);
            }
        });
        table.getColumns().addAll(List.of(title, company, location, posted, via));
        table.setRowFactory(t -> {
            TableRow<Job> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) Ui.openUrl(row.getItem().url);
            });
            return row;
        });
    }

    // ---- job details ---------------------------------------------------------------------

    private void showDetails(Job job) {
        if (job == null) {
            details.getChildren().setAll(Ui.muted("Select a job to see its description."));
            return;
        }
        Label title = Ui.title(job.title);
        title.setWrapText(true);
        String where = Text.orEmpty(job.company) + (Text.isBlank(job.location) ? "" : " · " + job.location);
        Label sub = Ui.muted(where);
        sub.setWrapText(true);
        Button open = Ui.button("Open posting", Feather.EXTERNAL_LINK, Styles.FLAT);
        open.setOnAction(e -> Ui.openUrl(job.url));
        HBox facts = Ui.row(Ui.chip(applyVia(job), canAutoApply(job) ? "success" : "neutral"));
        if (job.postedAt != null) facts.getChildren().add(Ui.muted("Posted " + Ui.ago(job.postedAt)));
        if (!Text.isBlank(job.jobType)) facts.getChildren().add(Ui.muted(job.jobType));
        facts.getChildren().addAll(Ui.hgrow(), open);

        StackPane body = new StackPane();
        VBox.setVgrow(body, Priority.ALWAYS);
        VBox pane = new VBox(8, title, sub, facts, new Separator(), body);
        pane.setPadding(new Insets(12, 12, 12, 16));
        details.getChildren().setAll(pane);

        String cached = descriptions.get(job.id);
        if (cached != null) {
            body.getChildren().setAll(description(cached, job));
            return;
        }
        var platform = ctx.platforms.forJob(job);
        if (platform.isEmpty() || !job.isSupported()) {
            body.getChildren().setAll(description(snippetHtml(job), job));
            return;
        }
        ProgressIndicator loading = new ProgressIndicator();
        loading.setPrefSize(28, 28);
        body.getChildren().setAll(new VBox(8, loading, Ui.muted("Loading the description…")));
        Background.run(() -> platform.get().loadDescription(job),
                html -> {
                    descriptions.put(job.id, Text.orEmpty(html));
                    if (isShowing(job)) body.getChildren().setAll(description(html, job));
                },
                error -> {
                    if (isShowing(job)) {
                        body.getChildren().setAll(new VBox(8,
                                Ui.muted("Couldn't load the description (" + Ui.rootMessage(error) + ")."),
                                description(snippetHtml(job), job)));
                    }
                });
    }

    private boolean isShowing(Job job) {
        Job selected = table.getSelectionModel().getSelectedItem();
        return selected != null && selected.id == job.id;
    }

    /** Sources without a full description (Adzuna, SimplifyJobs) may still have a summary. */
    private static String snippetHtml(Job job) {
        return Text.isBlank(job.snippet) ? "" : "<p>" + escape(job.snippet) + "</p>";
    }

    private static Node description(String html, Job job) {
        if (Text.isBlank(Text.stripHtml(Text.orEmpty(html)))) {
            return Ui.muted("No description from this source. Use “Open posting” to read it on the company's site.");
        }
        javafx.scene.web.WebView view = Ui.html(html, 400);
        view.setMaxHeight(Double.MAX_VALUE);
        if (!job.isSupported()) {
            Label note = Ui.muted("This is the summary the job source provides; the full posting is on the company's site.");
            note.setWrapText(true);
            VBox box = new VBox(8, note, view);
            VBox.setVgrow(view, Priority.ALWAYS);
            return box;
        }
        return view;
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private boolean canAutoApply(Job job) {
        return job.isSupported() && ctx.platforms.supports(job.ats);
    }

    private String applyVia(Job job) {
        if (canAutoApply(job)) return job.ats.displayName();
        if (job.ats != null) return job.ats.displayName() + " (coming soon)";
        return "Manual only";
    }

    private static TableColumn<Job, String> column(String name, java.util.function.Function<Job, String> getter, double width) {
        TableColumn<Job, String> col = new TableColumn<>(name);
        col.setCellValueFactory(c -> new ReadOnlyStringWrapper(Text.orEmpty(getter.apply(c.getValue()))));
        col.setPrefWidth(width);
        return col;
    }

    @Override
    public Node root() {
        return root;
    }

    @Override
    public void refresh() {
        @SuppressWarnings("unchecked")
        javafx.collections.ObservableList<Job> source = (javafx.collections.ObservableList<Job>) jobs.getSource();
        // Re-apply preferences so edits on the Profile page take effect without a new search.
        com.trueapply.model.UserProfile profile = ctx.profiles.load();
        java.util.List<Job> all = ctx.jobs.findByStatus(Job.JobStatus.NEW);
        java.util.List<Job> matching = all.stream()
                .filter(j -> com.trueapply.discovery.JobFilter.matches(j, profile.preferences, profile.personal.country))
                .toList();
        java.util.List<Job> shown = showManual.isSelected()
                ? matching : matching.stream().filter(this::canAutoApply).toList();
        source.setAll(shown);
        int hiddenByPrefs = all.size() - matching.size();
        int manualOnly = matching.size() - shown.size();
        java.util.List<String> notes = new java.util.ArrayList<>();
        if (hiddenByPrefs > 0) notes.add(hiddenByPrefs + " hidden by your job preferences");
        if (manualOnly > 0) notes.add(manualOnly + " manual-only hidden");
        hiddenNote.setText(String.join(" · ", notes));
    }

    private void discover() {
        find.setDisable(true);
        spinner.setVisible(true);
        status.setText("Searching…");
        Background.run(
                () -> ctx.discovery.discover(msg -> Platform.runLater(() -> status.setText(msg))),
                (DiscoveryService.Result result) -> {
                    find.setDisable(false);
                    spinner.setVisible(false);
                    String text = result.added() + " new of " + result.matched() + " matching";
                    if (!result.perSource().isEmpty()) {
                        text += " (" + result.perSource().entrySet().stream()
                                .map(e -> e.getKey() + " " + e.getValue())
                                .collect(java.util.stream.Collectors.joining(", ")) + ")";
                    }
                    text += ".";
                    if (result.learnedBoards() > 0) {
                        text += " Learned " + result.learnedBoards() + " new Greenhouse boards.";
                    }
                    if (!result.warnings().isEmpty()) text += "  " + String.join(" ", result.warnings());
                    status.setText(text);
                    refresh();
                },
                error -> {
                    find.setDisable(false);
                    spinner.setVisible(false);
                    status.setText("");
                    Ui.error("Job search failed", error);
                });
    }

    private void applySelected() {
        List<Job> selected = List.copyOf(table.getSelectionModel().getSelectedItems());
        List<Job> manual = selected.stream().filter(j -> !j.isSupported() || !ctx.platforms.supports(j.ats)).toList();
        int queued = ctx.pipeline.enqueue(selected);
        refresh();
        manual.forEach(j -> Ui.openUrl(j.url));
        String message = queued > 0
                ? "Queued " + queued + " application" + (queued == 1 ? "" : "s") + ". Follow along in your Inbox."
                : "";
        if (!manual.isEmpty()) {
            message += (message.isEmpty() ? "" : " ") + "Opened " + manual.size() + " posting" + (manual.size() == 1 ? "" : "s")
                    + " in your browser to apply yourself.";
        }
        if (!message.isEmpty()) Ui.toast(table, message, Feather.CHECK_CIRCLE, Styles.SUCCESS);
    }

    private void dismissSelected() {
        for (Job job : List.copyOf(table.getSelectionModel().getSelectedItems())) {
            ctx.jobs.updateStatus(job.id, Job.JobStatus.DISMISSED);
        }
        refresh();
    }
}
