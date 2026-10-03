package com.trueapply.ui;

import javafx.application.Platform;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/** Runs blocking work off the FX thread and reports back on it. */
public final class Background {
    private static final ExecutorService POOL = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "ui-background");
        t.setDaemon(true);
        return t;
    });

    private Background() {
    }

    public static <T> void run(Callable<T> work, Consumer<T> onSuccess, Consumer<Throwable> onError) {
        POOL.submit(() -> {
            try {
                T result = work.call();
                Platform.runLater(() -> onSuccess.accept(result));
            } catch (Throwable e) {
                Platform.runLater(() -> onError.accept(e));
            }
        });
    }
}
