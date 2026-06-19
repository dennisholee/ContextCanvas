package com.contextcanvas.server.tool;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import com.contextcanvas.server.service.SchemaIntrospectionService;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * MCP tool that introspects the database schema at session start.
 * <p>
 * Returns all table names, column definitions (name, type, nullable, defaults, primary keys), and
 * foreign key relationships. The agent calls this once at session start to discover the data model
 * before generating SQL queries.
 */
@Component
public class IntrospectSchemaTool implements McpTool {

    private final SchemaIntrospectionService introspectionService;

    public IntrospectSchemaTool(SchemaIntrospectionService introspectionService) {
        this.introspectionService = introspectionService;
    }

    @Override
    public String getName() {
        return "introspect_schema";
    }

    @Override
    public String getDescription() {
        return "Returns the complete database schema including all tables, column names, types, "
                + "foreign key relationships, and constraints. Call this at session start to "
                + "understand the data model before generating SQL queries.";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        return Map.of("type", "object", "properties", Map.of(), "required", List.of());
    }

    @Override
    public Object execute(JsonNode arguments) {
        var tables = introspectionService.introspect();
        var tableMaps = tables.stream().map(t -> {
            var colMaps = t.columns().stream().map(c -> {
                var colMap = new java.util.LinkedHashMap<String, Object>();
                colMap.put("name", c.name());
                colMap.put("type", c.type());
                colMap.put("nullable", !c.required());
                colMap.put("defaultValue", c.defaultValue());
                colMap.put("primaryKey", c.primaryKey());
                return colMap;
            }).toList();
            var fkMaps = t.foreignKeys().stream().map(fk -> {
                var fkMap = new java.util.LinkedHashMap<String, Object>();
                fkMap.put("from", fk.fromColumn());
                fkMap.put("toTable", fk.referencedTable());
                fkMap.put("to", fk.referencedColumn());
                return fkMap;
            }).toList();
            var tableMap = new java.util.LinkedHashMap<String, Object>();
            tableMap.put("tableName", t.tableName());
            tableMap.put("columns", colMaps);
            tableMap.put("foreignKeys", fkMaps);
            return tableMap;
        }).toList();
        return Map.of("tables", tableMaps);
    }
}
