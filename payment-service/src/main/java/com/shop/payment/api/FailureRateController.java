package com.shop.payment.api;

import com.shop.payment.domain.PaymentFailureRate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/failure-rate")
public class FailureRateController {

    private final PaymentFailureRate failureRate;

    public FailureRateController(PaymentFailureRate failureRate) {
        this.failureRate = failureRate;
    }

    @GetMapping
    public FailureRateResponse get() {
        return new FailureRateResponse(failureRate.getPercent());
    }

    @PutMapping
    public FailureRateResponse put(@RequestBody FailureRateRequest body) {
        if (body == null || body.percent() == null) {
            throw new PaymentFailureRate.InvalidPercentException();
        }
        failureRate.setPercent(body.percent());
        return new FailureRateResponse(failureRate.getPercent());
    }

    public record FailureRateRequest(Integer percent) {}

    public record FailureRateResponse(int percent) {}
}
