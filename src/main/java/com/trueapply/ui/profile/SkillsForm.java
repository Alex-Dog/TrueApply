package com.trueapply.ui.profile;

import atlantafx.base.theme.Styles;
import com.trueapply.model.UserProfile;
import com.trueapply.ui.Ui;
import com.trueapply.util.Text;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.feather.Feather;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/** Skills plus the factual answers the user asked us to remember. */
public class SkillsForm implements ProfileSection {
    private static final String ARROW = "  →  ";

    private final TextArea skills = Ui.textArea("", 4);
    private final ListView<String> saved = new ListView<>();
    private final Map<String, String> answers = new LinkedHashMap<>();
    private final VBox root;

    public SkillsForm() {
        skills.setPromptText("Comma or newline separated, e.g. Java, SQL, Figma");
        saved.setPrefHeight(220);
        saved.setPlaceholder(Ui.muted("When you answer a “missing info” question and tick “Remember”, it shows up here."));
        Button remove = Ui.button("Forget selected", Feather.TRASH_2, Styles.SMALL);
        remove.disableProperty().bind(saved.getSelectionModel().selectedItemProperty().isNull());
        remove.setOnAction(e -> {
            String item = saved.getSelectionModel().getSelectedItem();
            if (item != null) {
                answers.remove(item.substring(0, item.indexOf(ARROW)));
                saved.getItems().remove(item);
            }
        });
        root = new VBox(10,
                Ui.heading("Skills"), skills,
                Ui.heading("Saved answers"),
                Ui.muted("Reused for matching factual questions on future applications. Creative answers are never saved."),
                saved, remove);
    }

    @Override
    public Node view() {
        return root;
    }

    @Override
    public void load(UserProfile profile) {
        skills.setText(String.join(", ", profile.skills));
        answers.clear();
        answers.putAll(profile.savedAnswers);
        saved.getItems().setAll(answers.entrySet().stream().map(e -> e.getKey() + ARROW + e.getValue()).toList());
    }

    @Override
    public void save(UserProfile profile) {
        profile.skills = new ArrayList<>(Text.splitList(skills.getText()));
        profile.savedAnswers = new LinkedHashMap<>(answers);
    }
}
