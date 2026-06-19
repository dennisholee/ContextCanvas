package com.contextcanvas.server.service;

import com.contextcanvas.server.model.ExecutionResult;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Handles INSERT/UPDATE/DELETE with a safety gate.
 * First call returns PENDING_CONFIRMATION with a token.
 * Second call with the correct confirmationToken executes the write.
 */
@Service
public class WriteExecutionService {

    private final JdbcTemplate jdbc;
    private final Map<String, PendingWrite> pendingWrites = new ConcurrentHashMap<>();

    public WriteExecutionService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * First call: validates the SQL and returns a confirmation payload.
     * Does NOT execute the write.
     */
    public ExecutionResult prepare(String sql, List<Object> params) {
        var token = UUID.randomUUID().toString().substring(0, 8);
        var actionType = detectActionType(sql);
        var entity = detectEntity(sql);

        // Extract new values from the SQL for the confirmation card
        var newValues = extractValues(sql, params);

        pendingWrites.put(token, new PendingWrite(sql, params, System.currentTimeMillis()));

        return new ExecutionResult(
                "PENDING_CONFIRMATION",
                actionType,
                entity,
                newValues,
                Map.of(),           // oldValues — determined by LLM
                token,
                "Confirmation required. Use the confirmationToken to execute.");
    }

    /**
     * Second call: executes the write if the confirmationToken is valid and not expired.
     */
    public ExecutionResult confirm(String confirmationToken) {
        var pending = pendingWrites.remove(confirmationToken);
        if (pending == null) {
            return new ExecutionResult(
                    "ERROR", "", "", Map.of(), Map.of(), "",
                    "Invalid or expired confirmation token.");
        }

        var now = System.currentTimeMillis();
        if (now - pending.timestamp() > 60_000) { // 60 second expiry
            return new ExecutionResult(
                    "ERROR", "", "", Map.of(), Map.of(), "",
                    "Confirmation token expired. Please submit the request again.");
        }

        try {
            int rows = jdbc.update(pending.sql(), pending.params().toArray());
            return new ExecutionResult(
                    "EXECUTED",
                    detectActionType(pending.sql()),
                    detectEntity(pending.sql()),
                    Map.of("affectedRows", rows),
                    Map.of(),
                    null,
                    "Successfully executed. %d row(s) affected.".formatted(rows));
        } catch (Exception e) {
            return new ExecutionResult(
                    "ERROR", "", "", Map.of(), Map.of(), "",
                    "Execution failed: " + e.getMessage());
        }
    }

    private String detectActionType(String sql) {
        var trimmed = sql.trim().toUpperCase();
        if (trimmed.startsWith("INSERT")) return "CREATE";
        if (trimmed.startsWith("UPDATE")) return "UPDATE";
        if (trimmed.startsWith("DELETE")) return "DELETE";
        return "UNKNOWN";
    }

    private String detectEntity(String sql) {
        var trimmed = sql.trim();
        // Very simple entity extraction — works for basic SQL
        var lower = trimmed.toLowerCase();
        if (lower.contains("clients")) return "clients";
        if (lower.contains("sales")) return "sales";
        if (lower.contains("contacts")) return "contacts";
        return "unknown";
    }

    private Map<String, Object> extractValues(String sql, List<Object> params) {
        // Extract column names from INSERT INTO table (col1, col2) VALUES (?, ?)
        var values = new LinkedHashMap<String, Object>();
        if (params == null || params.isEmpty()) return values;

        var lower = sql.toLowerCase();
        if (lower.startsWith("insert")) {
            var parenStart = sql.indexOf('(');
            var parenEnd = sql.indexOf(')');
            if (parenStart >= 0 && parenEnd > parenStart) {
                var cols = sql.substring(parenStart + 1, parenEnd)
                        .split(",");
                for (int i = 0; i < cols.length && i < params.size(); i++) {
                    values.put(cols[i].trim(), params.get(i));
                }
            }
        }
        return values;
    }

    private record PendingWrite(String sql, List<Object> params, long timestamp) {}
}