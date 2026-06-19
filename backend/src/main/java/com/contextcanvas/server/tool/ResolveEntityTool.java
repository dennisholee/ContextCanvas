package com.contextcanvas.server.tool;

import com.contextcanvas.server.validation.EntityResolver;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** MCP tool for fuzzy entity resolution (self-healing queries). */
@Component
public class ResolveEntityTool implements McpTool {

    private final EntityResolver entityResolver;

    public ResolveEntityTool(EntityResolver entityResolver) {
        this.entityResolver = entityResolver;
    }

    @Override
    public String getName() {
        return "resolve_entity";
    }

    @Override
    public String getDescription() {
        return "Search for an entity by name across a specific table. "
                + "Returns matching records with their ID and display name. "
                + "Uses exact match first, then LIKE, then contact name matching.";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "name", Map.of(
                                "type", "string",
                                "description", "The entity name to search for"),
                        "table", Map.of(
                                "type", "string",
                                "description", "The table to search in (clients, sales, contacts)")),
                "required", List.of("name", "table"));
    }

    @Override
    public Object execute(JsonNode arguments) {
        var name = arguments.get("name").asText();
        var table = arguments.get("table").asText();
        var matches = entityResolver.resolve(name, table);
        return Map.of("matches", matches);
    }
}