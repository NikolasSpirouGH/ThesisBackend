package com.cloud_ml_app_thesis.exception;

    public class FileProcessingException extends RuntimeException {

        public FileProcessingException(String message, Throwable cause) {
            super(message, cause);
        }

        public FileProcessingException(String message) {
            super(message);
        }
    }

