package com.contextcanvas.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import com.contextcanvas.server.config.McpServer;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * End-to-end tests for all MCP tools using real SQLite database. No mocks — tests against the
 * actual Spring Boot application context.
 */
@SpringBootTest(classes = ContextCanvasApplication.class)
class AllToolsE2ETest {

    @Autowired
    private McpServer mcpServer;

    @Autowired
    private JdbcTemplate jdbc;

    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void verifySeedData() {
        var count = jdbc.queryForObject("SELECT COUNT(*) FROM clients", Integer.class);
        assertTrue(count > 0, "Seed data should be loaded by Flyway");
    }

    @Test
    void introspectSchema_returnsAllTables() {
        var args = json.createObjectNode();
        var result = mcpServer.callTool("introspect_schema", args);
        assertTrue(result.containsKey("tables"), "Should return tables key");

        @SuppressWarnings("unchecked")
        var tables = (List<Map<String, Object>>) result.get("tables");
        var tableNames = tables.stream().map(t -> (String) t.get("tableName")).toList();
        assertTrue(tableNames.contains("clients"));
        assertTrue(tableNames.contains("sales"));
        assertTrue(tableNames.contains("contacts"));
    }

    @Test
    void queryTool_returnsData() {
        var args = json.createObjectNode().put("sql", "SELECT COUNT(*) as cnt FROM clients");
        var result = mcpServer.callTool("query", args);
        assertNotNull(result);
        assertTrue(result.containsKey("rows") || result.containsKey("columns"));
    }

    @Test
    void executeTool_returnsConfirmation_notExecuted() {
        var args = json.createObjectNode()
                .put("sql", "INSERT INTO clients (company_name, status) VALUES (?, ?)")
                .set("params", json.createArrayNode().add("Test Corp").add("active"));
        var result = mcpServer.callTool("execute", args);
        assertTrue(result.containsKey("status"));
        assertEquals("PENDING_CONFIRMATION", result.get("status"));

        // Verify NOT yet inserted
        var count = jdbc.queryForObject("SELECT COUNT(*) FROM clients WHERE company_name = ?",
                Integer.class, "Test Corp");
        assertEquals(0, count);
    }

    @Test
    void ddlIsRejected() {
        var args = json.createObjectNode().put("sql", "DROP TABLE clients");
        assertThrows(Exception.class, () -> {
            mcpServer.callTool("query", args);
        });
    }

    @Test
    void sqlInjectionIsBlocked() {
        var args =
                json.createObjectNode().put("sql", "SELECT * FROM clients WHERE company_name = ?")
                        .set("params", json.createArrayNode().add("'; DROP TABLE clients; --"));
        var result = mcpServer.callTool("query", args);
        assertNotNull(result);
        // Injection is data, not SQL — query should succeed safely
        assertTrue(result.containsKey("rows"));
    }

    @Test
    void resolveEntity_findsFuzzyMatch() {
        var args = json.createObjectNode().put("name", "Acme").put("table", "clients");
        var result = mcpServer.callTool("resolve_entity", args);
        assertTrue(result.containsKey("matches"));
    }

    @Test
    void trackAndGetPreferences() {
        // Track a preference
        var trackArgs = json.createObjectNode().put("key", "test_pref").put("value", "test_value");
        mcpServer.callTool("track_preference", trackArgs);

        // Get preferences
        var getArgs = json.createObjectNode();
        var result = mcpServer.callTool("get_preferences", getArgs);
        assertTrue(result.containsKey("preferences"));
    }
}
