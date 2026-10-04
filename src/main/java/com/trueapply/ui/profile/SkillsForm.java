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
    private final TextArea formSkills = Ui.textArea("", 4);
    private final ListView<String> saved = new ListView<>();
    private final Map<String, String> answers = new LinkedHashMap<>();
    private final VBox root;

    public SkillsForm() {
        skills.setPromptText("Comma or newline separated, e.g. Java, SQL, Figma");
        formSkills.setPromptText("e.g. Java, Python, SQL, Git, Agile");
        Button copy = Ui.button("Start from my skills above", Feather.COPY, Styles.SMALL);
        copy.setOnAction(e -> formSkills.setText(skills.getText()));
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
                Ui.heading("Skills"),
                Ui.muted("Background for the AI when answering factual questions. Re-reading your resume replaces this list."),
                skills,
                Ui.heading("Skills to select on applications"),
                Ui.muted("When a form asks you to pick skills from a list, exactly these are selected, in this order,"
                        + " and anything else already selected there is removed. Leave empty to let the AI choose from"
                        + " your skills above."),
                formSkills, copy,
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
        formSkills.setText(String.join(", ", profile.formSkills));
        answers.clear();
        answers.putAll(profile.savedAnswers);
        saved.getItems().setAll(answers.entrySet().stream().map(e -> e.getKey() + ARROW + e.getValue()).toList());
    }

    @Override
    public void save(UserProfile profile) {
        profile.skills = new ArrayList<>(Text.splitList(skills.getText()));
        profile.formSkills = new ArrayList<>(Text.splitList(formSkills.getText()));
        profile.savedAnswers = new LinkedHashMap<>(answers);
    }
}
