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
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.feather.Feather;

import java.util.ArrayList;
import java.util.List;

/** First-run setup: connections, resume, profile, demographics, and what to look for. */
public class OnboardingWizard {
    private record Step(String title, String subtitle, Node content, ProfileSection section) {
    }

    private final AppContext ctx;
    private final Runnable onFinish;
    private final UserProfile profile;
    private final List<Step> steps = new ArrayList<>();
    private final BorderPane root = new BorderPane();
    private final Label stepLabel = Ui.muted("");
    private final ProgressBar progress = new ProgressBar();
    private final Button back = Ui.button("Back", Feather.ARROW_LEFT);
    private final Button next = Ui.button("Next", Feather.ARROW_RIGHT, Styles.ACCENT);
    private final ConnectionsPanel connections;
    private int index;

    public OnboardingWizard(AppContext ctx, Runnable onFinish) {
        this.ctx = ctx;
        this.onFinish = onFinish;
        this.profile = ctx.profiles.load();
        this.connections = new ConnectionsPanel(ctx);

        Label pitch = new Label("""
                TrueApply finds jobs and fills out applications for you — but it never writes your answers to \
                questions like “Why do you want to work here?”. Those are saved to your inbox so you can answer \
                them in your own words. Everything factual (name, school, experience, work authorization…) is \
                filled in for you.""");
        pitch.setWrapText(true);
        steps.add(new Step("Welcome to TrueApply", "Applications without the slop.",
                new VBox(24, pitch, connections), null));

        PersonalForm personal = new PersonalForm();
        ExperienceEditor experience = new ExperienceEditor();
        EducationEditor education = new EducationEditor();
        ResumePanel resume = new ResumePanel(ctx, () -> {
            personal.load(profile);
            experience.load(profile);
            education.load(profile);
        });
        DemographicsForm demographics = new DemographicsForm();
        PreferencesForm preferences = new PreferencesForm();

        steps.add(new Step("Upload your resume", "We'll pull out your experience and education.", resume.view(), resume));
        steps.add(new Step("About you", "Contact details used on every application.", personal.view(), personal));
        steps.add(new Step("Work experience", "Check what we extracted, or add jobs yourself.", experience.view(), experience));
        steps.add(new Step("Education", "Schools, degrees and dates.", education.view(), education));
        steps.add(new Step("Work authorization & self-identification",
                "Answers for sponsorship and voluntary EEO questions.", demographics.view(), demographics));
        steps.add(new Step("What are you looking for?", "Used to pick which jobs to show you.", preferences.view(), preferences));
        steps.add(new Step("You're all set", "Head to Discover to find jobs.",
                Ui.muted("You can change any of this later from the Profile and Settings pages. Applications run in "
                        + "dry-run mode until you turn it off in Settings, so nothing is sent while you try things out."),
                null));

        back.setOnAction(e -> go(index - 1));
        next.setOnAction(e -> advance());
        next.setDefaultButton(true);
        progress.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(progress, javafx.scene.layout.Priority.ALWAYS);

        HBox footer = new HBox(12, stepLabel, progress, back, next);
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.setPadding(new Insets(16, 32, 20, 32));
        root.setBottom(footer);
        root.getStyleClass().add("onboarding");
        go(0);
    }

    public Node root() {
        return root;
    }

    private void advance() {
        Step step = steps.get(index);
        if (step.section() != null) {
            String problem = step.section().validate();
            if (problem != null) {
                Ui.info("One more thing", problem);
                return;
            }
            step.section().save(profile);
            ctx.profiles.save(profile);
        }
        if (index == 0) connections.save();
        if (index == steps.size() - 1) {
            ctx.profiles.save(profile);
            ctx.settings.setOnboardingComplete(true);
            onFinish.run();
            return;
        }
        go(index + 1);
    }

    private void go(int target) {
        if (target < 0 || target >= steps.size()) return;
        index = target;
        Step step = steps.get(index);
        if (step.section() != null) step.section().load(profile);

        VBox content = new VBox(18);
        content.setPadding(new Insets(36, 48, 24, 48));
        content.setMaxWidth(900);
        content.getChildren().addAll(new VBox(6, Ui.title(step.title()), Ui.muted(step.subtitle())), step.content());
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        root.setCenter(scroll);

        stepLabel.setText("Step " + (index + 1) + " of " + steps.size());
        progress.setProgress((index + 1) / (double) steps.size());
        back.setDisable(index == 0);
        boolean last = index == steps.size() - 1;
        next.setText(last ? "Start using TrueApply" : "Next");
        next.setGraphic(Ui.icon(last ? Feather.CHECK : Feather.ARROW_RIGHT));
    }
}
