package com.planwise.ai;

/** The AI call failed. Callers catch this and carry on without the AI's text. */
public class AiException extends RuntimeException {

    public AiException(String message) {
        super(message);
    }

    public AiException(String message, Throwable cause) {
        super(message, cause);
    }
}
