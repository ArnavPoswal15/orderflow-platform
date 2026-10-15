package com.shop.order.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.shop.order.domain.AdminStatsService;
import com.shop.order.domain.FlashSaleService;
import com.shop.order.domain.OrderApplicationService;
import com.shop.order.domain.PaymentClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminStatsService adminStatsService;
    private final PaymentClient paymentClient;
    private final FlashSaleService flashSaleService;

    public AdminController(
            AdminStatsService adminStatsService,
            PaymentClient paymentClient,
            FlashSaleService flashSaleService) {
        this.adminStatsService = adminStatsService;
        this.paymentClient = paymentClient;
        this.flashSaleService = flashSaleService;
    }

    @GetMapping("/stats")
    public AdminStatsService.AdminStatsView stats() throws Exception {
        return adminStatsService.stats();
    }

    @GetMapping("/failure-rate")
    public ResponseEntity<JsonNode> failureRate() {
        return paymentClient.getFailureRate();
    }

    @PutMapping("/failure-rate")
    public ResponseEntity<JsonNode> updateFailureRate(@RequestBody JsonNode body) {
        return paymentClient.putFailureRate(body);
    }

    @PostMapping("/flash-sale")
    public FlashSaleService.FlashSaleResult flashSale(@RequestBody FlashSaleRequest body) {
        if (body == null
                || body.sku == null
                || body.sku.isBlank()
                || body.concurrentOrders == null
                || body.concurrentOrders < 1
                || body.concurrentOrders > 100) {
            throw new OrderApplicationService.BadRequestException(
                    "sku is required and concurrentOrders must be between 1 and 100");
        }
        return flashSaleService.run(body.sku, body.concurrentOrders);
    }

    public static class FlashSaleRequest {
        public String sku;
        public Integer concurrentOrders;
    }
}
