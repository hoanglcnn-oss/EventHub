package com.eventhub.exception;

import com.eventhub.controller.dto.ApiError;
import com.eventhub.controller.dto.FieldViolation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidationException(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        
        List<FieldViolation> violations = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldViolation(
                        error.getField(),
                        error.getCode(),
                        error.getDefaultMessage()))
                .toList();

        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_FAILED",
                "Request validation failed",
                request.getRequestURI(),
                violations
        );
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolationException(
            ConstraintViolationException ex, HttpServletRequest request) {
        
        List<FieldViolation> violations = ex.getConstraintViolations().stream()
                .map(violation -> {
                    String path = violation.getPropertyPath().toString();
                    // Lấy phần tên tham số cuối cùng
                    String fieldName = path.substring(path.lastIndexOf('.') + 1);
                    return new FieldViolation(
                            fieldName,
                            violation.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName(),
                            violation.getMessage()
                    );
                })
                .toList();

        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_FAILED",
                "Parameter validation failed",
                request.getRequestURI(),
                violations
        );
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiError> handleMethodValidationException(
            HandlerMethodValidationException ex, HttpServletRequest request) {
        
        List<FieldViolation> violations = new ArrayList<>();
        ex.getAllValidationResults().forEach(result -> {
            String parameterName = result.getMethodParameter().getParameterName();
            result.getResolvableErrors().forEach(error -> {
                String code = "Invalid";
                String[] codes = error.getCodes();
                if (codes != null && codes.length > 0) {
                    code = codes[codes.length - 1];
                }
                violations.add(new FieldViolation(
                        parameterName,
                        code,
                        error.getDefaultMessage()
                ));
            });
        });

        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_FAILED",
                "Method parameter validation failed",
                request.getRequestURI(),
                violations
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleHttpMessageNotReadableException(
            HttpMessageNotReadableException ex, HttpServletRequest request) {
        
        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "MALFORMED_JSON",
                "Request body is missing or contains invalid JSON",
                request.getRequestURI(),
                List.of()
        );
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        
        return buildResponse(
                HttpStatus.METHOD_NOT_ALLOWED,
                "METHOD_NOT_ALLOWED",
                "HTTP method " + ex.getMethod() + " is not supported for this path",
                request.getRequestURI(),
                List.of()
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgumentException(
            IllegalArgumentException ex, HttpServletRequest request) {
        
        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "INVALID_PARAMETER",
                ex.getMessage(),
                request.getRequestURI(),
                List.of()
        );
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleResourceNotFound(
            ResourceNotFoundException ex, HttpServletRequest request) {
        
        return buildResponse(
                HttpStatus.NOT_FOUND,
                "RESOURCE_NOT_FOUND",
                ex.getMessage(),
                request.getRequestURI(),
                List.of()
        );
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiError> handleConflictException(
            ConflictException ex, HttpServletRequest request) {
        
        String code = "CONFLICT_ERROR";
        if (ex instanceof DuplicateEmailException) {
            code = "DUPLICATE_EMAIL";
        } else if (ex instanceof DuplicateRegistrationException) {
            code = "DUPLICATE_REGISTRATION";
        } else if (ex instanceof InvalidEventStateException) {
            code = "INVALID_EVENT_STATE";
        } else if (ex instanceof EventFullCapacityException) {
            code = "EVENT_FULL_CAPACITY";
        } else if (ex instanceof InvalidCancellationException) {
            code = "INVALID_CANCELLATION";
        }

        return buildResponse(
                HttpStatus.CONFLICT,
                code,
                ex.getMessage(),
                request.getRequestURI(),
                List.of()
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpectedException(
            Exception ex, HttpServletRequest request) {
        
        log.error("Unexpected failure for URI: {}", request.getRequestURI(), ex);

        return buildResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR",
                "An unexpected error occurred",
                request.getRequestURI(),
                List.of()
        );
    }

    private ResponseEntity<ApiError> buildResponse(
            HttpStatus status,
            String code,
            String message,
            String path,
            List<FieldViolation> violations) {
        
        ApiError apiError = new ApiError(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                code,
                message,
                path,
                violations
        );
        return ResponseEntity.status(status).body(apiError);
    }
}
