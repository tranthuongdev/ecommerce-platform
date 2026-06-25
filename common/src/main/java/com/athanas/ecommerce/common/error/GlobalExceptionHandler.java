package com.athanas.ecommerce.common.error;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import jakarta.validation.ConstraintViolationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;

import java.util.stream.Collectors;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
@RequiredArgsConstructor
@Slf4j
public class GlobalExceptionHandler {

    private final ProblemDetailFactory factory;

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(err -> errors.put(err.getField(), err.getDefaultMessage()));
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(factory.validation(errors));
    }

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<ProblemDetail> handleEmailExists(EmailAlreadyExistsException ex) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(factory.of(
                        HttpStatus.CONFLICT,
                        ex.getMessage(),
                        "https://athanas.dev/errors/email-exists"
                ));
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ProblemDetail> handleInvalidCredentials(InvalidCredentialsException ex) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(factory.of(
                        HttpStatus.UNAUTHORIZED,
                        ex.getMessage(),
                        "https://athanas.dev/errors/invalid-credentials"
                ));
    }

    @ExceptionHandler(TooManyAttemptsException.class)
    public ResponseEntity<ProblemDetail> handleTooManyAttempts(TooManyAttemptsException ex) {
        return ResponseEntity
                .status(HttpStatus.TOO_MANY_REQUESTS)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .header("Retry-After", String.valueOf(ex.getRetryAfter().toSeconds()))
                .body(factory.of(
                        HttpStatus.TOO_MANY_REQUESTS,
                        ex.getMessage(),
                        "https://athanas.dev/errors/too-many-attempts"
                ));
    }

    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ResponseEntity<ProblemDetail> handleInvalidRefreshToken(InvalidRefreshTokenException ex) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(factory.of(
                        HttpStatus.UNAUTHORIZED,
                        ex.getMessage(),
                        "https://athanas.dev/errors/invalid-refresh-token"
                ));
    }

    @ExceptionHandler(RefreshTokenReuseException.class)
    public ResponseEntity<ProblemDetail> handleRefreshTokenReuse(RefreshTokenReuseException ex) {
        log.warn("SECURITY: refresh token reuse detected for userId={}", ex.getUserId());
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(factory.of(
                        HttpStatus.UNAUTHORIZED,
                        ex.getMessage(),
                        "https://athanas.dev/errors/refresh-token-reuse"
                ));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraintViolation(ConstraintViolationException ex) {
        String detail = ex.getConstraintViolations().stream()
                .map(cv -> cv.getPropertyPath() + ": " + cv.getMessage())
                .collect(Collectors.joining("; "));
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(factory.of(HttpStatus.BAD_REQUEST, detail,
                        "https://athanas.dev/errors/validation-failed"));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleMessageNotReadable(HttpMessageNotReadableException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(factory.of(
                        HttpStatus.BAD_REQUEST,
                        "Malformed or missing request body",
                        "https://athanas.dev/errors/bad-request"
                ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGeneric(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(factory.of(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "An unexpected error occurred",
                        "about:blank"
                ));
    }
}
