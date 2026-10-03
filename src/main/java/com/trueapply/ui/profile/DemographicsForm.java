package com.trueapply.ui.profile;

import atlantafx.base.controls.Message;
import atlantafx.base.theme.Styles;
import com.trueapply.model.UserProfile;
import com.trueapply.model.UserProfile.Demographics;
import com.trueapply.ui.Ui;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.feather.Feather;

import java.util.ArrayList;
import java.util.List;

/** Work authorization and voluntary self-identification answers. */
public class DemographicsForm implements ProfileSection {
    private static final List<String> RACES = List.of(
            "American Indian or Alaska Native", "Asian", "Black or African American",
            "Native Hawaiian or Other Pacific Islander", "White", "Two or More Races");

    private final ComboBox<String> authorized = combo("Yes", "No");
    private final ComboBox<String> sponsorship = combo("No", "Yes");
    private final ComboBox<String> relocate = combo("No", "Yes");
    private final ComboBox<String> gender = combo("Male", "Female", "Non-binary", Demographics.DECLINE);
    private final ComboBox<String> hispanic = combo("Yes", "No", Demographics.DECLINE);
    private final List<CheckBox> race = RACES.stream().map(CheckBox::new).toList();
    private final ComboBox<String> veteran = combo(
            "I am not a protected veteran",
            "I identify as one or more of the classifications of a protected veteran",
            Demographics.DECLINE);
    private final ComboBox<String> disability = combo(
            "No, I do not have a disability",
            "Yes, I have a disability (or previously had one)",
            Demographics.DECLINE);
    private final ComboBox<String> lgbtq = combo("Yes", "No", Demographics.DECLINE);
    private final TextField pronouns = new TextField();
    private final VBox root;

    public DemographicsForm() {
        gender.setEditable(true);
        pronouns.setPromptText("Optional, e.g. she/her");

        GridPane work = Ui.form();
        Ui.addRow(work, "Legally authorized to work in your country?", authorized);
        Ui.addRow(work, "Will you need visa sponsorship (now or later)?", sponsorship);
        Ui.addRow(work, "Willing to relocate?", relocate);

        GridPane eeo = Ui.form();
        Ui.addRow(eeo, "Gender", gender);
        Ui.addRow(eeo, "Hispanic or Latino?", hispanic);
        VBox raceBox = new VBox(6);
        raceBox.getChildren().addAll(race);
        raceBox.getChildren().add(Ui.muted("Leave all unchecked to decline."));
        Ui.addRow(eeo, "Race", raceBox);
        Ui.addRow(eeo, "Veteran status", veteran);
        Ui.addRow(eeo, "Disability status", disability);
        Ui.addRow(eeo, "LGBTQ+", lgbtq);
        Ui.addRow(eeo, "Pronouns", pronouns);

        Message note = new Message("Only used for voluntary questions",
                "These answers are mapped to self-identification questions on forms. "
                        + "Choose “" + Demographics.DECLINE + "” for anything you'd rather not share.",
                Ui.icon(Feather.EYE_OFF));
        note.getStyleClass().add(Styles.ACCENT);

        root = new VBox(18, note, Ui.heading("Work authorization"), work, Ui.heading("Self-identification"), eeo);
    }

    private static ComboBox<String> combo(String... items) {
        ComboBox<String> box = new ComboBox<>();
        box.getItems().addAll(items);
        box.setMaxWidth(Double.MAX_VALUE);
        return box;
    }

    @Override
    public Node view() {
        return root;
    }

    @Override
    public void load(UserProfile profile) {
        Demographics d = profile.demographics;
        authorized.setValue(d.authorizedToWork);
        sponsorship.setValue(d.requiresSponsorship);
        relocate.setValue(d.willingToRelocate);
        gender.setValue(d.gender);
        hispanic.setValue(d.hispanicOrLatino);
        race.forEach(box -> box.setSelected(d.race.contains(box.getText())));
        veteran.setValue(d.veteranStatus);
        disability.setValue(d.disabilityStatus);
        lgbtq.setValue(d.lgbtq);
        pronouns.setText(d.pronouns);
    }

    @Override
    public void save(UserProfile profile) {
        Demographics d = profile.demographics;
        d.authorizedToWork = authorized.getValue();
        d.requiresSponsorship = sponsorship.getValue();
        d.willingToRelocate = relocate.getValue();
        d.gender = gender.getValue();
        d.hispanicOrLatino = hispanic.getValue();
        d.race = new ArrayList<>(race.stream().filter(CheckBox::isSelected).map(CheckBox::getText).toList());
        d.veteranStatus = veteran.getValue();
        d.disabilityStatus = disability.getValue();
        d.lgbtq = lgbtq.getValue();
        d.pronouns = pronouns.getText().trim();
    }
}
