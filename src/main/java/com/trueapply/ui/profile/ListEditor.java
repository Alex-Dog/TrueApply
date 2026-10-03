package com.trueapply.ui.profile;

import atlantafx.base.theme.Styles;
import com.trueapply.ui.Ui;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.feather.Feather;

import java.util.ArrayList;
import java.util.List;

/** Master/detail editor for a list of profile entries (jobs, schools). */
abstract class ListEditor<T> implements ProfileSection {
    protected final ListView<T> list = new ListView<>();
    private final BorderPane root = new BorderPane();
    private final Node form;
    private T current;

    ListEditor(String addLabel) {
        form = buildForm();
        list.setPrefWidth(260);
        list.setCellFactory(v -> new ListCell<>() {
            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : describe(item));
            }
        });
        list.getSelectionModel().selectedItemProperty().addListener((o, previous, next) -> select(next));

        Button add = Ui.button(addLabel, Feather.PLUS, Styles.SMALL);
        add.setOnAction(e -> {
            commit();
            T item = create();
            list.getItems().add(item);
            list.getSelectionModel().select(item);
        });
        Button remove = Ui.button("Remove", Feather.TRASH_2, Styles.SMALL, Styles.DANGER);
        remove.setOnAction(e -> {
            T item = list.getSelectionModel().getSelectedItem();
            if (item != null) {
                current = null;
                list.getItems().remove(item);
            }
        });
        remove.disableProperty().bind(list.getSelectionModel().selectedItemProperty().isNull());

        VBox left = new VBox(8, list, new HBox(8, add, remove));
        VBox.setVgrow(list, javafx.scene.layout.Priority.ALWAYS);
        ScrollPane scroll = new ScrollPane(form);
        scroll.setFitToWidth(true);
        scroll.setPadding(new Insets(0, 0, 0, 16));
        scroll.disableProperty().bind(list.getSelectionModel().selectedItemProperty().isNull());
        root.setLeft(left);
        root.setCenter(scroll);
        root.setPrefHeight(420);
    }

    protected abstract Node buildForm();

    protected abstract T create();

    protected abstract String describe(T item);

    protected abstract void loadItem(T item);

    protected abstract void commitItem(T item);

    protected abstract List<T> itemsOf(com.trueapply.model.UserProfile profile);

    protected abstract void setItems(com.trueapply.model.UserProfile profile, List<T> items);

    private void select(T next) {
        commit();
        current = next;
        if (next != null) loadItem(next);
    }

    private void commit() {
        if (current != null) {
            commitItem(current);
            list.refresh();
        }
    }

    @Override
    public Node view() {
        return root;
    }

    @Override
    public void load(com.trueapply.model.UserProfile profile) {
        current = null;
        list.getItems().setAll(itemsOf(profile));
        if (!list.getItems().isEmpty()) list.getSelectionModel().selectFirst();
    }

    @Override
    public void save(com.trueapply.model.UserProfile profile) {
        commit();
        setItems(profile, new ArrayList<>(list.getItems()));
    }
}
