package com.contextcanvas.server.tool;

import com.contextcanvas.server.service.PreferenceService;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** MCP tool for persisting user preferences (cross-session learning). */
@Component
public class TrackPreferenceTool implements McpTool {

    private final PreferenceService preferenceService;

    public TrackPreferenceTool(PreferenceService preferenceService) {
        this.preferenceService = preferenceService;
    }

    @Override
    public String getName() {
        return "track_preference";
    }

    @Override
    public String getDescription() {
        return "Save a user preference that persists across sessions. "
                + "Use this when the user has indicated a preference (e.g., chart type, date range, "
                + "grouping) that should be remembered. Preferences are lightweight key-value pairs.";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "key", Map.of(
                                "type", "string",
                                "description", "Preference key (e.g., preferred_chart_trend, default_date_range)"),
                        "value", Map.of(
                                "type", "string",
                                "description", "Preference value (e.g., bar_chart, quarter)")),
                "required", List.of("key", "value"));
    }

    @Override
    public Object execute(JsonNode arguments) {
        var key = arguments.get("key").asText();
        var value = arguments.get("value").asText();
        preferenceService.set(key, value);
        return Map.of("status", "saved", "key", key, "value", value);
    }
}