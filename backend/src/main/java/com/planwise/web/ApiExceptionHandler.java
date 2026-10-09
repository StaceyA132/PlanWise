package com.planwise.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.planwise.plan.InvalidAmountException;

/**
 * Turns exceptions into 400 Bad Request responses in the standard ProblemDetail format:
 * {"status": 400, "title": "Bad Request", "detail": "..."}
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(InvalidAmountException.class)
    public ProblemDetail invalidAmount(InvalidAmountException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    /** A @Valid check failed, e.g. a required field was missing. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail invalidRequest(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getDefaultMessage())
                .findFirst()
                .orElse("Invalid request");
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
    }

    /** The body was not valid JSON, or a field had the wrong type (e.g. "amount": "abc"). */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail unreadable(HttpMessageNotReadableException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request body is malformed");
    }
}
