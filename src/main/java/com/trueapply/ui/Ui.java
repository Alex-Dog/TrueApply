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
import java.util.List;

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

    private static javafx.stage.Window mainWindow;
    private static List<javafx.scene.image.Image> appIcons;

    /** The app window: dialogs open centred over it and share its icon. */
    public static void setMainWindow(javafx.stage.Stage stage) {
        mainWindow = stage;
        stage.getIcons().setAll(appIcons());
    }

    /** The app icon at the sizes Windows asks for (title bar, taskbar, Alt-Tab), scaled smoothly. */
    public static List<javafx.scene.image.Image> appIcons() {
        if (appIcons == null) {
            var url = Ui.class.getResource("/icons/icon.png");
            appIcons = url == null ? List.of() : java.util.stream.IntStream.of(16, 24, 32, 48, 64, 128, 256)
                    .mapToObj(size -> new javafx.scene.image.Image(url.toExternalForm(), size, size, true, true))
                    .toList();
        }
        return appIcons;
    }

    /** TrueApply's title and icon on a dialog, owned by the app window. */
    public static void brand(javafx.scene.control.Dialog<?> dialog) {
        if (dialog.getTitle() == null || dialog.getTitle().isBlank() || dialog instanceof Alert) dialog.setTitle("TrueApply");
        if (mainWindow != null && dialog.getOwner() == null) dialog.initOwner(mainWindow);
        if (dialog.getDialogPane().getScene().getWindow() instanceof javafx.stage.Stage stage) {
            stage.getIcons().setAll(appIcons());
        }
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
        return wrapping(label);
    }

    public static Label bold(String text) {
        Label label = new Label(text);
        label.getStyleClass().add(Styles.TEXT_BOLD);
        return wrapping(label);
    }

    /** Small rounded tag. Variants: accent, success, warning, danger, neutral. */
    public static Label chip(String text, String variant) {
        Label label = new Label(text);
        label.getStyleClass().addAll("chip", "chip-" + variant);
        label.setMinWidth(Region.USE_PREF_SIZE); // never squeeze a tag into "…"
        return label;
    }

    /** Wraps onto as many lines as needed instead of truncating with "…". */
    public static Label wrapping(Label label) {
        label.setWrapText(true);
        label.setMinHeight(Region.USE_PREF_SIZE);
        label.setMaxWidth(Double.MAX_VALUE);
        return label;
    }

    public static Label statusChip(ApplicationStatus status) {
        String variant = switch (status) {
            case NEEDS_INPUT, AWAITING_CONFIRMATION -> "warning";
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
        String page = """
                <html><head><style>
                body { font-family: 'Segoe UI', system-ui, sans-serif; font-size: 13px; line-height: 1.5;
                       color: #24292f; margin: 4px 8px; }
                a { color: #0969da; }
                </style></head><body>%s</body></html>""".formatted(html == null ? "" : html);
        view.getEngine().loadContent(page);
        // A clicked link opens in the user's browser; the description stays put.
        view.getEngine().locationProperty().addListener((o, was, now) -> {
            if (now != null && (now.startsWith("http:") || now.startsWith("https:") || now.startsWith("mailto:"))) {
                javafx.application.Platform.runLater(() -> view.getEngine().loadContent(page));
                openUrl(now);
            }
        });
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
        brand(alert);
        alert.setHeaderText(header);
        alert.showAndWait();
    }

    public static void info(String header, String body) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, body, ButtonType.OK);
        brand(alert);
        alert.setHeaderText(header);
        alert.showAndWait();
    }

    public static boolean confirm(String header, String body) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, body, ButtonType.OK, ButtonType.CANCEL);
        brand(alert);
        alert.setHeaderText(header);
        return alert.showAndWait().filter(b -> b == ButtonType.OK).isPresent();
    }

    /**
     * A small notification in the bottom-right corner of the app that closes itself; for
     * confirmations that don't need a decision. Falls back to a dialog outside the main window.
     */
    public static void toast(Node anyNodeInWindow, String message, Ikon icon, String style) {
        javafx.scene.Scene scene = anyNodeInWindow == null ? null : anyNodeInWindow.getScene();
        if (scene == null || !(scene.getRoot() instanceof javafx.scene.layout.StackPane layer)) {
            info(message, "");
            return;
        }
        atlantafx.base.controls.Notification toast = new atlantafx.base.controls.Notification(message, icon(icon));
        toast.getStyleClass().addAll(style, Styles.ELEVATED_2);
        toast.setPrefWidth(420);
        toast.setMaxWidth(420);
        toast.setMaxHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        javafx.scene.layout.StackPane.setAlignment(toast, javafx.geometry.Pos.BOTTOM_RIGHT);
        javafx.scene.layout.StackPane.setMargin(toast, new javafx.geometry.Insets(0, 24, 24, 0));
        Runnable close = () -> {
            if (!layer.getChildren().contains(toast)) return;
            var out = atlantafx.base.util.Animations.fadeOutDown(toast, javafx.util.Duration.millis(250));
            out.setOnFinished(e -> layer.getChildren().remove(toast));
            out.playFromStart();
        };
        toast.setOnClose(e -> close.run());
        layer.getChildren().add(toast);
        atlantafx.base.util.Animations.fadeInUp(toast, javafx.util.Duration.millis(250)).playFromStart();
        javafx.animation.PauseTransition stay = new javafx.animation.PauseTransition(javafx.util.Duration.seconds(4));
        stay.setOnFinished(e -> close.run());
        toast.hoverProperty().addListener((o, was, hovering) -> {
            if (hovering) stay.stop();
            else stay.playFromStart(); // read at your own pace
        });
        stay.play();
    }

    /** Yes / No question; empty if the dialog was closed without choosing. */
    public static java.util.Optional<Boolean> askYesNo(String header, String body) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, body, ButtonType.YES, ButtonType.NO);
        brand(alert);
        alert.setHeaderText(header);
        return alert.showAndWait().filter(b -> b == ButtonType.YES || b == ButtonType.NO).map(b -> b == ButtonType.YES);
    }

    public static String rootMessage(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null && cur.getCause() != cur && cur.getMessage() == null) cur = cur.getCause();
        return cur.getMessage() == null ? cur.getClass().getSimpleName() : cur.getMessage();
    }
}
