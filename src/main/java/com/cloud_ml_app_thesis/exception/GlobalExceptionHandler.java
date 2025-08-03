package com.cloud_ml_app_thesis.exception;

import com.cloud_ml_app_thesis.dto.response.GenericResponse;
import com.cloud_ml_app_thesis.dto.response.Metadata;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.converter.HttpMessageNotReadableException;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<GenericResponse<Object>> handleValidationErrors(MethodArgumentNotValidException ex) {
        log.warn("⚠️ Validation failed: {}", ex.getMessage());

        String errorMessage = ex.getBindingResult()
                .getAllErrors()
                .stream()
                .map(DefaultMessageSourceResolvable::getDefaultMessage)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse("Invalid input");

        return ResponseEntity
                .badRequest()
                .contentType(MediaType.APPLICATION_JSON)
                .body(GenericResponse.failure("VALIDATION_ERROR", errorMessage, new Metadata()));
    }

    // ✅ 2. Validation failed on query/path parameters
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<GenericResponse<?>> handleConstraintViolation(ConstraintViolationException ex) {
        String errorMessage = ex.getConstraintViolations()
                .stream()
                .map(cv -> cv.getPropertyPath() + ": " + cv.getMessage())
                .collect(Collectors.joining(", "));

        GenericResponse<?> response = new GenericResponse<>(null, "CONSTRAINT_VIOLATION", errorMessage, new Metadata());
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    // ✅ 3. Invalid/malformed JSON
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<GenericResponse<?>> handleHttpMessageNotReadable(HttpMessageNotReadableException ex) {
        GenericResponse<?> response = new GenericResponse<>(null, "MALFORMED_JSON", "Request body is invalid or malformed", new Metadata());
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    // ✅ 4. Access denied (forbidden)
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<GenericResponse<?>> handleAccessDeniedException(AccessDeniedException ex) {
        logger.warn("Access denied: {}", ex.getMessage());
        GenericResponse<?> response = new GenericResponse<>(null, "ACCESS_DENIED", "You are not authorized to access this resource", new Metadata());
        return new ResponseEntity<>(response, HttpStatus.FORBIDDEN);
    }

    // ✅ 5. Custom application-specific
    @ExceptionHandler(FileProcessingException.class)
    public ResponseEntity<GenericResponse<?>> handleFileProcessingException(FileProcessingException ex) {
        GenericResponse<?> errorResponse = new GenericResponse<>(null, "FILE_ERROR", "File processing error", new Metadata());
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(UserInitiatedStopException.class)
    public ResponseEntity<GenericResponse<?>> handleUserInitiatedStop(UserInitiatedStopException ex) {
        GenericResponse<?> errorResponse = new GenericResponse<>(null, "410", "Training was stopped by the user", new Metadata());
        return new ResponseEntity<>(errorResponse, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(MinioFileUploadException.class)
    public ResponseEntity<GenericResponse<?>> handleMinioFileUploadException(MinioFileUploadException ex) {
        logger.error("Minio file upload exception: {}", ex.getMessage(), ex);
        GenericResponse<?> errorResponse = new GenericResponse<>(null, "MINIO_UPLOAD_ERROR", "File upload failed", new Metadata());
        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(AlgorithmNotFoundException.class)
    public ResponseEntity<GenericResponse<?>> handleAlgorithmNotFoundException(AlgorithmNotFoundException ex) {
        logger.error("Algorithm not found exception: {}", ex.getMessage(), ex);
        GenericResponse<?> errorResponse = new GenericResponse<>(null, "NOT_FOUND", "Algorithm could not be found", new Metadata());
        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<GenericResponse<?>> handleBadRequest(BadRequestException ex) {
        return ResponseEntity.badRequest().body(new GenericResponse<>(null, "BAD_REQUEST", ex.getMessage(), new Metadata()));
    }


    @ExceptionHandler(MailSendingException.class)
    public ResponseEntity<GenericResponse<?>> handleMailSendingException(MailSendingException ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new GenericResponse<>(null, "MAIL_SENDING_ERROR", ex.getMessage(), new Metadata()));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<GenericResponse<?>> handleMissingRequestHeaderException(MissingRequestHeaderException ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new GenericResponse<>(null, "MISSING_REQUEST_HEADER_ERROR", ex.getMessage(), new Metadata()));
    }

    // ✅ 6. Generic fallback
    @ExceptionHandler(Exception.class)
    public ResponseEntity<GenericResponse<?>> handleGenericException(Exception ex) {
        logger.error("Unhandled exception: {}", ex.getMessage(), ex);
        GenericResponse<?> errorResponse = new GenericResponse<>(
                null,
                "INTERNAL_SERVER_ERROR",
                "Something went wrong. Please try again later.",
                new Metadata()
        );
        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }

}
