package com.shop.order.api;

import com.shop.order.api.ratelimit.RateLimitFilter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.shop.order.api.ratelimit.RateLimitProperties;

@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
public class WebMvcConfig {

    @Bean
    FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(RateLimitFilter filter) {
        FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setOrder(1);
        return registration;
    }
}
