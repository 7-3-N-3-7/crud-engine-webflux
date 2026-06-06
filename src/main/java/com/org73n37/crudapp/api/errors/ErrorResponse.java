package com.org73n37.crudapp.api.errors;

import java.time.Instant;
import java.util.Map;

public record ErrorResponse(
    Instant timestamp,
    int status,
    String error,
    String message,
    String path,
    String requestId,
    Map<String, String> details
) {}
