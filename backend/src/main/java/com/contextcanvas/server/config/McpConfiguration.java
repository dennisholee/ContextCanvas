package com.contextcanvas.server.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.contextcanvas.server.service.ToolDispatcher;

/**
 * Configures the MCP server with tool registrations. MCP uses JSON-RPC over stdin/stdout. Tools are
 * auto-discovered by ToolDispatcher via Spring DI.
 */
@Configuration
public class McpConfiguration {

    @Bean
    public McpServer mcpServer(ToolDispatcher dispatcher) {
        return new McpServer("contextcanvas-mcp", "1.0.0", dispatcher);
    }
}
