package com.trueapply.ui;

import atlantafx.base.theme.Styles;
import com.trueapply.model.ApplicationStatus;
import com.trueapply.model.FieldCategory;
import javafx.application.HostServices;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebView;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.javafx.FontIcon;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Small helpers so views read as layout, not boilerplate. */
public final class Ui {
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a")
            .withZone(ZoneId.systemDefault());
    private static HostServices hostServices;

    private Ui() {
    }

    public static void init(HostServices services) {
        hostServices = services;
    }

    public static void openUrl(String url) {
        if (hostServices != null && url != null && !url.isBlank()) hostServices.showDocument(url);
    }

    public static Label title(String text) {
        Label label = new Label(text);
        label.getStyleClass().add(Styles.TITLE_2);
        return label;
    }

    public static Label heading(String text) {
        Label label = new Label(text);
        label.getStyleClass().add(Styles.TITLE_4);
        return label;
    }

    public static Label muted(String text) {
        Label label = new Label(text);
        label.getStyleClass().add(Styles.TEXT_MUTED);
        label.setWrapText(true);
        return label;
    }

    public static Label bold(String text) {
        Label label = new Label(text);
        label.getStyleClass().add(Styles.TEXT_BOLD);
        label.setWrapText(true);
        return label;
    }

    /** Small rounded tag. Variants: accent, success, warning, danger, neutral. */
    public static Label chip(String text, String variant) {
        Label label = new Label(text);
        label.getStyleClass().addAll("chip", "chip-" + variant);
        return label;
    }

    public static Label statusChip(ApplicationStatus status) {
        String variant = switch (status) {
            case NEEDS_INPUT -> "warning";
            case SUBMITTED -> "success";
            case FAILED -> "danger";
            case DRY_RUN, READY -> "accent";
            default -> "neutral";
        };
        return chip(status.displayName(), variant);
    }

    public static Label categoryChip(FieldCategory category) {
        String variant = switch (category) {
            case CREATIVE -> "warning";
            case MISSING_INFO -> "danger";
            case DEMOGRAPHIC -> "accent";
            case SKIPPED -> "neutral";
            default -> "success";
        };
        return chip(category.displayName(), variant);
    }

    public static FontIcon icon(Ikon ikon) {
        return new FontIcon(ikon);
    }

    public static Button button(String text, Ikon ikon, String... styles) {
        Button button = new Button(text, ikon == null ? null : icon(ikon));
        button.getStyleClass().addAll(styles);
        return button;
    }

    public static Region hgrow() {
        Region region = new Region();
        HBox.setHgrow(region, Priority.ALWAYS);
        return region;
    }

    public static HBox row(Node... nodes) {
        HBox box = new HBox(8, nodes);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    /** Standard page: title, optional subtitle, then content. */
    public static VBox page(String title, String subtitle, Node... content) {
        VBox box = new VBox(16);
        box.setPadding(new Insets(24, 28, 24, 28));
        VBox header = new VBox(4, title(title));
        if (subtitle != null) header.getChildren().add(muted(subtitle));
        box.getChildren().add(header);
        box.getChildren().addAll(content);
        return box;
    }

    /** Two-column label/control grid. */
    public static GridPane form() {
        GridPane grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(10);
        ColumnConstraints labels = new ColumnConstraints();
        labels.setMinWidth(170);
        ColumnConstraints controls = new ColumnConstraints();
        controls.setHgrow(Priority.ALWAYS);
        controls.setFillWidth(true);
        grid.getColumnConstraints().addAll(labels, controls);
        return grid;
    }

    public static void addRow(GridPane grid, String label, Node control) {
        int row = grid.getRowCount();
        Label l = new Label(label);
        l.setWrapText(true);
        GridPane.setValignment(l, javafx.geometry.VPos.TOP);
        l.setPadding(new Insets(5, 0, 0, 0));
        grid.add(l, 0, row);
        grid.add(control, 1, row);
        if (control instanceof Region r) r.setMaxWidth(Double.MAX_VALUE);
    }

    public static TextArea textArea(String text, int rows) {
        TextArea area = new TextArea(text == null ? "" : text);
        area.setWrapText(true);
        area.setPrefRowCount(rows);
        return area;
    }

    public static WebView html(String html, double height) {
        WebView view = new WebView();
        view.setContextMenuEnabled(false);
        view.setPrefHeight(height);
        view.getEngine().loadContent("""
                <html><head><style>
                body { font-family: 'Segoe UI', system-ui, sans-serif; font-size: 13px; line-height: 1.5;
                       color: #24292f; margin: 4px 8px; }
                a { color: #0969da; }
                </style></head><body>%s</body></html>""".formatted(html == null ? "" : html));
        return view;
    }

    public static String formatTime(Instant instant) {
        return instant == null ? "" : DATE_TIME.format(instant);
    }

    public static String ago(Instant instant) {
        if (instant == null) return "";
        Duration d = Duration.between(instant, Instant.now());
        if (d.toDays() >= 1) return d.toDays() + "d ago";
        if (d.toHours() >= 1) return d.toHours() + "h ago";
        return Math.max(1, d.toMinutes()) + "m ago";
    }

    public static void error(String header, Throwable error) {
        Alert alert = new Alert(Alert.AlertType.ERROR, rootMessage(error), ButtonType.OK);
        alert.setHeaderText(header);
        alert.showAndWait();
    }

    public static void info(String header, String body) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, body, ButtonType.OK);
        alert.setHeaderText(header);
        alert.showAndWait();
    }

    public static boolean confirm(String header, String body) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, body, ButtonType.OK, ButtonType.CANCEL);
        alert.setHeaderText(header);
        return alert.showAndWait().filter(b -> b == ButtonType.OK).isPresent();
    }

    public static String rootMessage(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null && cur.getCause() != cur && cur.getMessage() == null) cur = cur.getCause();
        return cur.getMessage() == null ? cur.getClass().getSimpleName() : cur.getMessage();
    }
}
