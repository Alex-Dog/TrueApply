package com.trueapply.ui.profile;

import com.trueapply.model.UserProfile;
import com.trueapply.model.UserProfile.Education;
import com.trueapply.ui.Ui;
import javafx.scene.Node;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;

import java.util.List;

public class EducationEditor extends ListEditor<Education> {
    private TextField school;
    private TextField degree;
    private TextField field;
    private TextField start;
    private TextField end;
    private TextField gpa;

    public EducationEditor() {
        super("Add school");
    }

    @Override
    protected Node buildForm() {
        school = new TextField();
        degree = new TextField();
        degree.setPromptText("e.g. Bachelor of Science");
        field = new TextField();
        field.setPromptText("e.g. Computer Science");
        start = new TextField();
        end = new TextField();
        end.setPromptText("or expected graduation");
        gpa = new TextField();
        gpa.setPromptText("Optional");
        GridPane grid = Ui.form();
        Ui.addRow(grid, "School", school);
        Ui.addRow(grid, "Degree", degree);
        Ui.addRow(grid, "Field of study", field);
        Ui.addRow(grid, "Start", start);
        Ui.addRow(grid, "End", end);
        Ui.addRow(grid, "GPA", gpa);
        return grid;
    }

    @Override
    protected Education create() {
        Education e = new Education();
        e.school = "New school";
        return e;
    }

    @Override
    protected String describe(Education e) {
        return e.toString();
    }

    @Override
    protected void loadItem(Education e) {
        school.setText(e.school);
        degree.setText(e.degree);
        field.setText(e.fieldOfStudy);
        start.setText(e.startDate);
        end.setText(e.endDate);
        gpa.setText(e.gpa);
    }

    @Override
    protected void commitItem(Education e) {
        e.school = school.getText().trim();
        e.degree = degree.getText().trim();
        e.fieldOfStudy = field.getText().trim();
        e.startDate = start.getText().trim();
        e.endDate = end.getText().trim();
        e.gpa = gpa.getText().trim();
    }

    @Override
    protected List<Education> itemsOf(UserProfile profile) {
        return profile.education;
    }

    @Override
    protected void setItems(UserProfile profile, List<Education> items) {
        profile.education = items;
    }
}
