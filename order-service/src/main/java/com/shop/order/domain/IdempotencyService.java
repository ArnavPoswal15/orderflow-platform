package com.shop.order.domain;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class IdempotencyService {

    private static final Duration TTL = Duration.ofHours(24);

    private final StringRedisTemplate redis;

    public IdempotencyService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public String getCachedOrderId(String idempotencyKey) {
        return redis.opsForValue().get(key(idempotencyKey));
    }

    public void cacheAfterCommit(String idempotencyKey, String orderId) {
        redis.opsForValue().set(key(idempotencyKey), orderId, TTL);
    }

    private static String key(String idempotencyKey) {
        return "idem:" + idempotencyKey;
    }
}
