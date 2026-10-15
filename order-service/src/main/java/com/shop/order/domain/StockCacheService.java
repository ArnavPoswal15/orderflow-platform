package com.shop.order.domain;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collection;

@Service
public class StockCacheService {

    private static final Duration TTL = Duration.ofSeconds(5);
    private static final String PREFIX = "stock:";

    private final StringRedisTemplate redis;

    public StockCacheService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public Integer getAvailable(String sku) {
        String value = redis.opsForValue().get(PREFIX + sku);
        if (value == null) {
            return null;
        }
        return Integer.parseInt(value);
    }

    public void cacheAvailable(String sku, int available) {
        redis.opsForValue().set(PREFIX + sku, String.valueOf(available), TTL);
    }

    public void invalidate(String sku) {
        redis.delete(PREFIX + sku);
    }

    public void invalidateAll(Collection<String> skus) {
        if (skus == null || skus.isEmpty()) {
            return;
        }
        for (String sku : skus) {
            if (sku != null && !sku.isBlank()) {
                invalidate(sku);
            }
        }
    }
}
