package com.planwise.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.planwise.auth.EmailAlreadyRegisteredException;
import com.planwise.auth.InvalidCredentialsException;
import com.planwise.plan.InvalidAmountException;
import com.planwise.plan.InvalidPlanChoiceException;
import com.planwise.plan.PaymentAlreadyPaidException;

/**
 * Turns exceptions into error responses in the standard ProblemDetail format:
 * {"status": 400, "title": "Bad Request", "detail": "..."}
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(InvalidAmountException.class)
    public ProblemDetail invalidAmount(InvalidAmountException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(InvalidPlanChoiceException.class)
    public ProblemDetail invalidPlanChoice(InvalidPlanChoiceException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail notFound(ResourceNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(PaymentAlreadyPaidException.class)
    public ProblemDetail alreadyPaid(PaymentAlreadyPaidException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    public ProblemDetail emailTaken(EmailAlreadyRegisteredException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ProblemDetail invalidCredentials(InvalidCredentialsException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
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

    /** A URL parameter had the wrong format, e.g. ?month=October instead of ?month=2026-10. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail badParameter(MethodArgumentTypeMismatchException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid value for '" + ex.getName() + "'");
    }

    /** The body was not valid JSON, or a field had the wrong type (e.g. "amount": "abc"). */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail unreadable(HttpMessageNotReadableException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request body is malformed");
    }
}
