package com.org73n37.crudapp.infrastructure.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * [SECURITY HARDENING / EDGE OPTIMIZATION]
 * Thread-safe, reactive, in-memory rate limiter using a sliding Token Bucket algorithm.
 * Restricts excessive API querying to prevent Denial of Service (DoS) and brute force attacks.
 */
@Component
public class ReactiveRateLimiterFilter implements WebFilter {
    private static final Logger log = LoggerFactory.getLogger(ReactiveRateLimiterFilter.class);

    private static final int MAX_TOKENS = 50; // Max requests allowed in bucket
    private static final long REFILL_DURATION_MS = 60000; // Time frame: 1 minute

    private final Map<String, TokenBucket> ipBuckets = new ConcurrentHashMap<>();

    @org.springframework.beans.factory.annotation.Autowired
    private com.org73n37.crudapp.infrastructure.config.AppModeConfig appModeConfig;

    private volatile boolean forceRateLimit = false;

    public boolean isForceRateLimit() {
        return forceRateLimit;
    }

    public void setForceRateLimit(boolean forceRateLimit) {
        this.forceRateLimit = forceRateLimit;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String ip = exchange.getRequest().getRemoteAddress() != null 
                ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress() 
                : "unknown";

        TokenBucket bucket = ipBuckets.computeIfAbsent(ip, k -> new TokenBucket(MAX_TOKENS, REFILL_DURATION_MS));

        if ((appModeConfig.isDevelopment() && !forceRateLimit) || bucket.tryConsume()) {
            return chain.filter(exchange);
        } else {
            log.warn("[SECURITY EVENT] Action=RATE_LIMIT_EXCEEDED IP={}", ip);
            exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
            String jsonError = "{\n" +
                    "  \"timestamp\": \"" + Instant.now() + "\",\n" +
                    "  \"status\": 429,\n" +
                    "  \"error\": \"Too Many Requests\",\n" +
                    "  \"message\": \"API rate limit exceeded. Please try again later.\"\n" +
                    "}";
            byte[] bytes = jsonError.getBytes(StandardCharsets.UTF_8);
            return exchange.getResponse().writeWith(Mono.just(
                    exchange.getResponse().bufferFactory().wrap(bytes)
            ));
        }
    }

    private static class TokenBucket {
        private final int capacity;
        private final long refillDurationMs;
        private double tokens;
        private long lastRefillTime;

        public TokenBucket(int capacity, long refillDurationMs) {
            this.capacity = capacity;
            this.refillDurationMs = refillDurationMs;
            this.tokens = capacity;
            this.lastRefillTime = System.currentTimeMillis();
        }

        public synchronized boolean tryConsume() {
            refill();
            if (tokens >= 1) {
                tokens -= 1;
                return true;
            }
            return false;
        }

        private void refill() {
            long now = System.currentTimeMillis();
            long elapsedTime = now - lastRefillTime;
            if (elapsedTime > 0) {
                double tokensToAdd = ((double) elapsedTime / refillDurationMs) * capacity;
                tokens = Math.min(capacity, tokens + tokensToAdd);
                lastRefillTime = now;
            }
        }
    }

    public void reset() {
        this.ipBuckets.clear();
    }
}
