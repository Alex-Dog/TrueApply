package com.trueapply.ai;

/** Any failure talking to the AI provider, with a message fit to show the user. */
public class AiException extends RuntimeException {
    public AiException(String message) {
        super(message);
    }

    public AiException(String message, Throwable cause) {
        super(message, cause);
    }
}
