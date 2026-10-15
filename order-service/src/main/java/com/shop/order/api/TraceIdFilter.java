package com.shop.order.api;

import com.shop.events.TraceContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

public class TraceIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        UUID traceId = TraceContext.parseUuid(request.getHeader(TraceContext.HTTP_TRACE_HEADER))
                .orElseGet(UUID::randomUUID);
        TraceContext.put(TraceContext.TRACE_ID, traceId.toString());
        response.setHeader(TraceContext.HTTP_TRACE_HEADER, traceId.toString());
        try {
            filterChain.doFilter(request, response);
        } finally {
            TraceContext.clear();
        }
    }
}
