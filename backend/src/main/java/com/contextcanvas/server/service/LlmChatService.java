package com.contextcanvas.server.service;

import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * LLM chat service that handles the function-calling loop on the backend.
 *
 * Architecture: Frontend sends user message to this service via HTTP. This service calls the LLM
 * API with MCP tool definitions (from ToolDispatcher), handles the function-calling loop, and
 * returns the final AI response including A2UI payload.
 *
 * This eliminates the fragile frontend function-calling loop (useLLM.ts) and keeps the LLM API key
 * on the server.
 */
@Service
public class LlmChatService {

    private static final Logger log = LoggerFactory.getLogger(LlmChatService.class);

    private final ToolDispatcher toolDispatcher;
    private final ObjectMapper json;
    private final RestClient restClient;
    private final String llmModel;
    private final String llmApiKey;
    private final boolean testMode;

    private static final int MAX_LOOPS = 8;

    /**
     * Creates the chat service with the LLM provider configuration.
     *
     * @param toolDispatcher dispatcher for MCP tool invocation
     * @param json Jackson ObjectMapper for JSON processing
     * @param restClientBuilder Spring RestClient builder for LLM HTTP calls
     * @param apiUrl LLM API base URL (default: {@code https://api.openai.com/v1})
     * @param llmModel LLM model name (default: {@code gpt-4o})
     * @param llmApiKey LLM API key (from env, empty = test mode)
     * @param testMode if true, uses simulated responses instead of real LLM calls
     */
    public LlmChatService(ToolDispatcher toolDispatcher, ObjectMapper json,
            RestClient.Builder restClientBuilder,
            @Value("${contextcanvas.llm.api-url:https://api.openai.com/v1}") String apiUrl,
            @Value("${contextcanvas.llm.model:gpt-4o}") String llmModel,
            @Value("${contextcanvas.llm.api-key:}") String llmApiKey,
            @Value("${contextcanvas.llm.test-mode:false}") boolean testMode) {
        this.toolDispatcher = toolDispatcher;
        this.json = json;
        this.restClient = restClientBuilder.baseUrl(apiUrl + "/chat/completions").build();
        this.llmModel = llmModel;
        this.llmApiKey = llmApiKey;
        this.testMode = testMode;
    }

    /**
     * Sends a user message to the LLM and returns the AI response with optional A2UI payload.
     */
    public ChatResponse chat(String userMessage, List<ChatMessage> history) {
        if (testMode) {
            return simulateResponse(userMessage);
        }

        try {
            // Build messages array
            var messages = json.createArrayNode();
            messages.add(buildSystemMessage());

            for (var msg : history) {
                var node = json.createObjectNode();
                node.put("role", msg.role());
                node.put("content", msg.content());
                messages.add(node);
            }

            var userNode = json.createObjectNode();
            userNode.put("role", "user");
            userNode.put("content", userMessage);
            messages.add(userNode);

            // Build tool definitions from ToolDispatcher
            var tools = json.createArrayNode();
            for (var toolDef : toolDispatcher.listTools()) {
                var toolNode = json.createObjectNode();
                toolNode.put("type", "function");
                var fn = toolNode.putObject("function");
                fn.put("name", (String) toolDef.get("name"));
                fn.put("description", (String) toolDef.get("description"));
                fn.set("parameters", json.valueToTree(toolDef.get("parameters")));
                tools.add(toolNode);
            }
            log.debug("Built {} tool definitions for LLM", tools.size());

            // Function-calling loop
            int toolCallCount = 0;
            int loopCount = 0;
            long loopStartTime = System.currentTimeMillis();
            while (loopCount < MAX_LOOPS) {
                loopCount++;
                log.info("LLM iteration #{}/{} with {} messages in context", loopCount, MAX_LOOPS,
                        messages.size());

                var request = json.createObjectNode();
                request.put("model", llmModel);
                request.set("messages", messages);
                if (!tools.isEmpty()) {
                    request.set("tools", tools);
                }

                var response = callLlm(request);

                var choice = response.get("choices").get(0);
                var msg = choice.get("message");
                var content = msg.has("content") && !msg.get("content").isNull()
                        ? msg.get("content").asText()
                        : "";
                var toolCalls = msg.has("tool_calls") ? msg.get("tool_calls") : null;

                if (toolCalls != null && toolCalls.isArray() && !toolCalls.isEmpty()) {
                    // Add assistant message with tool_calls
                    var assistantMsg = json.createObjectNode();
                    assistantMsg.put("role", "assistant");
                    assistantMsg.set("content",
                            content.isEmpty() ? null : json.valueToTree(content));
                    assistantMsg.set("tool_calls", toolCalls);
                    messages.add(assistantMsg);

                    // Log tool calls received from LLM
                    var toolNames = new java.util.ArrayList<String>();
                    for (var tc : toolCalls) {
                        toolNames.add(tc.get("function").get("name").asText());
                    }
                    log.info("LLM returned {} tool_calls: {} (iteration #{})", toolCalls.size(),
                            toolNames, loopCount);

                    // Execute each tool and add result
                    for (var tc : toolCalls) {
                        var toolName = tc.get("function").get("name").asText();
                        var toolArgs = tc.get("function").get("arguments").asText();
                        var callId = tc.get("id").asText();

                        JsonNode argsNode;
                        try {
                            argsNode = json.readTree(toolArgs);
                        } catch (Exception e) {
                            argsNode = json.createObjectNode();
                        }

                        Object toolResult;
                        try {
                            toolResult = toolDispatcher.callTool(toolName, argsNode);
                            toolCallCount++;
                        } catch (Exception e) {
                            toolResult = Map.of("error", e.getMessage());
                            log.error("Tool execution failed: {} - {}", toolName, e.getMessage());
                        }

                        var toolResultMsg = json.createObjectNode();
                        toolResultMsg.put("role", "tool");
                        toolResultMsg.put("tool_call_id", callId);
                        toolResultMsg.put("content", json.writeValueAsString(toolResult));
                        messages.add(toolResultMsg);
                    }
                    // Continue loop for LLM to process results
                } else {
                    // No tool calls — this is the final response
                    // Extract A2UI block from the content
                    String text = content;
                    String a2uiJson = null;
                    int startIdx = content.indexOf("---A2UI_START---");
                    if (startIdx >= 0) {
                        text = content.substring(0, startIdx).trim();
                        int endIdx = content.indexOf("---A2UI_END---", startIdx);
                        if (endIdx > startIdx) {
                            a2uiJson = content
                                    .substring(startIdx + "---A2UI_START---".length(), endIdx)
                                    .trim();
                            // Validate it's parseable JSON
                            try {
                                json.readTree(a2uiJson);
                                log.info("A2UI extracted: {} bytes, component={}",
                                        a2uiJson.length(),
                                        json.readTree(a2uiJson).path("componentType").asText("?"));
                            } catch (Exception e) {
                                log.warn("Invalid A2UI JSON from LLM: {}", e.getMessage());
                                a2uiJson = null;
                            }
                        }
                    }

                    long elapsed = System.currentTimeMillis() - loopStartTime;
                    log.info(
                            "Chat complete: {} iterations, {} tool calls, {} chars text, a2ui={}, elapsed={}ms",
                            loopCount, toolCallCount, text.length(),
                            a2uiJson != null ? "YES (" + a2uiJson.length() + " bytes)" : "NO",
                            elapsed);

                    return new ChatResponse(text, a2uiJson);
                }
            }

            // Max loops exceeded
            long elapsed = System.currentTimeMillis() - loopStartTime;
            log.warn("Chat exceeded max loops ({} iterations, {} tool calls, {}ms)", MAX_LOOPS,
                    toolCallCount, elapsed);
            return new ChatResponse("I'm having trouble completing that request. Please try again.",
                    null);

        } catch (Exception e) {
            log.error("LLM chat failed", e);
            return new ChatResponse("Sorry, an error occurred: " + e.getMessage(), null);
        }
    }

    private JsonNode callLlm(ObjectNode request) {
        var response = restClient.post().headers(h -> {
            if (!llmApiKey.isEmpty()) {
                h.setBearerAuth(llmApiKey);
            }
            h.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        }).body(request).retrieve().body(JsonNode.class);

        if (response == null) {
            throw new RuntimeException("LLM returned empty response");
        }
        return response;
    }

    private JsonNode buildSystemMessage() {
        var system = json.createObjectNode();
        system.put("role", "system");
        system.put("content",
                """
                        You are ContextCanvas, an AI assistant connected to a SQLite database.

                        You have access to MCP tools that let you query the database, search for records,
                        and manage user preferences. Use these tools to answer the user's questions.

                        Always use ? placeholders in SQL for parameterized queries.

                        CRITICAL OUTPUT RULE — You MUST include an A2UI visualization block at the end of
                        every response that contains data.

                        Format:
                        Explain the results in text, then at the very end add:

                        ---A2UI_START---
                        {"componentType":"...","title":"...","data":[...]}
                        ---A2UI_END---

                        Available component types:
                        - line_chart: data = [{"label":"Jan","value":100},{"label":"Feb","value":200}]
                        - bar_chart: data = [{"label":"Tech","value":50000},{"label":"Finance","value":30000}]
                        - pie_chart: data = [{"label":"Active","value":3},{"label":"Lead","value":2}]
                        - area_chart: data = [{"date":"Jan","value":100},{"date":"Feb","value":200}]
                        - metric_card: data = {"value":450000,"unit":"USD"}
                        - data_table: data = [{"col1":"val1","col2":"val2"},{"col1":"val3","col2":"val4"}]
                        - dashboard: {"layout":"grid","components":[component1,component2,...]}
                        - confirmation_card: {"action":{"type":"create"/"update"/"delete","entity":"table","newValues":{...}}}
                        """);
        return system;
    }

    /**
     * Simulates LLM responses for testing without an API key.
     */
    private ChatResponse simulateResponse(String userMessage) {
        var lower = userMessage.toLowerCase();
        if (lower.contains("trend") || lower.contains("month")) {
            return new ChatResponse("Here's the trend data.",
                    """
                            {"componentType":"line_chart","title":"Sales Trend","data":[{"month":"Jul","revenue":45000},{"month":"Aug","revenue":52000},{"month":"Sep","revenue":48000}]}
                            """);
        }
        if (lower.contains("summary") || lower.contains("total")) {
            return new ChatResponse("Here's the summary.",
                    "{\"componentType\":\"metric_card\",\"title\":\"Total Revenue\",\"data\":{\"value\":450000,\"unit\":\"USD\"}}");
        }
        if (lower.contains("list") || lower.contains("show me")) {
            return new ChatResponse("Here are the results.",
                    "{\"componentType\":\"data_table\",\"title\":\"Clients\",\"data\":[{\"id\":1,\"name\":\"Acme Corp\",\"status\":\"active\"},{\"id\":2,\"name\":\"BetterCloud\",\"status\":\"lead\"}]}");
        }
        if (lower.contains("add") || lower.contains("create") || lower.contains("new")) {
            return new ChatResponse("I found the relevant record. Please review and confirm.",
                    "{\"componentType\":\"confirmation_card\",\"title\":\"Confirm Transaction\",\"action\":{\"type\":\"create\",\"entity\":\"sales\",\"newValues\":{\"client_id\":42,\"deal_amount\":5000,\"stage\":\"pipeline\"}}}");
        }
        if (lower.contains("dashboard") || lower.contains("overview")) {
            return new ChatResponse("Here's the full overview.",
                    "{\"componentType\":\"dashboard\",\"layout\":\"grid\",\"components\":[{\"componentType\":\"metric_card\",\"title\":\"Total Revenue\",\"data\":{\"value\":450000,\"unit\":\"USD\"}},{\"componentType\":\"data_table\",\"title\":\"Recent Deals\",\"data\":[{\"deal\":\"Q3 Contract\",\"amount\":50000},{\"deal\":\"Support Plan\",\"amount\":12000}]}]}");
        }
        return new ChatResponse("I received your request: \"" + userMessage
                + "\". In test mode, I simulate responses. Try asking for \"trends\", \"summary\", \"list all clients\", or \"dashboard\".",
                null);
    }

    /** A chat response with the AI's text and optional A2UI JSON payload. */
    public record ChatResponse(String text, String a2uiJson) {
    }

    /** A single message in the conversation history. */
    public record ChatMessage(String role, String content) {
    }
}
