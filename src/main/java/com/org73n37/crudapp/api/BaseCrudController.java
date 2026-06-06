package com.org73n37.crudapp.api;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.support.ServerRequestWrapper;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.Map;
import java.util.HashMap;

/**
 * Base class for dynamically generated RestControllers.
 * Inherits standard request mappings and delegates to UniversalCrudController.
 */
public abstract class BaseCrudController {

    private final UniversalCrudController delegate;
    private final String resource;

    @Autowired
    private ServerCodecConfigurer codecConfigurer;

    protected BaseCrudController(UniversalCrudController delegate, String resource) {
        this.delegate = delegate;
        this.resource = resource;
    }

    private ServerRequest createRequest(ServerWebExchange exchange) {
        ServerRequest req = ServerRequest.create(exchange, codecConfigurer.getReaders());
        
        Map<String, String> uriVariables = exchange.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        final Map<String, String> mergedVars = new HashMap<>();
        if (uriVariables != null) {
            mergedVars.putAll(uriVariables);
        }
        mergedVars.put("resource", resource);

        return new ServerRequestWrapper(req) {
            @Override
            public String pathVariable(String name) {
                String val = mergedVars.get(name);
                if (val != null) {
                    return val;
                }
                return super.pathVariable(name);
            }

            @Override
            public Map<String, String> pathVariables() {
                Map<String, String> pvs = new HashMap<>(super.pathVariables());
                pvs.putAll(mergedVars);
                return pvs;
            }
        };
    }

    @GetMapping
    public Mono<ResponseEntity<Flux<?>>> getAll(ServerWebExchange exchange) {
        return delegate.getAll(createRequest(exchange));
    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<?>> getById(ServerWebExchange exchange) {
        return delegate.getById(createRequest(exchange));
    }

    @PostMapping
    public Mono<ResponseEntity<?>> create(ServerWebExchange exchange) {
        return delegate.create(createRequest(exchange));
    }

    @PutMapping("/{id}")
    public Mono<ResponseEntity<?>> update(ServerWebExchange exchange) {
        return delegate.update(createRequest(exchange));
    }

    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Void>> delete(ServerWebExchange exchange) {
        return delegate.delete(createRequest(exchange));
    }
}
