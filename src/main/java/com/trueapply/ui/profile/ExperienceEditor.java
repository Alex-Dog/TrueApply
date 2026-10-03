package com.trueapply.ui.profile;

import com.trueapply.model.UserProfile;
import com.trueapply.model.UserProfile.WorkExperience;
import com.trueapply.ui.Ui;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;

import java.util.List;

public class ExperienceEditor extends ListEditor<WorkExperience> {
    private TextField company;
    private TextField title;
    private TextField location;
    private TextField start;
    private TextField end;
    private CheckBox current;
    private TextArea description;

    public ExperienceEditor() {
        super("Add job");
    }

    @Override
    protected Node buildForm() {
        company = new TextField();
        title = new TextField();
        location = new TextField();
        start = new TextField();
        start.setPromptText("e.g. Jun 2023");
        end = new TextField();
        current = new CheckBox("I currently work here");
        end.disableProperty().bind(current.selectedProperty());
        description = Ui.textArea("", 8);
        GridPane grid = Ui.form();
        Ui.addRow(grid, "Company", company);
        Ui.addRow(grid, "Title", title);
        Ui.addRow(grid, "Location", location);
        Ui.addRow(grid, "Start", start);
        Ui.addRow(grid, "End", end);
        Ui.addRow(grid, "", current);
        Ui.addRow(grid, "Description", description);
        return grid;
    }

    @Override
    protected WorkExperience create() {
        WorkExperience w = new WorkExperience();
        w.title = "New position";
        return w;
    }

    @Override
    protected String describe(WorkExperience w) {
        return w.title + (w.company.isBlank() ? "" : " @ " + w.company);
    }

    @Override
    protected void loadItem(WorkExperience w) {
        company.setText(w.company);
        title.setText(w.title);
        location.setText(w.location);
        start.setText(w.startDate);
        end.setText(w.endDate);
        current.setSelected(w.current);
        description.setText(w.description);
    }

    @Override
    protected void commitItem(WorkExperience w) {
        w.company = company.getText().trim();
        w.title = title.getText().trim();
        w.location = location.getText().trim();
        w.startDate = start.getText().trim();
        w.endDate = current.isSelected() ? "" : end.getText().trim();
        w.current = current.isSelected();
        w.description = description.getText().strip();
    }

    @Override
    protected List<WorkExperience> itemsOf(UserProfile profile) {
        return profile.experience;
    }

    @Override
    protected void setItems(UserProfile profile, List<WorkExperience> items) {
        profile.experience = items;
    }
}
