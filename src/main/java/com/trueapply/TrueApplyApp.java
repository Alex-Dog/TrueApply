package com.trueapply;

import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import com.trueapply.ui.MainWindow;
import com.trueapply.ui.OnboardingWizard;
import com.trueapply.ui.Ui;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import java.util.Objects;

public class TrueApplyApp extends Application {
    private AppContext ctx;
    private final StackPane root = new StackPane();

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        Ui.init(getHostServices());
        ctx = new AppContext();
        applyTheme(ctx.settings.darkTheme());

        Scene scene = new Scene(root, 1280, 820);
        scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/styles/app.css")).toExternalForm());
        stage.setTitle("TrueApply");
        Ui.setMainWindow(stage);
        stage.setMinWidth(980);
        stage.setMinHeight(640);
        stage.setScene(scene);

        if (ctx.settings.onboardingComplete()) {
            showMain(false);
        } else {
            OnboardingWizard wizard = new OnboardingWizard(ctx, () -> showMain(true));
            setContent((Parent) wizard.root());
        }
        stage.show();
        ctx.pipeline.recoverInterrupted();
    }

    private void showMain(boolean afterOnboarding) {
        MainWindow main = new MainWindow(ctx, this::applyTheme);
        setContent((Parent) main.root());
        if (afterOnboarding) main.showDiscover();
    }

    private void setContent(Parent content) {
        root.getChildren().setAll(content);
    }

    private void applyTheme(boolean dark) {
        Application.setUserAgentStylesheet(dark
                ? new PrimerDark().getUserAgentStylesheet()
                : new PrimerLight().getUserAgentStylesheet());
    }

    @Override
    public void stop() {
        if (ctx != null) ctx.close();
        Platform.exit();
        // Playwright and HTTP clients leave non-daemon threads behind.
        System.exit(0);
    }
}
