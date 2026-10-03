package com.trueapply.ui.profile;

import atlantafx.base.theme.Styles;
import com.trueapply.AppContext;
import com.trueapply.ai.tasks.ResumeExtraction;
import com.trueapply.ai.tasks.ResumeExtractor;
import com.trueapply.model.UserProfile;
import com.trueapply.resume.ResumeText;
import com.trueapply.ui.Background;
import com.trueapply.ui.Ui;
import com.trueapply.util.AppPaths;
import com.trueapply.util.Text;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.kordamp.ikonli.feather.Feather;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Upload a resume: copies it into the app folder, extracts the text, and has the AI fill in
 * experience, education and skills. {@code onParsed} lets the host refresh other sections.
 */
public class ResumePanel implements ProfileSection {
    private final AppContext ctx;
    private final Runnable onParsed;
    private final Label file = Ui.bold("No resume uploaded yet");
    private final Label status = Ui.muted("");
    private final ProgressIndicator spinner = new ProgressIndicator();
    private final VBox root;
    private UserProfile profile;

    public ResumePanel(AppContext ctx, Runnable onParsed) {
        this.ctx = ctx;
        this.onParsed = onParsed;
        spinner.setPrefSize(22, 22);
        spinner.setVisible(false);

        Button choose = Ui.button("Upload resume…", Feather.UPLOAD, Styles.ACCENT);
        choose.setOnAction(e -> chooseFile(choose));
        Button reparse = Ui.button("Re-extract details", Feather.REFRESH_CW);
        reparse.setOnAction(e -> {
            if (profile != null && !Text.isBlank(profile.resumeText)) parse(profile.resumeText);
        });

        root = new VBox(14,
                Ui.muted("PDF or TXT. It's uploaded to every application, and we extract your experience, "
                        + "education and skills from it so you don't have to retype them. You can edit everything afterwards."),
                file,
                Ui.row(choose, reparse, spinner),
                status);
    }

    private void chooseFile(Button owner) {
        FileChooser chooser = new FileChooser();
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Resume (PDF, TXT)", "*.pdf", "*.txt"));
        File chosen = chooser.showOpenDialog(owner.getScene().getWindow());
        if (chosen == null) return;
        busy("Reading " + chosen.getName() + "…");
        Background.run(() -> {
            Path target = AppPaths.resumes().resolve(chosen.getName());
            Files.copy(chosen.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
            return new Object[] {target, ResumeText.extract(target)};
        }, result -> {
            profile.resumePath = result[0].toString();
            profile.resumeText = (String) result[1];
            file.setText(((Path) result[0]).getFileName().toString());
            parse(profile.resumeText);
        }, error -> done("Couldn't read the file: " + Ui.rootMessage(error)));
    }

    private void parse(String text) {
        if (Text.isBlank(text)) {
            done("No text found in that file (is it a scanned image?). Fill in your details manually.");
            return;
        }
        busy("Extracting your experience and education…");
        Background.run(() -> new ResumeExtractor(ctx.ai()).extract(text), (ResumeExtraction data) -> {
            ResumeExtractor.merge(data, profile);
            done("Found " + profile.experience.size() + " jobs, " + profile.education.size()
                    + " schools and " + profile.skills.size() + " skills. Review them in the next sections.");
            onParsed.run();
        }, error -> done("Resume saved, but extraction failed: " + Ui.rootMessage(error)
                + " You can fill in the details manually."));
    }

    private void busy(String message) {
        spinner.setVisible(true);
        status.setText(message);
    }

    private void done(String message) {
        spinner.setVisible(false);
        status.setText(message);
    }

    @Override
    public Node view() {
        return root;
    }

    @Override
    public void load(UserProfile profile) {
        this.profile = profile;
        file.setText(Text.isBlank(profile.resumePath) ? "No resume uploaded yet" : Path.of(profile.resumePath).getFileName().toString());
    }

    @Override
    public void save(UserProfile profile) {
        // resumePath/resumeText are written directly into the shared profile object
    }
}
