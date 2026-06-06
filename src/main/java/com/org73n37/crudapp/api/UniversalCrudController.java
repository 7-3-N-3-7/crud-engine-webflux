package com.org73n37.crudapp.api;

import com.org73n37.crudapp.data.core.BaseEntity;
import com.org73n37.crudapp.logic.CrudEngine;
import com.org73n37.crudapp.logic.ResourceMetadata;
import com.org73n37.crudapp.logic.core.CrudService;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.DeserializationFeature;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * [INTERFACE LAYER]
 * Unified Spring WebFlux handler exposing dynamic REST CRUD endpoints,
 * OpenAPI documentation, and visual Swagger UI configurations.
 */
@Component
public class UniversalCrudController {
    private static final Logger log = LoggerFactory.getLogger(UniversalCrudController.class);

    private final CrudEngine crudManager;
    private final ObjectMapper objectMapper;
    private final Validator validator;
    private final TransactionTemplate transactionTemplate;
    private final jakarta.persistence.EntityManager entityManager;

    public UniversalCrudController(CrudEngine crudManager, PlatformTransactionManager transactionManager, jakarta.persistence.EntityManager entityManager) {
        this.crudManager = crudManager;
        this.entityManager = entityManager;
        this.transactionTemplate = new TransactionTemplate(transactionManager);

        tools.jackson.databind.module.SimpleModule module = new tools.jackson.databind.module.SimpleModule();
        module.addDeserializer(String.class, new XssSanitizingDeserializer());

        this.objectMapper = JsonMapper.builder()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .addModule(module)
                .build();
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        this.validator = factory.getValidator();
    }

    public Mono<ServerResponse> getMetadata(ServerRequest request) {
        log.debug("🔍 Fetching global metadata");
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, ResourceMetadata<?, ?>> e : crudManager.getResources().entrySet()) {
            Map<String, Object> resMeta = new LinkedHashMap<>();
            ResourceMetadata<?, ?> meta = e.getValue();
            resMeta.put("basePath", meta.getBasePath());
            resMeta.put("version", meta.getVersion());
            resMeta.put("entityClass", meta.getEntityClass().getName());
            resMeta.put("dtoClass", meta.getDtoClass().getName());
            resMeta.put("fields", meta.getFields());
            result.put(e.getKey(), resMeta);
        }
        return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(result);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public Mono<ResponseEntity<Flux<?>>> getAll(ServerRequest request) {
        String resource = request.pathVariable("resource");
        int page = Integer.parseInt(request.queryParam("page").orElse("0"));
        int size = Integer.parseInt(request.queryParam("size").orElse("10"));
        String sort = request.queryParam("sort").orElse(null);

        // Capture all query params for criteria filtering
        Map<String, List<String>> queryParams = new HashMap<>();
        request.queryParams().forEach((k, v) -> queryParams.put(k, new ArrayList<>(v)));

        ResourceMetadata metadata = getMetadataOrThrow(resource);

        return Mono.deferContextual(ctx -> {
            String tenantId = ctx.getOrDefault("tenantId", "default");
            String username = ctx.getOrDefault("username", "anonymous");
            String requestId = ctx.getOrDefault("requestId", "unknown");
            org.springframework.security.core.Authentication auth = 
                (org.springframework.security.core.Authentication) ctx.getOrEmpty(org.springframework.security.core.Authentication.class).orElse(null);

            return Mono.fromCallable(() -> {
                com.org73n37.crudapp.infrastructure.security.TenantContext.setTenantId(tenantId);
                org.slf4j.MDC.put("requestId", requestId);
                org.slf4j.MDC.put("tenantId", tenantId);
                org.slf4j.MDC.put("username", username);
                if (auth != null) {
                    org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);
                }
                try {
                    return transactionTemplate.execute(status -> {
                        setDbTenantContext(tenantId);
                        return metadata.getService().findAll(page, size, queryParams, sort, metadata.getDtoClass());
                    });
                } finally {
                    com.org73n37.crudapp.infrastructure.security.TenantContext.clear();
                    org.springframework.security.core.context.SecurityContextHolder.clearContext();
                    org.slf4j.MDC.clear();
                }
            }).subscribeOn(Schedulers.boundedElastic());
        }).flatMap(entityPage -> {
            // [PERFORMANCE OPTIMIZATION] Streaming response using Flux
            Flux<? extends BaseEntity> flux = Flux.fromIterable(entityPage.getContent());
            return Mono.just(ResponseEntity.ok()
                    .header("X-Total-Count", String.valueOf(entityPage.getTotalElements()))
                    .body((Flux<?>) flux));
        });
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public Mono<ResponseEntity<?>> getById(ServerRequest request) {
        String resource = request.pathVariable("resource");
        Long id = Long.parseLong(request.pathVariable("id"));

        ResourceMetadata metadata = getMetadataOrThrow(resource);

        return Mono.deferContextual(ctx -> {
            String tenantId = ctx.getOrDefault("tenantId", "default");
            String username = ctx.getOrDefault("username", "anonymous");
            String requestId = ctx.getOrDefault("requestId", "unknown");
            org.springframework.security.core.Authentication auth = 
                (org.springframework.security.core.Authentication) ctx.getOrEmpty(org.springframework.security.core.Authentication.class).orElse(null);

            return Mono.fromCallable(() -> {
                com.org73n37.crudapp.infrastructure.security.TenantContext.setTenantId(tenantId);
                org.slf4j.MDC.put("requestId", requestId);
                org.slf4j.MDC.put("tenantId", tenantId);
                org.slf4j.MDC.put("username", username);
                if (auth != null) {
                    org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);
                }
                try {
                    return transactionTemplate.execute(status -> {
                        setDbTenantContext(tenantId);
                        return metadata.getService().findById(id);
                    });
                } finally {
                    com.org73n37.crudapp.infrastructure.security.TenantContext.clear();
                    org.springframework.security.core.context.SecurityContextHolder.clearContext();
                    org.slf4j.MDC.clear();
                }
            }).subscribeOn(Schedulers.boundedElastic());
        }).flatMap(opt -> {
            if (opt.isPresent()) {
                return Mono.just(ResponseEntity.ok().body(opt.get()));
            } else {
                return Mono.error(new com.org73n37.crudapp.api.errors.ResourceNotFoundException("Resource '" + resource + "' with ID " + id + " not found"));
            }
        });
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public Mono<ResponseEntity<?>> create(ServerRequest request) {
        String resource = request.pathVariable("resource");
        ResourceMetadata metadata = getMetadataOrThrow(resource);

        return request.bodyToMono(metadata.getDtoClass())
            .flatMap(dto -> {
                validate(dto);
                BaseEntity entity = (BaseEntity) objectMapper.convertValue(dto, metadata.getEntityClass());

                return Mono.deferContextual(ctx -> {
                    String tenantId = ctx.getOrDefault("tenantId", "default");
                    String username = ctx.getOrDefault("username", "system");
                    String requestId = ctx.getOrDefault("requestId", "unknown");
                    org.springframework.security.core.Authentication auth = 
                        (org.springframework.security.core.Authentication) ctx.getOrEmpty(org.springframework.security.core.Authentication.class).orElse(null);

                    return Mono.fromCallable(() -> {
                        com.org73n37.crudapp.infrastructure.security.TenantContext.setTenantId(tenantId);
                        org.slf4j.MDC.put("requestId", requestId);
                        org.slf4j.MDC.put("tenantId", tenantId);
                        org.slf4j.MDC.put("username", username);
                        if (auth != null) {
                            org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);
                        } else {
                            org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(username, null, List.of())
                            );
                        }
                        try {
                            return transactionTemplate.execute(status -> {
                                setDbTenantContext(tenantId);
                                metadata.getInterceptor().beforeCreate(entity);
                                BaseEntity res = (BaseEntity) metadata.getService().save(entity);
                                metadata.getInterceptor().afterCreate(res);
                                log.info("[AUDIT MUTATION] Action=CREATE Resource={} User={} Tenant={} RecordID={}", resource, username, tenantId, res.getId());
                                return res;
                            });
                        } finally {
                            com.org73n37.crudapp.infrastructure.security.TenantContext.clear();
                            org.springframework.security.core.context.SecurityContextHolder.clearContext();
                            org.slf4j.MDC.clear();
                        }
                    }).subscribeOn(Schedulers.boundedElastic());
                });
            })
            .flatMap(saved -> Mono.just(ResponseEntity.status(201).body(saved)));
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public Mono<ResponseEntity<?>> update(ServerRequest request) {
        String resource = request.pathVariable("resource");
        Long id = Long.parseLong(request.pathVariable("id"));
        ResourceMetadata metadata = getMetadataOrThrow(resource);

        return request.bodyToMono(metadata.getDtoClass())
            .flatMap(dto -> {
                validate(dto);
                BaseEntity entity = (BaseEntity) objectMapper.convertValue(dto, metadata.getEntityClass());

                return Mono.deferContextual(ctx -> {
                    String tenantId = ctx.getOrDefault("tenantId", "default");
                    String username = ctx.getOrDefault("username", "system");
                    String requestId = ctx.getOrDefault("requestId", "unknown");
                    org.springframework.security.core.Authentication auth = 
                        (org.springframework.security.core.Authentication) ctx.getOrEmpty(org.springframework.security.core.Authentication.class).orElse(null);

                    return Mono.fromCallable(() -> {
                        com.org73n37.crudapp.infrastructure.security.TenantContext.setTenantId(tenantId);
                        org.slf4j.MDC.put("requestId", requestId);
                        org.slf4j.MDC.put("tenantId", tenantId);
                        org.slf4j.MDC.put("username", username);
                        if (auth != null) {
                            org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);
                        } else {
                            org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(username, null, List.of())
                            );
                        }
                        try {
                            return transactionTemplate.execute(status -> {
                                setDbTenantContext(tenantId);
                                metadata.getInterceptor().beforeUpdate(entity);
                                BaseEntity res = (BaseEntity) metadata.getService().update(id, entity);
                                metadata.getInterceptor().afterUpdate(res);
                                log.info("[AUDIT MUTATION] Action=UPDATE Resource={} User={} Tenant={} RecordID={}", resource, username, tenantId, res.getId());
                                return res;
                            });
                        } finally {
                            com.org73n37.crudapp.infrastructure.security.TenantContext.clear();
                            org.springframework.security.core.context.SecurityContextHolder.clearContext();
                            org.slf4j.MDC.clear();
                        }
                    }).subscribeOn(Schedulers.boundedElastic());
                });
            })
            .flatMap(updated -> Mono.just(ResponseEntity.ok().body(updated)));
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public Mono<ResponseEntity<Void>> delete(ServerRequest request) {
        String resource = request.pathVariable("resource");
        Long id = Long.parseLong(request.pathVariable("id"));
        ResourceMetadata metadata = getMetadataOrThrow(resource);

        return Mono.deferContextual(ctx -> {
            String tenantId = ctx.getOrDefault("tenantId", "default");
            String username = ctx.getOrDefault("username", "anonymous");
            String requestId = ctx.getOrDefault("requestId", "unknown");
            org.springframework.security.core.Authentication auth = 
                (org.springframework.security.core.Authentication) ctx.getOrEmpty(org.springframework.security.core.Authentication.class).orElse(null);

            return Mono.fromRunnable(() -> {
                com.org73n37.crudapp.infrastructure.security.TenantContext.setTenantId(tenantId);
                org.slf4j.MDC.put("requestId", requestId);
                org.slf4j.MDC.put("tenantId", tenantId);
                org.slf4j.MDC.put("username", username);
                if (auth != null) {
                    org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);
                }
                try {
                    transactionTemplate.executeWithoutResult(status -> {
                        setDbTenantContext(tenantId);
                        metadata.getInterceptor().beforeDelete(id);
                        metadata.getService().deleteById(id);
                        metadata.getInterceptor().afterDelete(id);
                        log.info("[AUDIT MUTATION] Action=DELETE Resource={} User={} Tenant={} RecordID={}", resource, username, tenantId, id);
                    });
                } finally {
                    com.org73n37.crudapp.infrastructure.security.TenantContext.clear();
                    org.springframework.security.core.context.SecurityContextHolder.clearContext();
                    org.slf4j.MDC.clear();
                }
            }).subscribeOn(Schedulers.boundedElastic());
        }).then(Mono.just(ResponseEntity.noContent().build()));
    }

    private ResourceMetadata getMetadataOrThrow(String resource) {
        ResourceMetadata metadata = crudManager.getMetadata(resource);
        if (metadata == null) {
            throw new com.org73n37.crudapp.api.errors.ResourceNotFoundException("Resource not found: " + resource);
        }
        return metadata;
    }

    private void validate(Object dto) {
        Set<ConstraintViolation<Object>> violations = validator.validate(dto);
        if (!violations.isEmpty()) {
            throw new com.org73n37.crudapp.api.errors.ValidationException(violations);
        }
    }

    public Mono<ServerResponse> getSwaggerUi(ServerRequest request) {
        return ServerResponse.ok().contentType(MediaType.TEXT_HTML).bodyValue("""
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <title>Swagger UI - Generic CRUD Engine</title>
                <link rel="stylesheet" type="text/css" href="https://unpkg.com/swagger-ui-dist@5/swagger-ui.css" />
                <style>
                    html { box-sizing: border-box; overflow: -y-scroll; }
                    *, *:before, *:after { box-sizing: inherit; }
                    body { margin:0; background: #fafafa; }
                </style>
            </head>
            <body>
                <div id="swagger-ui"></div>
                <script src="https://unpkg.com/swagger-ui-dist@5/swagger-ui-bundle.js"></script>
                <script src="https://unpkg.com/swagger-ui-dist@5/swagger-ui-standalone-preset.js"></script>
                <script>
                    window.onload = function() {
                        const ui = SwaggerUIBundle({
                            url: "/api-docs",
                            dom_id: '#swagger-ui',
                            deepLinking: true,
                            presets: [
                                SwaggerUIBundle.presets.apis,
                                SwaggerUIStandalonePreset
                            ],
                            plugins: [
                                SwaggerUIBundle.plugins.DownloadUrl
                            ],
                            layout: "BaseLayout"
                        });
                        window.ui = ui;
                    };
                </script>
            </body>
            </html>
            """);
    }

    @SuppressWarnings("unchecked")
    public Mono<ServerResponse> getOpenApiJson(ServerRequest request) {
        Map<String, Object> openapi = new LinkedHashMap<>();
        openapi.put("openapi", "3.0.1");

        Map<String, Object> info = new LinkedHashMap<>();
        info.put("title", "Generic CRUD Engine API");
        info.put("version", "2.0.0");
        info.put("description", "Dynamic metadata-driven secured CRUD endpoints.");
        openapi.put("info", info);

        openapi.put("servers", List.of(Map.of("url", "/")));

        Map<String, Object> paths = new LinkedHashMap<>();
        Map<String, Object> schemas = new LinkedHashMap<>();

        Map<String, Object> components = new LinkedHashMap<>();
        Map<String, Object> securitySchemes = new LinkedHashMap<>();
        securitySchemes.put("BearerAuth", Map.of(
                "type", "http",
                "scheme", "bearer",
                "bearerFormat", "JWT"
        ));
        components.put("securitySchemes", securitySchemes);
        components.put("schemas", schemas);
        openapi.put("components", components);

        openapi.put("security", List.of(Map.of("BearerAuth", List.of())));

        for (Map.Entry<String, ResourceMetadata<?, ?>> entry : crudManager.getResources().entrySet()) {
            String path = entry.getKey();
            ResourceMetadata<?, ?> metadata = entry.getValue();
            String schemaName = metadata.getDtoClass().getSimpleName();
            String version = metadata.getVersion();

            Map<String, Object> schema = new LinkedHashMap<>();
            schema.put("type", "object");

            Map<String, Object> properties = new LinkedHashMap<>();
            List<String> requiredFields = new ArrayList<>();

            for (ResourceMetadata.FieldInfo field : metadata.getFields()) {
                Map<String, Object> fieldProp = new LinkedHashMap<>();
                String typeName = field.getType().toLowerCase();

                if (typeName.contains("int") || typeName.contains("long")) {
                    fieldProp.put("type", "integer");
                } else if (typeName.contains("double") || typeName.contains("float") || typeName.contains("price") || typeName.contains("number")) {
                    fieldProp.put("type", "number");
                } else if (typeName.contains("boolean")) {
                    fieldProp.put("type", "boolean");
                } else {
                    fieldProp.put("type", "string");
                }

                properties.put(field.getName(), fieldProp);
                if (field.isRequired()) {
                    requiredFields.add(field.getName());
                }
            }

            schema.put("properties", properties);
            if (!requiredFields.isEmpty()) {
                schema.put("required", requiredFields);
            }
            schemas.put(schemaName, schema);

            String resourcePath = "/api/" + path;
            Map<String, Object> pathOperations = new LinkedHashMap<>();

            Map<String, Object> getOp = new LinkedHashMap<>();
            getOp.put("tags", List.of(path));
            getOp.put("summary", "List all " + path);
            getOp.put("parameters", List.of(
                    Map.of("name", "page", "in", "query", "required", false, "schema", Map.of("type", "integer", "default", 0)),
                    Map.of("name", "size", "in", "query", "required", false, "schema", Map.of("type", "integer", "default", 10)),
                    Map.of("name", "sort", "in", "query", "required", false, "schema", Map.of("type", "string"))
            ));
            getOp.put("responses", Map.of(
                    "200", Map.of(
                            "description", "Successful operation",
                            "content", Map.of("application/json", Map.of("schema", Map.of("type", "array", "items", Map.of("$ref", "#/components/schemas/" + schemaName))))
                    )
            ));
            pathOperations.put("get", getOp);

            Map<String, Object> postOp = new LinkedHashMap<>();
            postOp.put("tags", List.of(path));
            postOp.put("summary", "Create new " + path);
            postOp.put("requestBody", Map.of(
                    "required", true,
                    "content", Map.of("application/json", Map.of("schema", Map.of("$ref", "#/components/schemas/" + schemaName)))
            ));
            postOp.put("responses", Map.of(
                    "201", Map.of(
                            "description", "Created",
                            "content", Map.of("application/json", Map.of("schema", Map.of("$ref", "#/components/schemas/" + schemaName)))
                    )
            ));
            pathOperations.put("post", postOp);
            paths.put(resourcePath, pathOperations);

            String singleResourcePath = resourcePath + "/{id}";
            Map<String, Object> singlePathOperations = new LinkedHashMap<>();

            List<Map<String, Object>> pathParams = List.of(
                    Map.of("name", "id", "in", "path", "required", true, "schema", Map.of("type", "integer"))
            );

            Map<String, Object> getByIdOp = new LinkedHashMap<>();
            getByIdOp.put("tags", List.of(path));
            getByIdOp.put("summary", "Get " + path + " by ID");
            getByIdOp.put("parameters", pathParams);
            getByIdOp.put("responses", Map.of(
                    "200", Map.of(
                            "description", "Successful operation",
                            "content", Map.of("application/json", Map.of("schema", Map.of("$ref", "#/components/schemas/" + schemaName)))
                    ),
                    "404", Map.of("description", "Not Found")
            ));
            singlePathOperations.put("get", getByIdOp);

            Map<String, Object> putOp = new LinkedHashMap<>();
            putOp.put("tags", List.of(path));
            putOp.put("summary", "Update " + path + " by ID");
            putOp.put("parameters", pathParams);
            putOp.put("requestBody", Map.of(
                    "required", true,
                    "content", Map.of("application/json", Map.of("schema", Map.of("$ref", "#/components/schemas/" + schemaName)))
            ));
            putOp.put("responses", Map.of(
                    "200", Map.of(
                            "description", "Successful operation",
                            "content", Map.of("application/json", Map.of("schema", Map.of("$ref", "#/components/schemas/" + schemaName)))
                    )
            ));
            singlePathOperations.put("put", putOp);

            Map<String, Object> deleteOp = new LinkedHashMap<>();
            deleteOp.put("tags", List.of(path));
            deleteOp.put("summary", "Delete " + path + " by ID");
            deleteOp.put("parameters", pathParams);
            deleteOp.put("responses", Map.of(
                    "204", Map.of("description", "No Content")
            ));
            singlePathOperations.put("delete", deleteOp);

            paths.put(singleResourcePath, singlePathOperations);
        }

        openapi.put("paths", paths);
        return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(openapi);
    }

    private void setDbTenantContext(String tenantId) {
        if (tenantId == null || !tenantId.matches("^[a-zA-Z0-9_\\-]+$")) {
            throw new IllegalArgumentException("Invalid tenant identifier format");
        }
        entityManager.createNativeQuery("SET LOCAL app.current_tenant = '" + tenantId + "'").executeUpdate();
    }

    public static class XssSanitizingDeserializer extends tools.jackson.databind.ValueDeserializer<String> {
        @Override
        public String deserialize(tools.jackson.core.JsonParser p, tools.jackson.databind.DeserializationContext ctxt) {
            String value = p.getValueAsString();
            if (value == null) {
                return null;
            }
            // Basic anti-XSS: strip HTML tag patterns and javascript prefixes
            return value.replaceAll("(?i)<script.*?>.*?</script.*?>", "")
                        .replaceAll("(?i)<[^>]*?>", "")
                        .replaceAll("(?i)javascript:", "");
        }
    }
}
