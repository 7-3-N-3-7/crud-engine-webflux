package com.org73n37.crudapp.infrastructure.web;

import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;
import java.util.UUID;

/**
 * [PERFORMANCE OPTIMIZATION]
 * Reactive correlation log tracer WebFilter.
 * Generates and propagates a request-specific diagnostic trace token (X-Request-ID) 
 * across asynchronous Reactive Context boundaries.
 */
@Component
public class ReactiveRequestTracingFilter implements WebFilter {
    public static final String REQUEST_ID_HEADER = "X-Request-ID";
    public static final String MDC_KEY = "requestId";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String reqId = exchange.getRequest().getHeaders().getFirst(REQUEST_ID_HEADER);
        if (reqId == null || reqId.isBlank() || !reqId.matches("^[a-zA-Z0-9_\\-]+$")) {
            reqId = UUID.randomUUID().toString();
        }

        final String requestId = reqId;
        exchange.getResponse().getHeaders().add(REQUEST_ID_HEADER, requestId);
        exchange.getAttributes().put(MDC_KEY, requestId);

        // Put request ID into Reactive context so that downstream logs/handlers can fetch it
        return chain.filter(exchange)
                .contextWrite(Context.of(MDC_KEY, requestId));
    }
}
