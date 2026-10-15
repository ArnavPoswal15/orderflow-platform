package com.shop.order.api.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "order.rate-limit")
public class RateLimitProperties {

    private int limitPerMinute = 20;
    private int windowSeconds = 60;

    public int getLimitPerMinute() {
        return limitPerMinute;
    }

    public void setLimitPerMinute(int limitPerMinute) {
        this.limitPerMinute = limitPerMinute;
    }

    public int getWindowSeconds() {
        return windowSeconds;
    }

    public void setWindowSeconds(int windowSeconds) {
        this.windowSeconds = windowSeconds;
    }

    public long windowMillis() {
        return windowSeconds * 1000L;
    }
}
