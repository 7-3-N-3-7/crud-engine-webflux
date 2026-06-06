package com.org73n37.crudapp.api.errors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ServerWebExchange;

import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * [CHECKLIST FEATURE / DX OPTIMIZATION]
 * Unified global exception handler mapping application exceptions 
 * to standard RFC 7807 ProblemDetail schemas enriched with diagnostic request IDs.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Helper method to map and build ProblemDetail according to RFC 7807
     * while maintaining backwards compatibility with pre-existing integration tests.
     */
    private ResponseEntity<ProblemDetail> createProblemDetail(
            HttpStatus status,
            String title,
            String detail,
            ServerWebExchange exchange,
            Map<String, String> validationErrors) {

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
        problemDetail.setTitle(title);

        String path = exchange.getRequest().getPath().value();
        problemDetail.setInstance(URI.create(path));

        String reqId = getRequestId(exchange);
        
        // Add custom extension properties flat at root for compatibility and diagnostics
        problemDetail.setProperty("timestamp", Instant.now());
        problemDetail.setProperty("requestId", reqId);
        problemDetail.setProperty("error", title);
        problemDetail.setProperty("message", detail);
        problemDetail.setProperty("path", path);

        if (validationErrors != null) {
            problemDetail.setProperty("details", validationErrors);
        }

        return ResponseEntity.status(status).body(problemDetail);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleNotFound(ResourceNotFoundException ex, ServerWebExchange exchange) {
        return createProblemDetail(HttpStatus.NOT_FOUND, "Not Found", ex.getMessage(), exchange, null);
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ProblemDetail> handleValidation(ValidationException ex, ServerWebExchange exchange) {
        return createProblemDetail(HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage(), exchange, ex.getErrors());
    }

    @ExceptionHandler(org.springframework.orm.ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ProblemDetail> handleLocking(org.springframework.orm.ObjectOptimisticLockingFailureException ex, ServerWebExchange exchange) {
        return createProblemDetail(
                HttpStatus.CONFLICT,
                "Conflict",
                "Concurrency conflict detected: the record has been modified by another transaction",
                exchange,
                null
        );
    }

    @ExceptionHandler({SecurityException.class, org.springframework.security.access.AccessDeniedException.class})
    public ResponseEntity<ProblemDetail> handleSecurity(Exception ex, ServerWebExchange exchange) {
        return createProblemDetail(HttpStatus.FORBIDDEN, "Forbidden", ex.getMessage(), exchange, null);
    }

    @ExceptionHandler(org.springframework.web.server.ServerWebInputException.class)
    public ResponseEntity<ProblemDetail> handleWebInputException(org.springframework.web.server.ServerWebInputException ex, ServerWebExchange exchange) {
        Throwable rootCause = ex.getRootCause();
        String message = rootCause != null ? rootCause.getMessage() : ex.getReason();
        return createProblemDetail(HttpStatus.BAD_REQUEST, "Bad Request", "Invalid payload format: " + message, exchange, null);
    }

    @ExceptionHandler(tools.jackson.core.JacksonException.class)
    public ResponseEntity<ProblemDetail> handleJacksonException(tools.jackson.core.JacksonException ex, ServerWebExchange exchange) {
        return createProblemDetail(HttpStatus.BAD_REQUEST, "Bad Request", "JSON payload parsing error: " + ex.getOriginalMessage(), exchange, null);
    }

    @ExceptionHandler(org.springframework.web.bind.support.WebExchangeBindException.class)
    public ResponseEntity<ProblemDetail> handleWebExchangeBind(org.springframework.web.bind.support.WebExchangeBindException ex, ServerWebExchange exchange) {
        Map<String, String> errors = new HashMap<>();
        ex.getFieldErrors().forEach(fieldError -> {
            errors.put(fieldError.getField(), fieldError.getDefaultMessage());
        });
        return createProblemDetail(HttpStatus.BAD_REQUEST, "Bad Request", "Validation failed: " + ex.getReason(), exchange, errors);
    }

    @ExceptionHandler(jakarta.validation.ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraintViolation(jakarta.validation.ConstraintViolationException ex, ServerWebExchange exchange) {
        Map<String, String> errors = new HashMap<>();
        ex.getConstraintViolations().forEach(violation -> {
            errors.put(violation.getPropertyPath().toString(), violation.getMessage());
        });
        return createProblemDetail(HttpStatus.BAD_REQUEST, "Bad Request", "Validation failed", exchange, errors);
    }

    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> handleDataIntegrityViolation(org.springframework.dao.DataIntegrityViolationException ex, ServerWebExchange exchange) {
        String message = "Database integrity constraint violation";
        HttpStatus status = HttpStatus.BAD_REQUEST;
        String title = "Bad Request";
        Throwable cause = ex.getRootCause();
        if (cause != null) {
            String causeMsg = cause.getMessage();
            if (causeMsg != null) {
                if (causeMsg.contains("violates unique constraint") || causeMsg.contains("Duplicate entry")) {
                    message = "Duplicate entry or unique constraint violation: A resource with the same key already exists.";
                    status = HttpStatus.CONFLICT;
                    title = "Conflict";
                } else if (causeMsg.contains("violates foreign key constraint")) {
                    message = "Foreign key constraint violation: The operation references a non-existent parent entity or is referenced by existing child entities.";
                } else if (causeMsg.contains("violates not-null constraint")) {
                    message = "Data validation error: A required field is missing or null.";
                } else {
                    message = "Database integrity violation: " + causeMsg;
                }
            }
        }
        return createProblemDetail(status, title, message, exchange, null);
    }

    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public ResponseEntity<ProblemDetail> handleResponseStatus(org.springframework.web.server.ResponseStatusException ex, ServerWebExchange exchange) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        String reason = ex.getReason() != null ? ex.getReason() : ex.getMessage();
        return createProblemDetail(status, status.getReasonPhrase(), reason, exchange, null);
    }

    @ExceptionHandler({NumberFormatException.class, IllegalArgumentException.class})
    public ResponseEntity<ProblemDetail> handleBadRequest(Exception ex, ServerWebExchange exchange) {
        return createProblemDetail(HttpStatus.BAD_REQUEST, "Bad Request", "Invalid parameter or input format: " + ex.getMessage(), exchange, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGeneric(Exception ex, ServerWebExchange exchange) {
        log.error("Unhandled internal server error", ex);
        return createProblemDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", "An unexpected error occurred", exchange, null);
    }

    private String getRequestId(ServerWebExchange exchange) {
        Object attribute = exchange.getAttribute(com.org73n37.crudapp.infrastructure.web.ReactiveRequestTracingFilter.MDC_KEY);
        return attribute != null ? attribute.toString() : exchange.getResponse().getHeaders().getFirst(com.org73n37.crudapp.infrastructure.web.ReactiveRequestTracingFilter.REQUEST_ID_HEADER);
    }
}
