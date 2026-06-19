package com.contextcanvas.server.config;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import com.contextcanvas.server.service.ToolDispatcher;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;

/**
 * MCP server implementation using JSON-RPC over stdin/stdout. Delegates to {@link ToolDispatcher}
 * for all tool dispatch, ensuring consistency with the HTTP REST transport (McpRestController).
 *
 * This transport is available for direct LLM integration via MCP protocol but is NOT the primary
 * path used by the frontend. The frontend uses HTTP REST via McpRestController.
 */
@Component
public class McpServer {

    private static final Logger log = LoggerFactory.getLogger(McpServer.class);

    private final String serverName;
    private final String serverVersion;
    private final ToolDispatcher dispatcher;
    private final ObjectMapper json = new ObjectMapper();

    public McpServer(String serverName, String serverVersion, ToolDispatcher dispatcher) {
        this.serverName = serverName;
        this.serverVersion = serverVersion;
        this.dispatcher = dispatcher;
    }

    /**
     * Direct tool invocation for testing. Bypasses JSON-RPC transport. Returns the result object
     * directly.
     */
    public Map<String, Object> callTool(String toolName, JsonNode arguments) {
        var result = dispatcher.callTool(toolName, arguments);
        if (result instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            var typed = (Map<String, Object>) map;
            return typed;
        }
        return Map.of("result", result);
    }

    @PostConstruct
    public void start() {
        log.info("Starting MCP server: {} v{} with {} tools (delegating to ToolDispatcher)",
                serverName, serverVersion, dispatcher.toolCount());

        // Start stdin reader thread for MCP stdio transport.
        // If stdin is closed (e.g. running as a web app without a MCP host),
        // this thread will exit gracefully without affecting the application.
        new Thread(() -> {
            try (var reader = new BufferedReader(new InputStreamReader(System.in));
                    var writer = new PrintWriter(System.out, true)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    handleMessage(line, writer);
                }
            } catch (Exception e) {
                // Stdin closed — this is expected when running as a web app
                // without an MCP host process. Graceful exit.
                log.debug("MCP stdin reader exited (expected if no MCP host connected): {}",
                        e.getMessage());
            }
        }, "mcp-stdin-reader").start();
    }

    void handleMessage(String line, PrintWriter writer) {
        try {
            var request = json.readTree(line);
            var id = request.get("id");
            var method = request.get("method").asText();

            switch (method) {
                case "initialize" -> sendResponse(writer, id, Map.of("protocolVersion", "0.1.0",
                        "serverInfo", Map.of("name", serverName, "version", serverVersion)));
                case "tools/list" -> {
                    var toolDefs = dispatcher.listTools();
                    sendResponse(writer, id, Map.of("tools", toolDefs));
                }
                case "tools/call" -> {
                    var params = request.get("params");
                    var toolName = params.get("name").asText();
                    var arguments = params.has("arguments") ? params.get("arguments")
                            : json.createObjectNode();
                    try {
                        var result = dispatcher.callTool(toolName, arguments);
                        sendResponse(writer, id,
                                Map.of("content", List.of(Map.of("type", "json", "json", result))));
                    } catch (IllegalArgumentException e) {
                        sendError(writer, id, -32601, "Tool not found: " + toolName);
                    }
                }
                case "notifications/initialized" -> {
                    // No response needed for notifications
                }
                default -> sendError(writer, id, -32601, "Method not found: " + method);
            }
        } catch (Exception e) {
            log.error("Error handling MCP message: {}", line, e);
            try {
                var root = json.readTree(line);
                sendError(writer, root.get("id"), -32700, "Parse error: " + e.getMessage());
            } catch (Exception ex) {
                log.error("Could not send error response", ex);
            }
        }
    }

    private void sendResponse(PrintWriter writer, com.fasterxml.jackson.databind.JsonNode id,
            Object result) {
        try {
            var response = json.createObjectNode();
            response.set("id", id);
            response.set("result", json.valueToTree(result));
            writer.println(json.writeValueAsString(response));
            writer.flush();
        } catch (Exception e) {
            log.error("Error sending response", e);
        }
    }

    private void sendError(PrintWriter writer, com.fasterxml.jackson.databind.JsonNode id, int code,
            String message) {
        try {
            var response = json.createObjectNode();
            response.set("id", id);
            response.set("error",
                    json.createObjectNode().put("code", code).put("message", message));
            writer.println(json.writeValueAsString(response));
            writer.flush();
        } catch (Exception e) {
            log.error("Error sending error", e);
        }
    }
}
