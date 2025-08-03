package com.cloud_ml_app_thesis.exception;


public class UserInitiatedStopException extends RuntimeException {

    public UserInitiatedStopException() {
        super("Operation stopped by user request.");
    }

    public UserInitiatedStopException(String message) {
        super(message);
    }

    public UserInitiatedStopException(String message, Throwable cause) {
        super(message, cause);
    }
}
