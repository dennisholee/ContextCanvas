package com.contextcanvas.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the ContextCanvas MCP server.
 * <p>
 * Bootstraps the Spring Boot application which provides:
 * <ul>
 * <li>LLM function-calling loop via {@code LlmChatService}</li>
 * <li>MCP tool dispatch via {@code ToolDispatcher}</li>
 * <li>SQLite database access with schema introspection</li>
 * <li>Safety gate for write operations (two-phase confirmation)</li>
 * <li>HTTP REST and stdio JSON-RPC transports</li>
 * </ul>
 */
@SpringBootApplication
public class ContextCanvasApplication {

    public static void main(String[] args) {
        SpringApplication.run(ContextCanvasApplication.class, args);
    }
}
