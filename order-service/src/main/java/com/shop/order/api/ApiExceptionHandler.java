package com.shop.order.api;

import com.shop.order.domain.InventoryClient;
import com.shop.order.domain.OrderApplicationService;
import com.shop.order.domain.OrderStateMachine;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(OrderApplicationService.NotFoundException.class)
    public ResponseEntity<ProblemDetail> notFound(OrderApplicationService.NotFoundException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        return problem(HttpStatus.NOT_FOUND, pd);
    }

    @ExceptionHandler(InventoryClient.UnknownSkuException.class)
    public ResponseEntity<ProblemDetail> unknownSku(InventoryClient.UnknownSkuException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        return problem(HttpStatus.NOT_FOUND, pd);
    }

    @ExceptionHandler(OrderStateMachine.IllegalTransitionException.class)
    public ResponseEntity<ProblemDetail> conflict(OrderStateMachine.IllegalTransitionException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        return problem(HttpStatus.CONFLICT, pd);
    }

    @ExceptionHandler(OrderApplicationService.BadRequestException.class)
    public ResponseEntity<ProblemDetail> badRequest(OrderApplicationService.BadRequestException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        return problem(HttpStatus.BAD_REQUEST, pd);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> validation(MethodArgumentNotValidException ex) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        pd.setDetail("Validation failed");
        return problem(HttpStatus.BAD_REQUEST, pd);
    }

    private static ResponseEntity<ProblemDetail> problem(HttpStatus status, ProblemDetail pd) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(pd);
    }
}
