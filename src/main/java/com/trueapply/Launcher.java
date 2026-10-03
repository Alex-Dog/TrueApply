package com.trueapply;

/**
 * Plain entry point. JavaFX refuses to start from a main class that extends
 * Application when it is on the classpath instead of the module path, so this
 * class just delegates.
 */
public final class Launcher {
    public static void main(String[] args) {
        TrueApplyApp.main(args);
    }
}
