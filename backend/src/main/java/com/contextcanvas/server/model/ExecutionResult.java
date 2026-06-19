package com.contextcanvas.server.model;

import java.util.Map;

/** Result of an execute (INSERT/UPDATE/DELETE) operation. */
public record ExecutionResult(
        String status,               // "PENDING_CONFIRMATION" or "EXECUTED"
        String actionType,           // "CREATE", "UPDATE", "DELETE"
        String entity,               // Table name
        Map<String, Object> newValues,
        Map<String, Object> oldValues,
        String confirmationToken,    // Present when status is PENDING_CONFIRMATION
        String message) {}           // Success or error message