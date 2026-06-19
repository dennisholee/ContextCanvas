package com.contextcanvas.server.tool;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import com.contextcanvas.server.service.QueryExecutionService;
import com.contextcanvas.server.validation.SqlValidator;
import com.fasterxml.jackson.databind.JsonNode;

/** MCP tool that executes parameterized SELECT queries. */
@Component
public class QueryTool implements McpTool {

    private final QueryExecutionService queryService;
    private final SqlValidator sqlValidator;

    public QueryTool(QueryExecutionService queryService, SqlValidator sqlValidator) {
        this.queryService = queryService;
        this.sqlValidator = sqlValidator;
    }

    @Override
    public String getName() {
        return "query";
    }

    @Override
    public String getDescription() {
        return "Execute a parameterized SELECT query against the database. "
                + "Use ? placeholders for all user-supplied values. "
                + "Only SELECT statements are permitted. Returns column names and row data.";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        return Map.of("type", "object", "properties", Map.of("sql",
                Map.of("type", "string", "description", "SELECT statement with ? placeholders"),
                "params", Map.of("type", "array", "items", Map.of("type", "string"), "description",
                        "Parameter values matching ? placeholders")),
                "required", List.of("sql"));
    }

    @Override
    public Object execute(JsonNode arguments) {
        var sql = arguments.get("sql").asText();
        var params = extractParams(arguments);

        sqlValidator.assertSelectOnly(sql);
        sqlValidator.assertParameterized(sql, params);

        var result = queryService.execute(sql, params);
        return Map.of("columns", result.columns(), "rows", result.rows());
    }

    private List<Object> extractParams(JsonNode args) {
        var params = new ArrayList<>();
        if (args.has("params") && args.get("params").isArray()) {
            args.get("params").forEach(p -> params.add(p.asText()));
        }
        return params;
    }
}
