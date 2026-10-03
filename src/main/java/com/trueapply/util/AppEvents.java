package com.trueapply.util;

import javafx.application.Platform;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** "Something changed, refresh" notifications from background work to the UI. */
public class AppEvents {
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    public void onChange(Runnable listener) {
        listeners.add(listener);
    }

    public void fireChanged() {
        for (Runnable listener : listeners) runOnFx(listener);
    }

    private static void runOnFx(Runnable r) {
        try {
            if (Platform.isFxApplicationThread()) r.run();
            else Platform.runLater(r);
        } catch (IllegalStateException toolkitNotRunning) {
            r.run();
        }
    }
}
