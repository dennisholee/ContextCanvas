package com.contextcanvas.server.tool;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

/**
 * Interface for all MCP tools.
 * Each tool provides a name, description, JSON Schema parameters, and an execute method.
 */
public interface McpTool {

    /** The name of the tool as exposed to the LLM. */
    String getName();

    /** A human-readable description of what the tool does. */
    String getDescription();

    /** JSON Schema definition of the tool's parameters for LLM consumption. */
    Map<String, Object> getParameterSchema();

    /** Execute the tool with the given arguments (from JSON-RPC params). */
    Object execute(JsonNode arguments);

    /** Returns the full tool definition as a Map for the MCP tools/list response. */
    default Map<String, Object> toDefinition() {
        return Map.of(
                "name", getName(),
                "description", getDescription(),
                "parameters", getParameterSchema());
    }
}