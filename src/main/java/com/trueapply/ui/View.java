package com.trueapply.ui;

import javafx.scene.Node;

/** A page in the main window. */
public interface View {
    Node root();

    /** Reload data. Called when the page is shown and when background work changes something. */
    void refresh();
}
