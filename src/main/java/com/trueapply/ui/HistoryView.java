package com.trueapply.ui;

import atlantafx.base.theme.Styles;
import atlantafx.base.theme.Tweaks;
import com.trueapply.AppContext;
import com.trueapply.model.ApplicationStatus;
import com.trueapply.model.FieldCategory;
import com.trueapply.model.FormField;
import com.trueapply.model.JobApplication;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import org.kordamp.ikonli.feather.Feather;

import java.util.EnumSet;
import java.util.List;
import java.util.function.Function;

/** Every application that went out, with every question and answer as submitted. */
public class HistoryView implements View {
    private final AppContext ctx;
    private final ListView<JobApplication> list = new ListView<>();
    private final StackPane detail = new StackPane();
    private final TextField search = new TextField();
    private final SplitPane root;
    private List<JobApplication> all = List.of();

    public HistoryView(AppContext ctx) {
        this.ctx = ctx;
        search.setPromptText("Search company or title");
        search.textProperty().addListener((o, a, b) -> applyFilter());
        list.setPlaceholder(Ui.muted("Nothing submitted yet."));
        list.setCellFactory(v -> new ListCell<>() {
            @Override
            protected void updateItem(JobApplication app, boolean empty) {
                super.updateItem(app, empty);
                if (empty || app == null) {
                    setGraphic(null);
                    return;
                }
                Label title = Ui.bold(app.job == null ? "Unknown job" : app.job.title);
                title.setWrapText(false);
                VBox box = new VBox(3, title,
                        Ui.row(Ui.muted(app.job == null ? "" : app.job.company), Ui.hgrow(), Ui.statusChip(app.status)),
                        Ui.muted(Ui.formatTime(app.submittedAt)));
                box.setPadding(new Insets(4, 2, 4, 2));
                setGraphic(box);
            }
        });
        list.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> show(b));

        VBox left = new VBox(12, Ui.title("History"), search, list);
        left.setPadding(new Insets(24, 12, 24, 24));
        VBox.setVgrow(list, Priority.ALWAYS);
        left.setMinWidth(300);
        root = new SplitPane(left, detail);
        root.setDividerPositions(0.32);
        show(null);
    }

    @Override
    public Node root() {
        return root;
    }

    @Override
    public void refresh() {
        JobApplication selected = list.getSelectionModel().getSelectedItem();
        all = ctx.applications.findByStatus(EnumSet.of(ApplicationStatus.SUBMITTED, ApplicationStatus.DRY_RUN));
        applyFilter();
        if (selected != null) {
            list.getItems().stream().filter(a -> a.id == selected.id).findFirst()
                    .ifPresent(a -> list.getSelectionModel().select(a));
        }
    }

    private void applyFilter() {
        String q = com.trueapply.util.Text.normalize(search.getText());
        list.getItems().setAll(all.stream()
                .filter(a -> q.isEmpty() || a.job == null
                        || com.trueapply.util.Text.normalize(a.job.title + " " + a.job.company).contains(q))
                .toList());
    }

    private void show(JobApplication app) {
        if (app == null) {
            detail.getChildren().setAll(new StackPane(Ui.muted("Select an application to see what was sent.")));
            return;
        }
        Button open = Ui.button("Open posting", Feather.EXTERNAL_LINK, Styles.FLAT);
        open.setOnAction(e -> Ui.openUrl(app.job == null ? null : app.job.url));
        Button toInbox = Ui.button("Move back to Inbox", Feather.INBOX, Styles.FLAT);
        toInbox.setOnAction(e -> ctx.pipeline.returnToInbox(app,
                "Moved back from History. Your answers are kept; press Submit application to open it again, or Discard."));
        String sub = app.job == null ? "" : app.job.company
                + (com.trueapply.util.Text.isBlank(app.job.location) ? "" : " · " + app.job.location);

        TableView<FormField> table = new TableView<>();
        table.getStyleClass().addAll(Styles.STRIPED, Tweaks.EDGE_TO_EDGE);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.getColumns().addAll(List.of(
                column("Section", f -> f.group, 140, false),
                column("Question", f -> f.label + (f.required ? " *" : ""), 260, true),
                column("Answer", HistoryView::answerText, 360, true),
                column("Filled by", f -> f.source.displayName(), 100, false)));
        table.getItems().setAll(app.fields.stream().filter(f -> f.category != FieldCategory.SKIPPED).toList());
        VBox.setVgrow(table, Priority.ALWAYS);

        VBox content = new VBox(10,
                Ui.title(app.job == null ? "Unknown job" : app.job.title),
                Ui.row(Ui.muted(sub), Ui.statusChip(app.status), Ui.hgrow(), toInbox, open),
                Ui.muted((app.status == ApplicationStatus.DRY_RUN ? "Filled (not submitted) " : "Submitted ")
                        + Ui.formatTime(app.submittedAt)
                        + (com.trueapply.util.Text.isBlank(app.statusMessage) ? "" : " — " + app.statusMessage)),
                table);
        content.setPadding(new Insets(24, 28, 24, 20));
        detail.getChildren().setAll(content);
    }

    private static String answerText(FormField f) {
        if (f.type == com.trueapply.model.FieldType.FILE && f.hasAnswer()) {
            return f.answer.substring(Math.max(f.answer.lastIndexOf('/'), f.answer.lastIndexOf('\\')) + 1);
        }
        return f.displayAnswer();
    }

    private static TableColumn<FormField, String> column(String name, Function<FormField, String> getter,
                                                         double width, boolean wrap) {
        TableColumn<FormField, String> col = new TableColumn<>(name);
        col.setPrefWidth(width);
        col.setCellValueFactory(c -> new ReadOnlyStringWrapper(com.trueapply.util.Text.orEmpty(getter.apply(c.getValue()))));
        if (wrap) {
            col.setCellFactory(c -> new TableCell<>() {
                private final Text text = new Text();

                {
                    text.wrappingWidthProperty().bind(col.widthProperty().subtract(16));
                    text.getStyleClass().add("wrapping-cell-text");
                }

                @Override
                protected void updateItem(String value, boolean empty) {
                    super.updateItem(value, empty);
                    text.setText(empty ? null : value);
                    setGraphic(empty ? null : text);
                }
            });
        }
        return col;
    }
}
