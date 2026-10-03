package com.trueapply.ui;

import atlantafx.base.theme.Styles;
import com.trueapply.model.AnswerSource;
import com.trueapply.model.FieldType;
import com.trueapply.model.FormField;
import com.trueapply.util.Text;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.kordamp.ikonli.feather.Feather;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/** Edits one {@link FormField} with the control that suits its type. */
public class FieldEditor {
    private final FormField field;
    private final Node node;
    private final Supplier<Object> value;
    private Control focusTarget;

    public FieldEditor(FormField field, boolean editable) {
        this.field = field;
        switch (field.type) {
            case TEXTAREA -> {
                TextArea area = Ui.textArea(field.answer, field.needsHuman() ? 8 : 4);
                Label counter = Ui.muted("");
                counter.getStyleClass().add(Styles.TEXT_SMALL);
                area.textProperty().addListener((o, a, b) -> counter.setText(b.length() + " characters"));
                counter.setText(area.getText().length() + " characters");
                area.setEditable(editable);
                node = new VBox(4, area, counter);
                value = area::getText;
                focusTarget = area;
            }
            case SINGLE_SELECT -> {
                if (field.options.isEmpty()) {
                    TextField text = textField(editable);
                    node = text;
                    value = text::getText;
                } else {
                    ComboBox<String> combo = new ComboBox<>();
                    combo.getItems().setAll(field.options);
                    combo.setValue(field.answer);
                    combo.setMaxWidth(Double.MAX_VALUE);
                    combo.setDisable(!editable);
                    // Searchable site pickers (Workday "prompt") also accept a typed value to search for.
                    boolean typeable = "prompt".equals(field.control);
                    combo.setEditable(typeable);
                    node = combo;
                    value = typeable ? () -> combo.getEditor().getText() : combo::getValue;
                    focusTarget = combo;
                }
            }
            case MULTI_SELECT -> {
                VBox pane = new VBox(8); // one option per line; long compliance options read badly side by side
                List<CheckBox> boxes = new ArrayList<>();
                for (String option : field.options) {
                    CheckBox box = new CheckBox(option);
                    box.setSelected(field.answers != null && field.answers.contains(option));
                    box.setDisable(!editable);
                    boxes.add(box);
                }
                pane.getChildren().addAll(boxes);
                node = pane;
                value = () -> boxes.stream().filter(CheckBox::isSelected).map(CheckBox::getText).toList();
            }
            case FILE -> {
                Label name = new Label(Text.isBlank(field.answer) ? "No file chosen" : new File(field.answer).getName());
                String[] chosen = {field.answer};
                Button choose = Ui.button("Choose file…", Feather.UPLOAD);
                choose.setDisable(!editable);
                choose.setOnAction(e -> {
                    File file = new FileChooser().showOpenDialog(choose.getScene().getWindow());
                    if (file != null) {
                        chosen[0] = file.getAbsolutePath();
                        name.setText(file.getName());
                    }
                });
                node = Ui.row(choose, name);
                value = () -> chosen[0];
                focusTarget = choose;
            }
            default -> {
                TextField text = textField(editable);
                node = text;
                value = text::getText;
            }
        }
    }

    private TextField textField(boolean editable) {
        TextField text = new TextField(field.answer == null ? "" : field.answer);
        text.setEditable(editable);
        focusTarget = text;
        return text;
    }

    public Node node() {
        return node;
    }

    public FormField field() {
        return field;
    }

    public boolean isAnswered() {
        Object v = value.get();
        if (v instanceof List<?> list) return !list.isEmpty();
        return v != null && !v.toString().isBlank();
    }

    public void markInvalid(boolean invalid) {
        if (focusTarget != null) focusTarget.pseudoClassStateChanged(Styles.STATE_DANGER, invalid);
    }

    /** Writes the control's value into the field. Changed answers are attributed to the user. */
    @SuppressWarnings("unchecked")
    public boolean commit() {
        Object v = value.get();
        boolean changed;
        if (field.type == FieldType.MULTI_SELECT) {
            List<String> selected = new ArrayList<>((List<String>) v);
            changed = !selected.equals(field.answers);
            field.answers = selected;
        } else {
            String s = v == null ? null : v.toString().strip();
            if (s != null && s.isEmpty()) s = null;
            changed = !Objects.equals(s, field.answer);
            field.answer = s;
        }
        if (changed) field.source = field.hasAnswer() ? AnswerSource.USER : AnswerSource.NONE;
        return changed;
    }
}
