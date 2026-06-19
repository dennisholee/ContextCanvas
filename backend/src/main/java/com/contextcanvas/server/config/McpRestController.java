package com.contextcanvas.server.config;

import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.contextcanvas.server.service.ToolDispatcher;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * REST controller exposing MCP tools over HTTP. Delegates to {@link ToolDispatcher} for all tool
 * dispatch, ensuring consistency with the stdio MCP server transport.
 *
 * This is the primary transport used by the frontend's function-calling loop (see useLLM.ts).
 */
@RestController
@RequestMapping("/api/mcp")
@CrossOrigin(origins = "*")
public class McpRestController {

    private static final Logger log = LoggerFactory.getLogger(McpRestController.class);

    private final ToolDispatcher dispatcher;
    private final ObjectMapper json;

    public McpRestController(ToolDispatcher dispatcher, ObjectMapper json) {
        this.dispatcher = dispatcher;
        this.json = json;
    }

    /** GET /api/mcp/tools — returns the list of available tool definitions. */
    @GetMapping("/tools")
    public ResponseEntity<List<Map<String, Object>>> listTools() {
        return ResponseEntity.ok(dispatcher.listTools());
    }

    /** POST /api/mcp/call — invoke a tool by name with given arguments. */
    @PostMapping("/call")
    public ResponseEntity<?> callTool(@RequestBody Map<String, Object> request) {
        var toolName = (String) request.get("name");
        var arguments = request.get("arguments");

        try {
            JsonNode argsNode =
                    arguments != null ? json.valueToTree(arguments) : json.createObjectNode();
            var result = dispatcher.callTool(toolName, argsNode);
            return ResponseEntity.ok(Map.of("result", result));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error executing tool: {}", toolName, e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
}
