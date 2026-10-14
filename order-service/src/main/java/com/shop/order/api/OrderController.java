package com.shop.order.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shop.order.domain.OrderApplicationService;
import com.shop.order.domain.OrderView;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderApplicationService orderService;
    private final ObjectMapper objectMapper;

    public OrderController(OrderApplicationService orderService, ObjectMapper objectMapper) {
        this.orderService = orderService;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public ResponseEntity<?> create(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody CreateOrderRequest body) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
            pd.setDetail("Idempotency-Key header required");
            return ResponseEntity.badRequest().body(pd);
        }
        if (body.items == null || body.items.isEmpty()) {
            ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
            pd.setDetail("items required");
            return ResponseEntity.badRequest().body(pd);
        }
        for (CreateOrderRequest.Item item : body.items) {
            if (item.sku == null || item.qty <= 0) {
                ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
                pd.setDetail("items: [{sku, qty>0}] required");
                return ResponseEntity.badRequest().body(pd);
            }
        }
        String hash;
        try {
            hash = OrderApplicationService.hashRequest(objectMapper.writeValueAsString(body));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        List<OrderApplicationService.LineRequest> lines =
                body.items.stream().map(i -> new OrderApplicationService.LineRequest(i.sku, i.qty)).toList();
        try {
            OrderApplicationService.CreateOrderResult result =
                    orderService.createOrder(idempotencyKey, hash, lines, body.paymentToken);
            if (result.hashMismatch()) {
                ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.UNPROCESSABLE_ENTITY);
                pd.setDetail("Idempotency-Key reused with different request body");
                return ResponseEntity.unprocessableEntity().body(pd);
            }
            HttpStatus status = result.replay() ? HttpStatus.OK : HttpStatus.ACCEPTED;
            return ResponseEntity.status(status).body(result.order());
        } catch (OrderApplicationService.BadRequestException e) {
            ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
            pd.setDetail(e.getMessage());
            return ResponseEntity.badRequest().body(pd);
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderView> get(@PathVariable UUID id) {
        return orderService.getOrder(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    public static class CreateOrderRequest {
        public List<Item> items;
        public String paymentToken;

        public static class Item {
            public String sku;
            public int qty;
        }
    }
}
