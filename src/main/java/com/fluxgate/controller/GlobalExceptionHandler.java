package com.fluxgate.controller;

import com.fluxgate.model.ErrorResponse;
import com.fluxgate.service.BadGatewayException;
import com.fluxgate.service.GatewayTimeoutException;
import com.fluxgate.service.NoHealthyBackendException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Global exception handler providing safe, structured error responses
 * without exposing sensitive stack traces to clients.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(NoHealthyBackendException.class)
    public ResponseEntity<ErrorResponse> handleNoHealthyBackend(NoHealthyBackendException ex, HttpServletRequest request) {
        log.warn("[503 SERVICE UNAVAILABLE] {}", ex.getMessage());
        ErrorResponse error = new ErrorResponse(
                HttpStatus.SERVICE_UNAVAILABLE.value(),
                HttpStatus.SERVICE_UNAVAILABLE.getReasonPhrase(),
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error);
    }

    @ExceptionHandler(BadGatewayException.class)
    public ResponseEntity<ErrorResponse> handleBadGateway(BadGatewayException ex, HttpServletRequest request) {
        log.warn("[502 BAD GATEWAY] Backend: {}, Message: {}", ex.getBackendUrl(), ex.getMessage());
        ErrorResponse error = new ErrorResponse(
                HttpStatus.BAD_GATEWAY.value(),
                HttpStatus.BAD_GATEWAY.getReasonPhrase(),
                ex.getMessage(),
                request.getRequestURI(),
                ex.getBackendUrl()
        );
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(error);
    }

    @ExceptionHandler(GatewayTimeoutException.class)
    public ResponseEntity<ErrorResponse> handleGatewayTimeout(GatewayTimeoutException ex, HttpServletRequest request) {
        log.warn("[504 GATEWAY TIMEOUT] Backend: {}, Message: {}", ex.getBackendUrl(), ex.getMessage());
        ErrorResponse error = new ErrorResponse(
                HttpStatus.GATEWAY_TIMEOUT.value(),
                HttpStatus.GATEWAY_TIMEOUT.getReasonPhrase(),
                ex.getMessage(),
                request.getRequestURI(),
                ex.getBackendUrl()
        );
        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).body(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneralException(Exception ex, HttpServletRequest request) {
        log.error("[500 INTERNAL SERVER ERROR] Request: {} - {}", request.getRequestURI(), ex.getMessage(), ex);
        ErrorResponse error = new ErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
                "An unexpected internal error occurred while processing the request.",
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}
