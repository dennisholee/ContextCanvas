package com.contextcanvas.server.tool;

import com.contextcanvas.server.service.PreferenceService;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** MCP tool that retrieves all saved user preferences (cross-session memory). */
@Component
public class GetPreferencesTool implements McpTool {

    private final PreferenceService preferenceService;

    public GetPreferencesTool(PreferenceService preferenceService) {
        this.preferenceService = preferenceService;
    }

    @Override
    public String getName() {
        return "get_preferences";
    }

    @Override
    public String getDescription() {
        return "Returns all saved user preferences. Call this at session start to learn the user's "
                + "preferred chart types, default date ranges, and other settings from previous sessions.";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(),
                "required", List.of());
    }

    @Override
    public Object execute(JsonNode arguments) {
        return Map.of("preferences", preferenceService.getAll());
    }
}