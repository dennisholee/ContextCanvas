package com.contextcanvas.server.service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import com.contextcanvas.server.tool.McpTool;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Single dispatcher for all MCP tool invocations. Both McpServer (stdio) and McpRestController
 * (HTTP) delegate to this service, ensuring consistent tool dispatch behavior regardless of
 * transport layer.
 *
 * Eliminates the dual-dispatch bug pattern where stdio and HTTP paths could diverge.
 */
@Component
public class ToolDispatcher {

    private static final Logger log = LoggerFactory.getLogger(ToolDispatcher.class);

    private final Map<String, McpTool> tools;
    private final ObjectMapper json;

    public ToolDispatcher(List<McpTool> toolList, ObjectMapper json) {
        this.json = json;
        this.tools = new ConcurrentHashMap<>();
        for (McpTool tool : toolList) {
            this.tools.put(tool.getName(), tool);
            log.info("Registered tool: {} - {}", tool.getName(), tool.getDescription());
        }
    }

    /**
     * Returns the list of tool definitions for the LLM to discover.
     */
    public List<Map<String, Object>> listTools() {
        return tools.values().stream().map(McpTool::toDefinition).toList();
    }

    /**
     * Invokes a tool by name with the given arguments.
     *
     * @param toolName the name of the tool to invoke
     * @param arguments the JSON arguments for the tool
     * @return the tool's result object
     * @throws IllegalArgumentException if the tool is not found
     */
    public Object callTool(String toolName, JsonNode arguments) {
        var tool = tools.get(toolName);
        if (tool == null) {
            log.warn("Tool not found: {}", toolName);
            throw new IllegalArgumentException("Tool not found: " + toolName);
        }

        // Log tool args summary (truncate SQL for readability)
        var argsSummary = summarizeArgs(toolName, arguments);
        long startTime = System.nanoTime();

        var result = tool.execute(arguments);

        long elapsedNs = System.nanoTime() - startTime;
        long elapsedMs = elapsedNs / 1_000_000;

        // Log result summary
        var resultSummary = summarizeResult(result);
        log.debug("Tool call: {}({}) → {} ({}ms)", toolName, argsSummary, resultSummary, elapsedMs);

        if (result instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            var typed = (Map<String, Object>) map;
            return typed;
        }
        return Map.of("result", result);
    }

    /** Creates a compact string summary of tool arguments for logging. */
    private String summarizeArgs(String toolName, JsonNode args) {
        if (args == null)
            return "";
        if (toolName.equals("query") || toolName.equals("execute")) {
            var sql = args.has("sql") ? args.get("sql").asText() : "";
            var params = args.has("params") ? args.get("params").toString() : "[]";
            return "sql=\"" + truncate(sql, 60) + "\" params=" + truncate(params, 40);
        }
        return truncate(args.toString(), 80);
    }

    /** Creates a compact string summary of tool result for logging. */
    private String summarizeResult(Object result) {
        if (result instanceof Map<?, ?> map) {
            if (map.containsKey("status") && map.containsKey("rows")) {
                // QueryResult
                var rows = map.get("rows");
                int rowCount = (rows instanceof List) ? ((List<?>) rows).size() : 0;
                var cols = map.get("columns");
                int colCount = (cols instanceof List) ? ((List<?>) cols).size() : 0;
                return rowCount + " rows, " + colCount + " cols";
            }
            if (map.containsKey("status")) {
                // ExecutionResult
                var actionType = map.get("actionType");
                return "status=" + map.get("status")
                        + (actionType != null ? " action=" + actionType : "");
            }
            if (map.containsKey("tables")) {
                // Schema introspection
                var tables = map.get("tables");
                int tableCount = (tables instanceof List) ? ((List<?>) tables).size() : 0;
                return tableCount + " tables";
            }
            return "Map(" + map.size() + " keys)";
        }
        return String.valueOf(result).substring(0, Math.min(60, String.valueOf(result).length()));
    }

    private static String truncate(String s, int maxLen) {
        if (s == null)
            return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }

    /**
     * Returns the number of registered tools (for health-check reporting).
     */
    public int toolCount() {
        return tools.size();
    }
}
