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
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.feather.Feather;

import java.util.List;

/** Run discovery, browse matches, and queue applications. */
public class DiscoverView implements View {
    private final AppContext ctx;
    private final FilteredList<Job> jobs = new FilteredList<>(FXCollections.observableArrayList());
    private final TableView<Job> table = new TableView<>(jobs);
    private final Label status = Ui.muted("");
    private final ProgressIndicator spinner = new ProgressIndicator();
    private final Button find = Ui.button("Find jobs", Feather.SEARCH, Styles.ACCENT);
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

        Button apply = Ui.button("Apply to selected", Feather.SEND, Styles.SUCCESS);
        apply.setOnAction(e -> applySelected());
        apply.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        Button dismiss = Ui.button("Dismiss", Feather.X);
        dismiss.setOnAction(e -> dismissSelected());
        dismiss.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());

        buildTable();
        VBox.setVgrow(table, Priority.ALWAYS);
        root = Ui.page("Discover",
                "Matches for your job preferences from Greenhouse boards and Adzuna. Select jobs (Ctrl/Shift-click) and apply.",
                Ui.row(find, spinner, status, Ui.hgrow(), filter),
                table,
                Ui.row(apply, dismiss, Ui.hgrow(),
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

    private String applyVia(Job job) {
        if (job.isSupported() && ctx.platforms.supports(job.ats)) {
            return job.ats.displayName() + ("adzuna".equals(job.source) ? " (via Adzuna)" : "");
        }
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
        source.setAll(ctx.jobs.findByStatus(Job.JobStatus.NEW));
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
                    String text = result.matched() + " matching jobs, " + result.added() + " new.";
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
        String body = "Queued " + queued + " application" + (queued == 1 ? "" : "s") + ". "
                + "Ones with creative questions will wait in your Inbox; the rest are submitted automatically"
                + (ctx.settings.dryRun() ? " (dry run is on, so nothing is actually sent)." : ".");
        if (!manual.isEmpty()) {
            body += "\n\n" + manual.size() + " job(s) aren't on a supported site yet; opening them so you can apply manually.";
            manual.forEach(j -> Ui.openUrl(j.url));
        }
        Ui.info("Applications queued", body);
    }

    private void dismissSelected() {
        for (Job job : List.copyOf(table.getSelectionModel().getSelectedItems())) {
            ctx.jobs.updateStatus(job.id, Job.JobStatus.DISMISSED);
        }
        refresh();
    }
}
