package com.contextcanvas.server.config;

import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.contextcanvas.server.service.LlmChatService;
import com.contextcanvas.server.service.LlmChatService.ChatMessage;

/**
 * REST controller for chat interactions. Frontend sends user messages here instead of calling the
 * LLM directly. This service handles the function-calling loop with the LLM on the backend.
 *
 * Architecture: Browser → Backend (/api/chat) → LLM Provider (using ToolDispatcher for MCP tools)
 */
@RestController
@RequestMapping("/api/chat")
@CrossOrigin(origins = "*")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final LlmChatService chatService;

    public ChatController(LlmChatService chatService) {
        this.chatService = chatService;
    }

    /**
     * POST /api/chat — sends a user message to the LLM via the backend's LlmChatService. The
     * backend handles the entire function-calling loop with the LLM and ToolDispatcher.
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> chat(@RequestBody ChatRequest request) {
        var startTime = System.currentTimeMillis();
        int historySize = request.history() != null ? request.history().size() : 0;
        log.info("→ Chat request: msg=\"{}\" history={} msgs", truncate(request.message(), 80),
                historySize);

        var response = chatService.chat(request.message(), request.history());

        long elapsed = System.currentTimeMillis() - startTime;
        boolean hasA2ui = response.a2uiJson() != null && !response.a2uiJson().isEmpty();
        log.info("← Chat response: {} chars text, a2ui={}, elapsed={}ms",
                response.text() != null ? response.text().length() : 0,
                hasA2ui ? "YES (" + response.a2uiJson().length() + " bytes)" : "NO", elapsed);

        return ResponseEntity.ok(Map.of("text", response.text(), "a2uiJson",
                response.a2uiJson() != null ? response.a2uiJson() : ""));
    }

    private static String truncate(String s, int maxLen) {
        if (s == null)
            return "null";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }

    /** Request body for the chat endpoint. */
    public record ChatRequest(String message, List<ChatMessage> history) {
    }
}
