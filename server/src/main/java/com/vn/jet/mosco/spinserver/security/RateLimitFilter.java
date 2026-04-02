package com.vn.jet.mosco.spinserver.security;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Deque;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limiting filter: max 60 requests per IP per minute.
 * Uses a sliding window approach with in-memory timestamp tracking.
 */
public class RateLimitFilter implements Filter {

    private static final Logger logger = LoggerFactory.getLogger(RateLimitFilter.class);
    private static final int MAX_REQUESTS = 60;
    private static final long TIME_WINDOW_MS = 60_000; // 1 minute

    private final Map<String, Deque<Long>> requestCounts = new ConcurrentHashMap<>();

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String clientIp = getClientIp(httpRequest);
        long now = System.currentTimeMillis();

        Deque<Long> timestamps = requestCounts.computeIfAbsent(clientIp, k -> new LinkedList<>());

        synchronized (timestamps) {
            // Evict timestamps older than the time window
            while (!timestamps.isEmpty() && now - timestamps.peekFirst() > TIME_WINDOW_MS) {
                timestamps.pollFirst();
            }

            if (timestamps.size() >= MAX_REQUESTS) {
                logger.warn("Rate limit exceeded for IP: {} ({} requests in window)", clientIp, timestamps.size());
                httpResponse.setStatus(429);
                httpResponse.setContentType("application/json");
                httpResponse.setCharacterEncoding("UTF-8");
                httpResponse.setHeader("Retry-After", "60");
                httpResponse.getWriter().write(
                        "{\"error\":\"Rate limit exceeded. Maximum " + MAX_REQUESTS + " requests per minute.\"}");
                return;
            }

            timestamps.addLast(now);
        }

        // Add rate-limit headers for client visibility
        httpResponse.setHeader("X-RateLimit-Limit", String.valueOf(MAX_REQUESTS));
        httpResponse.setHeader("X-RateLimit-Remaining", String.valueOf(MAX_REQUESTS - timestamps.size()));

        chain.doFilter(request, response);
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        return request.getRemoteAddr();
    }
}
