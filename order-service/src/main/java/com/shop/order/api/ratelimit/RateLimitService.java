package com.shop.order.api.ratelimit;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class RateLimitService {

    private static final String LUA = """
            local key = KEYS[1]
            local now = tonumber(ARGV[1])
            local window = tonumber(ARGV[2])
            local member = ARGV[3]
            redis.call('ZREMRANGEBYSCORE', key, 0, now - window)
            redis.call('ZADD', key, now, member)
            local count = redis.call('ZCARD', key)
            redis.call('PEXPIRE', key, window)
            return count
            """;

    private final StringRedisTemplate redis;
    private final RateLimitProperties properties;
    private final DefaultRedisScript<Long> script;

    public RateLimitService(StringRedisTemplate redis, RateLimitProperties properties) {
        this.redis = redis;
        this.properties = properties;
        this.script = new DefaultRedisScript<>(LUA, Long.class);
    }

    public RateLimitResult check(String clientId) {
        long now = System.currentTimeMillis();
        long windowMs = properties.windowMillis();
        int limit = properties.getLimitPerMinute();
        String key = "rl:" + clientId;
        String member = now + ":" + UUID.randomUUID();

        Long count = redis.execute(script, List.of(key), String.valueOf(now), String.valueOf(windowMs), member);
        long used = count != null ? count : 0L;
        boolean allowed = used <= limit;
        long remaining = Math.max(0, limit - used);
        long resetEpochSec = (now + windowMs) / 1000;
        long retryAfterSec = allowed ? 0 : Math.max(1, (resetEpochSec * 1000 - now) / 1000);
        return new RateLimitResult(allowed, limit, remaining, resetEpochSec, retryAfterSec);
    }

    public record RateLimitResult(
            boolean allowed, int limit, long remaining, long resetEpochSec, long retryAfterSec) {}
}
