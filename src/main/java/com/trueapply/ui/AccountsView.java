package com.trueapply.ui;

import atlantafx.base.controls.Message;
import atlantafx.base.theme.Styles;
import atlantafx.base.theme.Tweaks;
import com.trueapply.AppContext;
import com.trueapply.model.SavedAccount;
import com.trueapply.security.PasswordGenerator;
import com.trueapply.util.Text;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.feather.Feather;

import java.util.List;
import java.util.function.Function;

/** Logins created on job sites, with their (encrypted at rest) passwords. */
public class AccountsView implements View {
    private final AppContext ctx;
    private final TableView<SavedAccount> table = new TableView<>();
    private final ToggleButton reveal = new ToggleButton("Show passwords", Ui.icon(Feather.EYE));
    private final VBox root;

    public AccountsView(AppContext ctx) {
        this.ctx = ctx;
        table.getStyleClass().addAll(Styles.STRIPED, Tweaks.EDGE_TO_EDGE);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(Ui.muted("No accounts yet."));
        table.getColumns().addAll(List.of(
                column("Company", a -> a.company, 160),
                column("Website", a -> a.siteUrl, 240),
                column("Username / email", a -> a.username, 220),
                column("Password", a -> reveal.isSelected() ? a.password : "••••••••••", 200),
                column("Created", a -> Ui.formatTime(a.createdAt), 160)));
        reveal.selectedProperty().addListener((o, a, b) -> table.refresh());
        VBox.setVgrow(table, Priority.ALWAYS);

        Button add = Ui.button("Add account", Feather.PLUS);
        add.setOnAction(e -> addAccount());
        Button copy = Ui.button("Copy password", Feather.COPY);
        copy.setOnAction(e -> {
            SavedAccount a = table.getSelectionModel().getSelectedItem();
            ClipboardContent content = new ClipboardContent();
            content.putString(a.password);
            Clipboard.getSystemClipboard().setContent(content);
        });
        Button open = Ui.button("Open site", Feather.EXTERNAL_LINK);
        open.setOnAction(e -> Ui.openUrl(table.getSelectionModel().getSelectedItem().siteUrl));
        Button delete = Ui.button("Delete", Feather.TRASH_2, Styles.DANGER, Styles.FLAT);
        delete.setOnAction(e -> {
            SavedAccount a = table.getSelectionModel().getSelectedItem();
            if (Ui.confirm("Delete the saved login for " + a.company + "?",
                    "This only removes it from TrueApply; the account on the site still exists.")) {
                ctx.accounts.delete(a.id);
                refresh();
            }
        });
        for (Button b : List.of(copy, open, delete)) {
            b.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        }

        Message note = new Message("Where accounts come from",
                "When a job site requires an account, TrueApply creates one with a random password and records it here. "
                        + "Greenhouse applications don't need accounts. Passwords are encrypted on disk.",
                Ui.icon(Feather.KEY));
        root = Ui.page("Accounts", null, note, Ui.row(add, copy, open, Ui.hgrow(), reveal, delete), table);
    }

    private static TableColumn<SavedAccount, String> column(String name, Function<SavedAccount, String> getter, double width) {
        TableColumn<SavedAccount, String> col = new TableColumn<>(name);
        col.setPrefWidth(width);
        col.setCellValueFactory(c -> new ReadOnlyStringWrapper(Text.orEmpty(getter.apply(c.getValue()))));
        return col;
    }

    private void addAccount() {
        Dialog<SavedAccount> dialog = new Dialog<>();
        dialog.setTitle("Add account");
        Ui.brand(dialog);
        dialog.setHeaderText("Save a job-site login");
        TextField company = new TextField();
        TextField site = new TextField();
        site.setPromptText("https://…");
        TextField username = new TextField(ctx.profiles.load().personal.email);
        TextField password = new TextField(PasswordGenerator.generate());
        Button regenerate = Ui.button("Generate", Feather.REFRESH_CW, Styles.FLAT);
        regenerate.setOnAction(e -> password.setText(PasswordGenerator.generate()));
        GridPane grid = Ui.form();
        Ui.addRow(grid, "Company", company);
        Ui.addRow(grid, "Website", site);
        Ui.addRow(grid, "Username / email", username);
        Ui.addRow(grid, "Password", Ui.row(password, regenerate));
        javafx.scene.layout.HBox.setHgrow(password, Priority.ALWAYS);
        grid.setPrefWidth(520);
        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.getDialogPane().lookupButton(ButtonType.OK).disableProperty().bind(
                company.textProperty().isEmpty().or(username.textProperty().isEmpty()).or(password.textProperty().isEmpty()));
        dialog.setResultConverter(button -> {
            if (button != ButtonType.OK) return null;
            SavedAccount a = new SavedAccount();
            a.company = company.getText().trim();
            a.siteUrl = site.getText().trim();
            a.username = username.getText().trim();
            a.password = password.getText();
            return a;
        });
        dialog.showAndWait().ifPresent(a -> {
            ctx.accounts.insert(a);
            refresh();
        });
    }

    @Override
    public Node root() {
        return root;
    }

    @Override
    public void refresh() {
        try {
            table.getItems().setAll(ctx.accounts.findAll());
        } catch (IllegalStateException e) {
            Ui.error("Couldn't decrypt saved passwords", e);
        }
    }
}
