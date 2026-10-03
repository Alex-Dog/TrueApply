package com.trueapply.ui.profile;

import atlantafx.base.controls.ToggleSwitch;
import com.trueapply.model.UserProfile;
import com.trueapply.model.UserProfile.JobPreferences;
import com.trueapply.ui.Ui;
import com.trueapply.util.Text;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;

import java.util.ArrayList;

/** What kind of job the user wants; drives discovery filtering. */
public class PreferencesForm implements ProfileSection {
    private final TextArea titles = Ui.textArea("", 3);
    private final TextArea locations = Ui.textArea("", 2);
    private final ToggleSwitch remote = new ToggleSwitch("Include remote jobs");
    private final ComboBox<String> employment = new ComboBox<>();
    private final TextField minSalary = new TextField();
    private final TextArea excludeKeywords = Ui.textArea("", 2);
    private final TextArea excludeCompanies = Ui.textArea("", 2);
    private final ComboBox<String> country = new ComboBox<>();
    private final GridPane grid = Ui.form();

    public PreferencesForm() {
        titles.setPromptText("One per line, e.g.\nSoftware Engineer\nBackend Developer");
        locations.setPromptText("One per line, e.g. San Francisco, New York. Leave empty for anywhere.");
        employment.getItems().addAll("Full-time", "Part-time", "Internship", "Contract", "Any");
        minSalary.setPromptText("Optional, yearly");
        excludeKeywords.setPromptText("Skip titles containing… e.g. Senior, Staff, Manager");
        excludeCompanies.setPromptText("Companies to skip");
        country.getItems().addAll("us", "gb", "ca", "au", "de", "fr", "in", "nl", "nz", "sg", "za");

        Ui.addRow(grid, "Job titles *", titles);
        Ui.addRow(grid, "Locations", locations);
        Ui.addRow(grid, "Remote", remote);
        Ui.addRow(grid, "Employment type", employment);
        Ui.addRow(grid, "Minimum salary", minSalary);
        Ui.addRow(grid, "Exclude title keywords", excludeKeywords);
        Ui.addRow(grid, "Exclude companies", excludeCompanies);
        Ui.addRow(grid, "Adzuna country", country);
    }

    @Override
    public Node view() {
        return grid;
    }

    @Override
    public void load(UserProfile profile) {
        JobPreferences p = profile.preferences;
        titles.setText(String.join("\n", p.titles));
        locations.setText(String.join("\n", p.locations));
        remote.setSelected(p.remoteOk);
        employment.setValue(p.employmentType);
        minSalary.setText(p.minimumSalary == null ? "" : p.minimumSalary.toString());
        excludeKeywords.setText(String.join("\n", p.excludeKeywords));
        excludeCompanies.setText(String.join("\n", p.excludeCompanies));
        country.setValue(p.adzunaCountry);
    }

    @Override
    public void save(UserProfile profile) {
        JobPreferences p = profile.preferences;
        p.titles = new ArrayList<>(Text.splitList(titles.getText()));
        p.locations = new ArrayList<>(Text.splitList(locations.getText()));
        p.remoteOk = remote.isSelected();
        p.employmentType = employment.getValue();
        String salary = minSalary.getText().replaceAll("[^0-9]", "");
        p.minimumSalary = salary.isEmpty() ? null : Integer.valueOf(salary);
        p.excludeKeywords = new ArrayList<>(Text.splitList(excludeKeywords.getText()));
        p.excludeCompanies = new ArrayList<>(Text.splitList(excludeCompanies.getText()));
        p.adzunaCountry = country.getValue();
    }

    @Override
    public String validate() {
        return Text.splitList(titles.getText()).isEmpty() ? "Add at least one job title to search for." : null;
    }
}
