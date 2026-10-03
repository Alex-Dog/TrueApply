package com.trueapply.ui;

import atlantafx.base.theme.Styles;
import com.trueapply.AppContext;
import com.trueapply.model.UserProfile;
import com.trueapply.ui.profile.DemographicsForm;
import com.trueapply.ui.profile.EducationEditor;
import com.trueapply.ui.profile.ExperienceEditor;
import com.trueapply.ui.profile.PersonalForm;
import com.trueapply.ui.profile.PreferencesForm;
import com.trueapply.ui.profile.ProfileSection;
import com.trueapply.ui.profile.ResumePanel;
import com.trueapply.ui.profile.SkillsForm;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.feather.Feather;

import java.util.LinkedHashMap;
import java.util.Map;

/** View and edit everything in the profile. */
public class ProfileView implements View {
    private final AppContext ctx;
    private final Map<String, ProfileSection> sections = new LinkedHashMap<>();
    private final Label saved = Ui.muted("");
    private final VBox root;
    private UserProfile profile;

    public ProfileView(AppContext ctx) {
        this.ctx = ctx;
        sections.put("Resume", new ResumePanel(ctx, this::reloadSections));
        sections.put("Personal", new PersonalForm());
        sections.put("Experience", new ExperienceEditor());
        sections.put("Education", new EducationEditor());
        sections.put("Skills & saved answers", new SkillsForm());
        sections.put("Demographics", new DemographicsForm());
        sections.put("Job preferences", new PreferencesForm());

        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        sections.forEach((name, section) -> {
            ScrollPane scroll = new ScrollPane(section.view());
            scroll.setFitToWidth(true);
            scroll.setPadding(new Insets(16, 4, 4, 4));
            tabs.getTabs().add(new Tab(name, scroll));
        });
        VBox.setVgrow(tabs, Priority.ALWAYS);

        Button save = Ui.button("Save profile", Feather.SAVE, Styles.ACCENT);
        save.setOnAction(e -> save());
        root = Ui.page("Profile", "Everything TrueApply uses to fill in applications.", tabs, Ui.row(save, saved));
    }

    private void reloadSections() {
        sections.values().forEach(s -> s.load(profile));
    }

    private void save() {
        for (ProfileSection section : sections.values()) {
            String problem = section.validate();
            if (problem != null) {
                Ui.info("Check your profile", problem);
                return;
            }
        }
        sections.values().forEach(s -> s.save(profile));
        ctx.profiles.save(profile);
        saved.setText("Saved.");
    }

    @Override
    public Node root() {
        return root;
    }

    @Override
    public void refresh() {
        // Only load once per visit; background refreshes shouldn't discard unsaved edits.
        if (profile == null) {
            profile = ctx.profiles.load();
            reloadSections();
        }
    }

    /** Called when navigating to the page so it shows the latest saved data. */
    public void reload() {
        profile = null;
        saved.setText("");
        refresh();
    }
}
