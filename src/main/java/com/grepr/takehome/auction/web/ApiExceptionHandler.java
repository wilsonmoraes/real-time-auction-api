package com.grepr.takehome.auction.web;

import com.grepr.takehome.auction.service.BadRequestException;
import com.grepr.takehome.auction.service.ConflictException;
import com.grepr.takehome.auction.service.NotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

  public record ApiError(
      Instant timestamp,
      int status,
      String error,
      String message,
      String path
  ) {}

  @ExceptionHandler(NotFoundException.class)
  public ResponseEntity<ApiError> handleNotFound(NotFoundException ex, HttpServletRequest request) {
    return error(HttpStatus.NOT_FOUND, ex.getMessage(), request);
  }

  @ExceptionHandler(ConflictException.class)
  public ResponseEntity<ApiError> handleConflict(ConflictException ex, HttpServletRequest request) {
    return error(HttpStatus.CONFLICT, ex.getMessage(), request);
  }

  @ExceptionHandler({BadRequestException.class, MethodArgumentNotValidException.class})
  public ResponseEntity<ApiError> handleBadRequest(Exception ex, HttpServletRequest request) {
    String message = ex instanceof MethodArgumentNotValidException manv
        ? manv.getBindingResult().getFieldErrors().stream()
            .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
            .collect(Collectors.joining(", "))
        : ex.getMessage();

    return error(HttpStatus.BAD_REQUEST, message, request);
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<ApiError> handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
    return error(HttpStatus.CONFLICT, "Data integrity violation", request);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiError> handleGeneric(Exception ex, HttpServletRequest request) {
    return error(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error", request);
  }

  private ResponseEntity<ApiError> error(HttpStatus status, String message, HttpServletRequest request) {
    return ResponseEntity.status(status).body(new ApiError(
        Instant.now(),
        status.value(),
        status.getReasonPhrase(),
        message,
        request.getRequestURI()
    ));
  }
}

