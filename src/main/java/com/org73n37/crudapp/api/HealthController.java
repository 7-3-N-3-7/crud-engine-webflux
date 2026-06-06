package com.org73n37.crudapp.api;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Map;

/**
 * [INTERFACE LAYER]
 * Health check endpoint controller for liveness and database connection readiness queries.
 */
@Component
public class HealthController {
    private final DataSource dataSource;

    public HealthController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public Mono<ServerResponse> liveness(ServerRequest request) {
        return ServerResponse.ok().contentType(MediaType.TEXT_PLAIN).bodyValue("UP");
    }

    public Mono<ServerResponse> readiness(ServerRequest request) {
        return Mono.fromCallable(() -> {
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.execute("SELECT 1");
                return Map.of("status", "UP", "database", "UP");
            }
        })
        .subscribeOn(Schedulers.boundedElastic())
        .flatMap(health -> ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(health))
        .onErrorResume(e -> ServerResponse.status(503).contentType(MediaType.APPLICATION_JSON).bodyValue(
            Map.of("status", "DOWN", "database", "DOWN", "error", e.getMessage())
        ));
    }
}
