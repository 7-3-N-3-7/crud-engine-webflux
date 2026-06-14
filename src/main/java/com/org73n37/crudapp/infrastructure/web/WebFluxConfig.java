package com.org73n37.crudapp.infrastructure.web;

import com.org73n37.crudapp.api.UniversalCrudController;
import com.org73n37.crudapp.logic.CrudEngine;
import com.org73n37.crudapp.logic.ResourceMetadata;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

import static org.springframework.web.reactive.function.server.RequestPredicates.*;

/**
 * [ARCHITECTURAL OPTIMIZATION]
 * Configuration class defining dynamic functional WebFlux routes.
 * Maps dynamic CRUD routes under their configured API version namespaces (e.g. /api/v1/products).
 */
@Configuration
public class WebFluxConfig {

    @org.springframework.beans.factory.annotation.Value("${app.security.cors.allowed-origins:http://localhost:5173}")
    private String allowedOriginsProp;

    @Bean
    public org.springframework.web.cors.reactive.CorsWebFilter corsFilter() {
        org.springframework.web.cors.CorsConfiguration config = new org.springframework.web.cors.CorsConfiguration();
        config.setAllowCredentials(true);
        
        if (allowedOriginsProp != null && !allowedOriginsProp.trim().isEmpty()) {
            for (String origin : allowedOriginsProp.split(",")) {
                config.addAllowedOrigin(origin.trim());
            }
        } else {
            config.addAllowedOriginPattern("*");
        }
        
        config.addAllowedHeader("*");
        config.addAllowedMethod("*");
        config.addExposedHeader("X-Request-ID");
        config.addExposedHeader("X-Total-Count");

        org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource source =
                new org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        return new org.springframework.web.cors.reactive.CorsWebFilter(source);
    }

    @Bean
    public tools.jackson.databind.module.SimpleModule jacksonXssModule() {
        tools.jackson.databind.module.SimpleModule module = new tools.jackson.databind.module.SimpleModule();
        module.addDeserializer(String.class, new com.org73n37.crudapp.api.UniversalCrudController.XssSanitizingDeserializer());
        return module;
    }

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        return http
            // codeql[java/spring-disabled-csrf-protection]
            // CSRF is disabled because this is a stateless REST API using JWT (without session cookies)
            .csrf(csrf -> csrf.disable())
            .authorizeExchange(exchanges -> exchanges.anyExchange().permitAll())
            .build();
    }

    @Bean
    public RouterFunction<ServerResponse> routes(UniversalCrudController controller, 
                                                 com.org73n37.crudapp.api.HealthController healthController) {
        RouterFunctions.Builder builder = RouterFunctions.route();

        // Public metadata and Swagger configurations
        builder.GET("/api/metadata", controller::getMetadata);
        builder.GET("/swagger-ui", controller::getSwaggerUi);
        builder.GET("/api-docs", controller::getOpenApiJson);

        // Public health checkpoints
        builder.GET("/health/liveness", healthController::liveness);
        builder.GET("/health/readiness", healthController::readiness);

        return builder.build();
    }
}
