package com.trueapply.ui.profile;

import com.trueapply.model.UserProfile;
import com.trueapply.ui.Ui;
import com.trueapply.util.Text;
import javafx.scene.Node;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;

public class PersonalForm implements ProfileSection {
    private final TextField firstName = new TextField();
    private final TextField lastName = new TextField();
    private final TextField preferredName = new TextField();
    private final TextField email = new TextField();
    private final TextField phone = new TextField();
    private final TextField city = new TextField();
    private final TextField state = new TextField();
    private final TextField country = new TextField();
    private final TextField postalCode = new TextField();
    private final TextField linkedin = new TextField();
    private final TextField github = new TextField();
    private final TextField website = new TextField();
    private final GridPane grid = Ui.form();

    public PersonalForm() {
        preferredName.setPromptText("Optional");
        linkedin.setPromptText("https://www.linkedin.com/in/…");
        github.setPromptText("Optional");
        website.setPromptText("Portfolio or personal site (optional)");
        Ui.addRow(grid, "First name *", firstName);
        Ui.addRow(grid, "Last name *", lastName);
        Ui.addRow(grid, "Preferred name", preferredName);
        Ui.addRow(grid, "Email *", email);
        Ui.addRow(grid, "Phone", phone);
        Ui.addRow(grid, "City", city);
        Ui.addRow(grid, "State / region", state);
        Ui.addRow(grid, "Country", country);
        Ui.addRow(grid, "Postal code", postalCode);
        Ui.addRow(grid, "LinkedIn", linkedin);
        Ui.addRow(grid, "GitHub", github);
        Ui.addRow(grid, "Website", website);
    }

    @Override
    public Node view() {
        return grid;
    }

    @Override
    public void load(UserProfile profile) {
        UserProfile.PersonalInfo p = profile.personal;
        firstName.setText(p.firstName);
        lastName.setText(p.lastName);
        preferredName.setText(p.preferredName);
        email.setText(p.email);
        phone.setText(p.phone);
        city.setText(p.city);
        state.setText(p.state);
        country.setText(p.country);
        postalCode.setText(p.postalCode);
        linkedin.setText(p.linkedinUrl);
        github.setText(p.githubUrl);
        website.setText(p.websiteUrl);
    }

    @Override
    public void save(UserProfile profile) {
        UserProfile.PersonalInfo p = profile.personal;
        p.firstName = firstName.getText().trim();
        p.lastName = lastName.getText().trim();
        p.preferredName = preferredName.getText().trim();
        p.email = email.getText().trim();
        p.phone = phone.getText().trim();
        p.city = city.getText().trim();
        p.state = state.getText().trim();
        p.country = country.getText().trim();
        p.postalCode = postalCode.getText().trim();
        p.linkedinUrl = linkedin.getText().trim();
        p.githubUrl = github.getText().trim();
        p.websiteUrl = website.getText().trim();
    }

    @Override
    public String validate() {
        if (Text.isBlank(firstName.getText()) || Text.isBlank(lastName.getText())) return "Please enter your name.";
        if (!email.getText().contains("@")) return "Please enter a valid email address.";
        return null;
    }
}
