package com.shop.payment.api;

import com.shop.payment.domain.PaymentFailureRate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(PaymentFailureRate.InvalidPercentException.class)
    public ResponseEntity<ProblemDetail> invalidPercent(PaymentFailureRate.InvalidPercentException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        return problem(HttpStatus.BAD_REQUEST, pd);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> unreadable(HttpMessageNotReadableException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "percent must be between 0 and 100");
        return problem(HttpStatus.BAD_REQUEST, pd);
    }

    private static ResponseEntity<ProblemDetail> problem(HttpStatus status, ProblemDetail pd) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(pd);
    }
}
