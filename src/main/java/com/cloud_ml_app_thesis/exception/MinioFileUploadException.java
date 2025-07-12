package com.cloud_ml_app_thesis.exception;

public class MinioFileUploadException extends RuntimeException {
    public MinioFileUploadException(String message, Throwable cause) {
        super(message, cause);
    }
}
