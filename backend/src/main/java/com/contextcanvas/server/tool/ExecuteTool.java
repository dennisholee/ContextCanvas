package com.contextcanvas.server.tool;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import com.contextcanvas.server.model.ExecutionResult;
import com.contextcanvas.server.service.WriteExecutionService;
import com.contextcanvas.server.validation.SqlValidator;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * MCP tool for executing INSERT/UPDATE/DELETE statements. First call returns PENDING_CONFIRMATION.
 * Second call with confirmationToken executes.
 */
@Component
public class ExecuteTool implements McpTool {

    private final WriteExecutionService writeService;
    private final SqlValidator sqlValidator;

    public ExecuteTool(WriteExecutionService writeService, SqlValidator sqlValidator) {
        this.writeService = writeService;
        this.sqlValidator = sqlValidator;
    }

    @Override
    public String getName() {
        return "execute";
    }

    @Override
    public String getDescription() {
        return "Execute an INSERT, UPDATE, or DELETE statement. "
                + "Use ? placeholders for all user-supplied values. "
                + "On first call WITHOUT a confirmationToken, returns a PENDING_CONFIRMATION "
                + "response with a confirmationToken. The user must approve via the confirmation "
                + "card before you call execute again WITH the confirmationToken to finalize.";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        return Map.of("type", "object", "properties", Map.of("sql",
                Map.of("type", "string", "description",
                        "INSERT, UPDATE, or DELETE with ? placeholders"),
                "params",
                Map.of("type", "array", "items", Map.of("type", "string"), "description",
                        "Parameter values matching ? placeholders"),
                "confirmationToken",
                Map.of("type", "string", "description",
                        "Optional. Include ONLY after user has approved the confirmation card.")),
                "required", List.of("sql"));
    }

    @Override
    public Object execute(JsonNode arguments) {
        var sql = arguments.get("sql").asText();
        var params = extractParams(arguments);

        sqlValidator.assertDmlOnly(sql);
        sqlValidator.assertParameterized(sql, params);

        ExecutionResult result;
        // If confirmationToken is present, this is the second call — execute
        if (arguments.has("confirmationToken") && !arguments.get("confirmationToken").isNull()) {
            var token = arguments.get("confirmationToken").asText();
            result = writeService.confirm(token);
        } else {
            // First call — prepare (don't execute)
            result = writeService.prepare(sql, params);
        }
        return Map.of("status", result.status(), "actionType", result.actionType(), "entity",
                result.entity(), "newValues", result.newValues(), "oldValues", result.oldValues(),
                "confirmationToken",
                result.confirmationToken() != null ? result.confirmationToken() : "", "message",
                result.message());
    }

    private List<Object> extractParams(JsonNode args) {
        var params = new ArrayList<>();
        if (args.has("params") && args.get("params").isArray()) {
            args.get("params").forEach(p -> params.add(p.asText()));
        }
        return params;
    }
}
