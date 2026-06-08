# WebFlux Routing Module Architecture (Mermaid)

This file contains Mermaid diagrams visualizing the structure and design of the WebFlux routing module (`crud-engine-webflux`).

## 1. Class Structure

```mermaid
classDiagram
    class UniversalCrudController {
        -CrudEngine crudManager
        -ObjectMapper objectMapper
        -Validator validator
        +getMetadata(ServerRequest) Mono
        +getAll(ServerRequest) Mono
        +getById(ServerRequest) Mono
        +create(ServerRequest) Mono
        +update(ServerRequest) Mono
        +delete(ServerRequest) Mono
    }

    class DynamicControllerRegister {
        -CrudEngine engine
        -UniversalCrudController controller
        +getRouterFunction() RouterFunction
    }

    class WebFluxConfig {
        +corsWebFilter() CorsWebFilter
    }

    class ReactiveRequestTracingFilter {
        +filter(ServerWebExchange, WebFilterChain) Mono
    }

    DynamicControllerRegister --> UniversalCrudController : references
```

## 2. Reactive Request Lifecycle

```mermaid
sequenceDiagram
    autonumber
    actor User
    participant Filter as ReactiveRequestTracingFilter
    participant Register as DynamicControllerRegister
    participant Controller as UniversalCrudController
    
    User->>Filter: HTTP GET /api/products
    Filter->>Filter: Inject X-Request-ID Header
    Filter->>Register: Match route
    Register->>Controller: Invoke getAll(request)
    Controller->>Controller: Fetch reactive Context
    Controller-->>User: HTTP 200 OK + JSON Stream
```
